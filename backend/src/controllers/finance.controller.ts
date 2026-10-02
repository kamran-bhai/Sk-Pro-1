import { Request, Response } from 'express';
import crypto from 'crypto';
import { db, AgreementRecord, PaymentRecord, generateInstallmentSchedule, EnrollmentRecord } from '../services/store.js';
import { AuditService } from '../services/audit.service.js';
import { CryptoService } from '../services/crypto.service.js';
import { ProtectionPolicyController } from './protection-policy.controller.js';

export function syncOverdueFinancingState(): void {
  const now = Date.now();

  for (const agreement of db.agreements) {
    if (!['ACTIVE', 'OVERDUE'].includes(agreement.status)) continue;

    const installments = db.installments
      .filter(i => i.agreementId === agreement.id && i.status !== 'PAID' && i.status !== 'WAIVED')
      .sort((a, b) => a.installmentNumber - b.installmentNumber);

    let agreementIsOverdue = false;

    for (const installment of installments) {
      const dueTime = new Date(installment.dueDate + 'T23:59:59Z').getTime();
      const graceDays = Math.max(0, Number((agreement as any).gracePeriodDays ?? 5));
      const restrictionTime = dueTime + graceDays * 24 * 60 * 60 * 1000;

      if (now > restrictionTime && installment.status !== 'OVERDUE') {
        installment.status = 'OVERDUE';
        agreementIsOverdue = true;

        const existingAlert = db.alerts.find(
          a => a.agreementId === agreement.id &&
               a.alertType === 'PAYMENT_OVERDUE' &&
               a.details.includes('installment #' + installment.installmentNumber)
        );

        if (!existingAlert) {
          db.alerts.unshift({
            id: `alt-${crypto.randomBytes(3).toString('hex')}`,
            enrollmentId: db.enrollments.find(e => e.agreementId === agreement.id)?.id || '',
            agreementId: agreement.id,
            severity: 'CRITICAL',
            alertType: 'PAYMENT_OVERDUE',
            title: 'Payment Overdue',
            details: `Installment #${installment.installmentNumber} is overdue after the ${graceDays}-day grace period.`,
            isAcknowledged: false,
            createdAt: new Date().toISOString()
          });
        }
      }

      if (installment.status === 'OVERDUE') {
        agreementIsOverdue = true;
      }
    }

    if (!agreementIsOverdue) continue;

    agreement.status = 'OVERDUE';
    agreement.updatedAt = new Date().toISOString();

    const customer = db.customers.find(c => c.id === agreement.customerId);
    if (customer && customer.status !== 'COMPLETED') {
      customer.status = 'OVERDUE';
      customer.updatedAt = new Date().toISOString();
    }

    const enrollment = db.enrollments.find(e => e.agreementId === agreement.id);
    if (!enrollment) continue;

    if (!['ACTIVE', 'LOCKED'].includes(enrollment.enrollmentStatus)) {
      continue;
    }

    // Only managed devices may receive a remote lock command.
    if (!['DEVICE_OWNER', 'DEVICE_ADMIN'].includes(enrollment.managementMode)) {
      continue;
    }

    if (enrollment.autoLockEnabled === false) continue;
    if (enrollment.enrollmentStatus === 'LOCKED') continue;

    const commandId = ProtectionPolicyController.queueLock(
      enrollment.id,
      'FINANCING_OVERDUE',
      {
        agreementId: agreement.id,
        agreementCode: agreement.agreementCode,
        installmentNumber: installments.find(i => i.status === 'OVERDUE')?.installmentNumber
      }
    );
    if (!commandId) continue;
    enrollment.lastSecurityEvent = 'FINANCING_OVERDUE_LOCK_PENDING';

    AuditService.log({
      action: 'FINANCING_OVERDUE_LOCK_QUEUED',
      entityName: 'device_commands',
      entityId: commandId,
      changes: {
        agreementId: agreement.id,
        enrollmentId: enrollment.id,
        reason: 'FINANCING_OVERDUE',
        managementMode: enrollment.managementMode
      }
    });
  }

  db.save();
}

export class FinanceController {
  /**
   * GET /api/v1/agreements
   * Lists financing agreements. IDOR filtered if role is CUSTOMER.
   */
  static async listAgreements(req: Request, res: Response): Promise<void> {
    syncOverdueFinancingState();
    let list = db.agreements.map(a => {
      const cust = db.customers.find(c => c.id === a.customerId);
      const dev = db.devices.find(d => d.id === a.deviceId);
      const installments = db.installments.filter(i => i.agreementId === a.id);
      const overdueCount = installments.filter(i => i.status === 'OVERDUE').length;

      return {
        ...a,
        customerName: cust?.fullName,
        customerPhone: cust?.phoneNumber,
        deviceModel: dev ? `${dev.brand} ${dev.model}` : 'Unknown',
        overdueCount
      };
    });

    // IDOR check: CUSTOMER can only see agreements belonging to their customerId
    if (req.user?.role === 'CUSTOMER') {
      const user = db.users.find(u => u.id === req.user?.userId);
      list = list.filter(a => a.customerId === user?.customerId);
    }

    res.json({ success: true, data: list });
  }

