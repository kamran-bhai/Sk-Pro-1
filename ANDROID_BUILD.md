# SK Pro Android Build & Deployment Guide

This document provides exact, step-by-step instructions for compiling and packaging the **SK Pro** client application (`/android-dpc`), targeting the live authority backend, and deploying the compiled APK to customer devices.

---

## 1. Architecture & Security Overview

The SK Pro Android client is a production-grade enterprise application and Device Policy Controller (DPC) designed for smartphone financing protection:
* **Hardware Attestation**: Private keys reside inside **Android Keystore (StrongBox Keymaster / TEE)** and never leave the device.
* **Mutual Challenge-Response**: Every enrollment and periodic heartbeat is cryptographically signed using NIST P-256 (`secp256r1`) Elliptic Curve cryptography.
* **Strict Transparency**: No hidden surveillance, no secret access to personal files, photos, or messages. Only authorized contract compliance state is managed.
* **Background Telemetry**: Uses **Android WorkManager** with network constraints and exponential backoff retry. Android Doze mode and OEM battery optimization guidelines are strictly respected.

---

## 2. Backend URL Configuration

The Android client is configured to connect to the live backend authority:

* **Production / Dev Authority URL**:
  `https://ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app`

* **Single Source of Truth File**:
  `android-dpc/app/src/main/java/com/protectfinanceddevices/app/core/network/ApiConfig.kt`

```kotlin
object ApiConfig {
    const val DEFAULT_BASE_URL = "https://ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app"
    const val API_PREFIX = "/api/v1"
    
    // Core Endpoints
    const val ENDPOINT_ENROLLMENT_DISCLOSURE = "$API_PREFIX/enrollments" // GET /:code
    const val ENDPOINT_ENROLLMENT_CHALLENGE = "$API_PREFIX/enrollments"  // POST /:id/challenge
    const val ENDPOINT_ENROLLMENT_VERIFY = "$API_PREFIX/enrollments"     // POST /:id/verify
    const val ENDPOINT_HEARTBEAT = "$API_PREFIX/device/heartbeat"         // POST
    const val ENDPOINT_DEVICE_STATUS = "$API_PREFIX/devices"             // GET /:enrollmentId/status
}
```

---

## 3. How to Build the APK

### Option A: Local Build with Android Studio (Recommended)

1. **Prerequisites**:
   * Android Studio (Hedgehog 2023.1.1 or Ladybug 2024.2+ recommended)
   * JDK 17 (Eclipse Temurin or Android Studio bundled JDK)
   * Android SDK Platform 35 and Build-Tools 35.0.0

2. **Open the Project**:
   * Launch Android Studio.
   * Select **File > Open...** and navigate to the `/android-dpc` directory in this repository.
   * Allow Gradle to sync dependencies from Google Maven and Maven Central.

3. **Build the Debug APK**:
   * In Android Studio top menu, select:
     `Build > Build Bundle(s) / APK(s) > Build APK(s)`
   * Alternatively, open the **Terminal** tab inside Android Studio and run:
     ```bash
     ./gradlew assembleDebug
     ```

4. **Output Location**:
   The generated APK will be produced at:
   ```
   android-dpc/app/build/outputs/apk/debug/app-debug.apk
   ```

---

### Option B: Automated Build via GitHub Actions CI/CD (Zero Local Setup)

A production CI/CD workflow is included at `.github/workflows/android-build.yml`.

1. **Triggering the Build**:
   * Push your changes to GitHub (`main` or `master` branch), or:
   * Go to **GitHub > Actions > Build SK Pro Android APK > Run workflow**.

2. **CI Pipeline Steps**:
   * Checks out repository
   * Configures JDK 17 (`temurin`)
   * Configures Android SDK Build-Tools 35
   * Executes `./gradlew assembleDebug`
   * Uploads `app-debug.apk` as a downloadable artifact named `sk-pro-debug-apk`.

3. **Downloading the APK**:
   * In GitHub, navigate to the completed workflow run.
   * Scroll down to **Artifacts** and download `sk-pro-debug-apk.zip`.
   * Extract `app-debug.apk`.

---

## 4. Serving the APK from the Backend

