package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSnapshotEntity
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot

/** [BearSnapshot] ↔ [BearSnapshotEntity] 변환 (data 계층, §6.1). */
object BearSnapshotMapper {

    fun toEntity(snapshot: BearSnapshot): BearSnapshotEntity = BearSnapshotEntity(
        day = snapshot.day,
        phase = snapshot.phase.name,
        lead = snapshot.lead,
        gate = snapshot.gate,
        s1 = snapshot.s1,
        s2 = snapshot.s2,
        s3 = snapshot.s3,
        amp = snapshot.amp,
        configBasis = snapshot.configBasis,
        inputsJson = snapshot.inputsJson,
        fieldMetaJson = snapshot.fieldMetaJson,
        createdAt = snapshot.createdAt
    )

    fun toDomain(entity: BearSnapshotEntity): BearSnapshot = BearSnapshot(
        day = entity.day,
        phase = BearPhase.valueOf(entity.phase),
        lead = entity.lead,
        gate = entity.gate,
        s1 = entity.s1,
        s2 = entity.s2,
        s3 = entity.s3,
        amp = entity.amp,
        configBasis = entity.configBasis,
        inputsJson = entity.inputsJson,
        fieldMetaJson = entity.fieldMetaJson,
        createdAt = entity.createdAt
    )
}
