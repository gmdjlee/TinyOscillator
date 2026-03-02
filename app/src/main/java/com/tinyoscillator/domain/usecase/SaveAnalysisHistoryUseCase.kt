package com.tinyoscillator.domain.usecase

import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.entity.AnalysisHistoryEntity
import javax.inject.Inject

class SaveAnalysisHistoryUseCase @Inject constructor(
    private val analysisHistoryDao: AnalysisHistoryDao
) {
    /**
     * 분석 기록 저장 (FIFO, 최대 30건).
     *
     * 1. 기존 동일 종목 삭제 (중복 방지)
     * 2. 새 기록 삽입
     * 3. 30건 초과시 가장 오래된 기록 삭제
     */
    suspend operator fun invoke(ticker: String, name: String) {
        // 기존 동일 종목 제거
        analysisHistoryDao.deleteByTicker(ticker)

        // 새 기록 삽입
        analysisHistoryDao.insert(
            AnalysisHistoryEntity(
                ticker = ticker,
                name = name,
                lastAnalyzedAt = System.currentTimeMillis()
            )
        )

        // 30건 초과시 가장 오래된 기록 삭제
        val count = analysisHistoryDao.getCount()
        if (count > MAX_HISTORY) {
            analysisHistoryDao.deleteOldest(count - MAX_HISTORY)
        }
    }

    companion object {
        private const val MAX_HISTORY = 30
    }
}
