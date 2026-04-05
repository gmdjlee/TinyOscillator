package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.WatchlistGroupEntity
import com.tinyoscillator.core.database.entity.WatchlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    // ── Items ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchlistItemEntity): Long

    @Delete
    suspend fun delete(item: WatchlistItemEntity)

    @Query("SELECT * FROM watchlist_items WHERE id = :id")
    suspend fun getById(id: Long): WatchlistItemEntity?

    @Query("SELECT * FROM watchlist_items WHERE ticker = :ticker")
    suspend fun getByTicker(ticker: String): WatchlistItemEntity?

    @Query("SELECT * FROM watchlist_items ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<WatchlistItemEntity>>

    @Query("SELECT * FROM watchlist_items ORDER BY sort_order ASC")
    suspend fun getAllSortedByOrder(): List<WatchlistItemEntity>

    @Query("""
        SELECT * FROM watchlist_items
        WHERE group_id = :groupId
        ORDER BY sort_order ASC
    """)
    fun observeByGroup(groupId: Long): Flow<List<WatchlistItemEntity>>

    @Query("""
        SELECT * FROM watchlist_items
        ORDER BY COALESCE(cached_signal, 0.5) DESC
    """)
    fun observeBySignal(): Flow<List<WatchlistItemEntity>>

    @Query("UPDATE watchlist_items SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("UPDATE watchlist_items SET group_id = :groupId WHERE id = :id")
    suspend fun moveToGroup(id: Long, groupId: Long)

    @Query("""
        UPDATE watchlist_items
        SET cached_price = :price, cached_change = :change,
            cached_signal = :signal, price_updated_at = :now
        WHERE ticker = :ticker
    """)
    suspend fun updateCache(
        ticker: String,
        price: Long,
        change: Float,
        signal: Float,
        now: Long = System.currentTimeMillis(),
    )

    // ── Groups ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: WatchlistGroupEntity): Long

    @Query("SELECT * FROM watchlist_groups ORDER BY sort_order ASC")
    fun observeGroups(): Flow<List<WatchlistGroupEntity>>

    @Delete
    suspend fun deleteGroup(group: WatchlistGroupEntity)
}
