import { Router } from 'express';
import { AuthController } from './controllers/auth.controller.js';
import { CustomerController } from './controllers/customer.controller.js';
import { DeviceController } from './controllers/device.controller.js';
import { EnrollmentController } from './controllers/enrollment.controller.js';
import { CommandController } from './controllers/command.controller.js';
import { HeartbeatController } from './controllers/heartbeat.controller.js';
import { FinanceController } from './controllers/finance.controller.js';
import { AuditController } from './controllers/audit.controller.js';
import { authenticateToken, requireRole } from './middleware/auth.js';
import { createRateLimiter } from './middleware/rateLimiter.js';
import { validateRequiredFields } from './middleware/validator.js';
import { SECURITY_CONFIG } from './config/security.js';

export const apiRouter = Router();

// Rate limiters
const authLimiter = createRateLimiter({
  maxRequests: SECURITY_CONFIG.RATE_LIMIT.AUTH_MAX_REQUESTS,
  windowMs: SECURITY_CONFIG.RATE_LIMIT.AUTH_WINDOW_MS
});

const generalLimiter = createRateLimiter({
  maxRequests: SECURITY_CONFIG.RATE_LIMIT.API_MAX_REQUESTS,
  windowMs: SECURITY_CONFIG.RATE_LIMIT.API_WINDOW_MS
});

const heartbeatLimiter = createRateLimiter({
  maxRequests: SECURITY_CONFIG.RATE_LIMIT.HEARTBEAT_MAX_REQUESTS,
  windowMs: SECURITY_CONFIG.RATE_LIMIT.HEARTBEAT_WINDOW_MS
});

// 1. Authentication Routes
apiRouter.post(
  '/auth/login',
  authLimiter,
  validateRequiredFields(['email', 'password']),
  AuthController.login
);

apiRouter.post(
  '/auth/refresh',
  authLimiter,
  validateRequiredFields(['refreshToken']),
  AuthController.refresh
);

apiRouter.get(
  '/auth/me',
  authenticateToken,
  AuthController.getProfile
);

// 2. Customer Management Routes
apiRouter.get(
  '/customers',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  CustomerController.listCustomers
);

apiRouter.post(
  '/customers',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  validateRequiredFields(['fullName', 'phoneNumber']),
  CustomerController.createCustomer
);

apiRouter.get(
  '/customers/:id',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT', 'CUSTOMER'),
  CustomerController.getCustomerDetails
);

apiRouter.put(
  '/customers/:id',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  CustomerController.updateCustomer
);

// 3. Cryptographic Device Enrollment Routes (Admin & Device Gateway)
apiRouter.post(
  '/enrollments',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  validateRequiredFields(['customerId', 'deviceId', 'agreementId']),
  EnrollmentController.createEnrollmentTicket
);

apiRouter.get(
  '/enrollments',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  EnrollmentController.listEnrollments
);

apiRouter.get(
  '/enrollments/:id',
  generalLimiter,
  EnrollmentController.getEnrollmentDetails
);

apiRouter.post(
  '/enrollments/:id/challenge',
  generalLimiter,
  EnrollmentController.issueEnrollmentChallenge
);

apiRouter.post(
  '/enrollments/:id/verify',
  generalLimiter,
  validateRequiredFields(['devicePublicKeyPem', 'signedChallenge', 'challengeNonce']),
  EnrollmentController.verifyEnrollmentKey
);

apiRouter.post(
  '/enrollments/:id/complete',
  generalLimiter,
  EnrollmentController.completeEnrollment
);

apiRouter.post(
  '/enrollments/:id/revoke',
  authenticateToken,
  requireRole('ADMIN'),
  EnrollmentController.revokeEnrollment
);

// Device Routine Challenge-Response Authentication
apiRouter.post(
  '/device/challenge',
  generalLimiter,
  validateRequiredFields(['enrollmentId']),
  EnrollmentController.requestDeviceChallenge
);

apiRouter.post(
  '/device/verify',
  generalLimiter,
  validateRequiredFields(['enrollmentId', 'challengeNonce', 'signature']),
  EnrollmentController.verifyDeviceAuthentication
);

// 4. Device Telemetry & Heartbeat Gateway
apiRouter.post(
  '/device/heartbeat',
  heartbeatLimiter,
  HeartbeatController.processHeartbeat
);

apiRouter.post(
  '/device-gateway/heartbeat',
  heartbeatLimiter,
  HeartbeatController.processHeartbeat
);

apiRouter.post(
  '/device-gateway/command-ack',
  generalLimiter,
  validateRequiredFields(['commandId', 'executionStatus']),
  CommandController.acknowledgeCommand
);

// 5. Device Management & Status Routes (Protected: ADMIN, SUPPORT, CUSTOMER with IDOR)
apiRouter.get(
  '/devices',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT', 'CUSTOMER'),
  DeviceController.listDevices
);

apiRouter.get(
  '/devices/:id/status',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT', 'CUSTOMER'),
  HeartbeatController.getDeviceStatus
);

apiRouter.get(
  '/devices/:id/activity',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT', 'CUSTOMER'),
  HeartbeatController.getDeviceActivities
);

apiRouter.get(
  '/devices/:id',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT', 'CUSTOMER'),
  DeviceController.getDeviceDetails
);

