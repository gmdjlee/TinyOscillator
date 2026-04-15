# .claude/hooks/pre-commit-check.md
# Claude Code가 코드 생성 후 자동으로 확인할 체크리스트

## Auto-validation Rules

### For *Engine.kt files:
1. `@Inject constructor` 존재 확인
2. `suspend fun analyze(` 시그니처 존재 확인
3. 반환 타입이 해당 `*Result` data class와 일치
4. `kotlin.math.` import 확인 (외부 ML 라이브러리 import 없음)
5. 빈 리스트 체크: `if (prices.isEmpty()) return ...empty()`
6. NaN 방어: `isNaN()` 또는 `.takeIf { it.isFinite() }` 사용
7. Division by zero 방어

### For *PromptBuilder.kt files:
1. "PRE-COMPUTED" 또는 "NEVER recalculate" 문자열 포함
2. JSON 스키마 정의 포함
3. ChatML 태그 (<|im_start|>) 포함
4. temperature ≤ 0.5 설정

### For *Test.kt files:
1. 최소 3개 @Test 메서드
2. 확률 합 검증: `assertEquals(1.0, sum, 0.001)`
3. 빈 입력 테스트 존재
4. 범위 검증 (0.0..1.0)
