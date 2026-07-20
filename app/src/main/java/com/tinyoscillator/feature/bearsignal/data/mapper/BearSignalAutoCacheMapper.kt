package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAutoCacheEntity
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearIndicatorKey
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.IpoBigConsumption
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionField
import com.tinyoscillator.feature.bearsignal.domain.usecase.IpoEtfDirectionCalculator
import com.tinyoscillator.feature.bearsignal.domain.usecase.RateGateInputCalculator

/**
 * [AutoBearSignalInputs] ↔ [BearSignalAutoCacheEntity] 변환 (data 계층).
 *
 * Phase 2에서 [BearIndicatorKey.AMP_SEMI]/[BearIndicatorKey.AMP_BUFFER]/[BearIndicatorKey.GATE_RATE]/
 * [BearIndicatorKey.GATE_DIR]/[BearIndicatorKey.S3_ETF] 5개 키가 추가됐다. 캐시 테이블 컬럼이
 * `value: Double` 하나뿐이라(마이그레이션 회피, P1 설계 의도) Boolean/String 지표는 다음과 같이
 * 인코딩한다:
 * - `buffer`(Boolean): true=1.0, false=0.0
 * - `dir`(String, "ease"/"hold"/"hike"): ease=-1.0, hold=0.0, hike=1.0
 * - `etf`(String, "up"/"flat"/"down"): down=-1.0, flat=0.0, up=1.0
 */
object BearSignalAutoCacheMapper {

    private const val BOOL_TRUE = 1.0
    private const val BOOL_FALSE = 0.0

    private const val DIR_EASE_CODE = -1.0
    private const val DIR_HOLD_CODE = 0.0
    private const val DIR_HIKE_CODE = 1.0

    private const val ETF_DOWN_CODE = -1.0
    private const val ETF_FLAT_CODE = 0.0
    private const val ETF_UP_CODE = 1.0

    private const val BIG_SMOOTH_CODE = 0.0
    private const val BIG_PENDING_CODE = 1.0
    private const val BIG_FAILED_CODE = 2.0

    /** 자동 수집 결과 → Room upsert용 엔티티. Phase 2/4 필드는 null이 아닌 것만 포함한다. */
    fun toEntities(inputs: AutoBearSignalInputs): List<BearSignalAutoCacheEntity> {
        val entities = mutableListOf(
            intEntity(BearIndicatorKey.S2_UP3, inputs.up3),
            intEntity(BearIndicatorKey.S2_DOWN3, inputs.down3),
            intEntity(BearIndicatorKey.S2_UP4, inputs.up4),
            intEntity(BearIndicatorKey.S2_DOWN4, inputs.down4),
            doubleEntity(BearIndicatorKey.AMP_KOSPI2, inputs.kospi2)
        )
        inputs.semi?.let { entities.add(doubleEntity(BearIndicatorKey.AMP_SEMI, it)) }
        inputs.buffer?.let { entities.add(boolEntity(BearIndicatorKey.AMP_BUFFER, it)) }
        inputs.rate?.let { entities.add(doubleEntity(BearIndicatorKey.GATE_RATE, it)) }
        inputs.dir?.let { entities.add(dirEntity(it)) }
        inputs.etf?.let { entities.add(etfEntity(it)) }
        // Phase 4(§4.5) — 웹/LLM 제안 승인 전용 필드
        inputs.credit?.let { entities.add(doubleEntity(BearIndicatorKey.GATE_CREDIT, it)) }
        inputs.lossRatio?.let { entities.add(doubleEntity(BearIndicatorKey.S3_LOSS_RATIO, it)) }
        inputs.bigDeal?.let { entities.add(bigDealEntity(it)) }
        return entities
    }

    /**
     * Phase 2 [B]등급 외부 지표 중 **실제 수집 성공한 키만** upsert용 엔티티로 변환(Phase 3-7).
     *
     * null(수집 실패·API 키 미설정) 지표는 제외한다 — 전체 엔티티 read-modify-write 대신 개별 upsert로,
     * 수집 중 도착한 §4.5 승인값·워커 기록을 stale 값으로 되덮지 않도록 한다. A등급 필수 키
     * (S2_UP3~DOWN4, AMP_KOSPI2)와 credit/lossRatio/bigDeal은 이 경로가 다루지 않는다.
     * MANUAL 오버라이드는 별도 테이블이라 영향 없다.
     */
    fun externalEntities(
        semi: AutoIndicator<Double>?,
        buffer: AutoIndicator<Boolean>?,
        rate: AutoIndicator<Double>?,
        dir: AutoIndicator<String>?,
        etf: AutoIndicator<String>?
    ): List<BearSignalAutoCacheEntity> {
        val entities = mutableListOf<BearSignalAutoCacheEntity>()
        semi?.let { entities.add(doubleEntity(BearIndicatorKey.AMP_SEMI, it)) }
        buffer?.let { entities.add(boolEntity(BearIndicatorKey.AMP_BUFFER, it)) }
        rate?.let { entities.add(doubleEntity(BearIndicatorKey.GATE_RATE, it)) }
        dir?.let { entities.add(dirEntity(it)) }
        etf?.let { entities.add(etfEntity(it)) }
        return entities
    }

