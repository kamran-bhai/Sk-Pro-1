package com.protectfinanceddevices.app.core.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_logs",
    indices = [Index("entityId"), Index("createdAt")]
)
data class AuditLogEntity(
    @PrimaryKey
    val id: String,
    val actorType: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val details: String,
    val createdAt: Long = System.currentTimeMillis()
)
