package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 정적 참조 데이터 — 약세장 3유형·역사 검증·지표 매핑·면책 (TASK.md §3.7, 부록 B #5·#6·#9, 부록 C).
 *
 * 프로토타입 `bear_signal_dashboard.jsx`의 `TYPES` 상수 + 역사 검증 섹션 + 푸터 문구를 값 그대로
 * 이관한다(§0 "기능 무손실" — 1:1 이식). 순수 정적 데이터이며 §3 스코어링 SSOT와 무관하므로
 * 변경 시에도 골든 테스트 영향 없음.
 */
object BearSignalStaticContent {

    /** 약세장 3유형 (프로토타입 `TYPES` 배열과 순서·값 동일) */
    val TYPES: List<BearType> = listOf(
        BearType(
            index = 0,
            title = "경쟁 · 역전",
            axis = "이익 훼손",
            recoveryLabel = "회복 가능성 최저",
            recoveryOutlook = RecoveryOutlook.LOWEST,
            theory = "크리스텐슨 파괴적 혁신 · 슘페터 창조적 파괴",
            cases = "노키아(스마트폰 오판, 5년 내 한 자릿수) · 인텔(파운드리 주도권 상실, 2025 배당 중단) · 팬택 · LG 스마트폰",
            why = "전방 수요는 견조하나 기술 전환 실패·후발 추격으로 1위 자리를 내줌. 점유율→가격결정력→마진이 영구 손상.",
            monitor = listOf(
                "CXMT·YMTC 양산 규모 / 수율 안정화 / 세대 격차 축소",
                "마이크론 HBM 점유율 변화 · 차세대(HBM4) 진척도",
                "미국 정부 보조금·지분 참여 강도",
                "엔비디아 공급망 다변화 정책 속도"
            )
        ),
        BearType(
            index = 1,
            title = "전방 수요 · 사이클",
            axis = "이익 훼손",
            recoveryLabel = "회복 가능성 중간",
            recoveryOutlook = RecoveryOutlook.MEDIUM,
            theory = "거미집 이론 · 챈슬러 자본 사이클",
            cases = "한진해운(발틱운임 12,000→700, 4년 연속 적자, 2017 파산) · 에릭손(통신버블, 고점比 −90%) · 국내 조선",
            why = "기업 경쟁력은 유지되나 전방 수요·가격 사이클이 붕괴. 호황기 증설이 부메랑(시차의 함정). " +
                "사이클 반전 시 재기 가능하나 버틸 체력 필요.",
            monitor = listOf(
                "HBM 연간 계약 유지 여부 · 평균판매가(ASP) 변화",
                "범용 D램·낸드 고정거래가 / 현물가 추이",
                "북미 AI 데이터센터 전력 확보 현황 · 완공 일정",
                "글로벌 PC·스마트폰 실제 출하량 전망"
            )
        ),
        BearType(
            index = 2,
            title = "밸류에이션 · 금리",
            axis = "멀티플 수축",
            recoveryLabel = "펀더멘털 생존 · 인내 필요",
            recoveryOutlook = RecoveryOutlook.PATIENCE,
            theory = "고든·윌리엄스 배당할인모형(고듀레이션 자산)",
            cases = "니프티 피프티(PER 40+ → −70~90%) · 시스코(닷컴 고점 회복에 25년) · 코카콜라(70년대 멀티플 압축)",
            why = "이익은 견고한데 금리 상승·위험선호 후퇴로 멀티플이 순식간에 압축. 듀레이션 긴 성장주일수록 타격이 " +
                "치명적. — 리포트가 꼽은 최유력 경로.",
            monitor = listOf(
                "엔비디아 실적 달성률 · 향후 CAPEX 가이던스",
                "미국 장기·실질 금리 환경",
                "연준(워시)·한국은행 금리 경로 및 정상화 한계선"
            )
        )
    )

    /** 유형3(밸류에이션·금리)이 현재 활성 방아쇠 — `gate>=1`일 때 하이라이트(§3.7, 프로토타입 1:1) */
    const val ACTIVE_TYPE_INDEX = 2

    /** 역사 검증 섹션 — 3대 모니터링 지표 한 행(헤더+본문) */
    data class HistoryMetric(val header: String, val body: String)

    /** 프로토타입 역사 검증 3열 그리드와 순서·값 동일 */
    val HISTORY_METRICS: List<HistoryMetric> = listOf(
        HistoryMetric("매크로", "환율 지형·보호무역 장벽 — 인위적 환율 변동성 및 SCM 재편 방어막"),
        HistoryMetric("경쟁", "중국 레거시 잠식·미국 자국 지원 — 역사이클 CAPEX 유입 속도·치킨게임 재발"),
        HistoryMetric("포트폴리오", "단일 품목 의존 탈피 — HBM 맞춤형·파운드리·시스템LSI 다변화 성과")
    )

    const val HISTORY_TITLE = "최악의 조합 — 3충격 동시 결합 (일본 1980s)"

    /**
     * 역사 검증 본문 — 1980년대 일본 서사부(v1.4 §4.7 "동적 갱신 금지" 대상, 정적 전용).
     *
     * [HISTORY_BODY]가 [HISTORY_BODY_STATIC] + [HISTORY_BODY_CURRENT]로 분리된 배경은
     * TASK_bear_signal_console.md §4.7 "동적 갱신 대상" 표 — `history_current`(현재 비교 문단)만
     * LLM 웹검색 갱신 대상이고, 이 서사부는 갱신 금지(`HISTORY_BODY` 전반부)다.
     */
    const val HISTORY_BODY_STATIC =
        "세 유형은 독립적으로만 오지 않는다. 1980년대 일본 메모리 산업은 세 충격을 시차를 두고 겹쳐 맞으며 무너졌다 — " +
            "① 플라자 합의 엔고 + 버블 붕괴·금리(멀티플·유형3), ② PC 전환·다운사이클(전방수요·유형2), " +
            "③ 한국·대만 추격(경쟁·유형1). 결국 엘피다가 2013년 마이크론에 피인수. "

    /**
     * 역사 검증 본문 — "현재 한국 위치 비교" 문단(v1.4 §4.7 동적 갱신 대상 `history_current`,
     * 승인 캐시 존재 시 AI 배지·as_of·STALE 오버레이 렌더, P7-3 몫).
     */
    const val HISTORY_BODY_CURRENT =
        "지금 한국이 서 있는 자리가 1988년 일본과 겹치지 않는지 감시해야 할 3대 지표:"

    /** 무손실 결합(기존 참조 호환) — [HISTORY_BODY_STATIC] + [HISTORY_BODY_CURRENT]와 문자 단위로 동일해야 한다. */
    const val HISTORY_BODY = HISTORY_BODY_STATIC + HISTORY_BODY_CURRENT

    /**
     * 부록 B #9 "지표↔리포트 매핑" — 프로토타입 푸터 문구 그대로.
     *
     * v1.4 §4.7 "면책 정리" — 이 도표 매핑은 **정적 기준선 전용** 표기로 존치한다(전역 면책
     * 문구·[BearSignalFooterSection][com.tinyoscillator.feature.bearsignal.presentation.ui.BearSignalFooterSection]의
     * `DISCLAIMER` 표시는 전면 제거됐다 — §5.2-7 개정).
     */
    const val INDICATOR_MAPPING =
        "신호1 도표46~48 · 신호2 도표49~50 · 신호3 도표51~53 · 신호4(금리) 도표54~57 · " +
            "집중 증폭 도표44 · 약세장 3유형 도표26~35 · 역사 검증 도표58."
}