    /**
     * Room 캐시 → 도메인 모델. Phase 1 필수 5개 키(S2_UP3~DOWN4, AMP_KOSPI2) 중 하나라도 없으면
     * 미수집 상태로 간주해 null 반환(구버전 캐시 호환 — Phase 2 5개 키는 선택적).
     */
    fun toDomain(entities: List<BearSignalAutoCacheEntity>): AutoBearSignalInputs? {
        val byKey = entities.associateBy { it.indicatorKey }
        val up3 = byKey[BearIndicatorKey.S2_UP3.key] ?: return null
        val down3 = byKey[BearIndicatorKey.S2_DOWN3.key] ?: return null
        val up4 = byKey[BearIndicatorKey.S2_UP4.key] ?: return null
        val down4 = byKey[BearIndicatorKey.S2_DOWN4.key] ?: return null
        val kospi2 = byKey[BearIndicatorKey.AMP_KOSPI2.key] ?: return null
        return AutoBearSignalInputs(
            up3 = toIntIndicator(up3),
            down3 = toIntIndicator(down3),
            up4 = toIntIndicator(up4),
            down4 = toIntIndicator(down4),
            kospi2 = toDoubleIndicator(kospi2),
            semi = byKey[BearIndicatorKey.AMP_SEMI.key]?.let { toDoubleIndicator(it) },
            buffer = byKey[BearIndicatorKey.AMP_BUFFER.key]?.let { toBoolIndicator(it) },
            rate = byKey[BearIndicatorKey.GATE_RATE.key]?.let { toDoubleIndicator(it) },
            dir = byKey[BearIndicatorKey.GATE_DIR.key]?.let { toDirIndicator(it) },
            etf = byKey[BearIndicatorKey.S3_ETF.key]?.let { toEtfIndicator(it) },
            credit = byKey[BearIndicatorKey.GATE_CREDIT.key]?.let { toDoubleIndicator(it) },
            lossRatio = byKey[BearIndicatorKey.S3_LOSS_RATIO.key]?.let { toDoubleIndicator(it) },
            bigDeal = byKey[BearIndicatorKey.S3_BIG_DEAL.key]?.let { toBigDealIndicator(it) }
        )
    }

    private fun intEntity(key: BearIndicatorKey, indicator: AutoIndicator<Int>): BearSignalAutoCacheEntity =
        BearSignalAutoCacheEntity(
            indicatorKey = key.key,
            value = indicator.value.toDouble(),
            source = indicator.source.name,
            updatedAt = indicator.updatedAt
        )

    private fun doubleEntity(key: BearIndicatorKey, indicator: AutoIndicator<Double>): BearSignalAutoCacheEntity =
        BearSignalAutoCacheEntity(
            indicatorKey = key.key,
            value = indicator.value,
            source = indicator.source.name,
            updatedAt = indicator.updatedAt
        )

    private fun boolEntity(key: BearIndicatorKey, indicator: AutoIndicator<Boolean>): BearSignalAutoCacheEntity =
        BearSignalAutoCacheEntity(
            indicatorKey = key.key,
            value = if (indicator.value) BOOL_TRUE else BOOL_FALSE,
            source = indicator.source.name,
            updatedAt = indicator.updatedAt
        )

    private fun dirEntity(indicator: AutoIndicator<String>): BearSignalAutoCacheEntity {
        val code = when (indicator.value) {
            RateGateInputCalculator.DIR_EASE -> DIR_EASE_CODE
            RateGateInputCalculator.DIR_HIKE -> DIR_HIKE_CODE
            else -> DIR_HOLD_CODE
        }
        return BearSignalAutoCacheEntity(
            indicatorKey = BearIndicatorKey.GATE_DIR.key,
            value = code,
            source = indicator.source.name,
            updatedAt = indicator.updatedAt
        )
    }

    private fun etfEntity(indicator: AutoIndicator<String>): BearSignalAutoCacheEntity {
        val code = when (indicator.value) {
            IpoEtfDirectionCalculator.DIR_DOWN -> ETF_DOWN_CODE
            IpoEtfDirectionCalculator.DIR_UP -> ETF_UP_CODE
            else -> ETF_FLAT_CODE
        }
        return BearSignalAutoCacheEntity(
            indicatorKey = BearIndicatorKey.S3_ETF.key,
            value = code,
            source = indicator.source.name,
            updatedAt = indicator.updatedAt
        )
    }

    private fun bigDealEntity(indicator: AutoIndicator<String>): BearSignalAutoCacheEntity {
        val code = when (indicator.value) {
            IpoBigConsumption.SMOOTH -> BIG_SMOOTH_CODE
            IpoBigConsumption.FAILED -> BIG_FAILED_CODE
            else -> BIG_PENDING_CODE
        }
        return BearSignalAutoCacheEntity(
            indicatorKey = BearIndicatorKey.S3_BIG_DEAL.key,
            value = code,
            source = indicator.source.name,
            updatedAt = indicator.updatedAt
        )
    }

