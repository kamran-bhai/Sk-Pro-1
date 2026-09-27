package com.protectfinanceddevices.app.core.storage.dao

import androidx.room.*
import com.protectfinanceddevices.app.core.storage.entities.DeviceEnrollmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceEnrollmentDao {
    @Query("SELECT * FROM device_enrollment LIMIT 1")
    suspend fun getActiveEnrollment(): DeviceEnrollmentEntity?

    @Query("SELECT * FROM device_enrollment LIMIT 1")
    fun getActiveEnrollmentFlow(): Flow<DeviceEnrollmentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEnrollment(enrollment: DeviceEnrollmentEntity)

    @Update
    suspend fun updateEnrollment(enrollment: DeviceEnrollmentEntity)

    @Query("UPDATE device_enrollment SET lastSyncTimestamp = :timestamp WHERE enrollmentId = :enrollmentId")
    suspend fun updateLastSync(enrollmentId: String, timestamp: Long)

    @Query("UPDATE device_enrollment SET enrollmentStatus = :status WHERE enrollmentId = :enrollmentId")
    suspend fun updateEnrollmentStatus(enrollmentId: String, status: String)

    @Query("DELETE FROM device_enrollment")
    suspend fun clearEnrollment()
}
