package com.protectfinanceddevices.app.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Manages device-bound cryptographic identities using the Android Keystore system.
 * Generates NIST P-256 (secp256r1) Elliptic Curve keypairs backed by TEE or StrongBox.
 */
class AndroidKeyStoreManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Retrieves the existing device enrollment public key, or creates a new
     * hardware-backed EC P-256 keypair if none exists.
     * Returns PEM formatted Base64 public key.
     */
    fun getOrCreateEnrollmentKeyPair(): String {
        if (!keyStore.containsAlias(ENROLLMENT_KEY_ALIAS)) {
            generateEnrollmentKeyPair()
        }
        val publicKey = getPublicKey()
            ?: throw IllegalStateException("Failed to load enrollment public key from Android Keystore")
        return encodePublicKeyToPem(publicKey)
    }

    /**
     * Generates a new hardware-isolated EC P-256 keypair.
     */
    private fun generateEnrollmentKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            ENROLLMENT_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            setUserAuthenticationRequired(false) // Non-interactive background telemetry signing
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // StrongBox Keymaster support where hardware chips exist
                try {
                    setIsStrongBoxBacked(true)
                } catch (e: Exception) {
                    // Fall back gracefully to standard TEE if StrongBox is unavailable
                }
            }
        }

        keyPairGenerator.initialize(builder.build())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Signs data using the device's private key inside Android Keystore.
     */
    fun signData(data: ByteArray): String {
        val privateKeyEntry = keyStore.getEntry(ENROLLMENT_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Key alias not found in Keystore")

        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKeyEntry.privateKey)
            update(data)
        }
        val signatureBytes = signature.sign()
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    /**
     * Verifies if the private key resides in TEE/Secure Hardware.
     */
    fun isHardwareBacked(): Boolean {
        return try {
            val privateKeyEntry = keyStore.getEntry(ENROLLMENT_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: return false
            val factory = KeyFactory.getInstance(privateKeyEntry.privateKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(privateKeyEntry.privateKey, KeyInfo::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                        keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getPublicKey(): PublicKey? {
        val certificate = keyStore.getCertificate(ENROLLMENT_KEY_ALIAS) ?: return null
        return certificate.publicKey
    }

    private fun encodePublicKeyToPem(publicKey: PublicKey): String {
        val base64Encoded = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$base64Encoded\n-----END PUBLIC KEY-----"
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ENROLLMENT_KEY_ALIAS = "com.protectfinanceddevices.enrollment_key"
    }
}