    private fun toBigDealIndicator(entity: BearSignalAutoCacheEntity): AutoIndicator<String> {
        val value = when {
            entity.value <= BIG_SMOOTH_CODE + 0.5 -> IpoBigConsumption.SMOOTH
            entity.value >= BIG_FAILED_CODE - 0.5 -> IpoBigConsumption.FAILED
            else -> IpoBigConsumption.PENDING
        }
        return AutoIndicator(value, parseSource(entity.source), entity.updatedAt)
    }

    /**
     * 로컬 예탁금 테이블(`market_deposits`, NaverFinance 스크랩)에서 수집한 신용잔고(조원) →
     * [BearIndicatorKey.GATE_CREDIT] 캐시 엔티티(개별 필드 upsert 경로). §4 데이터 소스 표의
     * "신용잔고 v2 배치" 항목 — 값은 이미 조원으로 변환된 상태여야 한다(억→조 변환은 수집처 책임).
     */
    fun creditEntity(creditJo: Double, updatedAt: Long): BearSignalAutoCacheEntity =
        BearSignalAutoCacheEntity(
            indicatorKey = BearIndicatorKey.GATE_CREDIT.key,
            value = creditJo,
            source = InputSource.AUTO.name,
            updatedAt = updatedAt
        )

    /**
     * §4.5 승인된 제안 하나를 캐시 엔티티로 변환(Phase 4, 개별 필드 upsert 경로).
     *
     * [rawValue]는 [com.tinyoscillator.feature.bearsignal.domain.model.Suggestion.nextValue] 원문
     * 문자열이다 — [com.tinyoscillator.feature.bearsignal.domain.model.SuggestionValidation]이 이미
     * 열거형 필드(dir/bigDeal)를 검증했으므로 여기서는 인코딩만 수행한다.
     */
    fun suggestionEntity(field: SuggestionField, rawValue: String, updatedAt: Long): BearSignalAutoCacheEntity {
        val value = when (field) {
            SuggestionField.RATE, SuggestionField.LOSS_RATIO, SuggestionField.CREDIT -> rawValue.toDouble()
            SuggestionField.DIR -> when (rawValue) {
                RateGateInputCalculator.DIR_EASE -> DIR_EASE_CODE
                RateGateInputCalculator.DIR_HIKE -> DIR_HIKE_CODE
                else -> DIR_HOLD_CODE
            }
            SuggestionField.BIG_DEAL -> when (rawValue) {
                IpoBigConsumption.SMOOTH -> BIG_SMOOTH_CODE
                IpoBigConsumption.FAILED -> BIG_FAILED_CODE
                else -> BIG_PENDING_CODE
            }
        }
        return BearSignalAutoCacheEntity(
            indicatorKey = field.indicatorKey.key,
            value = value,
            source = InputSource.AUTO.name,
            updatedAt = updatedAt
        )
    }

    private fun toIntIndicator(entity: BearSignalAutoCacheEntity): AutoIndicator<Int> =
        AutoIndicator(entity.value.toInt(), parseSource(entity.source), entity.updatedAt)

    private fun toDoubleIndicator(entity: BearSignalAutoCacheEntity): AutoIndicator<Double> =
        AutoIndicator(entity.value, parseSource(entity.source), entity.updatedAt)

    private fun toBoolIndicator(entity: BearSignalAutoCacheEntity): AutoIndicator<Boolean> =
        AutoIndicator(entity.value >= 0.5, parseSource(entity.source), entity.updatedAt)

    private fun toDirIndicator(entity: BearSignalAutoCacheEntity): AutoIndicator<String> {
        val value = when {
            entity.value <= DIR_EASE_CODE + 0.5 -> RateGateInputCalculator.DIR_EASE
            entity.value >= DIR_HIKE_CODE - 0.5 -> RateGateInputCalculator.DIR_HIKE
            else -> RateGateInputCalculator.DIR_HOLD
        }
        return AutoIndicator(value, parseSource(entity.source), entity.updatedAt)
    }

    private fun toEtfIndicator(entity: BearSignalAutoCacheEntity): AutoIndicator<String> {
        val value = when {
            entity.value <= ETF_DOWN_CODE + 0.5 -> IpoEtfDirectionCalculator.DIR_DOWN
            entity.value >= ETF_UP_CODE - 0.5 -> IpoEtfDirectionCalculator.DIR_UP
            else -> IpoEtfDirectionCalculator.DIR_FLAT
        }
        return AutoIndicator(value, parseSource(entity.source), entity.updatedAt)
    }

    private fun parseSource(raw: String): InputSource =
        runCatching { InputSource.valueOf(raw) }.getOrDefault(InputSource.AUTO)
}
