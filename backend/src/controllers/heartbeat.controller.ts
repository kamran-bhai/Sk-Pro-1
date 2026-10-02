import { Request, Response } from 'express';
import crypto from 'crypto';
import { db, AlertRecord, DeviceActivityRecord, DeviceHeartbeatRecord, calculateDeviceOnlineStatus } from '../services/store.js';
import { AuditService } from '../services/audit.service.js';
import { CryptoService } from '../services/crypto.service.js';
import { SECURITY_CONFIG } from '../config/security.js';
import { syncOverdueFinancingState } from './finance.controller.js';

export class HeartbeatController {
  /**
   * Helper to append an immutable event to the device activity timeline.
   */
  static logActivity(
    deviceId: string,
    enrollmentId: string,
    eventType: DeviceActivityRecord['eventType'],
    description: string,
    details?: Record<string, any>
  ): void {
    const activity: DeviceActivityRecord = {
      id: `act-${crypto.randomBytes(4).toString('hex')}`,
      deviceId,
      enrollmentId,
      eventType,
      description,
      details,
      timestamp: new Date().toISOString()
    };
    db.deviceActivities.unshift(activity);
    db.save();
  }

  /**
   * POST /api/v1/device/heartbeat & /api/v1/device-gateway/heartbeat
   * Cryptographically authenticated, replay-protected device heartbeat.
   */
  static async processHeartbeat(req: Request, res: Response): Promise<void> {
    // Reconcile financing status before returning device commands.
    // This may queue a signed lock command only for supported managed devices after grace period.
    syncOverdueFinancingState();

    const enrollmentId =
      req.body.enrollmentId ||
      (req.headers['x-enrollment-id'] as string);

    const {
      nonce,
      timestamp,
      signature,
      androidVersion,
      appVersion,
      managementStatus,
      batteryPercent,
      batteryPercentage,
      networkType,
      networkConnectivity,
      simCarrier,
      simFingerprint,
      simSubscriptionCount,
      usbDebuggingEnabled
    } = req.body;

    if (!enrollmentId) {
      res.status(400).json({
        success: false,
        error: 'BAD_REQUEST',
        message: 'enrollmentId is required'
      });
      return;
    }

    const enrollment = db.enrollments.find(e => e.id === enrollmentId);
    if (!enrollment) {
      res.status(404).json({
        success: false,
        error: 'DEVICE_NOT_FOUND',
        message: `Unknown enrollment ID: ${enrollmentId}`
      });
      return;
    }

    // Check enrollment lifecycle status
    if (enrollment.enrollmentStatus === 'REVOKED') {
      res.status(403).json({
        success: false,
        error: 'ENROLLMENT_REVOKED',
        message: 'Device enrollment has been revoked by system administrator.'
      });
      return;
    }

    if (enrollment.enrollmentStatus === 'SUSPENDED') {
      res.status(403).json({
        success: false,
        error: 'DEVICE_SUSPENDED',
        message: 'Device enrollment is currently suspended.'
      });
      return;
    }

    // Every heartbeat is authenticated. Do not accept an unsigned heartbeat.
    // The Android client signs the canonical enrollmentId|nonce|timestamp payload
    // with the enrollment's registered Android Keystore public key.
    if (!nonce) {
      res.status(400).json({
        success: false,
        error: 'NONCE_REQUIRED',
        message: 'Heartbeat replay protection requires a unique nonce.'
      });
      return;
    }

    if (!timestamp) {
      res.status(400).json({
        success: false,
        error: 'TIMESTAMP_REQUIRED',
        message: 'Device timestamp is required for heartbeat timeliness validation.'
      });
      return;
    }

    if (!signature) {
      res.status(401).json({
        success: false,
        error: 'SIGNATURE_REQUIRED',
        message: 'Cryptographic signature from Android Keystore is required.'
      });
      return;
    }

    // Replay attack prevention: check if nonce was already consumed

      // Replay attack prevention: check if nonce was already consumed
      if (db.usedNonces.has(nonce)) {
        res.status(400).json({
          success: false,
          error: 'REPLAY_ATTACK_DETECTED',
          message: 'Nonce has already been used. Replay attacks are strictly rejected.'
        });
        return;
      }

      // Expired heartbeat verification: ensure timestamp is within permitted skew window
      const reqTime = typeof timestamp === 'number' ? timestamp : new Date(timestamp).getTime();
      const now = Date.now();
      if (isNaN(reqTime) || Math.abs(now - reqTime) > SECURITY_CONFIG.HEARTBEAT_MAX_SKEW_MS) {
        res.status(400).json({
          success: false,
          error: 'HEARTBEAT_EXPIRED',
          message: `Heartbeat timestamp is outside the allowed skew window (+/- ${SECURITY_CONFIG.HEARTBEAT_MAX_SKEW_MS / 1000}s).`
        });
        return;
      }

      if (!enrollment.devicePublicKeyPem) {
        res.status(400).json({
          success: false,
          error: 'DEVICE_KEY_NOT_REGISTERED',
          message: 'No registered public key found for this enrollment.'
        });
        return;
      }

      // Verify only the canonical payload. Do not accept a nonce-only signature.
      const canonicalData = `${enrollmentId}|${nonce}|${timestamp}`;
      const isSignatureValid =
        CryptoService.verifyDeviceSignature(canonicalData, signature, enrollment.devicePublicKeyPem);

      if (!isSignatureValid) {
        res.status(401).json({
          success: false,
          error: 'INVALID_SIGNATURE',
          message: 'Cryptographic device signature verification failed against registered Keystore public key.'
        });
        return;
      }

      // Mark nonce as used
      db.usedNonces.add(nonce);

    const previousStatus = calculateDeviceOnlineStatus(enrollment);
    const nowIso = new Date().toISOString();

    // Update telemetry state on enrollment record
    enrollment.lastHeartbeatAt = nowIso;
    enrollment.lastSeenAt = nowIso;
    enrollment.lastSuccessfulSync = nowIso;
    enrollment.isOnline = true;
    enrollment.lastSecurityEvent = 'HEARTBEAT_RECEIVED';

    const bat = batteryPercent !== undefined ? batteryPercent : batteryPercentage;
    if (bat !== undefined && !isNaN(Number(bat))) {
      enrollment.lastKnownBattery = Number(bat);
    }

    const net = networkType || networkConnectivity;
    if (net) {
      enrollment.networkType = String(net);
    }

    if (androidVersion) {
      enrollment.androidVersion = String(androidVersion);
    }

    if (appVersion) {
      enrollment.appVersion = String(appVersion);
    }

    if (managementStatus) {
      if (managementStatus === 'DEVICE_OWNER' || managementStatus === 'DEVICE_ADMIN' || managementStatus === 'UNMANAGED') {
        enrollment.managementMode = managementStatus;
      }
    }

    // SIM / subscription change detection.
    // Prefer the Android-provided subscription fingerprint because carrier names alone
    // are not a reliable identity signal (e.g. MVNO/roaming/name changes).
    const simIdentityChanged =
      !!simFingerprint &&
      !!enrollment.simFingerprint &&
      simFingerprint !== enrollment.simFingerprint;
    const carrierChangedWithoutFingerprint =
      !simFingerprint &&
      !!simCarrier &&
      !!enrollment.simCarrier &&
      simCarrier !== enrollment.simCarrier;

    if (simIdentityChanged || carrierChangedWithoutFingerprint) {
      const previousCarrier = enrollment.simCarrier || 'UNKNOWN';
      const currentCarrier = simCarrier || 'UNKNOWN';
      const previousCount = enrollment.simSubscriptionCount ?? null;
      const currentCount = simSubscriptionCount ?? null;
      const alert: AlertRecord = {
        id: `alt-${crypto.randomBytes(3).toString('hex')}`,
        enrollmentId: enrollment.id,
        agreementId: enrollment.agreementId,
        severity: 'CRITICAL',
        alertType: 'SIM_CHANGE',
        title: 'SIM CHANGE DETECTED',
        details: `Android reported a SIM/subscription identity change. Previous carrier: '${previousCarrier}'. Current carrier: '${currentCarrier}'. Previous subscriptions: ${previousCount ?? 'unknown'}. Current subscriptions: ${currentCount ?? 'unknown'}.`,
        isAcknowledged: false,
        createdAt: nowIso
      };
      db.alerts.unshift(alert);

      AuditService.log({
        action: 'SIM_SWAP_DETECTED',
        entityName: 'device_enrollments',
        entityId: enrollment.id,
        changes: {
          previousCarrier,
          currentCarrier,
          previousSubscriptionCount: previousCount,
          currentSubscriptionCount: currentCount,
          fingerprintChanged: simIdentityChanged
        },
        ipAddress: req.ip
      });

      HeartbeatController.logActivity(
        enrollment.deviceId,
        enrollment.id,
        'SECURITY_EVENT',
        'SIM/subscription change detected from Android-supported subscription telemetry.',
        {
          previousCarrier,
          currentCarrier,
          previousSubscriptionCount: previousCount,
          currentSubscriptionCount: currentCount,
          fingerprintChanged: simIdentityChanged
        }
      );
    }
    if (simCarrier !== undefined) {
      enrollment.simCarrier = String(simCarrier);
    }
    if (simFingerprint !== undefined) {
      enrollment.simFingerprint = String(simFingerprint);
    }
    if (simSubscriptionCount !== undefined && !isNaN(Number(simSubscriptionCount))) {
      enrollment.simSubscriptionCount = Number(simSubscriptionCount);
    }

    // USB Debugging Anomaly Detection
    if (usbDebuggingEnabled && !enrollment.usbDebuggingActive) {
      const alert: AlertRecord = {
        id: `alt-${crypto.randomBytes(3).toString('hex')}`,
        enrollmentId: enrollment.id,
        severity: 'WARNING',
        alertType: 'DEBUG_ENABLED',
        title: 'USB Debugging Activated',
        details: 'ADB USB debugging has been enabled on managed unit.',
        isAcknowledged: false,
        createdAt: nowIso
      };
      db.alerts.unshift(alert);

      HeartbeatController.logActivity(
        enrollment.deviceId,
        enrollment.id,
        'SECURITY_EVENT',
        'Developer USB Debugging (ADB) activation flagged on device.',
        { usbDebuggingActive: true }
      );
    }
    enrollment.usbDebuggingActive = !!usbDebuggingEnabled;

    // Transition activity: If device was previously OFFLINE or UNKNOWN, log DEVICE_ONLINE
    if (previousStatus !== 'ONLINE') {
      HeartbeatController.logActivity(
        enrollment.deviceId,
        enrollment.id,
        'DEVICE_ONLINE',
        'Device transitioned to ONLINE following verified heartbeat telemetry.',
        { battery: enrollment.lastKnownBattery, network: enrollment.networkType }
      );
    }

    // Log routine HEARTBEAT_RECEIVED event
    HeartbeatController.logActivity(
      enrollment.deviceId,
      enrollment.id,
      'HEARTBEAT_RECEIVED',
      'Cryptographically verified heartbeat processed via WorkManager background worker.',
      {
        battery: enrollment.lastKnownBattery,
        network: enrollment.networkType,
        appVersion: enrollment.appVersion
      }
    );

    // Record persistent heartbeat telemetry entry
    const hbRecord: DeviceHeartbeatRecord = {
      id: `hb-${crypto.randomBytes(4).toString('hex')}`,
      enrollmentId: enrollment.id,
      deviceId: enrollment.deviceId,
      batteryPercent: enrollment.lastKnownBattery,
      networkType: enrollment.networkType,
      appVersion: enrollment.appVersion,
      managementStatus: enrollment.managementMode,
      timestamp: nowIso,
      clientNonce: nonce,
      signatureVerified: !!signature,
      serverReceivedAt: nowIso
    };
    db.heartbeats.unshift(hbRecord);
    db.save();

    // Retrieve pending commands for this device
    const pendingCommands = db.commands.filter(
      c => c.enrollmentId === enrollment.id && c.status === 'PENDING'
    );

    res.json({
      success: true,
      data: {
        serverTimestamp: nowIso,
        deviceOnlineStatus: 'ONLINE',
        enrollmentStatus: enrollment.enrollmentStatus,
        lastSuccessfulSync: enrollment.lastSuccessfulSync,
        pendingCommands: pendingCommands.map(c => ({
          commandId: c.id,
          commandType: c.commandType,
          payload: c.payload,
          nonce: c.nonce,
          monotonicSequence: c.monotonicSequence,
          serverSignature: c.serverSignature,
          expiresAt: c.expiresAt
        }))
      }
    });
  }

