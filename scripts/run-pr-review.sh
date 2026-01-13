#!/bin/bash

set -e

# Скрипт для запуска AI ревью Pull Request
# Используется в GitHub Actions или может быть запущен локально

echo "🤖 Starting AI PR Review..."

# Проверяем необходимые переменные окружения
if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "❌ Error: ANTHROPIC_API_KEY environment variable is not set"
    exit 1
fi

if [ -z "$PR_BRANCH" ]; then
    echo "❌ Error: PR_BRANCH environment variable is not set"
    exit 1
fi

BASE_BRANCH=${BASE_BRANCH:-main}
PR_NUMBER=${PR_NUMBER:-}
GITHUB_TOKEN=${GITHUB_TOKEN:-}
REPO_OWNER=${REPO_OWNER:-}
REPO_NAME=${REPO_NAME:-}

echo "📊 PR Information:"
echo "  Branch: $PR_BRANCH → $BASE_BRANCH"
echo "  PR Number: $PR_NUMBER"
echo "  Repository: $REPO_OWNER/$REPO_NAME"

# Компилируем Kotlin CLI инструмент для ревью
echo "🔨 Building PR review tool..."
./gradlew :remoteAgentServer:shadowJar

# Запускаем ревью
echo "🔍 Running AI code review..."
java -jar remoteAgentServer/build/libs/remoteAgentServer-all.jar \
  review-pr \
  --branch "$PR_BRANCH" \
  --base-branch "$BASE_BRANCH" \
  --output pr-review-report.md \
  ${PR_TITLE:+--pr-title "$PR_TITLE"}

if [ ! -f pr-review-report.md ]; then
    echo "❌ Error: Review report was not generated"
    exit 1
fi

echo "✅ Review completed successfully"
cat pr-review-report.md

# Публикуем результат в PR если доступен GitHub API
if [ -n "$GITHUB_TOKEN" ] && [ -n "$PR_NUMBER" ] && [ -n "$REPO_OWNER" ] && [ -n "$REPO_NAME" ]; then
    echo "📤 Publishing review to GitHub PR..."
    
    # Экранируем markdown для JSON
    REVIEW_BODY=$(cat pr-review-report.md | jq -Rs .)
    
    # Создаем комментарий в PR
    curl -X POST \
      -H "Authorization: token $GITHUB_TOKEN" \
      -H "Accept: application/vnd.github.v3+json" \
      "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/issues/$PR_NUMBER/comments" \
      -d "{\"body\": $REVIEW_BODY}"
    
    if [ $? -eq 0 ]; then
        echo "✅ Review published to PR #$PR_NUMBER"
    else
        echo "⚠️ Failed to publish review to GitHub"
    fi
else
    echo "ℹ️ GitHub publishing skipped (missing credentials or PR number)"
fi

echo "🎉 AI PR Review completed!"
