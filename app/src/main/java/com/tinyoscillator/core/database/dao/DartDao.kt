package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.DartCorpCodeEntity

@Dao
interface DartDao {

    /** 종목코드로 corp_code 조회 */
    @Query("SELECT * FROM dart_corp_code WHERE ticker = :ticker LIMIT 1")
    suspend fun getCorpCode(ticker: String): DartCorpCodeEntity?

    /** corp_code 매핑 배치 삽입 (REPLACE로 갱신) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DartCorpCodeEntity>)

    /** corp_code 캐시 전체 수 */
    @Query("SELECT COUNT(*) FROM dart_corp_code")
    suspend fun count(): Int

    /** 캐시 최종 갱신 시점 */
    @Query("SELECT MAX(updated_at) FROM dart_corp_code")
    suspend fun lastUpdatedAt(): Long?

    /** 캐시 전체 삭제 (재다운로드 전) */
    @Query("DELETE FROM dart_corp_code")
    suspend fun deleteAll()
}
