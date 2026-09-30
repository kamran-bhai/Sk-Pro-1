package com.protectfinanceddevices.app.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.protectfinanceddevices.app.core.storage.entities.InstallmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentDao {

    @Query("SELECT * FROM installments ORDER BY agreementId, installmentNumber ASC")
    fun getAllInstallments(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE agreementId = :agreementId ORDER BY installmentNumber ASC")
    fun getInstallmentsForAgreement(agreementId: String): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE status = 'OVERDUE'")
    fun getOverdueInstallments(): Flow<List<InstallmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallment(installment: InstallmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallments(installments: List<InstallmentEntity>)

    @Update
    suspend fun updateInstallment(installment: InstallmentEntity)

    @Query("SELECT * FROM installments WHERE id = :installmentId LIMIT 1")
    suspend fun getInstallmentById(installmentId: String): InstallmentEntity?

    @Query("SELECT * FROM installments WHERE agreementId = :agreementId ORDER BY installmentNumber ASC")
    suspend fun getInstallmentsForAgreementOnce(agreementId: String): List<InstallmentEntity>

    @Query("UPDATE installments SET status = 'PAID', paidDate = :paidDate WHERE id = :installmentId AND status != 'PAID'")
    suspend fun markInstallmentPaid(
        installmentId: String,
        paidDate: String
    ): Int
}
