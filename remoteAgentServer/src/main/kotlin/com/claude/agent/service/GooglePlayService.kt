package com.claude.agent.service

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.LocalizedText
import com.google.api.services.androidpublisher.model.TrackRelease
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

/**
 * Сервис для публикации Android приложений в Google Play Console
 */
class GooglePlayService(
    private val serviceAccountKeyPath: String
) {
    private val logger = LoggerFactory.getLogger(GooglePlayService::class.java)
    private val applicationName = "AI Agent"

    private val androidPublisher: AndroidPublisher by lazy {
        try {
            logger.info("🔐 Инициализация Google Play Publisher API")

            // Загружаем Service Account credentials
            val credentials: GoogleCredentials = FileInputStream(serviceAccountKeyPath).use { inputStream ->
                ServiceAccountCredentials.fromStream(inputStream)
                    .createScoped(listOf("https://www.googleapis.com/auth/androidpublisher"))
            }

            // Создаем HTTP transport с увеличенными таймаутами для загрузки больших AAB файлов
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

            // Создаем HTTP request initializer с увеличенными таймаутами
            val requestInitializer = HttpRequestInitializer { request: HttpRequest ->
                // Применяем credentials
                HttpCredentialsAdapter(credentials).initialize(request)

                // Настраиваем таймауты для загрузки больших файлов (13+ MB)
                request.connectTimeout = 60_000  // 60 секунд на подключение
                request.readTimeout = 300_000    // 300 секунд (5 минут) на чтение

                logger.debug("⏱️ Request timeouts: connect=${request.connectTimeout}ms, read=${request.readTimeout}ms")
            }

            logger.info("⏱️ Настройка таймаутов для загрузки больших AAB файлов:")
            logger.info("  - Connect timeout: 60 секунд")
            logger.info("  - Read timeout: 300 секунд (5 минут)")

            // Создаем Android Publisher сервис
            AndroidPublisher.Builder(
                httpTransport,
                GsonFactory.getDefaultInstance(),
                requestInitializer
            )
                .setApplicationName(applicationName)
                .build()
                .also {
                    logger.info("✅ Google Play Publisher API инициализирован")
                }
        } catch (e: Exception) {
            logger.error("❌ Ошибка инициализации Google Play API: ${e.message}", e)
            throw RuntimeException("Не удалось инициализировать Google Play API: ${e.message}", e)
        }
    }

    /**
     * Публикует AAB файл в Google Play Console
     *
     * @param packageName Package name приложения (например, com.example.app)
     * @param aabFilePath Путь к AAB файлу
     * @param versionCode Версия кода (целое число, должно быть уникально)
     * @param versionName Версия приложения (строка, например "1.0.0")
     * @param releaseNotesMap Заметки к релизу на разных языках (ключ - код языка, значение - текст)
     * @param track Канал распространения (internal, alpha, beta, production)
     * @param rolloutPercent Процент пользователей для постепенного раскрытия (null = 100%)
     * @return ID редакции и версионный код
     */
    fun publishToGooglePlay(
        packageName: String,
        aabFilePath: String,
        versionCode: Long,
        versionName: String,
        releaseNotesMap: Map<String, String>,
        track: String = "internal",
        rolloutPercent: Double? = null
    ): PublishResult {
        try {
            logger.info("📦 Начинаем публикацию в Google Play")
            logger.info("Package: $packageName")
            logger.info("AAB file: $aabFilePath")
            logger.info("Version: $versionName ($versionCode)")
            logger.info("Track: $track")

            // Проверяем существование файла
            val aabFile = File(aabFilePath)
            if (!aabFile.exists()) {
                throw IllegalArgumentException("AAB файл не найден: $aabFilePath")
            }

            // 1. Создаем новую редакцию (edit)
            logger.info("1️⃣ Создаем новую редакцию...")
            val editRequest = androidPublisher.edits().insert(packageName, null)
            val edit = editRequest.execute()
            val editId = edit.id
            logger.info("✅ Редакция создана: $editId")

            try {
                // 2. Загружаем AAB файл
                logger.info("2️⃣ Загружаем AAB файл (${aabFile.length() / 1024 / 1024} MB)...")
                val aabContent = FileContent("application/octet-stream", aabFile)

                // Настраиваем таймауты для загрузки больших файлов
                val uploadRequest = androidPublisher.edits().bundles()
                    .upload(packageName, editId, aabContent)

                // Увеличиваем таймауты для загрузки больших AAB файлов
                // Connect timeout: 60 секунд, Read timeout: 300 секунд (5 минут)
                uploadRequest.mediaHttpUploader?.apply {
                    isDirectUploadEnabled = false  // Используем resumable upload для больших файлов
                    chunkSize = 2 * 1024 * 1024  // 2 MB chunks
                }

                logger.info("⏱️ Таймауты для загрузки: connect=60s, read=300s, resumable upload enabled")
                val bundleUpload = uploadRequest.execute()

                logger.info("✅ AAB загружен, версия: ${bundleUpload.versionCode}")

                // 3. Создаем release notes
                val localizedNotes = releaseNotesMap.map { (locale, text) ->
                    LocalizedText().apply {
                        language = locale
                        this.text = text
                    }
                }

                // 4. Создаем релиз
                logger.info("3️⃣ Создаем релиз на треке '$track'...")
                val trackRelease = TrackRelease().apply {
                    name = versionName
                    versionCodes = listOf(bundleUpload.versionCode.toLong())
                    status = if (rolloutPercent != null && rolloutPercent < 1.0) "inProgress" else "completed"
                    this.releaseNotes = localizedNotes

                    // Настраиваем rollout если нужно
                    if (rolloutPercent != null && rolloutPercent < 1.0) {
                        userFraction = rolloutPercent
                    }
                }

                val trackUpdate = com.google.api.services.androidpublisher.model.Track().apply {
                    this.track = track
                    releases = listOf(trackRelease)
                }

                androidPublisher.edits().tracks()
                    .update(packageName, editId, track, trackUpdate)
                    .execute()

                logger.info("✅ Релиз создан")

                // 5. Применяем изменения (commit)
                logger.info("4️⃣ Применяем изменения...")
                val committedEdit = androidPublisher.edits().commit(packageName, editId).execute()

                logger.info("🎉 Публикация успешно завершена!")
                logger.info("Edit ID: ${committedEdit.id}")

                return PublishResult(
                    success = true,
                    editId = committedEdit.id,
                    versionCode = bundleUpload.versionCode.toLong(),
                    track = track,
                    message = "Successfully published version $versionName (${bundleUpload.versionCode}) to $track track"
                )

            } catch (e: Exception) {
                // Если что-то пошло не так, отменяем редакцию
                logger.warn("⚠️ Ошибка, откатываем редакцию...")
                try {
                    androidPublisher.edits().delete(packageName, editId).execute()
                    logger.info("✅ Редакция отменена")
                } catch (deleteError: Exception) {
                    logger.error("❌ Не удалось отменить редакцию: ${deleteError.message}")
                }
                throw e
            }

        } catch (e: GoogleJsonResponseException) {
            logger.error("❌ Google Play API Error: ${e.details?.message}")
            logger.error("Status: ${e.statusCode}, Reason: ${e.details?.code}")

            return PublishResult(
                success = false,
                editId = null,
                versionCode = null,
                track = track,
                message = "Google Play API error: ${e.details?.message ?: e.message}",
                error = e.details?.message ?: e.message
            )
        } catch (e: Exception) {
            logger.error("❌ Unexpected error during publication: ${e.message}", e)

            return PublishResult(
                success = false,
                editId = null,
                versionCode = null,
                track = track,
                message = "Publication failed: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * Результат публикации
     */
    data class PublishResult(
        val success: Boolean,
        val editId: String?,
        val versionCode: Long?,
        val track: String,
        val message: String,
        val error: String? = null
    )
}