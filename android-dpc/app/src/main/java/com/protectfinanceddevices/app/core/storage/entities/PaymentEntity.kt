package com.protectfinanceddevices.app.core.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    indices = [
        Index("agreementId"),
        Index(value = ["installmentId"], unique = true)
    ]
)
data class PaymentEntity(
    @PrimaryKey
    val id: String,
    val agreementId: String,
    val installmentId: String,
    val amount: Double,
    val paidAt: String,
    val recordedAt: Long = System.currentTimeMillis(),
    val note: String? = null
)
