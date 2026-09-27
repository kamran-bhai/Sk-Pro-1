package com.protectfinanceddevices.app.core.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists local client device enrollment and telemetry synchronization state.
 * Stores server connection parameters, assigned enrollment identifier, and sync timestamps.
 */
@Entity(tableName = "device_enrollment")
data class DeviceEnrollmentEntity(
    @PrimaryKey
    val enrollmentId: String,
    val deviceId: String,
    val customerId: String,
    val agreementId: String,
    var enrollmentStatus: String, // PENDING, ACTIVE, SUSPENDED, REVOKED, LOCKED
    var managementMode: String,   // DEVICE_OWNER, DEVICE_ADMIN, UNMANAGED
    val serverUrl: String,
    var lastSyncTimestamp: Long = 0L,
    var lastHeartbeatNonce: String? = null,
    var enrolledAt: Long = System.currentTimeMillis()
)
