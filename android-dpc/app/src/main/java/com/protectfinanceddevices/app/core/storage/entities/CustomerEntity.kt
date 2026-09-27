package com.protectfinanceddevices.app.core.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: String,
    val fullName: String,
    val phoneNumber: String,
    val email: String?,
    val address: String?,
    val nationalIdMasked: String?,
    val createdAt: Long = System.currentTimeMillis()
)
