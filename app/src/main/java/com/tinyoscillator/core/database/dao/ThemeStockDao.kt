package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tinyoscillator.core.database.entity.ThemeStockEntity
import kotlinx.coroutines.flow.Flow

/**
 * Kiwoom ka90002 응답이 매핑되는 [ThemeStockEntity]의 DAO.
 *
 * 갱신 패턴: 테마 단위 [replaceForTheme]로 부분 교체 — 한 테마의 ka90002 페이징이 끝나면
 * 해당 테마의 기존 종목을 제거하고 새 결과를 일괄 삽입한다. 일부 테마 실패가 다른 테마의 캐시를
 * 무효화하지 않도록 격리.
 */
@Dao
interface ThemeStockDao {

    @Query(
        "SELECT * FROM theme_stock WHERE theme_code = :themeCode " +
            "ORDER BY period_return_rate DESC, stock_name ASC"
    )
    fun observeByTheme(themeCode: String): Flow<List<ThemeStockEntity>>

    /** 종목코드로 거꾸로 — "이 종목이 속한 모든 테마 행" 조회. `stock_code` 인덱스 사용. */
    @Query("SELECT * FROM theme_stock WHERE stock_code = :stockCode")
    suspend fun getByStockCode(stockCode: String): List<ThemeStockEntity>

    @Query("SELECT COUNT(*) FROM theme_stock")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM theme_stock WHERE theme_code = :themeCode")
    suspend fun countForTheme(themeCode: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ThemeStockEntity>)

    @Query("DELETE FROM theme_stock WHERE theme_code = :themeCode")
    suspend fun deleteForTheme(themeCode: String)

    @Query("DELETE FROM theme_stock")
    suspend fun deleteAll()

    /**
     * 단일 테마의 종목 캐시를 원자적으로 교체.
     * 부분 페이지 실패 시 이전 캐시를 그대로 두기 위해 호출자(Repository)가 모든 페이지를
     * 모은 뒤에만 호출하도록 한다.
     */
    @Transaction
    suspend fun replaceForTheme(themeCode: String, items: List<ThemeStockEntity>) {
        deleteForTheme(themeCode)
        insertAll(items)
    }
}