  /**
   * GET /api/v1/agreements/:id
   * Detailed agreement view with complete installment schedule & payments.
   */
  static async getAgreementDetails(req: Request, res: Response): Promise<void> {
    syncOverdueFinancingState();
    const { id } = req.params;

    const agreement = db.agreements.find(a => a.id === id || a.agreementCode === id);
    if (!agreement) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Agreement not found' });
      return;
    }

    // IDOR check
    if (req.user?.role === 'CUSTOMER') {
      const user = db.users.find(u => u.id === req.user?.userId);
      if (agreement.customerId !== user?.customerId) {
        res.status(403).json({ success: false, error: 'FORBIDDEN', message: 'Access denied to this agreement' });
        return;
      }
    }

    const customer = db.customers.find(c => c.id === agreement.customerId);
    const device = db.devices.find(d => d.id === agreement.deviceId);
    const enrollment = db.enrollments.find(e => e.agreementId === agreement.id);
    const installments = db.installments.filter(i => i.agreementId === agreement.id).sort((a, b) => a.installmentNumber - b.installmentNumber);
    const payments = db.payments.filter(p => p.agreementId === agreement.id).sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

    // Calculate overdue amount
    const overdueInstallments = installments.filter(i => i.status === 'OVERDUE');
    const overdueAmount = overdueInstallments.reduce((sum, i) => sum + i.amount + (i.penaltyFee || 0), 0);

