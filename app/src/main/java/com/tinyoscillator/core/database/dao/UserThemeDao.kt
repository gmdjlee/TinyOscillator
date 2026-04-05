package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tinyoscillator.core.database.entity.UserThemeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserThemeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(theme: UserThemeEntity): Long

    @Update
    suspend fun update(theme: UserThemeEntity)

    @Delete
    suspend fun delete(theme: UserThemeEntity)

    @Query("SELECT * FROM user_themes ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<UserThemeEntity>>

    @Query("SELECT * FROM user_themes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UserThemeEntity?

    @Query("SELECT COUNT(*) FROM user_themes")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM user_themes")
    suspend fun getMaxSortOrder(): Int
}
