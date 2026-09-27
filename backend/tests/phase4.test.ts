/**
 * Phase 4 Automated Test Suite: Secure Device Enrollment
 * Tests full cryptographic enrollment flow, challenge-response, replay attacks,
 * token expirations, token reuses, signature verification, and administrative revocation.
 */

import crypto from 'crypto';
import { db } from '../src/services/store.js';
import { EnrollmentController } from '../src/controllers/enrollment.controller.js';
import { CryptoService } from '../src/services/crypto.service.js';

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

function mockResponse() {
  const res: any = {
    statusCode: 200,
    body: null,
    status(code: number) {
      this.statusCode = code;
      return this;
    },
    json(data: any) {
      this.body = data;
      return this;
    }
  };
  return res;
}

// Simulates Android Keystore EC P-256 Keypair generation
function generateClientKeystoreKeyPair() {
  return crypto.generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
  });
}

// Signs data with client private key (simulating Android KeyStore signData)
function clientSignData(privateKeyPem: string, data: string): string {
  const signer = crypto.createSign('SHA256');
  signer.update(data);
  signer.end();
  return signer.sign(privateKeyPem, 'base64');
}

export async function runPhase4Tests(): Promise<{ passed: boolean; results: TestResult[] }> {
  results.length = 0;
  console.log('--- STARTING PHASE 4 SECURE ENROLLMENT TESTS ---');

  const clientKey1 = generateClientKeystoreKeyPair();
  const clientKey2 = generateClientKeystoreKeyPair(); // forged keypair

  let validEnrollmentId = '';
  let validEnrollmentCode = '';
  let validChallengeNonce = '';

  // Test 1: Wrong Customer / Device Agreement Mismatch Rejection
  await runTest('1. Enrollment Ticket Rejects Mismatched Customer/Device Agreement', async () => {
    const agr = db.agreements[0];
    const otherCust = db.customers.find(c => c.id !== agr.customerId)!; // guaranteed mismatched customer

    const req: any = {
      body: {
        customerId: otherCust.id,
        deviceId: agr.deviceId,
        agreementId: agr.id
      },
      user: { userId: 'usr-admin-01', role: 'ADMIN' },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await EnrollmentController.createEnrollmentTicket(req, res);

    if (res.statusCode !== 400 || res.body?.error !== 'MISMATCHED_AGREEMENT') {
      throw new Error(`Expected 400 MISMATCHED_AGREEMENT, got ${res.statusCode} ${JSON.stringify(res.body)}`);
    }
  });

  // Test 2: Admin Successfully Creates Enrollment Ticket
  await runTest('2. Admin Creates Valid One-Time Enrollment Ticket', async () => {
    // Fresh un-enrolled device and agreement for Phase 4
    const newCust = db.customers[2];
    const newDev = {
      id: `dev-p4-${Date.now()}`,
      manufacturer: 'Google',
      model: 'Pixel 9a',
      brand: 'Google',
      hardwareSerial: `PIX-P4-${Date.now()}`,
      initialCarrier: 'Unlocked',
      createdAt: new Date().toISOString()
    };
    db.devices.push(newDev);
    
    // Create new test agreement
    const testAgrId = `agr-p4-${Date.now()}`;
    db.agreements.push({
      id: testAgrId,
      agreementCode: `AGR-P4-${Math.floor(100 + Math.random() * 900)}`,
      customerId: newCust.id,
      deviceId: newDev.id,
      totalAmount: 999.00,
      downPayment: 99.00,
      remainingAmount: 900.00,
      installmentAmount: 90.00,
      numberOfInstallments: 10,
      paidInstallments: 0,
      remainingInstallments: 10,
      startDate: '2026-09-01',
      nextDueDate: '2026-10-01',
      gracePeriodDays: 5,
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    });

    const req: any = {
      body: {
        customerId: newCust.id,
        deviceId: newDev.id,
        agreementId: testAgrId,
        validityMinutes: 15
      },
      user: { userId: 'usr-admin-01', role: 'ADMIN' },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await EnrollmentController.createEnrollmentTicket(req, res);

    if (res.statusCode !== 201 || !res.body?.success) {
      throw new Error(`Failed to create enrollment ticket: ${res.statusCode} ${JSON.stringify(res.body)}`);
    }

    validEnrollmentId = res.body.data.enrollmentId;
    validEnrollmentCode = res.body.data.enrollmentCode;

    const record = db.enrollments.find(e => e.id === validEnrollmentId);
    if (!record || record.enrollmentStatus !== 'AWAITING_CUSTOMER') {
      throw new Error('Enrollment status is not AWAITING_CUSTOMER');
    }
  });

  // Test 3: Device Fetches Enrollment Details via Code
  await runTest('3. Device Fetches Enrollment Details & Disclosures via Code', async () => {
    const req: any = { params: { id: validEnrollmentCode } };
    const res = mockResponse();
    await EnrollmentController.getEnrollmentDetails(req, res);

    if (res.statusCode !== 200 || !res.body?.data?.disclosures) {
      throw new Error(`Failed to retrieve enrollment disclosures: ${res.statusCode}`);
    }
    if (res.body.data.enrollment.id !== validEnrollmentId) {
      throw new Error('Enrollment ID mismatch');
    }
  });

  // Test 4: Device Requests Challenge Nonce
  await runTest('4. Device Requests Challenge Nonce for Keystore Signature', async () => {
    const req: any = { params: { id: validEnrollmentId } };
    const res = mockResponse();
    await EnrollmentController.issueEnrollmentChallenge(req, res);

    if (res.statusCode !== 200 || !res.body?.data?.challengeNonce) {
      throw new Error(`Failed to obtain challenge nonce: ${res.statusCode}`);
    }
    validChallengeNonce = res.body.data.challengeNonce;
    if (validChallengeNonce.length !== 64) {
      throw new Error('Challenge nonce length must be 64 hexadecimal characters');
    }
  });

  // Test 5: Invalid / Forged Signature Rejection
  await runTest('5. Cryptographic Verification Rejects Forged / Invalid Signature', async () => {
    // Sign using clientKey2, but present clientKey1's public key
    const forgedSignature = clientSignData(clientKey2.privateKey, validChallengeNonce);

    const req: any = {
      params: { id: validEnrollmentId },
      body: {
        devicePublicKeyPem: clientKey1.publicKey,
        signedChallenge: forgedSignature,
        challengeNonce: validChallengeNonce,
        androidVersion: 'Android 15 (API 35)',
        managementMode: 'DEVICE_OWNER'
      },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await EnrollmentController.verifyEnrollmentKey(req, res);

    if (res.statusCode !== 401 || res.body?.error !== 'CRYPTOGRAPHIC_VERIFICATION_FAILED') {
      throw new Error(`Expected 401 CRYPTOGRAPHIC_VERIFICATION_FAILED, received ${res.statusCode} ${JSON.stringify(res.body)}`);
    }
  });

  // Test 6: Replay Attack Defense (Replayed Nonce Rejection)
  await runTest('6. Challenge Nonce Single-Use: Replay Attempt Rejected', async () => {
    // Generate fresh challenge
    const reqChal: any = { params: { id: validEnrollmentId } };
    const resChal = mockResponse();
    await EnrollmentController.issueEnrollmentChallenge(reqChal, resChal);
    const freshNonce = resChal.body.data.challengeNonce;

    // First legitimate sign
    const validSignature = clientSignData(clientKey1.privateKey, freshNonce);

    const reqVerify1: any = {
      params: { id: validEnrollmentId },
      body: {
        devicePublicKeyPem: clientKey1.publicKey,
        signedChallenge: validSignature,
        challengeNonce: freshNonce,
        androidVersion: 'Android 15',
        managementMode: 'DEVICE_OWNER'
      },
      ip: '127.0.0.1'
    };
    const resVerify1 = mockResponse();
    await EnrollmentController.verifyEnrollmentKey(reqVerify1, resVerify1);

    if (resVerify1.statusCode !== 200) {
      throw new Error(`First verification failed: ${resVerify1.statusCode}`);
    }

    // Replay attack: Re-submitting the exact same verification with the already consumed nonce
    const resVerify2 = mockResponse();
    await EnrollmentController.verifyEnrollmentKey(reqVerify1, resVerify2);

    if (resVerify2.statusCode !== 400 || resVerify2.body?.error !== 'INVALID_CHALLENGE_NONCE') {
      throw new Error(`Expected 400 INVALID_CHALLENGE_NONCE for replayed challenge, got ${resVerify2.statusCode}`);
    }
  });

  // Test 7: Complete Enrollment and Transition to ACTIVE
  await runTest('7. Complete Enrollment Transitions Device to ACTIVE', async () => {
    const req: any = {
      params: { id: validEnrollmentId },
      body: {
        managementMode: 'DEVICE_OWNER',
        fcmToken: 'mock-fcm-token-phase4'
      },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await EnrollmentController.completeEnrollment(req, res);

    if (res.statusCode !== 200 || res.body?.data?.enrollmentStatus !== 'ACTIVE') {
      throw new Error(`Failed to complete enrollment: ${res.statusCode} ${JSON.stringify(res.body)}`);
    }

    const record = db.enrollments.find(e => e.id === validEnrollmentId);
    if (!record || record.enrollmentStatus !== 'ACTIVE' || !record.isTokenUsed) {
      throw new Error('Record in store is not active or token not marked used');
    }
  });

  // Test 8: Reused Token Rejection
  await runTest('8. Reused Token Rejected After Enrollment Completion', async () => {
    // Attempting to re-verify an already completed enrollment
    const req: any = {
      params: { id: validEnrollmentId },
      body: {
        devicePublicKeyPem: clientKey1.publicKey,
        signedChallenge: 'dummy',
        challengeNonce: 'dummy'
      },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await EnrollmentController.verifyEnrollmentKey(req, res);

    if (res.statusCode !== 409 || res.body?.error !== 'TOKEN_ALREADY_USED') {
      throw new Error(`Expected 409 TOKEN_ALREADY_USED, got ${res.statusCode} ${JSON.stringify(res.body)}`);
    }
  });

  // Test 9: Expired Token Rejection
  await runTest('9. Expired Enrollment Token Rejected', async () => {
    // Create an enrollment that expired in the past
    const expiredId = `enr-exp-${Date.now()}`;
    db.enrollments.push({
      id: expiredId,
      deviceId: db.devices[0].id,
      customerId: db.customers[0].id,
      agreementId: db.agreements[0].id,
      enrollmentCode: 'EXPIRED-CODE',
      tokenHash: 'hash-exp',
      expiresAt: new Date(Date.now() - 60000).toISOString(), // 1 min ago
      isTokenUsed: false,
      managementMode: 'DEVICE_OWNER',
      enrollmentStatus: 'EXPIRED',
      isOnline: false,
      usbDebuggingActive: false
    });

    const req: any = { params: { id: expiredId } };
    const res = mockResponse();
    await EnrollmentController.issueEnrollmentChallenge(req, res);

    if (res.statusCode !== 400 || res.body?.error !== 'TOKEN_EXPIRED') {
      throw new Error(`Expected 400 TOKEN_EXPIRED, got ${res.statusCode} ${JSON.stringify(res.body)}`);
    }
  });

  // Test 10: Routine Device Authentication Challenge-Response
  await runTest('10. Routine Device Authentication Challenge-Response', async () => {
    // Request challenge for active enrollment
    const reqChal: any = { body: { enrollmentId: validEnrollmentId } };
    const resChal = mockResponse();
    await EnrollmentController.requestDeviceChallenge(reqChal, resChal);

    if (resChal.statusCode !== 200 || !resChal.body?.data?.challengeNonce) {
      throw new Error('Failed to obtain routine challenge nonce');
    }
    const nonce = resChal.body.data.challengeNonce;
    const signature = clientSignData(clientKey1.privateKey, nonce);

    // Verify
    const reqVer: any = {
      body: {
        enrollmentId: validEnrollmentId,
        challengeNonce: nonce,
        signature
      }
    };
    const resVer = mockResponse();
    await EnrollmentController.verifyDeviceAuthentication(reqVer, resVer);

    if (resVer.statusCode !== 200 || !resVer.body?.data?.authenticated) {
      throw new Error(`Device authentication verification failed: ${resVer.statusCode}`);
    }
  });

  // Test 11: Revocation by Administrator
  await runTest('11. Administrator Successfully Revokes Enrollment', async () => {
    const req: any = {
      params: { id: validEnrollmentId },
      body: { reason: 'Customer reported unit stolen or fraudulent setup' },
      user: { userId: 'usr-admin-01', role: 'ADMIN' },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await EnrollmentController.revokeEnrollment(req, res);

    if (res.statusCode !== 200 || res.body?.data?.enrollmentStatus !== 'REVOKED') {
      throw new Error(`Revocation failed: ${res.statusCode} ${JSON.stringify(res.body)}`);
    }

    // Subsequent challenge attempt must be rejected with 403
    const reqChal: any = { params: { id: validEnrollmentId } };
    const resChal = mockResponse();
    await EnrollmentController.issueEnrollmentChallenge(reqChal, resChal);

    if (resChal.statusCode !== 403 || resChal.body?.error !== 'ENROLLMENT_REVOKED') {
      throw new Error(`Expected 403 ENROLLMENT_REVOKED, received ${resChal.statusCode}`);
    }
  });

  const allPassed = results.every(r => r.passed);
  console.log(`--- PHASE 4 TESTS COMPLETE: ${results.filter(r => r.passed).length}/${results.length} PASSED ---`);
  return { passed: allPassed, results };
}

// If run directly from CLI
if (process.argv[1]?.endsWith('phase4.test.ts') || process.argv[1]?.endsWith('phase4.test.js')) {
  runPhase4Tests().then(({ passed }) => {
    process.exit(passed ? 0 : 1);
  });
}
