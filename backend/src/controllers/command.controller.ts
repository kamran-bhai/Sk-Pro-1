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

    // Update enrollment status
    if (commandType === 'LOCK_DEVICE') {
      enrollment.enrollmentStatus = 'LOCKED';
    } else if (commandType === 'UNLOCK_DEVICE') {
      enrollment.enrollmentStatus = 'ACTIVE';
    }

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
    const { commandId, executionStatus, deviceSignature, failureReason } = req.body;

    const command = db.commands.find(c => c.id === commandId);
    if (!command) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Command ID not found' });
      return;
    }

    command.status = executionStatus === 'SUCCESS' ? 'ACKNOWLEDGED' : 'FAILED';
    command.acknowledgedAt = new Date().toISOString();
    command.failureReason = failureReason;

    const enrollment = db.enrollments.find(e => e.id === command.enrollmentId);
    if (enrollment) {
      enrollment.lastCommandStatus = command.status;
      const eventType = command.status === 'ACKNOWLEDGED' ? 'COMMAND_ACKNOWLEDGED' : 'COMMAND_FAILED';
      const description =
        command.status === 'ACKNOWLEDGED'
          ? `Command ${command.commandType} successfully executed and acknowledged by device.`
          : `Command ${command.commandType} execution failed on device: ${failureReason || 'Unknown error'}`;

      db.deviceActivities.unshift({
        id: `act-${crypto.randomBytes(4).toString('hex')}`,
        deviceId: enrollment.deviceId,
        enrollmentId: enrollment.id,
        eventType,
        description,
        details: { commandId, commandType: command.commandType, executionStatus, failureReason },
        timestamp: new Date().toISOString()
      });
    }

    AuditService.log({
      action: `COMMAND_ACK_${executionStatus}`,
      entityName: 'device_commands',
      entityId: commandId,
      changes: { executionStatus, failureReason },
      ipAddress: req.ip
    });

    res.json({ success: true, message: 'Command acknowledgment recorded' });
  }
}
