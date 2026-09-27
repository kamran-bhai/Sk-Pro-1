package com.protectfinanceddevices.app.core.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val id: String,
    val customerId: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val enrollmentStatus: String, // ACTIVE, LOCKED, OVERDUE, SUSPENDED, COMPLETED, OFFLINE
    val managementMode: String,   // DEVICE_OWNER, DEVICE_ADMIN, UNMANAGED
    val enrollmentPublicKey: String,
    val lastSeenTimestamp: Long,
    val batteryPercent: Int,
    val isOnline: Boolean,
    val simCarrier: String?,
    val usbDebuggingActive: Boolean,
    val enrolledAt: Long = System.currentTimeMillis()
)
