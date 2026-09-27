package com.protectfinanceddevices.app.core.storage.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agreements",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("customerId"), Index("deviceId")]
)
data class AgreementEntity(
    @PrimaryKey
    val id: String,
    val customerId: String,
    val deviceId: String,
    val totalFinancedAmount: Double,
    val downPayment: Double,
    val remainingAmount: Double,
    val installmentAmount: Double,
    val numberOfInstallments: Int,
    val paidInstallments: Int,
    val remainingInstallments: Int,
    val startDate: String,
    val nextDueDate: String,
    val gracePeriodDays: Int,
    val status: String // ACTIVE, PAID, OVERDUE, COMPLETED, CANCELLED
)
