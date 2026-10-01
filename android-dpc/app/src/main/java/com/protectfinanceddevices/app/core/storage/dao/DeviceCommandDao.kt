package com.protectfinanceddevices.app.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.protectfinanceddevices.app.core.storage.entities.DeviceCommandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceCommandDao {
    @Query("SELECT * FROM device_commands ORDER BY issuedAt DESC")
    fun getAllCommands(): Flow<List<DeviceCommandEntity>>

    @Query("SELECT * FROM device_commands WHERE deviceId = :deviceId ORDER BY issuedAt DESC")
    fun getCommandsForDevice(deviceId: String): Flow<List<DeviceCommandEntity>>

    @Query("SELECT * FROM device_commands WHERE commandId = :commandId LIMIT 1")
    suspend fun getCommandById(commandId: String): DeviceCommandEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: DeviceCommandEntity)

    @Update
    suspend fun updateCommand(command: DeviceCommandEntity)

    @Query("UPDATE device_commands SET status = :status, executionLog = :log WHERE commandId = :commandId")
    suspend fun updateCommandStatus(commandId: String, status: String, log: String?)
}
