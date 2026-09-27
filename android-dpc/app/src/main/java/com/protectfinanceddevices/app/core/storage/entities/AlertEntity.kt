package com.protectfinanceddevices.app.core.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey
    val id: String,
    val deviceId: String?,
    val agreementId: String?,
    val severity: String, // INFO, WARNING, CRITICAL
    val alertType: String, // SIM_CHANGE, PAYMENT_OVERDUE, OFFLINE, IDENTITY_CHANGE
    val title: String,
    val details: String,
    val isAcknowledged: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
