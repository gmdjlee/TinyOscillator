package com.tinyoscillator.feature.bearsignal.presentation

/**
 * T9(Jade Terminal P3) 반응형 재편 — BearSignal 세부 7섹션 식별자.
 *
 * 폰(COMPACT)에서는 각 섹션이 [com.tinyoscillator.presentation.common.AccordionCard] 하나로,
 * 태블릿/폴더블(MEDIUM·EXPANDED)에서는 좌측 마스터 목록의 한 행 + 우측 상세 페인으로 렌더된다.
 * [title]/[subtitle]은 아코디언 헤더·목록 행·상세 헤더가 공유한다.
 *
 * **순수 표시 계층 열거형** — 스코어링/임계치와 무관하며 어떤 도메인 계산에도 참여하지 않는다.
 */
enum class BearSignalSectionKey(val title: String, val subtitle: String) {
    TREND("국면 추이", "스코어 이력과 전이 로그"),
    LEADING("선행 신호", "위험선호가 어디까지 식었나 · 온도계 3종"),
    COUNTRY("국가별 수익률", "글로벌 지수 이동평균 이탈 현황"),
    GATE("방아쇠 · 증폭", "금리(결정타)와 집중(증폭 계수)"),
    AI_SUGGEST("AI 제안", "웹/LLM 데이터 갱신 · 승인 필요(§4.5)"),
    TYPES("유형 진단", "약세장 3유형과 회복 가능성 · 주도주 하락세 판단"),
    HISTORY("역사 검증", "최악의 조합 · 3충격 동시 결합"),
}
