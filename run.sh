#!/bin/bash

# Скрипт для запуска приложения с правильной версией Java

export JAVA_HOME=$(/usr/libexec/java_home -v 24)
echo "Using Java: $JAVA_HOME"

./gradlew :app:run
