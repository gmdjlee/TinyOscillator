package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.repository.StockMasterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class SearchStocksUseCase @Inject constructor(
    private val stockMasterRepository: StockMasterRepository
) {
    /** 기존 Flow 기반 검색 (하위 호환) */
    operator fun invoke(query: String): Flow<List<StockMasterEntity>> {
        if (query.isBlank()) return flowOf(emptyList())
        return stockMasterRepository.searchStocks(query)
    }

    /** 초성/이름/티커 통합 검색 (suspend) */
    suspend fun searchWithChosung(query: String): List<StockMasterEntity> {
        if (query.isBlank()) return emptyList()
        return stockMasterRepository.searchWithChosung(query)
    }
}
