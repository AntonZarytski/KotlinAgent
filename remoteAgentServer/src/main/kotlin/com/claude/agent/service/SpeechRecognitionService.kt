package com.claude.agent.service

import org.slf4j.LoggerFactory
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes

/**
 * Сервис для распознавания речи с использованием Vosk
 * 
 * Поддерживает:
 * - Русский и английский языки
 * - Автоматическое определение языка
 * - Потоковое распознавание
 * - Offline работу
 */
class SpeechRecognitionService(
    private val modelPath: String,
    private val sampleRate: Float = 16000f
) {
    private val logger = LoggerFactory.getLogger(SpeechRecognitionService::class.java)
    
    // Vosk модель загружается один раз при инициализации
    private val model: Model by lazy {
        logger.info("Загрузка Vosk модели из: $modelPath")
        val modelDir = File(modelPath)
        
        if (!modelDir.exists()) {
            throw IllegalStateException(
                "Vosk модель не найдена по пути: $modelPath. " +
                "Скачайте модель с https://alphacephei.com/vosk/models и распакуйте в указанную директорию."
            )
        }
        
        Model(modelPath).also {
            logger.info("✅ Vosk модель успешно загружена")
        }
    }
    
    /**
     * Конвертирует WebM файл в WAV формат (16kHz, mono, 16-bit PCM)
     *
     * @param inputFile Входной WebM файл
     * @return Сконвертированный WAV файл
     * @throws SpeechRecognitionException если конвертация не удалась
     */
    private fun convertWebMToWav(inputFile: File): File {
	        var outputFile: File? = null
	        try {
            // На практике вход может быть не только WebM (например, ogg/mp4), но мы все равно
            // пытаемся привести аудио к требуемому Vosk WAV PCM16 16kHz mono.
	            logger.info(
	                "Конвертация аудио → WAV (FFmpeg/JAVE2): ${inputFile.name} (${inputFile.length()} байт)"
	            )
	            val inHeader = readFilePrefix(inputFile, 16)
	            logger.info("Заголовок входа для конвертации (первые ${inHeader.size} байт): ${toHex(inHeader)}")

            // Создаем временный файл для WAV
	            outputFile = File.createTempFile("converted_", ".wav")

            // Настройки аудио для WAV
            val audio = AudioAttributes()
            audio.setCodec("pcm_s16le")  // 16-bit PCM
            audio.setChannels(1)          // mono
            audio.setSamplingRate(16000)  // 16kHz
            audio.setBitRate(256000)      // битрейт

            // Настройки кодирования
            val attrs = EncodingAttributes()
            attrs.setOutputFormat("wav")
            attrs.setAudioAttributes(audio)

            // Выполняем конвертацию
            val encoder = Encoder()
	            encoder.encode(MultimediaObject(inputFile), outputFile, attrs)

	            val outHeader = readFilePrefix(outputFile, 16)
	            logger.info("Заголовок результата конвертации (первые ${outHeader.size} байт): ${toHex(outHeader)}")

            logger.info("✅ Конвертация завершена: ${outputFile.name} (${outputFile.length()} байт)")
            return outputFile

        } catch (e: Exception) {
            logger.error("Ошибка конвертации WebM → WAV: ${e.message}", e)
	            try {
	                outputFile?.delete()
	            } catch (_: Exception) {
	                // ignore
	            }
            throw SpeechRecognitionException("Не удалось сконвертировать аудио в WAV формат: ${e.message}", e)
        }
    }

	    private fun readFilePrefix(file: File, maxLen: Int = 16): ByteArray {
	        if (!file.exists() || maxLen <= 0) return ByteArray(0)
	        file.inputStream().use { input ->
	            val buf = ByteArray(maxLen)
	            val read = input.read(buf)
	            return if (read <= 0) ByteArray(0) else buf.copyOf(read)
	        }
	    }

	    private fun toHex(bytes: ByteArray): String =
	        bytes.joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }

	    private fun hasRiffWaveHeader(bytes: ByteArray): Boolean =
	        bytes.size >= 12 &&
	            bytes[0] == 'R'.code.toByte() &&
	            bytes[1] == 'I'.code.toByte() &&
	            bytes[2] == 'F'.code.toByte() &&
	            bytes[3] == 'F'.code.toByte() &&
	            bytes[8] == 'W'.code.toByte() &&
	            bytes[9] == 'A'.code.toByte() &&
	            bytes[10] == 'V'.code.toByte() &&
	            bytes[11] == 'E'.code.toByte()

    /**
     * Извлекает сырые PCM данные из WAV файла (пропускает заголовок)
     *
     * @param audioData Полный WAV файл (с заголовком)
     * @return Сырые PCM данные (только содержимое 'data' chunk)
     * @throws SpeechRecognitionException если не удалось найти 'data' chunk
     */
    private fun extractPcmData(audioData: ByteArray): ByteArray {
        if (audioData.size < 44) {
            throw SpeechRecognitionException("WAV файл слишком маленький (минимум 44 байта)")
        }

        // Ищем 'data' chunk в WAV файле
        // Формат: RIFF [size] WAVE [chunks...]
        // Каждый chunk: [4 bytes ID][4 bytes size][data...]
        var offset = 12 // Пропускаем "RIFF" + size + "WAVE"

        while (offset + 8 <= audioData.size) {
            val chunkId = String(audioData.sliceArray(offset until offset + 4))
            val chunkSize = ByteBuffer.wrap(audioData, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int

            if (chunkId == "data") {
                // Нашли data chunk!
                val dataStart = offset + 8
                val dataEnd = minOf(dataStart + chunkSize, audioData.size)
                logger.debug("Найден 'data' chunk: offset=$dataStart, size=$chunkSize байт")
                return audioData.sliceArray(dataStart until dataEnd)
            }

            // Переходим к следующему chunk
            offset += 8 + chunkSize
        }

        // Если не нашли 'data' chunk, возвращаем всё после стандартного заголовка (44 байта)
        logger.warn("'data' chunk не найден, используем offset=44 (стандартный WAV заголовок)")
        return audioData.sliceArray(44 until audioData.size)
    }

    /**
     * Валидирует формат WAV файла
     *
     * @param audioData Аудио данные
     * @throws SpeechRecognitionException если формат не соответствует требованиям
     */
    private fun validateWavFormat(audioData: ByteArray) {
        if (audioData.size < 44) {
            throw SpeechRecognitionException("Файл слишком маленький для WAV формата (минимум 44 байта)")
        }

        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        // Проверяем RIFF заголовок
        val riffHeader = String(audioData.copyOfRange(0, 4))
        if (riffHeader != "RIFF") {
            throw SpeechRecognitionException("Неверный формат файла: ожидается WAV (RIFF заголовок не найден)")
        }

        // Проверяем WAVE формат
        val waveFormat = String(audioData.copyOfRange(8, 12))
        if (waveFormat != "WAVE") {
            throw SpeechRecognitionException("Неверный формат файла: ожидается WAV (WAVE формат не найден)")
        }

        // Ищем fmt chunk
        var offset = 12
        while (offset < audioData.size - 8) {
            val chunkId = String(audioData.copyOfRange(offset, offset + 4))
            val chunkSize = buffer.getInt(offset + 4)

            if (chunkId == "fmt ") {
                // Читаем параметры формата
                val audioFormat = buffer.getShort(offset + 8).toInt()
                val numChannels = buffer.getShort(offset + 10).toInt()
                val sampleRateValue = buffer.getInt(offset + 12)
                val bitsPerSample = buffer.getShort(offset + 22).toInt()

                // Валидация параметров
                if (audioFormat != 1) {
                    throw SpeechRecognitionException("Неподдерживаемый аудио формат: ожидается PCM (код 1), получен код $audioFormat")
                }

                if (numChannels != 1) {
                    throw SpeechRecognitionException("Неподдерживаемое количество каналов: ожидается моно (1 канал), получено $numChannels")
                }

                if (sampleRateValue != 16000) {
                    throw SpeechRecognitionException("Неподдерживаемая частота дискретизации: ожидается 16000 Hz, получено $sampleRateValue Hz")
                }

                if (bitsPerSample != 16) {
                    throw SpeechRecognitionException("Неподдерживаемая разрядность: ожидается 16 бит, получено $bitsPerSample бит")
                }

                logger.debug("✅ WAV формат валиден: $sampleRateValue Hz, $numChannels канал(ов), $bitsPerSample бит")
                return
            }

            offset += 8 + chunkSize
        }

        throw SpeechRecognitionException("Не найден fmt chunk в WAV файле")
    }

    /**
     * Вычисляет длительность аудио в секундах
     */
    private fun calculateAudioDuration(audioData: ByteArray): Double {
        if (audioData.size < 44) return 0.0

        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        // Ищем data chunk
        var offset = 12
        while (offset < audioData.size - 8) {
            val chunkId = String(audioData.copyOfRange(offset, offset + 4))
            val chunkSize = buffer.getInt(offset + 4)

            if (chunkId == "data") {
                // Длительность = размер данных / (частота * каналы * байты на сэмпл)
                val duration = chunkSize.toDouble() / (16000.0 * 1 * 2) // 16kHz, mono, 16-bit
                logger.debug("Длительность аудио: ${"%.2f".format(duration)} сек")
                return duration
            }

            offset += 8 + chunkSize
        }

        return 0.0
    }

    /**
     * Распознает речь из аудио данных
     *
     * @param audioData Аудио данные в формате WAV (16kHz, mono, 16-bit PCM)
     * @param timeoutMs Таймаут распознавания в миллисекундах (по умолчанию 60 секунд)
     * @param maxDurationSeconds Максимальная длительность аудио в секундах (по умолчанию 60 секунд)
     * @return Распознанный текст
     */
    suspend fun recognize(
        audioData: ByteArray,
        timeoutMs: Long = 60000,
        maxDurationSeconds: Double = 60.0
    ): String = withContext(Dispatchers.IO) {
        try {
            logger.debug("Начало распознавания аудио (${audioData.size} байт)")

            // Валидация формата
            validateWavFormat(audioData)

            // Проверка длительности
            val duration = calculateAudioDuration(audioData)
            if (duration > maxDurationSeconds) {
                throw SpeechRecognitionException(
                    "Аудио слишком длинное: ${"%.2f".format(duration)} сек (максимум $maxDurationSeconds сек)"
                )
            }

            // Извлекаем сырые PCM данные (пропускаем WAV заголовок)
            val pcmData = extractPcmData(audioData)
            logger.debug("Извлечено PCM данных: ${pcmData.size} байт (из ${audioData.size} байт WAV)")

            // Распознавание с таймаутом
            withTimeout(timeoutMs) {
                // Создаем распознаватель для этого аудио
                val recognizer = Recognizer(model, sampleRate)

                // Обрабатываем сырые PCM данные (БЕЗ WAV заголовка!)
                val inputStream = ByteArrayInputStream(pcmData)
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    recognizer.acceptWaveForm(buffer, bytesRead)
                }

                // Получаем финальный результат
                val resultJson = recognizer.finalResult
                logger.debug("Результат распознавания: $resultJson")

                // Парсим JSON результат
                val result = Json.parseToJsonElement(resultJson).jsonObject
                val text = result["text"]?.jsonPrimitive?.content ?: ""

                logger.info("Распознан текст: '$text'")
                text
            }

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error("Таймаут распознавания речи (${timeoutMs}ms)")
            throw SpeechRecognitionException("Превышено время ожидания распознавания речи (${timeoutMs / 1000} сек)")
        } catch (e: SpeechRecognitionException) {
            throw e
        } catch (e: Exception) {
            logger.error("Ошибка распознавания речи: ${e.message}", e)
            throw SpeechRecognitionException("Не удалось распознать речь: ${e.message}", e)
        }
    }
    
    /**
     * Распознает речь из аудио файла (поддерживает WebM и WAV форматы)
     *
     * @param audioFile Путь к аудио файлу (WebM или WAV)
     * @param timeoutMs Таймаут распознавания в миллисекундах (по умолчанию 60 секунд)
     * @param maxDurationSeconds Максимальная длительность аудио в секундах (по умолчанию 60 секунд)
     * @return Распознанный текст
     */
    suspend fun recognizeFromFile(
        audioFile: File,
        timeoutMs: Long = 60000,
        maxDurationSeconds: Double = 60.0
    ): String {
        if (!audioFile.exists()) {
            throw IllegalArgumentException("Аудио файл не найден: ${audioFile.absolutePath}")
        }

	        logger.info(
	            "Распознавание из файла: ${audioFile.name} (${audioFile.length()} байт), path=${audioFile.absolutePath}"
	        )

        // Определяем формат файла по содержимому
        val audioData = audioFile.readBytes()
	        val headerLen = minOf(audioData.size, 16)
	        val headerHex = toHex(audioData.take(headerLen).toByteArray())
	        logger.info("Заголовок входного аудио (первые $headerLen байт): $headerHex")

        // Kotlin Byte знаковый, поэтому сравниваем как unsigned (and 0xFF)
        val isWebM = audioData.size >= 4 &&
            (audioData[0].toInt() and 0xFF) == 0x1A &&
            (audioData[1].toInt() and 0xFF) == 0x45 &&
            (audioData[2].toInt() and 0xFF) == 0xDF &&
            (audioData[3].toInt() and 0xFF) == 0xA3

        val isWav = audioData.size >= 12 &&
            audioData[0] == 'R'.code.toByte() &&
            audioData[1] == 'I'.code.toByte() &&
            audioData[2] == 'F'.code.toByte() &&
            audioData[3] == 'F'.code.toByte() &&
            audioData[8] == 'W'.code.toByte() &&
            audioData[9] == 'A'.code.toByte() &&
            audioData[10] == 'V'.code.toByte() &&
            audioData[11] == 'E'.code.toByte()

        // Если это WebM (EBML) — точно конвертируем.
        // Если это не WAV — тоже пробуем конвертацию (иначе validateWavFormat все равно упадет).
        val needConversion = isWebM || !isWav
	        logger.info("Детект формата: isWebM=$isWebM, isWav=$isWav, needConversion=$needConversion")
        val wavFile = if (needConversion) {
            if (isWebM) {
                logger.info("Обнаружен WebM/EBML формат, выполняется конвертация в WAV...")
            } else {
                logger.info("Вход не похож на WAV (RIFF/WAVE не найден), пробуем конвертацию в WAV...")
            }
            convertWebMToWav(audioFile)
        } else audioFile

        try {
            val wavData = wavFile.readBytes()
	            val wavHeaderLen = minOf(wavData.size, 16)
	            logger.info(
	                "Заголовок WAV-кандидата (первые $wavHeaderLen байт): ${toHex(wavData.take(wavHeaderLen).toByteArray())}"
	            )
	            if (!hasRiffWaveHeader(wavData)) {
	                throw SpeechRecognitionException(
	                    "Конвертация не дала WAV (нет RIFF/WAVE). " +
	                        "inputHeader=$headerHex, outputHeader=${toHex(wavData.take(wavHeaderLen).toByteArray())}, " +
	                        "outputSize=${wavData.size}"
	                )
	            }

            // 🔍 DEBUG: Сохраняем WAV для отладки (если распознавание вернёт пустой текст)
            val debugWavPath = "/tmp/debug_vosk_${System.currentTimeMillis()}.wav"
            File(debugWavPath).writeBytes(wavData)
            logger.info("🔍 DEBUG: WAV сохранён для отладки: $debugWavPath")

            val recognizedText = recognize(wavData, timeoutMs, maxDurationSeconds)

            // Если текст пустой, оставляем файл для анализа
            if (recognizedText.isBlank()) {
                logger.warn("⚠️ Vosk вернул пустой текст! WAV файл сохранён: $debugWavPath")
                logger.warn("   Проверьте файл вручную: ffplay $debugWavPath")
            } else {
                // Удаляем debug файл, если распознавание успешно
                File(debugWavPath).delete()
            }

            return recognizedText
        } finally {
            // Удаляем временный WAV файл, если он был создан
            if (wavFile != audioFile) {
                wavFile.delete()
                logger.debug("Временный WAV файл удален: ${wavFile.name}")
            }
        }
    }
    
    /**
     * Проверяет, загружена ли модель
     */
    fun isModelLoaded(): Boolean {
        return try {
            model
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Возвращает информацию о модели
     */
    fun getModelInfo(): String {
        return "Vosk model at: $modelPath, sample rate: $sampleRate Hz"
    }
}

/**
 * Исключение при ошибке распознавания речи
 */
class SpeechRecognitionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Результат распознавания речи
 */
data class RecognitionResult(
    val text: String,
    val confidence: Double = 1.0,
    val language: String = "unknown"
)

