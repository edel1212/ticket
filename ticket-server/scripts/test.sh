#!/bin/bash
# 테스트 실행 후 요약 결과만 출력합니다.
# 전체 Gradle 출력 대신 통과/실패 수만 표시해 Claude Code 컨텍스트 토큰을 절약합니다.
#
# 사용법:
#   ./scripts/test.sh                        # 전체 테스트
#   ./scripts/test.sh --tests "com.yoo.*.X"  # 단일 클래스

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

# 테스트 실행 (출력은 임시 파일에 저장)
TMPFILE=$(mktemp)
./gradlew test "$@" >"$TMPFILE" 2>&1
EXIT_CODE=$?

# XML 리포트에서 집계 (Gradle 생성 경로)
REPORT_DIR="build/test-results/test"
if [ -d "$REPORT_DIR" ]; then
    TOTAL=$(grep -rh 'testsuite' "$REPORT_DIR"/*.xml 2>/dev/null \
        | grep -o 'tests="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')
    FAILED=$(grep -rh 'testsuite' "$REPORT_DIR"/*.xml 2>/dev/null \
        | grep -o 'failures="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')
    ERRORS=$(grep -rh 'testsuite' "$REPORT_DIR"/*.xml 2>/dev/null \
        | grep -o 'errors="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')
    SKIPPED=$(grep -rh 'testsuite' "$REPORT_DIR"/*.xml 2>/dev/null \
        | grep -o 'skipped="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')

    echo "================================"
    echo "테스트 결과: 전체 $TOTAL | 통과 $((TOTAL - FAILED - ERRORS - SKIPPED)) | 실패 $FAILED | 오류 $ERRORS | 스킵 $SKIPPED"
    echo "================================"
fi

# 빌드 성공/실패 한 줄 요약
if [ $EXIT_CODE -eq 0 ]; then
    echo "BUILD SUCCESSFUL"
else
    echo "BUILD FAILED"
    echo ""
    echo "--- 실패한 테스트 ---"
    grep -h 'testcase.*failure\|FAILED' "$REPORT_DIR"/*.xml 2>/dev/null \
        | grep -o 'name="[^"]*"' | sed 's/name=//;s/"//g' || true
    echo ""
    echo "--- Gradle 오류 출력 (마지막 40줄) ---"
    tail -40 "$TMPFILE"
fi

rm -f "$TMPFILE"
exit $EXIT_CODE
