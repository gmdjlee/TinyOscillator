package com.tinyoscillator.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tinyoscillator.core.database.entity.IncrementalModelStateEntity
import com.tinyoscillator.core.database.entity.ModelDriftAlertEntity

@Dao
interface IncrementalModelDao {

    // ─── Model State ───

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveModelState(state: IncrementalModelStateEntity)

    @Query("SELECT * FROM incremental_model_state WHERE model_name = :modelName")
    suspend fun getModelState(modelName: String): IncrementalModelStateEntity?

    @Query("SELECT * FROM incremental_model_state")
    suspend fun getAllModelStates(): List<IncrementalModelStateEntity>

    @Query("DELETE FROM incremental_model_state WHERE model_name = :modelName")
    suspend fun deleteModelState(modelName: String)

    // ─── Drift Alerts ───

    @Insert
    suspend fun insertDriftAlert(alert: ModelDriftAlertEntity)

    @Query("SELECT * FROM model_drift_alert ORDER BY detected_at DESC")
    suspend fun getAllDriftAlerts(): List<ModelDriftAlertEntity>

    @Query("SELECT * FROM model_drift_alert WHERE model_name = :modelName ORDER BY detected_at DESC")
    suspend fun getDriftAlerts(modelName: String): List<ModelDriftAlertEntity>

    @Query("SELECT * FROM model_drift_alert ORDER BY detected_at DESC LIMIT :limit")
    suspend fun getRecentDriftAlerts(limit: Int = 20): List<ModelDriftAlertEntity>

    @Query("DELETE FROM model_drift_alert WHERE detected_at < :cutoff")
    suspend fun deleteOldAlerts(cutoff: Long)
}
