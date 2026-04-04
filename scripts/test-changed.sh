#!/usr/bin/env bash
# test-changed.sh — 변경된 파일에 대응하는 테스트만 실행
# 사용법: ./scripts/test-changed.sh [base_branch]
#
# 1) git diff로 변경된 .kt 소스 파일 검출
# 2) 대응하는 *Test.kt 패턴 매칭
# 3) --tests 플래그로 해당 테스트만 실행
#
# 전체 테스트 대비 5-20배 빠름

set -euo pipefail
cd "$(dirname "$0")/.."

BASE="${1:-HEAD}"
CHANGED_FILES=$(git diff --name-only "$BASE" -- 'app/src/main/**/*.kt' 2>/dev/null || true)

if [ -z "$CHANGED_FILES" ]; then
    # 스테이징 안 된 변경 포함
    CHANGED_FILES=$(git diff --name-only -- 'app/src/main/**/*.kt' 2>/dev/null || true)
fi

if [ -z "$CHANGED_FILES" ]; then
    echo "No changed Kotlin source files detected."
    exit 0
fi

# 소스 → 테스트 클래스명 매핑
TEST_PATTERNS=""
while IFS= read -r file; do
    [ -z "$file" ] && continue
    # 패키지 경로에서 FQCN 추출
    basename=$(basename "$file" .kt)
    # 해당 클래스의 테스트 패턴
    TEST_PATTERNS="$TEST_PATTERNS --tests *${basename}Test"
    TEST_PATTERNS="$TEST_PATTERNS --tests *${basename}EdgeCaseTest"
done <<< "$CHANGED_FILES"

if [ -z "$TEST_PATTERNS" ]; then
    echo "No matching test patterns found."
    exit 0
fi

echo "=== Running tests for changed files ==="
echo "$CHANGED_FILES" | head -20
echo "=== Test patterns ==="
echo "$TEST_PATTERNS" | tr ' ' '\n' | grep "tests" | head -20
echo "========================"

# shellcheck disable=SC2086
./gradlew :app:testDebugUnitTest \
    $TEST_PATTERNS \
    --build-cache \
    -x :app:generateDebugAssets \
    --fail-fast \
    2>&1
