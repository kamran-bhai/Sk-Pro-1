package com.protectfinanceddevices.app.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.protectfinanceddevices.app.core.storage.entities.AgreementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgreementDao {
    @Query("SELECT * FROM agreements ORDER BY startDate DESC")
    fun getAllAgreements(): Flow<List<AgreementEntity>>

    @Query("SELECT * FROM agreements WHERE id = :id LIMIT 1")
    suspend fun getAgreementById(id: String): AgreementEntity?

    @Query("SELECT * FROM agreements WHERE customerId = :customerId LIMIT 1")
    suspend fun getAgreementByCustomerId(customerId: String): AgreementEntity?

    @Query("SELECT * FROM agreements WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getAgreementByDeviceId(deviceId: String): AgreementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgreement(agreement: AgreementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgreements(agreements: List<AgreementEntity>)

    @Update
    suspend fun updateAgreement(agreement: AgreementEntity)
}
