package com.protectfinanceddevices.app

import android.app.Application
import android.util.Log
import com.protectfinanceddevices.app.core.crypto.AndroidKeyStoreManager
import com.protectfinanceddevices.app.core.storage.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FinancedDeviceApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val keyStoreManager: AndroidKeyStoreManager by lazy { AndroidKeyStoreManager() }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing Protect Your Financed Devices application engine...")
        
        // Ensure hardware-backed keypair exists or generate on first launch
        applicationScope.launch {
            try {
                val publicKeyPem = keyStoreManager.getOrCreateEnrollmentKeyPair()
                Log.i(TAG, "Hardware Keystore cryptographic identity initialized: ${publicKeyPem.take(32)}...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing Hardware Keystore: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "FinancedDeviceApp"
    }
}
