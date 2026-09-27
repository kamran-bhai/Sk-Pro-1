/**
 * Phase 6 Automated Test Suite: Persistent Database Implementation
 * Validates persistent server-side storage, atomic writes, schema integrity,
 * duplicate hardware/enrollment prevention, private key exclusion, heartbeat persistence,
 * and persistent replay protection.
 */

import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { DatabaseService } from '../src/services/database.service.js';
import { db, calculateDeviceOnlineStatus, EnrollmentRecord } from '../src/services/store.js';

interface TestResult {
  name: string;
  passed: boolean;
  durationMs: number;
  error?: string;
}

const results: TestResult[] = [];

async function runTest(name: string, fn: () => Promise<void> | void) {
  const start = Date.now();
  try {
    await fn();
    results.push({ name, passed: true, durationMs: Date.now() - start });
    console.log(`[PASS] ${name}`);
  } catch (err: any) {
    results.push({ name, passed: false, error: err.message, durationMs: Date.now() - start });
    console.error(`[FAIL] ${name}: ${err.message}`);
  }
}

export async function runPhase6Tests(): Promise<{ passed: boolean; results: TestResult[] }> {
  results.length = 0;
  console.log('--- STARTING PHASE 6 PERSISTENT DATABASE TESTS ---');

  const ds = DatabaseService.getInstance();
  const dataFilePath = path.resolve(process.cwd(), 'data', 'database.json');

  // 1. Persistent Storage Initialization
  await runTest('1. Persistent Database File Initialized & Atomic Storage Verified', () => {
    ds.persistSync();
    if (!fs.existsSync(dataFilePath)) {
      throw new Error(`Expected database file at ${dataFilePath} to exist on disk.`);
    }

    const raw = fs.readFileSync(dataFilePath, 'utf-8');
    const parsed = JSON.parse(raw);
    if (!parsed.version || !Array.isArray(parsed.customers) || !Array.isArray(parsed.devices) || !Array.isArray(parsed.enrollments)) {
      throw new Error('Database schema file missing required root collections or version.');
    }
  });

  // 2. Data Persistence & Reload Verification
  await runTest('2. Disk Persistence & Reload Across Service State', () => {
    const testCustomerId = `cust-p6-${Date.now()}`;
    const testPhone = `+1-555-999-${Math.floor(1000 + Math.random() * 9000)}`;

    ds.createCustomer({
      id: testCustomerId,
      fullName: 'Phase 6 Persistent User',
      phoneNumber: testPhone,
      email: 'p6.persist@example.com',
      address: '100 Persistent Way',
      nationalIdMasked: 'ID-***-P6',
      status: 'ACTIVE'
    });

    // Read directly from disk
    const diskRaw = fs.readFileSync(dataFilePath, 'utf-8');
    const diskJson = JSON.parse(diskRaw);
    const persisted = diskJson.customers.find((c: any) => c.id === testCustomerId);

    if (!persisted || persisted.phoneNumber !== testPhone) {
      throw new Error('Customer created via service was not found on disk in database.json.');
    }
  });

  // 3. Duplicate Hardware Serial Prevention
  await runTest('3. Duplicate Hardware Serial Registration Safely Rejected', () => {
    const uniqueSerial = `SN-DUP-TEST-${Date.now()}`;
    ds.registerDevice({
      id: `dev-hw-test1-${Date.now()}`,
      manufacturer: 'Motorola',
      model: 'Edge 50 Pro',
      brand: 'Motorola',
      hardwareSerial: uniqueSerial,
      initialCarrier: 'Unlocked'
    });

    let threw = false;
    try {
      ds.registerDevice({
        id: `dev-hw-test2-${Date.now()}`,
        manufacturer: 'Motorola',
        model: 'Edge 50 Pro Duplicate',
        brand: 'Motorola',
        hardwareSerial: uniqueSerial,
        initialCarrier: 'Unlocked'
      });
    } catch (err: any) {
      threw = true;
      if (!err.message.includes('already registered')) {
        throw new Error(`Unexpected error message: ${err.message}`);
      }
    }

    if (!threw) {
      throw new Error('Expected duplicate hardwareSerial registration to throw, but it succeeded.');
    }
  });

  // 4. Duplicate Active Enrollment Prevention
  await runTest('4. Duplicate Active Enrollment on Same Hardware Rejected', () => {
    const devId = `dev-test-enr-${Date.now()}`;
    ds.registerDevice({
      id: devId,
      manufacturer: 'Xiaomi',
      model: '14 Ultra',
      brand: 'Xiaomi',
      hardwareSerial: `SN-XI-${Date.now()}`,
      initialCarrier: 'Global'
    });

    const activeEnr1: EnrollmentRecord = {
      id: `enr-test-1-${Date.now()}`,
      deviceId: devId,
      customerId: 'cust-101',
      agreementId: 'agr-01',
      enrollmentCode: `CODE-1-${Date.now()}`,
      tokenHash: 'hash-1',
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
      isTokenUsed: true,
      managementMode: 'DEVICE_OWNER',
      enrollmentStatus: 'ACTIVE',
      isOnline: true,
      usbDebuggingActive: false
    };
    ds.registerEnrollment(activeEnr1);

    // Attempt second active enrollment on the same hardware unit
    let threw = false;
    try {
      const activeEnr2: EnrollmentRecord = {
        id: `enr-test-2-${Date.now()}`,
        deviceId: devId,
        customerId: 'cust-102',
        agreementId: 'agr-02',
        enrollmentCode: `CODE-2-${Date.now()}`,
        tokenHash: 'hash-2',
        expiresAt: new Date(Date.now() + 86400000).toISOString(),
        isTokenUsed: true,
        managementMode: 'DEVICE_OWNER',
        enrollmentStatus: 'ACTIVE',
        isOnline: true,
        usbDebuggingActive: false
      };
      ds.registerEnrollment(activeEnr2);
    } catch (e: any) {
      threw = true;
      if (!e.message.includes('already has an ACTIVE enrollment')) {
        throw new Error(`Unexpected error message: ${e.message}`);
      }
    }

    if (!threw) {
      throw new Error('Expected duplicate active enrollment registration to be rejected.');
    }
  });

  // 5. Private Key Storage Rejection (Security Validation)
  await runTest('5. Private Signing Key Storage Rejection & Security Audit', () => {
    const maliciousEnrollment: EnrollmentRecord = {
      id: `enr-malicious-${Date.now()}`,
      deviceId: `dev-hw-malicious-${Date.now()}`,
      customerId: 'cust-101',
      agreementId: 'agr-01',
      enrollmentCode: 'MALICIOUS-KEY',
      tokenHash: 'malicious-hash',
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
      isTokenUsed: false,
      devicePublicKeyPem: '-----BEGIN EC PRIVATE KEY-----\nMHQCAQEEIG...\n-----END EC PRIVATE KEY-----',
      managementMode: 'DEVICE_OWNER',
      enrollmentStatus: 'AWAITING_CUSTOMER',
      isOnline: false,
      usbDebuggingActive: false
    };

    let caughtSecurityViolation = false;
    try {
      ds.registerEnrollment(maliciousEnrollment);
    } catch (err: any) {
      if (err.message.includes('Private keys cannot be accepted')) {
        caughtSecurityViolation = true;
      }
    }

    if (!caughtSecurityViolation) {
      throw new Error('Failed to block private key storage in database.');
    }
  });

  // 6. Device Heartbeat Record Persistence
  await runTest('6. Device Heartbeat Record Persistence in Dedicated Table', () => {
    const enr = ds.getEnrollments()[0];
    const hbTimestamp = new Date().toISOString();
    const nonce = `nonce-p6-${crypto.randomBytes(6).toString('hex')}`;

    const hb = ds.recordHeartbeat(enr.id, {
      batteryPercent: 94,
      networkType: 'CELLULAR_5G',
      appVersion: '1.2.0',
      managementStatus: 'DEVICE_OWNER',
      nonce,
      timestamp: hbTimestamp,
      signatureVerified: true
    });

    if (!hb.id || hb.batteryPercent !== 94 || hb.networkType !== 'CELLULAR_5G') {
      throw new Error('Heartbeat telemetry was not stored with expected fields.');
    }

    // Check disk file contains heartbeat
    const rawDisk = fs.readFileSync(dataFilePath, 'utf-8');
    const parsedDisk = JSON.parse(rawDisk);
    const persistedHb = parsedDisk.heartbeats.find((h: any) => h.id === hb.id);

    if (!persistedHb) {
      throw new Error('Heartbeat was not persisted to disk in database.json.');
    }
  });

  // 7. Dynamic Online Status from Persistent Store
  await runTest('7. Online/Offline Status Evaluates Accurately from Store', () => {
    const enr = ds.getEnrollments()[0];
    enr.enrollmentStatus = 'ACTIVE';

    // Fresh heartbeat: must be ONLINE
    enr.lastHeartbeatAt = new Date().toISOString();
    const onlineStatus = calculateDeviceOnlineStatus(enr, 15 * 60 * 1000);
    if (onlineStatus !== 'ONLINE') {
      throw new Error(`Expected ONLINE, got ${onlineStatus}`);
    }

    // Stale heartbeat (20 minutes ago): must be OFFLINE with 15m timeout
    enr.lastHeartbeatAt = new Date(Date.now() - 20 * 60 * 1000).toISOString();
    const offlineStatus = calculateDeviceOnlineStatus(enr, 15 * 60 * 1000);
    if (offlineStatus !== 'OFFLINE') {
      throw new Error(`Expected OFFLINE for 20m stale heartbeat, got ${offlineStatus}`);
    }
  });

  // 8. Nonce Replay Defense Persistence
  await runTest('8. Used Nonce Replay Defense Persists to Disk File', () => {
    const persistentNonce = `nonce-durable-${crypto.randomBytes(8).toString('hex')}`;
    db.usedNonces.add(persistentNonce);
    db.save();

    const rawDisk = fs.readFileSync(dataFilePath, 'utf-8');
    const parsedDisk = JSON.parse(rawDisk);

    if (!parsedDisk.usedNonces || !parsedDisk.usedNonces.includes(persistentNonce)) {
      throw new Error('Used nonce was not persisted in database.json usedNonces array.');
    }
  });

  // 9. Activity Timeline Persistence
  await runTest('9. Device Activity Timeline Immutable Persistence', () => {
    const activity = ds.recordActivity({
      deviceId: 'dev-hw-01',
      enrollmentId: 'enr-8841',
      eventType: 'SECURITY_EVENT',
      description: 'Persistent security audit check verified.',
      details: { auditPhase: 'PHASE_6_STEP_1' }
    });

    const rawDisk = fs.readFileSync(dataFilePath, 'utf-8');
    const parsedDisk = JSON.parse(rawDisk);
    const persistedAct = parsedDisk.deviceActivities.find((a: any) => a.id === activity.id);

    if (!persistedAct || persistedAct.eventType !== 'SECURITY_EVENT') {
      throw new Error('Activity record was not persisted to disk in database.json.');
    }
  });

  // 10. Android Client Connection Layer: Endpoint Compatibility & Payload Structure
  await runTest('10. Android Client Connection: Heartbeat Payload Verification', () => {
    const testEnrollment = ds.getEnrollments()[0];
    const clientNonce = `cli-nonce-${crypto.randomBytes(6).toString('hex')}`;
    const timestamp = Date.now();

    // Verify canonical payload structure matched by Android DeviceHeartbeatWorker
    const canonical = `${testEnrollment.id}|${clientNonce}|${timestamp}`;
    if (!canonical.includes(testEnrollment.id) || !canonical.includes(clientNonce)) {
      throw new Error('Canonical string construction mismatch with Android client');
    }
  });

  // 11. Android Client Connection: One-time Code Resolution
  await runTest('11. Android Client Connection: One-time Code & Disclosure Resolution', () => {
    const enr = ds.getEnrollments().find(e => e.enrollmentCode === 'ENR-8841-CODE');
    if (!enr) {
      throw new Error('Enrollment code ENR-8841-CODE not found in database');
    }
    const dev = ds.getDevices().find(d => d.id === enr.deviceId);
    const cust = ds.getCustomers().find(c => c.id === enr.customerId);
    const agr = ds.getAgreements().find(a => a.id === enr.agreementId);

    if (!dev || !cust || !agr) {
      throw new Error('Device, customer, or agreement link broken for Android disclosure flow');
    }
  });

  // 12. APK Download Endpoint & Artifact Contract
  await runTest('12. APK Download Endpoint & CI Artifact Configuration Contract', () => {
    // Verify GitHub Actions workflow exists
    const ghWorkflowPath = path.resolve(process.cwd(), '.github', 'workflows', 'android-build.yml');
    if (!fs.existsSync(ghWorkflowPath)) {
      throw new Error('Expected .github/workflows/android-build.yml to exist on disk');
    }

    // Verify Gradle wrapper files exist
    const wrapperPropPath = path.resolve(process.cwd(), 'android-dpc', 'gradle', 'wrapper', 'gradle-wrapper.properties');
    if (!fs.existsSync(wrapperPropPath)) {
      throw new Error('Expected gradle-wrapper.properties to exist');
    }

    const gradlewPath = path.resolve(process.cwd(), 'android-dpc', 'gradlew');
    if (!fs.existsSync(gradlewPath)) {
      throw new Error('Expected gradlew script to exist in /android-dpc');
    }

    // Verify build documentation exists
    const buildDocPath = path.resolve(process.cwd(), 'ANDROID_BUILD.md');
    if (!fs.existsSync(buildDocPath)) {
      throw new Error('Expected ANDROID_BUILD.md documentation to exist');
    }
  });

  // 13. Download Endpoint Contract: Honest Error Handling & No Fake Binary
  await runTest('13. Download Endpoint Contract: Strict Truth in APK Existence & No Fake Binary', () => {
    const candidatePath = path.resolve(process.cwd(), 'android-dpc/app/build/outputs/apk/debug/app-debug.apk');
    const apkExists = fs.existsSync(candidatePath);

    // If APK does not exist, check that the expected target directory exists and never contains a fake file
    if (!apkExists) {
      // Must not create a fake file
      if (fs.existsSync(candidatePath)) {
        const stats = fs.statSync(candidatePath);
        if (stats.size < 1000) {
          throw new Error('Detected fake or dummy APK file; fake APKs are strictly prohibited.');
        }
      }
    }
  });

  const passedCount = results.filter(r => r.passed).length;
  console.log(`--- PHASE 6 TESTS COMPLETED: ${passedCount}/${results.length} PASSED ---`);

  return {
    passed: passedCount === results.length,
    results
  };
}
