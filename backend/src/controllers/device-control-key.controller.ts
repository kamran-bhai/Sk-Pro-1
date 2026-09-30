import { Request, Response } from 'express';
import crypto from 'crypto';
import { db } from '../services/store.js';
import { CryptoService } from '../services/crypto.service.js';

function generateRawControlKey(): string {
  // Human-enterable secret; only its SHA-256 hash is persisted.
  return `SK-${crypto.randomBytes(18).toString('base64url').toUpperCase()}`;
}

function isExpired(key: { expiresAt?: string }): boolean {
  return Boolean(key.expiresAt && new Date(key.expiresAt).getTime() <= Date.now());
}

export class DeviceControlKeyController {
  static issueKey(req: Request, res: Response): void {
    const { deviceId, enrollmentId, retailerId, expiresInDays = 30 } = req.body || {};
    if (deviceId && db.deviceControlKeys.some(k => k.deviceId === deviceId && ['ISSUED', 'ACTIVATED'].includes(k.status))) {
      res.status(409).json({ success: false, error: 'DEVICE_ALREADY_HAS_CONTROL_KEY', message: 'This device already has an active control key.' });
      return;
    }
    if (enrollmentId && db.deviceControlKeys.some(k => k.enrollmentId === enrollmentId && ['ISSUED', 'ACTIVATED'].includes(k.status))) {
      res.status(409).json({ success: false, error: 'ENROLLMENT_ALREADY_HAS_CONTROL_KEY', message: 'This enrollment already has an active control key.' });
      return;
    }

    const days = Number(expiresInDays);
    if (!Number.isInteger(days) || days < 1 || days > 365) {
      res.status(400).json({ success: false, error: 'INVALID_EXPIRY', message: 'expiresInDays must be an integer from 1 to 365.' });
      return;
    }

    const rawKey = generateRawControlKey();
    const now = new Date();
    const record = {
      id: crypto.randomUUID(),
      keyHash: CryptoService.hashToken(rawKey),
      keyLast4: rawKey.slice(-4),
      issuedBy: req.user?.userId,
      retailerId: retailerId || undefined,
      deviceId: deviceId || undefined,
      enrollmentId: enrollmentId || undefined,
      status: 'ISSUED' as const,
      expiresAt: new Date(now.getTime() + days * 86400000).toISOString(),
      createdAt: now.toISOString()
    };
    db.deviceControlKeys.unshift(record);
    db.save();
    db.auditLogs.unshift({
      id: `aud-${crypto.randomBytes(4).toString('hex')}`, timestamp: now.toISOString(),
      actorId: req.user?.userId, actorEmail: req.user?.email,
      action: 'CONTROL_KEY_ISSUED', entityName: 'device_control_key', entityId: record.id,
      changes: { deviceId: record.deviceId, enrollmentId: record.enrollmentId, keyLast4: record.keyLast4 }
    });
    db.save();

    // Raw secret is returned exactly once and is never persisted.
    res.status(201).json({ success: true, data: { keyId: record.id, controlKey: rawKey, keyLast4: record.keyLast4, status: record.status, expiresAt: record.expiresAt } });
  }

  static listKeys(req: Request, res: Response): void {
    const status = typeof req.query.status === 'string' ? req.query.status.toUpperCase() : undefined;
    const keys = db.deviceControlKeys
      .filter(k => !status || k.status === status)
      .map(k => ({ id: k.id, keyLast4: k.keyLast4, deviceId: k.deviceId, enrollmentId: k.enrollmentId, retailerId: k.retailerId, status: isExpired(k) && k.status === 'ISSUED' ? 'EXPIRED' : k.status, expiresAt: k.expiresAt, activatedAt: k.activatedAt, createdAt: k.createdAt }));
    res.json({ success: true, data: keys });
  }

  static activateKey(req: Request, res: Response): void {
    const { controlKey, deviceId, enrollmentId } = req.body || {};
    if (typeof controlKey !== 'string' || controlKey.length < 10 || !deviceId || !enrollmentId) {
      res.status(400).json({ success: false, error: 'INVALID_ACTIVATION_REQUEST', message: 'controlKey, deviceId and enrollmentId are required.' });
      return;
    }
    const record = db.deviceControlKeys.find(k => k.keyHash === CryptoService.hashToken(controlKey));
    if (!record) {
      res.status(404).json({ success: false, error: 'CONTROL_KEY_NOT_FOUND', message: 'Invalid control key.' });
      return;
    }
    if (isExpired(record)) {
      record.status = 'EXPIRED'; db.save();
      res.status(410).json({ success: false, error: 'CONTROL_KEY_EXPIRED', message: 'This control key has expired.' });
      return;
    }
    if (record.status !== 'ISSUED') {
      res.status(409).json({ success: false, error: 'CONTROL_KEY_NOT_ACTIVATABLE', message: `Control key is already ${record.status.toLowerCase()}.` });
      return;
    }
    if (record.deviceId && record.deviceId !== deviceId) {
      res.status(409).json({ success: false, error: 'KEY_DEVICE_MISMATCH', message: 'This key is bound to a different device.' });
      return;
    }
    if (record.enrollmentId && record.enrollmentId !== enrollmentId) {
      res.status(409).json({ success: false, error: 'KEY_ENROLLMENT_MISMATCH', message: 'This key is bound to a different enrollment.' });
      return;
    }
    const enrollment = db.enrollments.find(e => e.id === enrollmentId && e.deviceId === deviceId);
    if (!enrollment) {
      res.status(404).json({ success: false, error: 'ENROLLMENT_NOT_FOUND', message: 'The supplied device enrollment was not found.' });
      return;
    }
    const otherActive = db.deviceControlKeys.find(k => k.id !== record.id && k.deviceId === deviceId && k.status === 'ACTIVATED');
    if (otherActive) {
      res.status(409).json({ success: false, error: 'DEVICE_ALREADY_BOUND', message: 'This device already has another activated control key.' });
      return;
    }
    record.deviceId = deviceId;
    record.enrollmentId = enrollmentId;
    record.status = 'ACTIVATED';
    record.activatedAt = new Date().toISOString();
    db.save();
    db.auditLogs.unshift({ id: `aud-${crypto.randomBytes(4).toString('hex')}`, timestamp: new Date().toISOString(), action: 'CONTROL_KEY_ACTIVATED', entityName: 'device_control_key', entityId: record.id, changes: { deviceId, enrollmentId, keyLast4: record.keyLast4 } });
    db.save();
    res.json({ success: true, data: { keyId: record.id, keyLast4: record.keyLast4, status: record.status, deviceId, enrollmentId, activatedAt: record.activatedAt } });
  }

  static revokeKey(req: Request, res: Response): void {
    const record = db.deviceControlKeys.find(k => k.id === req.params.id);
    if (!record) { res.status(404).json({ success: false, error: 'CONTROL_KEY_NOT_FOUND' }); return; }
    if (record.status === 'REVOKED') { res.status(409).json({ success: false, error: 'CONTROL_KEY_ALREADY_REVOKED' }); return; }
    record.status = 'REVOKED';
    record.revokedAt = new Date().toISOString();
    db.save();
    db.auditLogs.unshift({ id: `aud-${crypto.randomBytes(4).toString('hex')}`, timestamp: new Date().toISOString(), actorId: req.user?.userId, actorEmail: req.user?.email, action: 'CONTROL_KEY_REVOKED', entityName: 'device_control_key', entityId: record.id, changes: { keyLast4: record.keyLast4 } });
    db.save();
    res.json({ success: true, data: { keyId: record.id, keyLast4: record.keyLast4, status: record.status, revokedAt: record.revokedAt } });
  }
}
