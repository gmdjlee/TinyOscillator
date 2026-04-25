package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tinyoscillator.core.database.entity.ThemeGroupEntity
import kotlinx.coroutines.flow.Flow

/**
 * Kiwoom ka90001 응답이 매핑되는 [ThemeGroupEntity]의 DAO.
 *
 * 갱신 패턴: [ThemeUpdateWorker]가 1일 1회 호출해 [replaceAll]로 전체 교체.
 * 조회 패턴: 화면에서 [observeAll]/[searchByName]을 구독, in-memory 정렬은 Repository에서 수행.
 */
@Dao
interface ThemeGroupDao {

    @Query("SELECT * FROM theme_group ORDER BY theme_name ASC")
    fun observeAll(): Flow<List<ThemeGroupEntity>>

    @Query(
        "SELECT * FROM theme_group " +
            "WHERE theme_name LIKE '%' || :query || '%' " +
            "ORDER BY theme_name ASC"
    )
    fun searchByName(query: String): Flow<List<ThemeGroupEntity>>

    @Query("SELECT * FROM theme_group WHERE theme_code = :themeCode LIMIT 1")
    suspend fun getByCode(themeCode: String): ThemeGroupEntity?

    @Query("SELECT COUNT(*) FROM theme_group")
    suspend fun count(): Int

    @Query("SELECT MAX(last_updated) FROM theme_group")
    suspend fun lastUpdatedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ThemeGroupEntity>)

    @Query("DELETE FROM theme_group")
    suspend fun deleteAll()

    /**
     * 전체 테마 목록을 원자적으로 교체. 부분 갱신을 막기 위해 트랜잭션으로 감싼다.
     * `ka90001` 페이지네이션이 모두 끝난 뒤 한 번에 호출.
     */
    @Transaction
    suspend fun replaceAll(items: List<ThemeGroupEntity>) {
        deleteAll()
        insertAll(items)
    }
}
