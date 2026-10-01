import { Request, Response } from 'express';
import crypto from 'crypto';
import { db, CommandRecord } from '../services/store.js';
import { CryptoService } from '../services/crypto.service.js';
import { AuditService } from '../services/audit.service.js';

export class CommandController {
  /**
   * POST /api/v1/devices/:id/commands
   * Issues a signed, nonce-guarded command with replay protection.
   */
  static async issueCommand(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const { commandType, reason, payload = {} } = req.body;

    const allowedTypes = ['LOCK_DEVICE', 'UNLOCK_DEVICE', 'STATUS_REQUEST', 'LOCATION_REQUEST', 'REFRESH_POLICIES'];
    if (!allowedTypes.includes(commandType)) {
      res.status(400).json({
        success: false,
        error: 'INVALID_COMMAND_TYPE',
        message: `Command type must be one of: ${allowedTypes.join(', ')}`
      });
      return;
    }

    const enrollment = db.enrollments.find(e => e.id === id || e.deviceId === id);
    if (!enrollment) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Enrolled device not found' });
      return;
    }

    if (!['ACTIVE', 'LOCKED'].includes(enrollment.enrollmentStatus)) {
      res.status(409).json({
        success: false,
        error: 'DEVICE_NOT_READY',
        message: 'Remote commands require an ACTIVE or LOCKED enrollment.'
      });
      return;
    }

    if (enrollment.managementMode === 'UNMANAGED') {
      res.status(409).json({
        success: false,
        error: 'DEVICE_MANAGEMENT_REQUIRED',
        message: 'Remote lock requires Android Device Owner or active Device Admin management on the enrolled device.'
      });
      return;
    }

    if (commandType === 'UNLOCK_DEVICE') {
      res.status(409).json({
        success: false,
        error: 'REMOTE_UNLOCK_UNSUPPORTED',
        message: 'Android does not permit a normal enrolled application to silently unlock the system lock screen.'
      });
      return;
    }

    const commandId = `cmd-${crypto.randomUUID()}`;
    const nonce = CryptoService.generateNonce();
    const sequence = CryptoService.getNextSequence().toString();
    const expiresAt = Date.now() + 24 * 60 * 60 * 1000; // 24 hours expiry

    // Cryptographic signature computed by server authority
    const serverSignature = CryptoService.signCommand({
      commandId,
      enrollmentId: enrollment.id,
      commandType,
      nonce,
      sequence,
      expiresAt
    });

    const newCommand: CommandRecord = {
      id: commandId,
      enrollmentId: enrollment.id,
      commandType,
      payload: { ...payload, reason },
      nonce,
      monotonicSequence: sequence,
      serverSignature,
      status: 'PENDING',
      issuedBy: req.user?.userId || 'system',
      expiresAt: new Date(expiresAt).toISOString(),
      createdAt: new Date().toISOString()
    };

