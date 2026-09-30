package com.protectfinanceddevices.app.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.protectfinanceddevices.app.core.storage.dao.*
import com.protectfinanceddevices.app.core.storage.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CustomerEntity::class,
        DeviceEntity::class,
        AgreementEntity::class,
        InstallmentEntity::class,
        PaymentEntity::class,
        AuditLogEntity::class,
        AlertEntity::class,
        DeviceCommandEntity::class,
        DeviceEnrollmentEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun deviceDao(): DeviceDao
    abstract fun agreementDao(): AgreementDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun paymentDao(): PaymentDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun alertDao(): AlertDao
    abstract fun deviceCommandDao(): DeviceCommandDao
    abstract fun deviceEnrollmentDao(): DeviceEnrollmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "protect_financed_devices.db"
                )
                .addCallback(DatabaseCallback())
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        seedInitialData(database)
                    }
                }
            }
        }

        private suspend fun seedInitialData(db: AppDatabase) {
            val customer1 = CustomerEntity(
                id = "CUST-001",
                fullName = "Marcus Vance",
                phoneNumber = "+1 (555) 234-8901",
                email = "m.vance@example.com",
                address = "742 Evergreen Terrace, Sector 4",
                nationalIdMasked = "ID-***-8821"
            )
            val customer2 = CustomerEntity(
                id = "CUST-002",
                fullName = "Elena Rostova",
                phoneNumber = "+1 (555) 871-3329",
                email = "elena.r@example.com",
                address = "1204 Biscayne Blvd, Suite 300",
                nationalIdMasked = "ID-***-4419"
            )
            db.customerDao().insertCustomers(listOf(customer1, customer2))

            val device1 = DeviceEntity(
                id = "DEV-8841-SAM",
                customerId = "CUST-001",
                model = "Galaxy S24 Ultra",
                manufacturer = "Samsung",
                androidVersion = "Android 14 (API 34)",
                enrollmentStatus = "ACTIVE",
                managementMode = "DEVICE_OWNER",
                enrollmentPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
                lastSeenTimestamp = System.currentTimeMillis() - 120_000,
                batteryPercent = 88,
                isOnline = true,
                simCarrier = "Verizon Wireless",
                usbDebuggingActive = false
            )
            val device2 = DeviceEntity(
                id = "DEV-9920-PIX",
                customerId = "CUST-002",
                model = "Pixel 9 Pro",
                manufacturer = "Google",
                androidVersion = "Android 15 (API 35)",
                enrollmentStatus = "OVERDUE",
                managementMode = "DEVICE_OWNER",
                enrollmentPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEB...",
                lastSeenTimestamp = System.currentTimeMillis() - 7_200_000,
                batteryPercent = 42,
                isOnline = true,
                simCarrier = "T-Mobile USA",
                usbDebuggingActive = true
            )
            db.deviceDao().insertDevices(listOf(device1, device2))

            val agreement1 = AgreementEntity(
                id = "AGR-2026-001",
                customerId = "CUST-001",
                deviceId = "DEV-8841-SAM",
                totalFinancedAmount = 1299.99,
                downPayment = 200.00,
                remainingAmount = 733.33,
                installmentAmount = 91.66,
                numberOfInstallments = 12,
                paidInstallments = 6,
                remainingInstallments = 6,
                startDate = "2026-03-15",
                nextDueDate = "2026-10-15",
                gracePeriodDays = 5,
                status = "ACTIVE"
            )
            val agreement2 = AgreementEntity(
                id = "AGR-2026-002",
                customerId = "CUST-002",
                deviceId = "DEV-9920-PIX",
                totalFinancedAmount = 1099.00,
                downPayment = 150.00,
                remainingAmount = 949.00,
                installmentAmount = 94.90,
                numberOfInstallments = 10,
                paidInstallments = 1,
                remainingInstallments = 9,
                startDate = "2026-07-01",
                nextDueDate = "2026-09-01",
                gracePeriodDays = 5,
                status = "OVERDUE"
            )
            db.agreementDao().insertAgreements(listOf(agreement1, agreement2))

            val installments = listOf(
                InstallmentEntity("INS-101", "AGR-2026-001", 1, "2026-04-15", 91.66, 0.0, "PAID", "2026-04-14"),
                InstallmentEntity("INS-102", "AGR-2026-001", 2, "2026-05-15", 91.66, 0.0, "PAID", "2026-05-14"),
                InstallmentEntity("INS-103", "AGR-2026-001", 3, "2026-06-15", 91.66, 0.0, "PAID", "2026-06-15"),
                InstallmentEntity("INS-201", "AGR-2026-002", 1, "2026-08-01", 94.90, 0.0, "PAID", "2026-08-01"),
                InstallmentEntity("INS-202", "AGR-2026-002", 2, "2026-09-01", 94.90, 15.0, "OVERDUE")
            )
            db.installmentDao().insertInstallments(installments)

            val alert1 = AlertEntity(
                id = "ALT-01",
                deviceId = "DEV-9920-PIX",
                agreementId = "AGR-2026-002",
                severity = "CRITICAL",
                alertType = "PAYMENT_OVERDUE",
                title = "Payment Default Threshold Exceeded",
                details = "Agreement AGR-2026-002 is 24 days overdue. Device policy lock eligible as per financing contract terms.",
                isAcknowledged = false
            )
            val alert2 = AlertEntity(
                id = "ALT-02",
                deviceId = "DEV-9920-PIX",
                agreementId = "AGR-2026-002",
                severity = "WARNING",
                alertType = "DEBUG_ENABLED",
                title = "USB Debugging Detected",
                details = "Developer options or USB debugging has been enabled on managed unit DEV-9920-PIX.",
                isAcknowledged = false
            )
            db.alertDao().insertAlerts(listOf(alert1, alert2))
        }
    }
}
