package com.protectfinanceddevices.app.core.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_commands")
data class DeviceCommandEntity(
    @PrimaryKey
    val commandId: String,
    val deviceId: String,
    val commandType: String, // LOCK_DEVICE, UNLOCK_DEVICE, STATUS_REQUEST, LOCATION_REQUEST, REFRESH_POLICIES
    val status: String,      // PENDING, SENT, ACKNOWLEDGED, FAILED, EXPIRED
    val nonce: String,
    val serverSignature: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val executionLog: String? = null
)
