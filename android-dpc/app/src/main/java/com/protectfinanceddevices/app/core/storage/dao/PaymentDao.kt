package com.protectfinanceddevices.app.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.protectfinanceddevices.app.core.storage.entities.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payments ORDER BY recordedAt DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE agreementId = :agreementId ORDER BY recordedAt DESC")
    fun getPaymentsForAgreement(agreementId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE installmentId = :installmentId LIMIT 1")
    suspend fun getPaymentForInstallment(installmentId: String): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity)
}
