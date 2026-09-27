package com.protectfinanceddevices.app.core.storage.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "installments",
    foreignKeys = [
        ForeignKey(
            entity = AgreementEntity::class,
            parentColumns = ["id"],
            childColumns = ["agreementId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("agreementId")]
)
data class InstallmentEntity(
    @PrimaryKey
    val id: String,
    val agreementId: String,
    val installmentNumber: Int,
    val dueDate: String,
    val amount: Double,
    val penaltyFee: Double = 0.0,
    val status: String, // PENDING, PAID, OVERDUE, WAIVED
    val paidDate: String? = null
)