  /**
   * GET /api/v1/devices/:id/status
   * Real-time computed device telemetry, online/offline state, and management status.
   */
  static async getDeviceStatus(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const timeoutMsParam = req.query.timeoutMs ? Number(req.query.timeoutMs) : undefined;
    const timeoutMs =
      timeoutMsParam && !isNaN(timeoutMsParam) && timeoutMsParam > 0
        ? timeoutMsParam
        : SECURITY_CONFIG.HEARTBEAT_TIMEOUT_MS;

    const enrollment = db.enrollments.find(e => e.id === id || e.deviceId === id);
    if (!enrollment) {
      res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: `Device or enrollment not found for identifier: ${id}`
      });
      return;
    }

    // IDOR check for CUSTOMER role
    if (req.user?.role === 'CUSTOMER') {
      const user = db.users.find(u => u.id === req.user?.userId);
      if (enrollment.customerId !== user?.customerId) {
        res.status(403).json({
          success: false,
          error: 'FORBIDDEN',
          message: 'Access denied: You can only query your own enrolled devices.'
        });
        return;
      }
    }

    const device = db.devices.find(d => d.id === enrollment.deviceId);
    const customer = db.customers.find(c => c.id === enrollment.customerId);
    const agreement = db.agreements.find(a => a.id === enrollment.agreementId);

    // Compute dynamic server status based on configured timeout
    const currentOnlineStatus = calculateDeviceOnlineStatus(enrollment, timeoutMs);

    // If device was recorded as online but has now timed out, reflect transition
    if (enrollment.isOnline && currentOnlineStatus === 'OFFLINE') {
      enrollment.isOnline = false;
      HeartbeatController.logActivity(
        enrollment.deviceId,
        enrollment.id,
        'DEVICE_OFFLINE',
        `Device transitioned to OFFLINE: no heartbeat received within ${timeoutMs / 1000}s timeout.`,
        { timeoutMs }
      );
    }

    const latestCommand = db.commands
      .filter(c => c.enrollmentId === enrollment.id)
      .slice(-1)[0];

    res.json({
      success: true,
      data: {
        deviceId: device?.id || enrollment.deviceId,
        enrollmentId: enrollment.id,
        deviceName: device ? `${device.manufacturer} ${device.model}` : 'Unknown Hardware',
        model: device?.model,
        manufacturer: device?.manufacturer,
        hardwareSerial: device?.hardwareSerial,
        customer: {
          id: customer?.id,
          fullName: customer?.fullName,
          phoneNumber: customer?.phoneNumber,
          email: customer?.email
        },
        agreement: {
          id: agreement?.id,
          agreementCode: agreement?.agreementCode,
          status: agreement?.status,
          remainingAmount: agreement?.remainingAmount
        },
        enrollmentStatus: enrollment.enrollmentStatus,
        deviceOnlineStatus: currentOnlineStatus,
        lastSeenAt: enrollment.lastSeenAt || enrollment.lastHeartbeatAt || null,
        lastHeartbeatAt: enrollment.lastHeartbeatAt || null,
        lastSuccessfulSync: enrollment.lastSuccessfulSync || enrollment.lastHeartbeatAt || null,
        androidVersion: enrollment.androidVersion || null,
        appVersion: enrollment.appVersion || '1.0.0',
        managementMode: enrollment.managementMode,
        managementStatus: enrollment.managementMode,
        batteryPercent: enrollment.lastKnownBattery ?? null,
        networkConnectivity: enrollment.networkType ?? 'WIFI',
        simCarrier: enrollment.simCarrier ?? null,
        usbDebuggingActive: enrollment.usbDebuggingActive,
        lastCommandStatus: latestCommand?.status || enrollment.lastCommandStatus || 'NONE',
        lastSecurityEvent: enrollment.lastSecurityEvent || 'NORMAL',
        timeoutConfiguredMs: timeoutMs,
        platformLimitations: {
          backgroundExecution: 'Android WorkManager scheduled background task',
          intervalConstraint: 'PeriodicWorkRequest minimum 15-minute execution window enforced by OS',
          powerManagement: 'Subject to Android Doze mode and App Standby battery buckets',
          truthModel: 'State represents verified last-known telemetry rather than an unverified live ping'
        }
      }
    });
  }

  /**
   * GET /api/v1/devices/:id/activity
   * Returns chronological activity timeline for the device.
   */
  static async getDeviceActivities(req: Request, res: Response): Promise<void> {
    const { id } = req.params;

    const enrollment = db.enrollments.find(e => e.id === id || e.deviceId === id);
    if (!enrollment) {
      res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: `Device or enrollment not found for identifier: ${id}`
      });
      return;
    }

    // IDOR check for CUSTOMER role
    if (req.user?.role === 'CUSTOMER') {
      const user = db.users.find(u => u.id === req.user?.userId);
      if (enrollment.customerId !== user?.customerId) {
        res.status(403).json({
          success: false,
          error: 'FORBIDDEN',
          message: 'Access denied: You can only query your own enrolled devices.'
        });
        return;
      }
    }

    const activities = db.deviceActivities
      .filter(a => a.deviceId === enrollment.deviceId || a.enrollmentId === enrollment.id)
      .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

    res.json({
      success: true,
      data: activities,
      meta: {
        total: activities.length,
        deviceId: enrollment.deviceId,
        enrollmentId: enrollment.id
      }
    });
  }
}
