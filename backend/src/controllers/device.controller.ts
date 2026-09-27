import { Request, Response } from 'express';
import crypto from 'crypto';
import { db, DeviceRecord } from '../services/store.js';
import { AuditService } from '../services/audit.service.js';

export class DeviceController {
  /**
   * GET /api/v1/devices
   * Supports search, filter, and returns associated customer & agreement.
   */
  static async listDevices(req: Request, res: Response): Promise<void> {
    const { status, search } = req.query;

    let result = db.enrollments.map(enr => {
      const dev = db.devices.find(d => d.id === enr.deviceId);
      const cust = db.customers.find(c => c.id === enr.customerId);
      const agr = db.agreements.find(a => a.id === enr.agreementId);

      return {
        enrollmentId: enr.id,
        deviceId: dev?.id,
        model: dev?.model,
        manufacturer: dev?.manufacturer,
        brand: dev?.brand,
        hardwareSerial: dev?.hardwareSerial,
        customerId: cust?.id,
        customerName: cust?.fullName,
        customerPhone: cust?.phoneNumber,
        agreementId: agr?.id,
        agreementCode: agr?.agreementCode,
        agreementStatus: agr?.status,
        remainingAmount: agr?.remainingAmount,
        status: enr.enrollmentStatus,
        managementMode: enr.managementMode,
        battery: enr.lastKnownBattery,
        isOnline: enr.isOnline,
        simCarrier: enr.simCarrier,
        usbDebugging: enr.usbDebuggingActive,
        lastHeartbeatAt: enr.lastHeartbeatAt,
        enrolledAt: enr.enrolledAt
      };
    });

    // IDOR filter: if CUSTOMER role, only return their own devices
    if (req.user?.role === 'CUSTOMER') {
      const user = db.users.find(u => u.id === req.user?.userId);
      result = result.filter(d => d.customerId === user?.customerId);
    }

    if (status && typeof status === 'string' && status !== 'ALL') {
      result = result.filter(d => d.status === status);
    }

    if (search && typeof search === 'string') {
      const q = search.toLowerCase();
      result = result.filter(d =>
        d.model?.toLowerCase().includes(q) ||
        d.customerName?.toLowerCase().includes(q) ||
        d.enrollmentId.toLowerCase().includes(q) ||
        d.hardwareSerial?.toLowerCase().includes(q)
      );
    }

    res.json({
      success: true,
      data: result,
      meta: {
        total: result.length,
        activeCount: result.filter(d => d.status === 'ACTIVE').length,
        overdueCount: result.filter(d => d.status === 'OVERDUE').length,
        lockedCount: result.filter(d => d.status === 'LOCKED').length
      }
    });
  }

  /**
   * GET /api/v1/devices/:id
   * Get device details with customer & agreement linkage and IDOR protection.
   */
  static async getDeviceDetails(req: Request, res: Response): Promise<void> {
    const { id } = req.params;

    const enr = db.enrollments.find(e => e.id === id || e.deviceId === id);
    if (!enr) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Device not found' });
      return;
    }

    // IDOR check for CUSTOMER role
    if (req.user?.role === 'CUSTOMER') {
      const user = db.users.find(u => u.id === req.user?.userId);
      if (enr.customerId !== user?.customerId) {
        res.status(403).json({ success: false, error: 'FORBIDDEN', message: 'Access denied: Not your device' });
        return;
      }
    }

    const dev = db.devices.find(d => d.id === enr.deviceId);
    const cust = db.customers.find(c => c.id === enr.customerId);
    const agr = db.agreements.find(a => a.id === enr.agreementId);
    const recentCommands = db.commands.filter(c => c.enrollmentId === enr.id).slice(0, 10);
    const alerts = db.alerts.filter(a => a.enrollmentId === enr.id);

    res.json({
      success: true,
      data: {
        enrollment: enr,
        hardware: dev,
        customer: cust,
        agreement: agr,
        recentCommands,
        alerts
      }
    });
  }

  /**
   * POST /api/v1/devices
   * Adds new smartphone hardware to inventory.
   */
  static async registerDeviceHardware(req: Request, res: Response): Promise<void> {
    const { manufacturer, model, brand, hardwareSerial, initialCarrier } = req.body;

    if (!manufacturer || !model) {
      res.status(400).json({ success: false, error: 'VALIDATION_FAILED', message: 'manufacturer and model are required' });
      return;
    }

    // Validate serial uniqueness
    const serial = (hardwareSerial || `SN-${Date.now()}`).trim();
    const existingSerial = db.devices.find(d => d.hardwareSerial === serial);
    if (existingSerial) {
      res.status(409).json({ success: false, error: 'SERIAL_EXISTS', message: 'A device with this hardware serial already exists' });
      return;
    }

    const newDevice: DeviceRecord = {
      id: `dev-hw-${crypto.randomBytes(3).toString('hex')}`,
      manufacturer: manufacturer.trim(),
      model: model.trim(),
      brand: (brand || manufacturer).trim(),
      hardwareSerial: serial,
      initialCarrier: (initialCarrier || 'Unlocked').trim(),
      createdAt: new Date().toISOString()
    };

    db.devices.push(newDevice);
    db.save();

    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: 'HARDWARE_REGISTERED',
      entityName: 'devices',
      entityId: newDevice.id,
      changes: newDevice,
      ipAddress: req.ip
    });

    res.status(201).json({ success: true, data: newDevice });
  }
}
