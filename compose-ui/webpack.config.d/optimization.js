// Оптимизация и подавление warnings

// Подавляем warnings о размере бандла
config.performance = {
    hints: false
};

// Настраиваем stats для уменьшения вывода
config.stats = {
    ...config.stats,
    warnings: false,
    // Показываем только ошибки
    all: false,
    errors: true,
    errorDetails: true,
    // Скрываем детали модулей
    modules: false,
    moduleTrace: false,
    // Скрываем детали assets
    assets: false,
    assetsSort: '!size',
    // Показываем только summary
    timings: true,
    builtAt: true
};

// Настраиваем infrastructureLogging для уменьшения вывода
config.infrastructureLogging = {
    level: 'error'
};

