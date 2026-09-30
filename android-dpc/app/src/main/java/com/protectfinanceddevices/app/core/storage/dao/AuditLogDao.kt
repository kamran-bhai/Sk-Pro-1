package com.protectfinanceddevices.app.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.protectfinanceddevices.app.core.storage.entities.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC")
    fun getAllLogs(): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insert(log: AuditLogEntity)
}
