package com.protectfinanceddevices.app.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.protectfinanceddevices.app.core.storage.entities.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE isAcknowledged = 0 ORDER BY timestamp DESC")
    fun getUnacknowledgedAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE isAcknowledged = 0")
    fun getUnacknowledgedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<AlertEntity>)

    @Query("UPDATE alerts SET isAcknowledged = 1 WHERE id = :alertId")
    suspend fun acknowledgeAlert(alertId: String)
}
