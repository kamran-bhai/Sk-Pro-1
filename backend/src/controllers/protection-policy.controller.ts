import { Request, Response } from 'express';
import crypto from 'crypto';
import { db } from '../services/store.js';
import { AuditService } from '../services/audit.service.js';
import { CryptoService } from '../services/crypto.service.js';

function queueProtectionLock(enrollmentId: string, reason: string, payload: Record<string, any>): string | null {
  const enrollment = db.enrollments.find(e => e.id === enrollmentId);
  if (!enrollment || !['ACTIVE', 'LOCKED'].includes(enrollment.enrollmentStatus)) return null;
  if (!['DEVICE_OWNER', 'DEVICE_ADMIN'].includes(enrollment.managementMode)) return null;
  if (enrollment.enrollmentStatus === 'LOCKED') return null;

  const pending = db.commands.some(c =>
    c.enrollmentId === enrollmentId &&
    c.commandType === 'LOCK_DEVICE' &&
    ['PENDING', 'SENT'].includes(c.status)
  );
  if (pending) return null;

  const commandId = `cmd-${crypto.randomUUID()}`;
  const nonce = CryptoService.generateNonce();
  const sequence = CryptoService.getNextSequence().toString();
  const expiresAtMs = Date.now() + 24 * 60 * 60 * 1000;
  const serverSignature = CryptoService.signCommand({
    commandId,
    enrollmentId,
    commandType: 'LOCK_DEVICE',
    nonce,
    sequence,
    expiresAt: expiresAtMs
  });

  db.commands.unshift({
    id: commandId,
    enrollmentId,
    commandType: 'LOCK_DEVICE',
    payload: { ...payload, reason },
    nonce,
    monotonicSequence: sequence,
    serverSignature,
    status: 'PENDING',
    issuedBy: 'system',
    expiresAt: new Date(expiresAtMs).toISOString(),
    createdAt: new Date().toISOString()
  });

  enrollment.lastSecurityEvent = 'PROTECTION_LOCK_PENDING';
  AuditService.log({
    action: 'PROTECTION_LOCK_QUEUED',
    entityName: 'device_commands',
    entityId: commandId,
    changes: { enrollmentId, reason, ...payload }
  });
  db.save();
  return commandId;
}

export class ProtectionPolicyController {
  static getPolicy(req: Request, res: Response): void {
    const enrollment = db.enrollments.find(e => e.id === req.params.id || e.deviceId === req.params.id);
    if (!enrollment) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Enrolled device not found.' });
      return;
    }

    res.json({
      success: true,
      data: {
        enrollmentId: enrollment.id,
        deviceId: enrollment.deviceId,
        autoLockEnabled: enrollment.autoLockEnabled !== false,
        antiTheftEnabled: enrollment.antiTheftEnabled !== false,
        lockOnSimChange: enrollment.lockOnSimChange !== false,
        lockOnUsbDebugging: enrollment.lockOnUsbDebugging !== false,
        managementMode: enrollment.managementMode
      }
    });
  }

  static updatePolicy(req: Request, res: Response): void {
    const enrollment = db.enrollments.find(e => e.id === req.params.id || e.deviceId === req.params.id);
    if (!enrollment) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Enrolled device not found.' });
      return;
    }

    const body = req.body || {};
    const fields: Array<keyof typeof enrollment> = [
      'autoLockEnabled',
      'antiTheftEnabled',
      'lockOnSimChange',
      'lockOnUsbDebugging'
    ];

    for (const field of fields) {
      if (body[field] !== undefined) {
        if (typeof body[field] !== 'boolean') {
          res.status(400).json({ success: false, error: 'INVALID_POLICY_VALUE', message: `${String(field)} must be boolean.` });
          return;
        }
        (enrollment as any)[field] = body[field];
      }
    }

    db.save();
    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: 'DEVICE_PROTECTION_POLICY_UPDATED',
      entityName: 'device_enrollments',
      entityId: enrollment.id,
      changes: {
        autoLockEnabled: enrollment.autoLockEnabled !== false,
        antiTheftEnabled: enrollment.antiTheftEnabled !== false,
        lockOnSimChange: enrollment.lockOnSimChange !== false,
        lockOnUsbDebugging: enrollment.lockOnUsbDebugging !== false
      },
      ipAddress: req.ip
    });

    res.json({
      success: true,
      data: {
        enrollmentId: enrollment.id,
        deviceId: enrollment.deviceId,
        autoLockEnabled: enrollment.autoLockEnabled !== false,
        antiTheftEnabled: enrollment.antiTheftEnabled !== false,
        lockOnSimChange: enrollment.lockOnSimChange !== false,
        lockOnUsbDebugging: enrollment.lockOnUsbDebugging !== false,
        managementMode: enrollment.managementMode
      }
    });
  }

  static queueLock = queueProtectionLock;
}