    res.json({
      success: true,
      data: {
        agreement,
        customer,
        device,
        enrollment,
        installments,
        payments,
        stats: {
          overdueAmount,
          overdueInstallmentsCount: overdueInstallments.length,
          paidAmount: +(agreement.totalAmount - agreement.downPayment - agreement.remainingAmount).toFixed(2)
        }
      }
    });
  }

  /**
   * POST /api/v1/agreements
   * Creates a new financing agreement with auto-generated installment schedule.
   */
  static async createAgreement(req: Request, res: Response): Promise<void> {
    const {
      customerId,
      deviceId,
      totalAmount,
      downPayment = 0,
      numberOfInstallments,
      startDate = new Date().toISOString().split('T')[0],
      firstDueDate,
      gracePeriodDays = 5
    } = req.body;

    // 1. Validation
    if (!customerId || !deviceId) {
      res.status(400).json({ success: false, error: 'VALIDATION_FAILED', message: 'customerId and deviceId are required' });
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

    // Check if device is already actively financed
    const activeAgreementOnDevice = db.agreements.find(
      a => a.deviceId === deviceId && (a.status === 'ACTIVE' || a.status === 'OVERDUE')
    );
    if (activeAgreementOnDevice) {
      res.status(409).json({
        success: false,
        error: 'DEVICE_ALREADY_FINANCED',
        message: `Device ${device.model} (${device.id}) is already actively financed under agreement ${activeAgreementOnDevice.agreementCode}.`
      });
      return;
    }

    const total = Number(totalAmount);
    const down = Number(downPayment);
    const tenure = Number(numberOfInstallments);

    if (isNaN(total) || total <= 0) {
      res.status(400).json({ success: false, error: 'VALIDATION_FAILED', message: 'totalAmount must be greater than 0' });
      return;
    }
    if (isNaN(down) || down < 0 || down >= total) {
      res.status(400).json({ success: false, error: 'VALIDATION_FAILED', message: 'downPayment must be >= 0 and less than totalAmount' });
      return;
    }
    if (isNaN(tenure) || tenure < 1 || tenure > 36) {
      res.status(400).json({ success: false, error: 'VALIDATION_FAILED', message: 'numberOfInstallments must be between 1 and 36' });
      return;
    }

    const financedAmount = +(total - down).toFixed(2);
    const monthlyInstallment = Math.floor((financedAmount / tenure) * 100) / 100;
    
    // Use the requested first due date when supplied; otherwise default to one month after start.
    const firstDue = firstDueDate ? new Date(firstDueDate) : new Date(startDate);
    if (!firstDueDate) {
      firstDue.setMonth(firstDue.getMonth() + 1);
    }
    if (Number.isNaN(firstDue.getTime())) {
      res.status(400).json({
        success: false,
        error: 'VALIDATION_FAILED',
        message: 'firstDueDate must be a valid date'
      });
      return;
    }
    const nextDueDate = firstDue.toISOString().split('T')[0];

    const agreementId = `agr-${crypto.randomBytes(3).toString('hex')}`;
    const agreementCode = `AGR-2026-${Math.floor(100 + Math.random() * 900)}`;

    const newAgreement: AgreementRecord = {
      id: agreementId,
      agreementCode,
      customerId,
      deviceId,
      totalAmount: total,
      downPayment: down,
      remainingAmount: financedAmount,
      installmentAmount: monthlyInstallment,
      numberOfInstallments: tenure,
      paidInstallments: 0,
      remainingInstallments: tenure,
      startDate,
      nextDueDate,
      gracePeriodDays: Number(gracePeriodDays) || 5,
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    db.agreements.push(newAgreement);

    // Auto-generate installment schedule with rounding adjustment
    const schedule = generateInstallmentSchedule(agreementId, total, down, tenure, startDate, nextDueDate);
    db.installments.push(...schedule);

    // Enrollment is a separate, explicit security step.
    // Creating an agreement must never fabricate an enrolled/managed device.
    // The admin must call POST /api/v1/enrollments to issue a real one-time ticket,
    // then the customer device performs cryptographic enrollment.

    db.save();

    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: 'AGREEMENT_CREATED',
      entityName: 'agreements',
      entityId: agreementId,
      changes: {
        agreementCode,
        customerId,
        deviceId,
        totalAmount: total,
        downPayment: down,
        financedAmount,
        tenure
      },
      ipAddress: req.ip
    });

    res.status(201).json({
      success: true,
      data: {
        agreement: newAgreement,
        installmentsCount: schedule.length,
        schedule
      }
    });
  }

  /**
   * POST /api/v1/payments
   * Records verified payment and automatically allocates it to installments.
   * NEVER stores credit card numbers, OTP, or passwords.
   */
  static async recordPayment(req: Request, res: Response): Promise<void> {
    const { agreementId, amount, paymentMethod, reference, notes, installmentId } = req.body;

    const agreement = db.agreements.find(a => a.id === agreementId || a.agreementCode === agreementId);
    if (!agreement) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Financing agreement not found' });
      return;
    }

    const payAmount = Number(amount);
    if (isNaN(payAmount) || payAmount <= 0) {
      res.status(400).json({ success: false, error: 'INVALID_AMOUNT', message: 'Payment amount must be greater than 0' });
      return;
    }

    if (agreement.status === 'COMPLETED' || agreement.remainingAmount <= 0) {
      res.status(400).json({ success: false, error: 'AGREEMENT_COMPLETED', message: 'This financing agreement is already completed' });
      return;
    }

    // Allocate payment to target installment or next pending/overdue installment
    const agreementInstallments = db.installments
      .filter(i => i.agreementId === agreement.id)
      .sort((a, b) => a.installmentNumber - b.installmentNumber);

    let targetInstallment = installmentId ? agreementInstallments.find(i => i.id === installmentId) : undefined;
    if (!targetInstallment) {
      // Find first OVERDUE or PENDING installment
      targetInstallment = agreementInstallments.find(i => i.status === 'OVERDUE') ||
                          agreementInstallments.find(i => i.status === 'PENDING');
    }

    if (!targetInstallment) {
      res.status(400).json({
        success: false,
        error: 'NO_PENDING_INSTALLMENT',
        message: 'No pending or overdue installment is available for this payment'
      });
      return;
    }

    if (targetInstallment.status === 'PAID' || targetInstallment.status === 'WAIVED') {
      res.status(409).json({
        success: false,
        error: 'INSTALLMENT_ALREADY_SETTLED',
        message: 'The selected installment is already settled'
      });
      return;
    }

    const expectedAmount = +(targetInstallment.amount + (targetInstallment.penaltyFee || 0)).toFixed(2);
    if (Math.abs(payAmount - expectedAmount) > 0.009) {
      res.status(400).json({
        success: false,
        error: 'PAYMENT_AMOUNT_MISMATCH',
        message: 'Payment amount must exactly settle installment #' + targetInstallment.installmentNumber + ': ' + expectedAmount.toFixed(2)
      });
      return;
    }

    if (payAmount > agreement.remainingAmount + 0.009) {
      res.status(400).json({
        success: false,
        error: 'PAYMENT_EXCEEDS_BALANCE',
        message: 'Payment cannot exceed the agreement remaining balance'
      });
      return;
    }

    const payment: PaymentRecord = {
      id: `pay-${crypto.randomUUID()}`,
      agreementId: agreement.id,
      installmentId: targetInstallment.id,
      amount: payAmount,
      paymentMethod: ['BANK_TRANSFER', 'CASH', 'POS', 'MOBILE_MONEY'].includes(paymentMethod) ? paymentMethod : 'BANK_TRANSFER',
      transactionReference: reference || `TXN-${Date.now()}`,
      receivedBy: req.user?.userId || 'system',
      receiptNotes: notes || `Installment #${targetInstallment?.installmentNumber || agreement.paidInstallments + 1} settlement`,
      createdAt: new Date().toISOString()
    };
    db.payments.push(payment);

    // Update target installment status
    if (targetInstallment) {
      targetInstallment.status = 'PAID';
      targetInstallment.paidAt = new Date().toISOString();
      targetInstallment.paymentId = payment.id;
    }

    // Ledger recalculation
    agreement.paidInstallments += 1;
    agreement.remainingInstallments = Math.max(0, agreement.numberOfInstallments - agreement.paidInstallments);
    agreement.remainingAmount = Math.max(0, +(agreement.remainingAmount - payAmount).toFixed(2));

    // Update next due date from next pending installment
    const nextPending = agreementInstallments.find(i => i.status === 'PENDING' || i.status === 'OVERDUE');
    if (nextPending) {
      agreement.nextDueDate = nextPending.dueDate;
    }

    // Recheck agreement status: if all installments paid -> COMPLETED; if no overdue remaining -> ACTIVE
    const anyOverdueLeft = agreementInstallments.some(i => i.status === 'OVERDUE');
    if (agreement.remainingAmount <= 0 || agreement.paidInstallments >= agreement.numberOfInstallments) {
      agreement.status = 'COMPLETED';
    } else if (anyOverdueLeft) {
      agreement.status = 'OVERDUE';
    } else {
      agreement.status = 'ACTIVE';
    }
    agreement.updatedAt = new Date().toISOString();

    // Financing status is the source of truth for protection eligibility.
    // Do not release device protection merely because one overdue installment was paid;
    // the agreement must be fully completed before protection can be released.
    const enrollment = db.enrollments.find(e => e.agreementId === agreement.id);
    if (enrollment) {
      if (agreement.status === 'COMPLETED') {
        enrollment.enrollmentStatus = 'ACTIVE';
        enrollment.lastSecurityEvent = 'AGREEMENT_COMPLETED_PROTECTION_RELEASE_ELIGIBLE';
      } else if (agreement.status === 'OVERDUE') {
        enrollment.lastSecurityEvent = 'AGREEMENT_OVERDUE_PROTECTION_REMAINING';
      }
    }

    db.save();

    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: 'PAYMENT_RECORDED',
      entityName: 'payments',
      entityId: payment.id,
      changes: {
        agreementId: agreement.id,
        amount: payAmount,
        remainingAmount: agreement.remainingAmount,
        status: agreement.status,
        installmentId: targetInstallment?.id
      },
      ipAddress: req.ip
    });

    res.status(201).json({
      success: true,
      data: {
        payment,
        installmentSettled: targetInstallment,
        agreementSummary: {
          remainingAmount: agreement.remainingAmount,
          paidInstallments: agreement.paidInstallments,
          remainingInstallments: agreement.remainingInstallments,
          nextDueDate: agreement.nextDueDate,
          status: agreement.status
        }
      }
    });
  }

  /**
   * GET /api/v1/payments
   * Lists payment transactions.
   */
  static async listPayments(req: Request, res: Response): Promise<void> {
    let payments = db.payments.map(p => {
      const agr = db.agreements.find(a => a.id === p.agreementId);
      const cust = agr ? db.customers.find(c => c.id === agr.customerId) : null;
      return {
        ...p,
        agreementCode: agr?.agreementCode,
        customerName: cust?.fullName
      };
    }).sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

    // IDOR filter for customer role
    if (req.user?.role === 'CUSTOMER') {
      const user = db.users.find(u => u.id === req.user?.userId);
      const userAgreements = db.agreements.filter(a => a.customerId === user?.customerId).map(a => a.id);
      payments = payments.filter(p => userAgreements.includes(p.agreementId));
    }

    res.json({ success: true, data: payments });
  }

  /**
   * GET /api/v1/alerts
   */
  static async listAlerts(req: Request, res: Response): Promise<void> {
    res.json({ success: true, data: db.alerts });
  }

  /**
   * POST /api/v1/alerts/:id/acknowledge
   */
  static async acknowledgeAlert(req: Request, res: Response): Promise<void> {
    const { id } = req.params;
    const alert = db.alerts.find(a => a.id === id);
    if (!alert) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Alert not found' });
      return;
    }

    alert.isAcknowledged = true;
    alert.acknowledgedBy = req.user?.userId;
    alert.acknowledgedAt = new Date().toISOString();

    AuditService.log({
      actorId: req.user?.userId,
      actorEmail: req.user?.email,
      action: 'ALERT_ACKNOWLEDGED',
      entityName: 'alerts',
      entityId: id,
      ipAddress: req.ip
    });

    res.json({ success: true, message: 'Alert acknowledged' });
  }
}
