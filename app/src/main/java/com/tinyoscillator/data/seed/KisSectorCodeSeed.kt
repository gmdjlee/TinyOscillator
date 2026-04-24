package com.tinyoscillator.data.seed

import com.tinyoscillator.core.database.entity.SectorMasterEntity
import com.tinyoscillator.domain.model.SectorLevel

/**
 * KIS 업종분류코드 정적 시드 (`inquire-daily-indexchartprice`, TR_ID=FHKUP03500100 지원).
 *
 * FID_INPUT_ISCD가 받는 4자리 KIS 업종분류코드만 담는다. 과거 구현에서는 KIS
 * `CTPF1002R`(search-stock-info)을 통해 대표 종목마다 업종을 긁어 왔지만, 업종 수 × 500ms
 * (20~30초) 초기 지연이 심해 정적 시드로 대체한다. 코드 체계는 KRX 통합 지수(5042~5600)와
 * 달리 FHKUP03500100이 직접 인식하므로 추가 변환 없이 그대로 전달한다.
 *
 * 출처: 한국투자증권 developers portal `inquire-daily-indexchartprice` 예시 +
 * FinanceDataReader wiki `Industry-Codes-KR`. KOSPI 업종은 0023(미사용)을 제외한 전통 23종 +
 * 대/중/소형주 3종. KOSDAQ 업종은 기타서비스부터 IT부품까지 주요 18종.
 */
object KisSectorCodeSeed {

    data class Entry(val code: String, val name: String, val level: SectorLevel)

    val ENTRIES: List<Entry> = listOf(
        // 대표지수
        Entry("0001", "코스피", SectorLevel.INDEX),
        Entry("1001", "코스닥", SectorLevel.INDEX),
        Entry("2001", "코스피 200", SectorLevel.INDEX),

        // 코스피 규모별 + 업종
        Entry("0002", "코스피 대형주", SectorLevel.KOSPI),
        Entry("0003", "코스피 중형주", SectorLevel.KOSPI),
        Entry("0004", "코스피 소형주", SectorLevel.KOSPI),
        Entry("0005", "코스피 음식료품", SectorLevel.KOSPI),
        Entry("0006", "코스피 섬유의복", SectorLevel.KOSPI),
        Entry("0007", "코스피 종이목재", SectorLevel.KOSPI),
        Entry("0008", "코스피 화학", SectorLevel.KOSPI),
        Entry("0009", "코스피 의약품", SectorLevel.KOSPI),
        Entry("0010", "코스피 비금속광물", SectorLevel.KOSPI),
        Entry("0011", "코스피 철강금속", SectorLevel.KOSPI),
        Entry("0012", "코스피 기계", SectorLevel.KOSPI),
        Entry("0013", "코스피 전기전자", SectorLevel.KOSPI),
        Entry("0014", "코스피 의료정밀", SectorLevel.KOSPI),
        Entry("0015", "코스피 운수장비", SectorLevel.KOSPI),
        Entry("0016", "코스피 유통업", SectorLevel.KOSPI),
        Entry("0017", "코스피 전기가스업", SectorLevel.KOSPI),
        Entry("0018", "코스피 건설업", SectorLevel.KOSPI),
        Entry("0019", "코스피 운수창고", SectorLevel.KOSPI),
        Entry("0020", "코스피 통신업", SectorLevel.KOSPI),
        Entry("0021", "코스피 금융업", SectorLevel.KOSPI),
        Entry("0022", "코스피 은행", SectorLevel.KOSPI),
        Entry("0024", "코스피 증권", SectorLevel.KOSPI),
        Entry("0025", "코스피 보험", SectorLevel.KOSPI),
        Entry("0026", "코스피 서비스업", SectorLevel.KOSPI),
        Entry("0027", "코스피 제조업", SectorLevel.KOSPI),

        // 코스닥 업종
        Entry("1002", "코스닥 기타서비스", SectorLevel.KOSDAQ),
        Entry("1003", "코스닥 IT종합", SectorLevel.KOSDAQ),
        Entry("1004", "코스닥 제조", SectorLevel.KOSDAQ),
        Entry("1005", "코스닥 건설", SectorLevel.KOSDAQ),
        Entry("1006", "코스닥 유통", SectorLevel.KOSDAQ),
        Entry("1007", "코스닥 숙박음식", SectorLevel.KOSDAQ),
        Entry("1008", "코스닥 금융", SectorLevel.KOSDAQ),
        Entry("1009", "코스닥 오락문화", SectorLevel.KOSDAQ),
        Entry("1010", "코스닥 통신서비스", SectorLevel.KOSDAQ),
        Entry("1011", "코스닥 방송서비스", SectorLevel.KOSDAQ),
        Entry("1012", "코스닥 인터넷", SectorLevel.KOSDAQ),
        Entry("1013", "코스닥 디지털컨텐츠", SectorLevel.KOSDAQ),
        Entry("1014", "코스닥 소프트웨어", SectorLevel.KOSDAQ),
        Entry("1015", "코스닥 컴퓨터서비스", SectorLevel.KOSDAQ),
        Entry("1016", "코스닥 통신장비", SectorLevel.KOSDAQ),
        Entry("1017", "코스닥 정보기기", SectorLevel.KOSDAQ),
        Entry("1018", "코스닥 반도체", SectorLevel.KOSDAQ),
        Entry("1019", "코스닥 IT부품", SectorLevel.KOSDAQ),
    )

    fun toEntities(now: Long): List<SectorMasterEntity> =
        ENTRIES.map { e ->
            SectorMasterEntity(
                code = e.code,
                name = e.name,
                level = e.level.code,
                parentCode = null,
                lastUpdated = now,
            )
        }
}