apiRouter.post(
  '/devices',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  validateRequiredFields(['manufacturer', 'model']),
  DeviceController.registerDeviceHardware
);

// 6. Remote Device Command Controller (Strictly ADMIN only)
apiRouter.post(
  '/devices/:id/commands',
  authenticateToken,
  requireRole('ADMIN'),
  validateRequiredFields(['commandType']),
  CommandController.issueCommand
);

apiRouter.get(
  '/devices/:id/commands',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  CommandController.getDeviceCommands
);

// 7. Financing Agreements, Installments & Payment Ledger
apiRouter.get(
  '/agreements',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT', 'CUSTOMER'),
  FinanceController.listAgreements
);

apiRouter.post(
  '/agreements',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  validateRequiredFields(['customerId', 'deviceId', 'totalAmount', 'numberOfInstallments']),
  FinanceController.createAgreement
);

apiRouter.get(
  '/agreements/:id',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT', 'CUSTOMER'),
  FinanceController.getAgreementDetails
);

apiRouter.get(
  '/payments',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT', 'CUSTOMER'),
  FinanceController.listPayments
);

apiRouter.post(
  '/payments',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  validateRequiredFields(['agreementId', 'amount']),
  FinanceController.recordPayment
);

// 8. Security Alerts
apiRouter.get(
  '/alerts',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  FinanceController.listAlerts
);

apiRouter.post(
  '/alerts/:id/acknowledge',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  FinanceController.acknowledgeAlert
);

// 9. Immutable Audit Trail (Strictly ADMIN only)
apiRouter.get(
  '/audit-logs',
  authenticateToken,
  requireRole('ADMIN'),
  AuditController.listLogs
);

// 10. Automated Test Runner Endpoint (Phases 3, 4, 5 & 6)
apiRouter.post(
  '/system/run-tests',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  async (req, res) => {
    const { runPhase3Tests } = await import('../tests/phase3.test.js');
    const { runPhase4Tests } = await import('../tests/phase4.test.js');
    const { runPhase5Tests } = await import('../tests/phase5.test.js');
    const { runPhase6Tests } = await import('../tests/phase6.test.js');
    const p3 = await runPhase3Tests();
    const p4 = await runPhase4Tests();
    const p5 = await runPhase5Tests();
    const p6 = await runPhase6Tests();
    res.json({
      success: true,
      data: {
        passed: p3.passed && p4.passed && p5.passed && p6.passed,
        results: [...p3.results, ...p4.results, ...p5.results, ...p6.results]
      }
    });
  }
);

// 11. Dev / Test Simulation Harness Endpoint (Strictly ADMIN / SUPPORT)
apiRouter.post(
  '/system/simulate-heartbeat',
  authenticateToken,
  requireRole('ADMIN', 'SUPPORT'),
  async (req, res) => {
    const {
      enrollmentId,
      batteryPercent = 85,
      networkType = 'WIFI',
      simCarrier,
      usbDebuggingEnabled,
      simulateReplay = false,
      simulateExpired = false,
      simulateTamperedSig = false,
      cachedNonce
    } = req.body;

    const { db } = await import('./services/store.js');
    const enrollment = db.enrollments.find(e => e.id === enrollmentId);
    if (!enrollment) {
      res.status(404).json({ success: false, error: 'DEVICE_NOT_FOUND', message: 'Enrollment not found' });
      return;
    }

    const crypto = await import('crypto');
    const kp = crypto.generateKeyPairSync('ec', {
      namedCurve: 'prime256v1',
      publicKeyEncoding: { type: 'spki', format: 'pem' },
      privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
    });
    enrollment.devicePublicKeyPem = kp.publicKey;
    const privateKeyPem = kp.privateKey;

    const nonce = simulateReplay && cachedNonce ? cachedNonce : `sim-nonce-${crypto.randomBytes(8).toString('hex')}`;
    const timestamp = simulateExpired
      ? new Date(Date.now() - 15 * 60 * 1000).toISOString()
      : new Date().toISOString();

    const canonicalData = `${enrollment.id}|${nonce}|${timestamp}`;
    let signature: string;

    if (simulateTamperedSig) {
      const forgedKp = crypto.generateKeyPairSync('ec', {
        namedCurve: 'prime256v1',
        publicKeyEncoding: { type: 'spki', format: 'pem' },
        privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
      });
      const signer = crypto.createSign('SHA256');
      signer.update(canonicalData);
      signer.end();
      signature = signer.sign(forgedKp.privateKey, 'base64');
    } else {
      const signer = crypto.createSign('SHA256');
      signer.update(canonicalData);
      signer.end();
      signature = signer.sign(privateKeyPem, 'base64');
    }

    const heartbeatReq: any = {
      body: {
        enrollmentId: enrollment.id,
        nonce,
        timestamp,
        signature,
        batteryPercent: Number(batteryPercent),
        networkType,
        simCarrier,
        usbDebuggingEnabled,
        appVersion: enrollment.appVersion || '1.0.0',
        managementStatus: enrollment.managementMode
      },
      headers: {},
      ip: req.ip
    };

    await HeartbeatController.processHeartbeat(heartbeatReq, res);
  }
);

