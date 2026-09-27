import { Request, Response } from 'express';
import crypto from 'crypto';
import { db, CustomerRecord } from '../services/store.js';
import { AuditService } from '../services/audit.service.js';

export class CustomerController {
  /**
   * GET /api/v1/customers
   * Lists customers. Accessible to ADMIN and SUPPORT.
   */
  static async listCustomers(req: Request, res: Response): Promise<void> {
    const { status, search } = req.query;

    let result = db.customers.map(c => {
      const agreements = db.agreements.filter(a => a.customerId === c.id);
      const devices = db.enrollments.filter(e => e.customerId === c.id);
      const totalFinanced = agreements.reduce((sum, a) => sum + a.totalAmount, 0);
      const totalRemaining = agreements.reduce((sum, a) => sum + a.remainingAmount, 0);

      return {
        ...c,
        activeAgreementsCount: agreements.filter(a => a.status === 'ACTIVE' || a.status === 'OVERDUE').length,
        devicesCount: devices.length,
        totalFinanced,
        totalRemaining
      };
    });

    if (status && typeof status === 'string' && status !== 'ALL') {
      result = result.filter(c => c.status === status);
    }

    if (search && typeof search === 'string') {
      const q = search.toLowerCase();
      result = result.filter(c =>
        c.fullName.toLowerCase().includes(q) ||
        c.phoneNumber.toLowerCase().includes(q) ||
        c.email.toLowerCase().includes(q) ||
        c.id.toLowerCase().includes(q)
      );
    }

    res.json({
      success: true,
      data: result,
      meta: {
        total: result.length,
        activeCount: result.filter(c => c.status === 'ACTIVE').length,
        overdueCount: result.filter(c => c.status === 'OVERDUE').length
      }
    });
  }

  /**
   * GET /api/v1/customers/:id
   * Detailed customer view with IDOR protection.
   */
  static async getCustomerDetails(req: Request, res: Response): Promise<void> {
    const { id } = req.params;

    // IDOR check: CUSTOMER role can only access their own record
    if (req.user?.role === 'CUSTOMER') {
      const loggedInCustomer = db.users.find(u => u.id === req.user?.userId);
      if (loggedInCustomer?.customerId !== id && loggedInCustomer?.email !== id) {
        res.status(403).json({
          success: false,
          error: 'FORBIDDEN_IDOR_VIOLATION',
          message: 'Access denied: You can only access your own customer profile.'
        });
        return;
      }
    }

    const customer = db.customers.find(c => c.id === id || c.email === id);
    if (!customer) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Customer not found' });
      return;
    }

    // Associated Agreements
    const agreements = db.agreements.filter(a => a.customerId === customer.id);
    
    // Associated Devices (from enrollments)
    const enrollments = db.enrollments.filter(e => e.customerId === customer.id);
    const linkedDevices = enrollments.map(e => {
      const dev = db.devices.find(d => d.id === e.deviceId);
      const agr = db.agreements.find(a => a.id === e.agreementId);
      return {
        enrollmentId: e.id,
        deviceId: dev?.id,
        model: dev?.model,
        manufacturer: dev?.manufacturer,
        status: e.enrollmentStatus,
        agreementCode: agr?.agreementCode,
        battery: e.lastKnownBattery,
        isOnline: e.isOnline,
        lastHeartbeatAt: e.lastHeartbeatAt
      };
    });

    res.json({
      success: true,
      data: {
        customer,
        agreements,
        devices: linkedDevices
      }
    });
  }

  /**
   * POST /api/v1/customers
   * Adds a new customer. Requires ADMIN or SUPPORT role.
   */
  static async createCustomer(req: Request, res: Response): Promise<void> {
    const { fullName, phoneNumber, email, address, nationalIdMasked } = req.body;

    // Validation
    if (!fullName || typeof fullName !== 'string' || fullName.trim().length < 2) {
      res.status(400).json({ success: false, error: 'VALIDATION_FAILED', message: 'Valid fullName is required' });
      return;
    }
    if (!phoneNumber || typeof phoneNumber !== 'string' || phoneNumber.trim().length < 7) {
      res.status(400).json({ success: false, error: 'VALIDATION_FAILED', message: 'Valid phoneNumber is required' });
      return;
    }

    // Check duplicate phone
    const existingPhone = db.customers.find(c => c.phoneNumber.replace(/[^0-9]/g, '') === phoneNumber.replace(/[^0-9]/g, ''));
    if (existingPhone) {
      res.status(409).json({ success: false, error: 'PHONE_EXISTS', message: 'A customer with this phone number already exists' });
      return;
    }

    const newCustomer: CustomerRecord = {
      id: `cust-${crypto.randomBytes(3).toString('hex')}`,
      fullName: fullName.trim(),
      phoneNumber: phoneNumber.trim(),
      email: (email || '').trim().toLowerCase(),
      address: (address || '').trim(),
      nationalIdMasked: (nationalIdMasked || 'ID-***-0000').trim(),
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    db.customers.unshift(newCustomer);
    db.save();

    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: 'CUSTOMER_CREATED',
      entityName: 'customers',
      entityId: newCustomer.id,
      changes: {
        fullName: newCustomer.fullName,
        phoneNumber: newCustomer.phoneNumber,
        email: newCustomer.email
      },
      ipAddress: req.ip
    });

    res.status(201).json({ success: true, data: newCustomer });
  }

  /**
   * PUT /api/v1/customers/:id
   * Edits customer details.
   */
  static async updateCustomer(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const { fullName, phoneNumber, email, address, status } = req.body;

    const customer = db.customers.find(c => c.id === id);
    if (!customer) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Customer not found' });
      return;
    }

    const previousState = { ...customer };

    if (fullName) customer.fullName = fullName.trim();
    if (phoneNumber) customer.phoneNumber = phoneNumber.trim();
    if (email !== undefined) customer.email = email.trim().toLowerCase();
    if (address !== undefined) customer.address = address.trim();
    if (status && ['ACTIVE', 'OVERDUE', 'COMPLETED', 'DEFAULTED'].includes(status)) {
      customer.status = status;
    }
    customer.updatedAt = new Date().toISOString();

    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: 'CUSTOMER_UPDATED',
      entityName: 'customers',
      entityId: customer.id,
      changes: {
        previous: previousState,
        updated: customer
      },
      ipAddress: req.ip
    });

    res.json({ success: true, data: customer });
  }
}
