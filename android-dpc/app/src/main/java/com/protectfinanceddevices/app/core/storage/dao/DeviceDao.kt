package com.protectfinanceddevices.app.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY enrolledAt DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    fun getDeviceByIdFlow(id: String): Flow<DeviceEntity?>

    @Query("SELECT COUNT(*) FROM devices")
    fun getTotalDevicesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM devices WHERE enrollmentStatus = 'ACTIVE'")
    fun getActiveDevicesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM devices WHERE enrollmentStatus = 'OVERDUE'")
    fun getOverdueDevicesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM devices WHERE enrollmentStatus = 'LOCKED'")
    fun getLockedDevicesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM devices WHERE isOnline = 0")
    fun getOfflineDevicesCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<DeviceEntity>)

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Query("UPDATE devices SET enrollmentStatus = :status WHERE id = :deviceId")
    suspend fun updateDeviceStatus(deviceId: String, status: String)
}
