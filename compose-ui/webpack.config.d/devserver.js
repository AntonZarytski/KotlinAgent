// Настройка webpack dev server
config.devServer = {
    ...config.devServer,
    // Отключаем автоматическое открытие браузера
    open: false,
    // Настраиваем порт (по умолчанию 8080, но можно изменить)
    port: 8080,
    // Отключаем hot module replacement для стабильности
    hot: false,
    // Настраиваем прокси для API запросов к серверу
    proxy: {
        '/api': {
            target: 'http://localhost:8001',
            changeOrigin: true,
            secure: false
        },
        '/ws': {
            target: 'ws://localhost:8001',
            ws: true,
            changeOrigin: true
        }
    }
};

// Отключаем performance warnings для больших бандлов
config.performance = {
    ...config.performance,
    hints: false,
    maxAssetSize: 15000000,
    maxEntrypointSize: 15000000
};

// Настраиваем оптимизацию для уменьшения размера бандла
if (config.mode === 'production') {
    config.optimization = {
        ...config.optimization,
        minimize: true,
        usedExports: true,
        sideEffects: false
    };
}