Once built, copy `app-debug.apk` into:
```
android-dpc/app/build/outputs/apk/debug/app-debug.apk
```
The backend server will automatically serve this file via:
```
GET /download/app.apk
```
* **Content-Type**: `application/vnd.android.package-archive`
* **Content-Disposition**: `attachment; filename="sk-pro.apk"`

If the APK has not been compiled yet, the endpoint returns an informative HTTP 404 JSON explaining that an external build must first be triggered.

---

## 5. Customer Installation & Device Provisioning Steps

1. **Build or Download the APK**:
   Obtain `app-debug.apk` via Android Studio or GitHub Actions.

2. **Transfer to Customer Device**:
   * Transfer via USB cable (`adb install -r app-debug.apk`), direct browser download, or local distribution.

3. **Enable Install from Unknown Sources (if required)**:
   * Android will request permission: *"For your security, your phone is not allowed to install unknown apps from this source."*
   * Tap **Settings** and toggle **Allow from this source**.

4. **Install SK Pro**:
   * Tap **Install** to complete standard package installation.

5. **Device Owner Provisioning (Optional / Factory Reset Mode)**:
   * To provision as full enterprise Device Owner on Android 8.0–15:
     ```bash
     adb shell dpm set-device-owner com.protectfinanceddevices.app/.core.dpc.FinancedDeviceAdminReceiver
     ```

6. **Launch & Complete Authorized Onboarding**:
   * Open **SK Pro**.
   * Enter the customer's one-time pairing code (e.g. `ENR-8841-CODE`) or scan the QR code generated in the Admin Operations Dashboard.
   * Review financing disclosures, accept terms, and allow Android Keystore hardware key generation.
   * Upon completion, the device transitions to **PROTECTED** and registers an initial online heartbeat with the server.

---

## 6. Development/Debug vs Production Release Configuration

### A. Development & Debug Builds (Pre-configured)
The project comes pre-configured with a standard debug build type in `android-dpc/app/build.gradle.kts`:
* Uses default Android SDK debug keystore (`~/.android/debug.keystore`) generated automatically by Gradle/Android Studio.
* Uses `applicationIdSuffix = ".debug"` so it can be installed alongside any production builds for side-by-side testing.
* `isDebuggable = true` to allow ADB logcat inspection and network debugging against `https://ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app`.

### B. Production Release Signing (Security Guidelines)
**CRITICAL SECURITY RULE:** Never commit production private signing keystores (`.jks` or `.keystore`) or passwords to this repository. Do not use dummy or fake production signing keys.

For production releases:
1. **Generate your official organizational keystore** on a secure workstation:
   ```bash
   keytool -genkeypair -v -keystore sk-pro-release.jks -alias skpro -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **Inject credentials via Environment Variables or GitHub Secrets** (never committed to code):
   - `KEYSTORE_BASE64`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
3. In `android-dpc/app/build.gradle.kts`, configure the signingConfigs block dynamically:
   ```kotlin
   signingConfigs {
       create("release") {
           val keystorePath = System.getenv("KEYSTORE_PATH")
           if (keystorePath != null && file(keystorePath).exists()) {
               storeFile = file(keystorePath)
               storePassword = System.getenv("KEYSTORE_PASSWORD")
               keyAlias = System.getenv("KEY_ALIAS")
               keyPassword = System.getenv("KEY_PASSWORD")
           }
       }
   }
   ```

---

## 7. Troubleshooting & Common Issues

| Issue | Cause | Resolution |
|---|---|---|
| `JAVA_HOME is not set` | Local terminal lacks JDK 17 environment variable | Install JDK 17 and export `JAVA_HOME=/path/to/jdk-17` |
| Gradle sync failed / timeout | Firewall blocking Google Maven repository | Verify internet connection or configure proxy in `gradle.properties` |
| Keystore StrongBox not supported on device | Budget device lacks dedicated StrongBox chip | App automatically falls back to standard TEE (hardware backed) |
| Heartbeat delayed past 15 minutes | Android Doze mode / aggressive OEM battery management | WorkManager batches heartbeats during maintenance windows; normal and compliant with OS policy |
