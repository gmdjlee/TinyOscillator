package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.repository.StockMasterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class SearchStocksUseCase @Inject constructor(
    private val stockMasterRepository: StockMasterRepository
) {
    operator fun invoke(query: String): Flow<List<StockMasterEntity>> {
        if (query.isBlank()) return flowOf(emptyList())
        return stockMasterRepository.searchStocks(query)
    }
}
