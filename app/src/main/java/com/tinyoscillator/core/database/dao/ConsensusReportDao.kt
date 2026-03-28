package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.ConsensusReportEntity

@Dao
interface ConsensusReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<ConsensusReportEntity>)

    @Query("SELECT * FROM consensus_reports WHERE write_date BETWEEN :startDate AND :endDate ORDER BY write_date DESC, stock_ticker ASC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<ConsensusReportEntity>

    @Query("SELECT * FROM consensus_reports WHERE stock_ticker = :ticker ORDER BY write_date ASC")
    suspend fun getByTicker(ticker: String): List<ConsensusReportEntity>

    @Query("SELECT * FROM consensus_reports ORDER BY write_date DESC, stock_ticker ASC")
    suspend fun getAll(): List<ConsensusReportEntity>

    @Query("SELECT DISTINCT write_date FROM consensus_reports ORDER BY write_date DESC")
    suspend fun getDistinctDates(): List<String>

    @Query("SELECT DISTINCT category FROM consensus_reports ORDER BY category")
    suspend fun getDistinctCategories(): List<String>

    @Query("SELECT DISTINCT prev_opinion FROM consensus_reports WHERE prev_opinion != '' ORDER BY prev_opinion")
    suspend fun getDistinctPrevOpinions(): List<String>

    @Query("SELECT DISTINCT opinion FROM consensus_reports ORDER BY opinion")
    suspend fun getDistinctOpinions(): List<String>

    @Query("SELECT DISTINCT author FROM consensus_reports ORDER BY author")
    suspend fun getDistinctAuthors(): List<String>

    @Query("SELECT DISTINCT institution FROM consensus_reports ORDER BY institution")
    suspend fun getDistinctInstitutions(): List<String>

    @Query("SELECT MAX(write_date) FROM consensus_reports")
    suspend fun getLatestDate(): String?

    @Query("SELECT MIN(write_date) FROM consensus_reports")
    suspend fun getEarliestDate(): String?

    @Query("SELECT DISTINCT stock_name FROM consensus_reports WHERE stock_name != '' ORDER BY stock_name")
    suspend fun getDistinctStockNames(): List<String>

    @Query("SELECT COUNT(*) FROM consensus_reports")
    suspend fun getCount(): Int

    @Query("DELETE FROM consensus_reports WHERE write_date < :beforeDate")
    suspend fun deleteBeforeDate(beforeDate: String)
}
