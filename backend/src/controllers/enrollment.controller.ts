import { Request, Response } from 'express';
import crypto from 'crypto';
import { db, EnrollmentRecord } from '../services/store.js';
import { CryptoService } from '../services/crypto.service.js';
import { AuditService } from '../services/audit.service.js';

export class EnrollmentController {
  /**
   * POST /api/v1/enrollments
   * Admin creates a one-time enrollment ticket bound to Customer, Device, and Agreement.
   */
  static async createEnrollmentTicket(req: Request, res: Response): Promise<void> {
    const { customerId, deviceId, agreementId, validityMinutes = 30 } = req.body;

    if (!customerId || !deviceId || !agreementId) {
      res.status(400).json({
        success: false,
        error: 'VALIDATION_FAILED',
        message: 'customerId, deviceId, and agreementId are required'
      });
      return;
    }

    const customer = db.customers.find(c => c.id === customerId);
    if (!customer) {
      res.status(404).json({ success: false, error: 'CUSTOMER_NOT_FOUND', message: 'Customer does not exist' });
      return;
    }

    const device = db.devices.find(d => d.id === deviceId);
    if (!device) {
      res.status(404).json({ success: false, error: 'DEVICE_NOT_FOUND', message: 'Device does not exist' });
      return;
    }

    const agreement = db.agreements.find(a => a.id === agreementId);
    if (!agreement) {
      res.status(404).json({ success: false, error: 'AGREEMENT_NOT_FOUND', message: 'Financing agreement does not exist' });
      return;
    }

    // Verify customer and device match the agreement
    if (agreement.customerId !== customerId || agreement.deviceId !== deviceId) {
      res.status(400).json({
        success: false,
        error: 'MISMATCHED_AGREEMENT',
        message: 'Agreement customer or device does not match the provided entities'
      });
      return;
    }

    // Check for existing ACTIVE enrollment on this device
    const existingActive = db.enrollments.find(
      e => e.deviceId === deviceId && e.enrollmentStatus === 'ACTIVE'
    );
    if (existingActive) {
      res.status(409).json({
        success: false,
        error: 'DEVICE_ALREADY_ACTIVE',
        message: `Device ${device.model} is already actively enrolled under ID ${existingActive.id}. Revoke previous enrollment first if re-enrolling.`
      });
      return;
    }

    // Generate enrollment ID and one-time pairing token
    const enrollmentId = `enr-${crypto.randomBytes(4).toString('hex')}`;
    const enrollmentCode = `ENR-${Math.floor(1000 + Math.random() * 9000)}-${crypto.randomBytes(2).toString('hex').toUpperCase()}`;
    const rawToken = crypto.randomBytes(32).toString('hex');
    const tokenHash = CryptoService.hashToken(rawToken);

    const expiresAt = new Date(Date.now() + Number(validityMinutes) * 60 * 1000).toISOString();

    const enrollmentRecord: EnrollmentRecord = {
      id: enrollmentId,
      deviceId,
      customerId,
      agreementId,
      enrollmentCode,
      tokenHash,
      expiresAt,
      isTokenUsed: false,
      managementMode: 'DEVICE_OWNER',
      enrollmentStatus: 'AWAITING_CUSTOMER',
      isOnline: false,
      usbDebuggingActive: false
    };

    db.enrollments.unshift(enrollmentRecord);
    db.save();

    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: 'ENROLLMENT_REQUEST_CREATED',
      entityName: 'device_enrollments',
      entityId: enrollmentId,
      changes: {
        enrollmentCode,
        customerId,
        deviceId,
        agreementId,
        expiresAt
      },
      ipAddress: req.ip
    });

    res.status(201).json({
      success: true,
      data: {
        enrollmentId,
        enrollmentCode,
        enrollmentToken: rawToken,
        qrPayload: JSON.stringify({
          serverUrl: process.env.APP_URL || 'https://ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app',
          enrollmentId,
          enrollmentCode,
          token: rawToken,
          agreementCode: agreement.agreementCode
        }),
        expiresAt,
        validityMinutes
      }
    });
  }

  /**
   * GET /api/v1/enrollments
   * Lists all device enrollments with dynamic expiration checks.
   */
  static async listEnrollments(req: Request, res: Response): Promise<void> {
    const now = new Date();

    const list = db.enrollments.map(enr => {
      // Dynamic expiration check
      if (
        (enr.enrollmentStatus === 'AWAITING_CUSTOMER' || enr.enrollmentStatus === 'PENDING' || enr.enrollmentStatus === 'AWAITING_DEVICE') &&
        new Date(enr.expiresAt) < now
      ) {
        enr.enrollmentStatus = 'EXPIRED';
      }

      const cust = db.customers.find(c => c.id === enr.customerId);
      const dev = db.devices.find(d => d.id === enr.deviceId);
      const agr = db.agreements.find(a => a.id === enr.agreementId);

      return {
        ...enr,
        customerName: cust?.fullName,
        customerPhone: cust?.phoneNumber,
        deviceModel: dev ? `${dev.brand} ${dev.model}` : 'Unknown',
        deviceSerial: dev?.hardwareSerial,
        agreementCode: agr?.agreementCode
      };
    });

    res.json({ success: true, data: list });
  }

  /**
   * GET /api/v1/enrollments/:id
   * Get enrollment details by ID, Enrollment Code, or raw Token.
   */
  static async getEnrollmentDetails(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const tokenHash = CryptoService.hashToken(id);

    const enr = db.enrollments.find(e => e.id === id || e.enrollmentCode === id || e.tokenHash === tokenHash);
    if (!enr) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Enrollment ticket not found' });
      return;
    }

    // Dynamic expiration check
    if (
      (enr.enrollmentStatus === 'AWAITING_CUSTOMER' || enr.enrollmentStatus === 'PENDING' || enr.enrollmentStatus === 'AWAITING_DEVICE') &&
      new Date(enr.expiresAt) < new Date()
    ) {
      enr.enrollmentStatus = 'EXPIRED';
    }

    const cust = db.customers.find(c => c.id === enr.customerId);
    const dev = db.devices.find(d => d.id === enr.deviceId);
    const agr = db.agreements.find(a => a.id === enr.agreementId);

    res.json({
      success: true,
      data: {
        enrollment: enr,
        customer: cust ? { id: cust.id, fullName: cust.fullName, phoneNumber: cust.phoneNumber } : null,
        device: dev ? { id: dev.id, manufacturer: dev.manufacturer, model: dev.model, brand: dev.brand } : null,
        agreement: agr ? {
          id: agr.id,
          agreementCode: agr.agreementCode,
          totalAmount: agr.totalAmount,
          downPayment: agr.downPayment,
          remainingAmount: agr.remainingAmount,
          installmentAmount: agr.installmentAmount,
          numberOfInstallments: agr.numberOfInstallments
        } : null,
        disclosures: {
          purpose: 'Installment financing compliance & payment reminder enforcement.',
          managementType: 'Android Enterprise Device Owner Kiosk Mode (Emergency calling preserved).',
          dataCollected: ['Battery percentage', 'Network connectivity state', 'SIM subscription carrier name', 'Cryptographic hardware attestation.'],
          privacyGuarantee: 'Personal photos, SMS text contents, private phone calls, web browsing history, and passwords are NEVER accessed or monitored.'
        }
      }
    });
  }

  /**
   * POST /api/v1/enrollments/:id/challenge
   * Generates single-use challenge nonce for the enrollment.
   */
  static async issueEnrollmentChallenge(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const tokenHash = CryptoService.hashToken(id);

    const enr = db.enrollments.find(e => e.id === id || e.enrollmentCode === id || e.tokenHash === tokenHash);
    if (!enr) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Enrollment not found' });
      return;
    }

    if (enr.enrollmentStatus === 'REVOKED') {
      res.status(403).json({ success: false, error: 'ENROLLMENT_REVOKED', message: 'This enrollment request was revoked by the administrator.' });
      return;
    }
    if (new Date(enr.expiresAt) < new Date() || enr.enrollmentStatus === 'EXPIRED') {
      enr.enrollmentStatus = 'EXPIRED';
      res.status(400).json({ success: false, error: 'TOKEN_EXPIRED', message: 'Enrollment ticket has expired. Request a new code.' });
      return;
    }
    if (enr.isTokenUsed && enr.enrollmentStatus === 'ACTIVE') {
      res.status(409).json({ success: false, error: 'TOKEN_ALREADY_USED', message: 'This enrollment token has already been consumed.' });
      return;
    }

    const nonce = CryptoService.generateNonce();
    enr.challengeNonce = nonce;
    enr.challengeExpiresAt = Date.now() + 5 * 60 * 1000; // 5 minutes validity
    enr.enrollmentStatus = 'AWAITING_DEVICE';

    res.json({
      success: true,
      data: {
        challengeNonce: nonce,
        expiresInSeconds: 300,
        enrollmentId: enr.id
      }
    });
  }

  /**
   * POST /api/v1/enrollments/:id/verify
   * Verifies the device-generated Keystore public key and ECDSA signature over the challenge nonce.
   */
  static async verifyEnrollmentKey(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const { devicePublicKeyPem, signedChallenge, challengeNonce, androidVersion, appVersion, managementMode } = req.body;

    const tokenHash = CryptoService.hashToken(id);
    const enr = db.enrollments.find(e => e.id === id || e.enrollmentCode === id || e.tokenHash === tokenHash);

    if (!enr) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Enrollment not found' });
      return;
    }

    if (enr.enrollmentStatus === 'REVOKED') {
      res.status(403).json({ success: false, error: 'ENROLLMENT_REVOKED', message: 'Enrollment was revoked by administrator' });
      return;
    }

    if (new Date(enr.expiresAt) < new Date() || enr.enrollmentStatus === 'EXPIRED') {
      enr.enrollmentStatus = 'EXPIRED';
      res.status(400).json({ success: false, error: 'TOKEN_EXPIRED', message: 'Enrollment token has expired' });
      return;
    }

    if (enr.isTokenUsed) {
      res.status(409).json({ success: false, error: 'TOKEN_ALREADY_USED', message: 'Enrollment token was already used' });
      return;
    }

    // Challenge check
    if (!enr.challengeNonce || enr.challengeNonce !== challengeNonce) {
      res.status(400).json({ success: false, error: 'INVALID_CHALLENGE_NONCE', message: 'Challenge nonce does not match current session' });
      return;
    }

    if (!enr.challengeExpiresAt || Date.now() > enr.challengeExpiresAt) {
      enr.challengeNonce = undefined;
      res.status(400).json({ success: false, error: 'CHALLENGE_EXPIRED', message: 'Challenge nonce has expired. Re-request challenge.' });
      return;
    }

    // Cryptographic signature verification using device public key
    const isSignatureValid = CryptoService.verifyDeviceSignature(challengeNonce, signedChallenge, devicePublicKeyPem);
    if (!isSignatureValid) {
      AuditService.log({
        action: 'ENROLLMENT_SIGNATURE_REJECTED',
        entityName: 'device_enrollments',
        entityId: enr.id,
        changes: { reason: 'ECDSA signature verification failed' },
        ipAddress: req.ip
      });

      res.status(401).json({
        success: false,
        error: 'CRYPTOGRAPHIC_VERIFICATION_FAILED',
        message: 'Device signature failed cryptographic validation against provided public key.'
      });
      return;
    }

    // Consume challenge nonce to prevent replay attacks
    enr.challengeNonce = undefined;
    enr.challengeExpiresAt = undefined;

    // Save verified public key & device metadata
    enr.devicePublicKeyPem = devicePublicKeyPem;
    enr.androidVersion = androidVersion || 'Android 14';
    enr.appVersion = appVersion || '1.0.0';
    if (managementMode) enr.managementMode = managementMode;
    enr.enrollmentStatus = 'AWAITING_DEVICE';

    AuditService.log({
      action: 'DEVICE_KEY_VERIFIED',
      entityName: 'device_enrollments',
      entityId: enr.id,
      changes: {
        publicKeySnippet: devicePublicKeyPem.slice(0, 40) + '...',
        managementMode: enr.managementMode
      },
      ipAddress: req.ip
    });

    res.json({
      success: true,
      data: {
        enrollmentId: enr.id,
        status: 'AWAITING_DEVICE',
        message: 'Device cryptographic public key successfully validated and registered.'
      }
    });
  }

  /**
   * POST /api/v1/enrollments/:id/complete
   * Final step: completes enrollment and transitions device to ACTIVE status.
   */
  static async completeEnrollment(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const { fcmToken, managementMode } = req.body;

    const tokenHash = CryptoService.hashToken(id);
    const enr = db.enrollments.find(e => e.id === id || e.enrollmentCode === id || e.tokenHash === tokenHash);

    if (!enr) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Enrollment not found' });
      return;
    }

    if (enr.enrollmentStatus === 'REVOKED') {
      res.status(403).json({ success: false, error: 'ENROLLMENT_REVOKED', message: 'Cannot complete revoked enrollment' });
      return;
    }

    if (!enr.devicePublicKeyPem) {
      res.status(400).json({
        success: false,
        error: 'PUBLIC_KEY_NOT_VERIFIED',
        message: 'Device public key must be cryptographically verified prior to completing enrollment.'
      });
      return;
    }

    if (enr.isTokenUsed && enr.enrollmentStatus === 'ACTIVE') {
      res.status(409).json({ success: false, error: 'ALREADY_COMPLETED', message: 'Enrollment already completed' });
      return;
    }

    // Activate enrollment
    enr.isTokenUsed = true;
    enr.tokenUsedAt = new Date().toISOString();
    enr.enrollmentStatus = 'ACTIVE';
    enr.enrolledAt = new Date().toISOString();
    enr.isOnline = true;
    if (fcmToken) enr.fcmToken = fcmToken;
    if (managementMode) enr.managementMode = managementMode;

    AuditService.log({
      action: 'DEVICE_ENROLLMENT_COMPLETED',
      entityName: 'device_enrollments',
      entityId: enr.id,
      changes: {
        deviceId: enr.deviceId,
        customerId: enr.customerId,
        managementMode: enr.managementMode,
        status: 'ACTIVE'
      },
      ipAddress: req.ip
    });

    db.deviceActivities.unshift({
      id: `act-${crypto.randomBytes(4).toString('hex')}`,
      deviceId: enr.deviceId,
      enrollmentId: enr.id,
      eventType: 'ENROLLMENT_COMPLETED',
      description: 'Device successfully completed cryptographic enrollment using Android Keystore EC P-256 identity.',
      details: { managementMode: enr.managementMode, customerId: enr.customerId },
      timestamp: enr.enrolledAt || new Date().toISOString()
    });

    db.save();

    res.json({
      success: true,
      data: {
        enrollmentId: enr.id,
        enrollmentStatus: 'ACTIVE',
        enrolledAt: enr.enrolledAt,
        message: 'Device successfully enrolled into financing protection system.'
      }
    });
  }

  /**
   * POST /api/v1/enrollments/:id/revoke
   * Admin revokes an enrollment request or active device enrollment.
   */
  static async revokeEnrollment(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const { reason = 'Revoked by administrator' } = req.body;

    const enr = db.enrollments.find(e => e.id === id || e.enrollmentCode === id);
    if (!enr) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Enrollment not found' });
      return;
    }

    const previousStatus = enr.enrollmentStatus;
    enr.enrollmentStatus = 'REVOKED';
    enr.revokedAt = new Date().toISOString();
    enr.revocationReason = reason;
    enr.revokedBy = req.user?.userId;
    db.save();

    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: 'ENROLLMENT_REVOKED',
      entityName: 'device_enrollments',
      entityId: enr.id,
      changes: {
        previousStatus,
        newStatus: 'REVOKED',
        reason
      },
      ipAddress: req.ip
    });

    res.json({
      success: true,
      data: {
        enrollmentId: enr.id,
        enrollmentStatus: 'REVOKED',
        revokedAt: enr.revokedAt,
        reason
      }
    });
  }

  /**
   * POST /api/v1/device/challenge
   * Routine authentication challenge for an active enrolled device.
   */
  static async requestDeviceChallenge(req: Request, res: Response): Promise<void> {
    const { enrollmentId } = req.body;
    if (!enrollmentId) {
      res.status(400).json({ success: false, error: 'VALIDATION_FAILED', message: 'enrollmentId is required' });
      return;
    }

    const enr = db.enrollments.find(e => e.id === enrollmentId);
    if (!enr || enr.enrollmentStatus !== 'ACTIVE') {
      res.status(403).json({ success: false, error: 'DEVICE_NOT_ACTIVE', message: 'Device is not actively enrolled' });
      return;
    }

    const nonce = CryptoService.generateNonce();
    enr.challengeNonce = nonce;
    enr.challengeExpiresAt = Date.now() + 5 * 60 * 1000;

    res.json({
      success: true,
      data: {
        challengeNonce: nonce,
        expiresInSeconds: 300
      }
    });
  }

  /**
   * POST /api/v1/device/verify
   * Routine authentication verification of device signature over challenge nonce.
   */
  static async verifyDeviceAuthentication(req: Request, res: Response): Promise<void> {
    const { enrollmentId, challengeNonce, signature } = req.body;

    const enr = db.enrollments.find(e => e.id === enrollmentId);
    if (!enr || !enr.devicePublicKeyPem) {
      res.status(404).json({ success: false, error: 'DEVICE_NOT_FOUND', message: 'Device not found or not enrolled' });
      return;
    }

    if (enr.enrollmentStatus === 'REVOKED') {
      res.status(403).json({ success: false, error: 'ENROLLMENT_REVOKED', message: 'Device enrollment was revoked' });
      return;
    }

    // Check nonce
    if (!enr.challengeNonce || enr.challengeNonce !== challengeNonce) {
      res.status(400).json({ success: false, error: 'INVALID_CHALLENGE', message: 'Challenge nonce mismatch or already consumed' });
      return;
    }

    if (!enr.challengeExpiresAt || Date.now() > enr.challengeExpiresAt) {
      enr.challengeNonce = undefined;
      res.status(400).json({ success: false, error: 'CHALLENGE_EXPIRED', message: 'Challenge has expired' });
      return;
    }

    // Cryptographic verification
    const isValid = CryptoService.verifyDeviceSignature(challengeNonce, signature, enr.devicePublicKeyPem);
    enr.challengeNonce = undefined; // consume immediately

    if (!isValid) {
      res.status(401).json({ success: false, error: 'SIGNATURE_INVALID', message: 'Device signature verification failed' });
      return;
    }

    enr.lastHeartbeatAt = new Date().toISOString();

    res.json({
      success: true,
      data: {
        authenticated: true,
        enrollmentId: enr.id,
        timestamp: new Date().toISOString()
      }
    });
  }
}
