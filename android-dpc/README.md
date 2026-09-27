# PROTECT YOUR FINANCED DEVICES — Android DPC & Client Application (Phase 1)

This repository contains the production-oriented Android application and Device Policy Controller (DPC) designed for smartphone installment financing management.

---

## 1. Project Overview & Architecture

The application implements a clean, layered architecture separating customer transparency from enterprise device administration:

- **Hardware Security Layer**: Uses `AndroidKeyStore` to generate NIST P-256 (secp256r1) Elliptic Curve keypairs backed by TEE (Trusted Execution Environment) or StrongBox keymaster chips. Public keys are registered with the backend during cryptographic enrollment.
- **DPC / Device Administration Layer**: Uses Android's `DevicePolicyManager` and `DeviceAdminReceiver` to enforce legitimate enterprise lock task kiosk modes (`startLockTask()`), disable safe mode/debugging where permitted, and prevent application uninstallation during the active financing term.
- **Local Persistence Layer (Room)**: Uses SQLite/Room with foreign keys and indices to store customer profiles, device records, installment schedules, nonce-guarded command queues, and security alerts.
- **Presentation Layer**: 100% Jetpack Compose using Material 3 design tokens, unidirectional data flow (UDF), and state observation from Room flows.

---

## 2. Directory Structure

```
/android-dpc/
├── build.gradle.kts                         # Root Gradle build script
├── settings.gradle.kts                      # Gradle settings & repository management
├── gradle.properties                        # JVM memory args & AndroidX flags
└── app/
    ├── build.gradle.kts                     # App module build script (Compose, Room, KSP)
    ├── proguard-rules.pro                   # ProGuard rules for Room & Crypto
    └── src/main/
        ├── AndroidManifest.xml              # Permissions, DPC Receiver, Activities
        ├── res/
        │   ├── xml/
        │   │   └── device_admin_policies.xml # DPC policy XML declaration
        │   └── values/
        │       ├── colors.xml               # Fintech enterprise color palette
        │       └── strings.xml              # UI strings and policy descriptions
        └── java/com/protectfinanceddevices/app/
            ├── FinancedDeviceApplication.kt # Application class initializing Keystore
            ├── MainActivity.kt              # Compose root activity with navigation
            ├── core/
            │   ├── crypto/
            │   │   └── AndroidKeyStoreManager.kt # EC P-256 key generation & signing
            │   ├── dpc/
            │   │   ├── FinancedDeviceAdminReceiver.kt # DeviceAdminReceiver callbacks
            │   │   └── DeviceLockManager.kt          # LockTaskMode, LockNow, Kiosk
            │   └── storage/
            │       ├── AppDatabase.kt       # Room Database with initial seed data
            │       ├── dao/                 # Data Access Objects (Devices, Customers, etc.)
            │       └── entities/            # Room Entity definitions
            └── ui/
                ├── admin/                   # Admin Dashboard, Devices, Financing, Alerts
                ├── customer/                # Customer transparency & support screen
                ├── lock/                    # "DEVICE TEMPORARILY RESTRICTED" Kiosk
                ├── navigation/              # NavRoutes and navigation graphs
                └── theme/                   # Material 3 Color, Type, and Theme
```

---

## 3. How to Build & Run in Android Studio

### Prerequisites:
- **Android Studio Jellyfish | 2024.1.1** or newer.
- **JDK 17** (configured in *Project Structure -> SDK Location -> Gradle JDK*).
- Physical Android device or Android Emulator running **Android 8.0 (API 26)** to **Android 15 (API 35)**.

### Step 1: Open Project in Android Studio
1. Launch Android Studio.
2. Select **Open** and select the `/android-dpc` folder.
3. Allow Gradle to synchronize dependencies.

### Step 2: Build the Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 4. Provisioning Device Owner (DPC) Mode

To test enterprise-grade lock task kiosk mode, uninstallation protection, and debug prevention, the app must be provisioned as a **Device Owner**:

### Option A: Using ADB on a Fresh/Factory-Reset Device or Emulator
1. Enable USB Debugging in Developer Options.
2. Ensure no Google or user accounts are configured on the device (or run on a fresh emulator).
3. Execute the standard Android DPC provision command:
```bash
adb shell dpm set-device-owner com.protectfinanceddevices.app/.core.dpc.FinancedDeviceAdminReceiver
```
4. Confirm output: `Success: Device owner set to package com.protectfinanceddevices.app`.

### Option B: Standard Device Admin Activation (Without Full Reset)
If testing without factory reset:
1. Open the app on the device.
2. Go to **Settings -> Security -> Device Admin Apps**.
3. Toggle on **Protect Your Financed Devices**.

---

## 5. Testing the Implementation

1. **Dashboard Overview**: Launch the app. The dashboard displays seeded financed smartphones, installment statuses, and active alerts.
2. **Device Lock Controller**: Select unit `DEV-9920-PIX` (Pixel 9 Pro). Click **Lock Device** to trigger a signed lock command.
3. **Restricted Kiosk Lock Screen**: Test the restricted screen preview. Notice the business name, agreement code, support dialer button, and emergency call access.
4. **Financing Ledger**: Open the **Financing** tab to inspect the installment schedule and click **Pay** to record a payment, recalculating the customer's remaining debt.
5. **Security Attestation**: Open the **Security** tab to verify that the hardware-backed NIST P-256 EC key is generated and stored in TEE / StrongBox.
