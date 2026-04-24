package com.tinyoscillator.data.seed

import com.tinyoscillator.core.database.entity.SectorMasterEntity
import com.tinyoscillator.domain.model.SectorLevel

/**
 * KRX 통합 지수 정적 시드 (FHPUP02140000 FID_INPUT_ISCD로 바로 조회 가능).
 *
 * 출처: FinanceDataReader 위키 "한국거래소 지수" + KIS idxcode.mst 범위.
 * KIS API는 업종 목록 전용 엔드포인트를 제공하지 않으므로 정적 리스트를 씨드한다.
 */
object KrxIntegratedIndexSeed {

    data class Entry(val code: String, val name: String, val level: SectorLevel)

    val ENTRIES: List<Entry> = listOf(
        // 대표지수
        Entry("5042", "KRX 100", SectorLevel.INDEX),
        Entry("5300", "KRX 300", SectorLevel.INDEX),
        Entry("5600", "KTOP 30", SectorLevel.INDEX),

        // KRX 섹터 (5042 제외한 5043~5065)
        Entry("5043", "KRX 자동차", SectorLevel.SECTOR),
        Entry("5044", "KRX 반도체", SectorLevel.SECTOR),
        Entry("5045", "KRX 헬스케어", SectorLevel.SECTOR),
        Entry("5046", "KRX 은행", SectorLevel.SECTOR),
        Entry("5048", "KRX 에너지화학", SectorLevel.SECTOR),
        Entry("5049", "KRX 철강", SectorLevel.SECTOR),
        Entry("5051", "KRX 방송통신", SectorLevel.SECTOR),
        Entry("5052", "KRX 건설", SectorLevel.SECTOR),
        Entry("5054", "KRX 증권", SectorLevel.SECTOR),
        Entry("5055", "KRX 기계장비", SectorLevel.SECTOR),
        Entry("5056", "KRX 보험", SectorLevel.SECTOR),
        Entry("5057", "KRX 운송", SectorLevel.SECTOR),
        Entry("5061", "KRX 경기소비재", SectorLevel.SECTOR),
        Entry("5062", "KRX 필수소비재", SectorLevel.SECTOR),
        Entry("5063", "KRX 미디어&엔터테인먼트", SectorLevel.SECTOR),
        Entry("5064", "KRX 정보기술", SectorLevel.SECTOR),
        Entry("5065", "KRX 유틸리티", SectorLevel.SECTOR),

        // KRX 300 업종
        Entry("5351", "KRX 300 정보기술", SectorLevel.KRX_300),
        Entry("5352", "KRX 300 금융", SectorLevel.KRX_300),
        Entry("5353", "KRX 300 자유소비재", SectorLevel.KRX_300),
        Entry("5354", "KRX 300 산업재", SectorLevel.KRX_300),
        Entry("5355", "KRX 300 헬스케어", SectorLevel.KRX_300),
        Entry("5356", "KRX 300 커뮤니케이션서비스", SectorLevel.KRX_300),
        Entry("5357", "KRX 300 소재", SectorLevel.KRX_300),
        Entry("5358", "KRX 300 필수소비재", SectorLevel.KRX_300),
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
