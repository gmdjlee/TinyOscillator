package com.tinyoscillator.feature.bearsignal.data.repository

import com.tinyoscillator.feature.bearsignal.data.local.BearSnapshotDao
import com.tinyoscillator.feature.bearsignal.data.mapper.BearSnapshotMapper
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import com.tinyoscillator.feature.bearsignal.domain.repository.SnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [SnapshotRepository] 구현 — [BearSnapshotDao] 위임 + [BearSnapshotMapper] 변환. */
class SnapshotRepositoryImpl(
    private val dao: BearSnapshotDao
) : SnapshotRepository {

    override suspend fun upsertToday(snapshot: BearSnapshot) {
        dao.upsert(BearSnapshotMapper.toEntity(snapshot))
    }

    override fun observeLatest(): Flow<BearSnapshot?> =
        dao.observeLatest().map { it?.let(BearSnapshotMapper::toDomain) }

    override fun observeRange(from: String, to: String): Flow<List<BearSnapshot>> =
        dao.observeRange(from, to).map { list -> list.map(BearSnapshotMapper::toDomain) }

    override suspend fun latestOrNull(): BearSnapshot? =
        dao.latest()?.let(BearSnapshotMapper::toDomain)
}