    db.commands.unshift(newCommand);

    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: `COMMAND_ISSUED_${commandType}`,
      entityName: 'device_commands',
      entityId: commandId,
      changes: {
        enrollmentId: enrollment.id,
        reason,
        newStatus: enrollment.enrollmentStatus
      },
      ipAddress: req.ip
    });

    res.status(201).json({
      success: true,
      data: {
        command: newCommand,
        message: `Command ${commandType} signed and dispatched.`
      }
    });
  }

  /**
   * GET /api/v1/devices/:id/commands
   */
  static async getDeviceCommands(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const commands = db.commands.filter(c => c.enrollmentId === id || c.enrollmentId === db.enrollments.find(e => e.deviceId === id)?.id);
    res.json({ success: true, data: commands });
  }

  /**
   * POST /api/v1/device-gateway/command-ack
   * Android client acknowledges command execution over TLS.
   */
  static async acknowledgeCommand(req: Request, res: Response): Promise<void> {
    const {
      commandId,
      enrollmentId,
      executionStatus,
      deviceSignature,
      failureReason
    } = req.body;

    if (
      typeof commandId !== 'string' ||
      typeof enrollmentId !== 'string' ||
      !['SUCCESS', 'FAILED'].includes(executionStatus) ||
      typeof deviceSignature !== 'string' ||
      deviceSignature.length < 16
    ) {
      res.status(400).json({
        success: false,
        error: 'INVALID_COMMAND_ACK',
        message: 'commandId, enrollmentId, executionStatus and deviceSignature are required.'
      });
      return;
    }

    const command = db.commands.find(c => c.id === commandId);
    if (!command) {
      res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Command ID not found.'
      });
      return;
    }

    if (command.enrollmentId !== enrollmentId) {
      res.status(409).json({
        success: false,
        error: 'COMMAND_ENROLLMENT_MISMATCH',
        message: 'Command does not belong to the supplied enrollment.'
      });
      return;
    }

    const enrollment = db.enrollments.find(e => e.id === enrollmentId);
    if (!enrollment) {
      res.status(404).json({
        success: false,
        error: 'ENROLLMENT_NOT_FOUND',
        message: 'Enrollment not found.'
      });
      return;
    }

    if (command.status === 'ACKNOWLEDGED' || command.status === 'FAILED') {
      res.json({
        success: true,
        data: {
          commandId,
          status: command.status,
          message: 'Command acknowledgment was already recorded.'
        }
      });
      return;
    }

    if (new Date(command.expiresAt).getTime() <= Date.now()) {
      command.status = 'EXPIRED';
      command.failureReason = 'COMMAND_EXPIRED';
      db.save();
      res.status(410).json({
        success: false,
        error: 'COMMAND_EXPIRED',
        message: 'This command has expired.'
      });
      return;
    }

    if (!enrollment.devicePublicKeyPem) {
      res.status(400).json({
        success: false,
        error: 'DEVICE_KEY_NOT_REGISTERED',
        message: 'No registered device public key is available for command acknowledgment.'
      });
      return;
    }

    const canonicalAck = `${command.id}|${enrollment.id}|${executionStatus}|${command.nonce}`;
    const validDeviceSignature = CryptoService.verifyDeviceSignature(
      canonicalAck,
      deviceSignature,
      enrollment.devicePublicKeyPem
    );

    if (!validDeviceSignature) {
      AuditService.log({
        action: 'COMMAND_ACK_INVALID_SIGNATURE',
        entityName: 'device_commands',
        entityId: commandId,
        changes: { enrollmentId, executionStatus },
        ipAddress: req.ip
      });
      res.status(401).json({
        success: false,
        error: 'INVALID_DEVICE_SIGNATURE',
        message: 'Command acknowledgment signature could not be verified.'
      });
      return;
    }

    command.status = executionStatus === 'SUCCESS' ? 'ACKNOWLEDGED' : 'FAILED';
    command.acknowledgedAt = new Date().toISOString();
    command.failureReason = executionStatus === 'FAILED'
      ? (failureReason || 'DEVICE_REPORTED_FAILURE')
      : undefined;

    enrollment.lastCommandStatus = command.status;

    if (executionStatus === 'SUCCESS' && command.commandType === 'LOCK_DEVICE') {
      enrollment.enrollmentStatus = 'LOCKED' as any;
      enrollment.lastSecurityEvent = 'REMOTE_LOCK_ACKNOWLEDGED';
    } else if (executionStatus === 'SUCCESS' && command.commandType === 'UNLOCK_DEVICE') {
      enrollment.enrollmentStatus = 'ACTIVE';
      enrollment.lastSecurityEvent = 'REMOTE_UNLOCK_ACKNOWLEDGED';
    } else if (executionStatus === 'FAILED') {
      enrollment.lastSecurityEvent = 'REMOTE_COMMAND_FAILED';
    }

    db.deviceActivities.unshift({
      id: `act-${crypto.randomBytes(4).toString('hex')}`,
      deviceId: enrollment.deviceId,
      enrollmentId: enrollment.id,
      eventType: command.status === 'ACKNOWLEDGED' ? 'COMMAND_ACKNOWLEDGED' : 'COMMAND_FAILED',
      description: command.status === 'ACKNOWLEDGED'
        ? `Command ${command.commandType} was executed and cryptographically acknowledged by the enrolled device.`
        : `Command ${command.commandType} failed on the enrolled device: ${command.failureReason || 'Unknown error'}`,
      details: {
        commandId,
        commandType: command.commandType,
        executionStatus,
        failureReason
      },
      timestamp: new Date().toISOString()
    });

    AuditService.log({
      action: `COMMAND_ACK_${executionStatus}`,
      entityName: 'device_commands',
      entityId: commandId,
      changes: {
        enrollmentId,
        commandType: command.commandType,
        executionStatus,
        failureReason
      },
      ipAddress: req.ip
    });

    db.save();

    res.json({
      success: true,
      data: {
        commandId,
        status: command.status,
        acknowledgedAt: command.acknowledgedAt
      }
    });
  }}
