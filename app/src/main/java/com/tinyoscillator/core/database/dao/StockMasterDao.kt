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
}

data class TickerMarketPair(val ticker: String, val market: String)

data class TickerSectorPair(val ticker: String, val sector: String)
