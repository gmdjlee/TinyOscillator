package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.StockMasterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMasterDao {

    @Query(
        """
        SELECT * FROM stock_master
        WHERE name LIKE :query || '%'
           OR ticker LIKE :query || '%'
        ORDER BY ticker ASC
        LIMIT 20
        """
    )
    fun searchStocks(query: String): Flow<List<StockMasterEntity>>

    /** 초성/이름/티커 동시 검색 (suspend, 결과 최대 30건) */
    @Query(
        """
        SELECT * FROM stock_master
        WHERE name    LIKE '%' || :query || '%'
           OR ticker  LIKE '%' || :query || '%'
        ORDER BY ticker ASC
        LIMIT :limit
        """
    )
    suspend fun searchByText(query: String, limit: Int = 30): List<StockMasterEntity>

    /** 초성 검색 (별도 쿼리) */
    @Query(
        """
        SELECT * FROM stock_master
        WHERE initial_consonants LIKE '%' || :chosung || '%'
        ORDER BY ticker ASC
        LIMIT :limit
        """
    )
    suspend fun searchByChosung(chosung: String, limit: Int = 30): List<StockMasterEntity>

    /** 티커 단건 조회 */
    @Query("SELECT * FROM stock_master WHERE ticker = :ticker LIMIT 1")
    suspend fun getByTicker(ticker: String): StockMasterEntity?

    @Query("SELECT * FROM stock_master ORDER BY ticker ASC")
    suspend fun getAll(): List<StockMasterEntity>

    @Query("SELECT COUNT(*) FROM stock_master")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stocks: List<StockMasterEntity>)

    @Query("DELETE FROM stock_master")
    suspend fun deleteAll()

    @Query("SELECT ticker, market FROM stock_master")
    suspend fun getTickerMarketMap(): List<TickerMarketPair>

    @Query("SELECT ticker, sector FROM stock_master WHERE sector != ''")
    suspend fun getTickerSectorMap(): List<TickerSectorPair>

    /** 종목명 조회 — 통계 엔진용 */
    @Query("SELECT name FROM stock_master WHERE ticker = :ticker")
    suspend fun getStockName(ticker: String): String?

    /** 종목의 섹터 조회 */
    @Query("SELECT sector FROM stock_master WHERE ticker = :ticker")
    suspend fun getSector(ticker: String): String?

    /** 동일 섹터 종목 조회 (시가총액 기준 상위 N개) */
    @Query("SELECT ticker FROM stock_master WHERE sector = :sector AND sector != '' ORDER BY ticker ASC LIMIT :limit")
    suspend fun getTickersBySector(sector: String, limit: Int): List<String>

    /** 스크리너용: 시장/섹터 필터링 후 후보 목록 */
    @Query("""
        SELECT * FROM stock_master
        WHERE (:marketType IS NULL OR market = :marketType)
          AND (:sectorCode IS NULL OR sector = :sectorCode)
        ORDER BY ticker ASC
        LIMIT :candidateLimit
    """)
    suspend fun getFilteredCandidates(
        marketType: String?,
        sectorCode: String?,
        candidateLimit: Int,
    ): List<StockMasterEntity>

    /** 전체 섹터 목록 (중복 제거) */
    @Query("SELECT DISTINCT sector FROM stock_master WHERE sector != '' ORDER BY sector ASC")
    suspend fun getAllSectors(): List<String>

    /** 섹터 목록 Flow (변경 감지) */
    @Query("SELECT DISTINCT sector FROM stock_master WHERE sector != '' ORDER BY sector ASC")
    fun observeAllSectors(): Flow<List<String>>

    /** 섹터별 전체 종목 티커 목록 */
    @Query("SELECT ticker FROM stock_master WHERE sector = :sector AND sector != '' ORDER BY ticker ASC")
    suspend fun getAllTickersBySector(sector: String): List<String>
}

data class TickerMarketPair(val ticker: String, val market: String)

data class TickerSectorPair(val ticker: String, val sector: String)
