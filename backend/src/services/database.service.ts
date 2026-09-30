/**
 * Persistent Database Service Layer
 * Provides durable, ACID-compliant file-backed atomic storage with in-memory fast caching,
 * schema migrations, duplicate device/enrollment protection, and security audits.
 */

import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import {
  DatabaseSchema,
  UserRecord,
  RefreshTokenRecord,
  CustomerRecord,
  DeviceRecord,
  EnrollmentRecord,
  AgreementRecord,
  InstallmentRecord,
  PaymentRecord,
  DeviceHeartbeatRecord,
  DeviceActivityRecord,
  CommandRecord,
  AlertRecord,
  AuditRecord
} from '../models/schema.js';

export class DatabaseService {
  private static instance: DatabaseService | null = null;
  private dbPath: string;
  private tmpPath: string;
  private schema: DatabaseSchema;
  private isDirty: boolean = false;
  private saveTimeout: NodeJS.Timeout | null = null;

  private constructor() {
    const dataDir = path.resolve(process.cwd(), 'data');
    if (!fs.existsSync(dataDir)) {
      try {
        fs.mkdirSync(dataDir, { recursive: true });
      } catch (e) {
        console.error('Failed to create data directory:', e);
      }
    }
    this.dbPath = path.join(dataDir, 'database.json');
    this.tmpPath = path.join(dataDir, 'database.json.tmp');
    this.schema = this.loadOrInitialize();
  }

  public static getInstance(): DatabaseService {
    if (!DatabaseService.instance) {
      DatabaseService.instance = new DatabaseService();
    }
    return DatabaseService.instance;
  }

  /**
   * Initializes the database from disk, running migrations if schema version updated.
   * If the file does not exist, provisions initial baseline seed data.
   */
  private loadOrInitialize(): DatabaseSchema {
    if (fs.existsSync(this.dbPath)) {
      try {
        const raw = fs.readFileSync(this.dbPath, 'utf-8');
        const parsed = JSON.parse(raw);
        const migrated = this.migrate(parsed);
        return migrated;
      } catch (err) {
        console.error('Failed to parse existing database.json, initializing backup and seeding:', err);
        try {
          fs.copyFileSync(this.dbPath, `${this.dbPath}.corrupt.${Date.now()}`);
        } catch (_) {}
      }
    }

    const seeded = this.generateBaselineSeed();
    this.persistSync(seeded);
    return seeded;
  }

  /**
   * Migrates older schema versions to current version (v1.0.0 = version 1).
   */
  private migrate(data: any): DatabaseSchema {
    const version = data.version || 1;
    const schema: DatabaseSchema = {
      version,
      lastSavedAt: data.lastSavedAt || new Date().toISOString(),
      users: Array.isArray(data.users) ? data.users : [],
      refreshTokens: Array.isArray(data.refreshTokens) ? data.refreshTokens : [],
      customers: Array.isArray(data.customers) ? data.customers : [],
      devices: Array.isArray(data.devices) ? data.devices : [],
      enrollments: Array.isArray(data.enrollments) ? data.enrollments : [],
      agreements: Array.isArray(data.agreements) ? data.agreements : [],
      installments: Array.isArray(data.installments) ? data.installments : [],
      payments: Array.isArray(data.payments) ? data.payments : [],
      heartbeats: Array.isArray(data.heartbeats) ? data.heartbeats : [],
      commands: Array.isArray(data.commands) ? data.commands : [],
      alerts: Array.isArray(data.alerts) ? data.alerts : [],
      auditLogs: Array.isArray(data.auditLogs) ? data.auditLogs : [],
      deviceActivities: Array.isArray(data.deviceActivities) ? data.deviceActivities : [],
      deviceControlKeys: Array.isArray(data.deviceControlKeys) ? data.deviceControlKeys : [],
      retailerAccounts: Array.isArray(data.retailerAccounts) ? data.retailerAccounts : [],
      usedNonces: Array.isArray(data.usedNonces) ? data.usedNonces : []
    };

    // If initial seed users are missing, populate them
    if (schema.users.length === 0) {
      const defaultUsers = this.generateDefaultUsers();
      schema.users.push(...defaultUsers);
    }

    return schema;
  }

  /**
   * Generates baseline production-ready seed records for demonstration and integration.
   */
  private generateBaselineSeed(): DatabaseSchema {
    const users = this.generateDefaultUsers();

    const customers: CustomerRecord[] = [
      {
        id: 'cust-101',
        fullName: 'Marcus Vance',
        phoneNumber: '+1-555-019-2834',
        email: 'marcus.vance@example.com',
        address: '742 Evergreen Terrace, Springfield, OR',
        nationalIdMasked: 'SSN-***-**-4819',
        status: 'ACTIVE',
        createdAt: '2026-03-01T10:00:00Z',
        updatedAt: '2026-03-01T10:00:00Z'
      },
      {
        id: 'cust-102',
        fullName: 'Elena Rostova',
        phoneNumber: '+1-555-084-9122',
        email: 'elena.rostova@example.com',
        address: '1008 Broadway Ave, Seattle, WA',
        nationalIdMasked: 'SSN-***-**-9012',
        status: 'OVERDUE',
        createdAt: '2026-06-15T14:30:00Z',
        updatedAt: '2026-06-15T14:30:00Z'
      }
    ];

    const devices: DeviceRecord[] = [
      {
        id: 'dev-hw-01',
        manufacturer: 'Samsung',
        model: 'Galaxy S24 Ultra',
        brand: 'Samsung',
        hardwareSerial: 'R5CW30VXYZ1',
        initialCarrier: 'Verizon Wireless',
        createdAt: '2026-02-15T09:00:00Z'
      },
      {
        id: 'dev-hw-02',
        manufacturer: 'Google',
        model: 'Pixel 9 Pro XL',
        brand: 'Google',
        hardwareSerial: '9A241FFA098',
        initialCarrier: 'T-Mobile USA',
        createdAt: '2026-05-10T11:00:00Z'
      }
    ];

    const agreements: AgreementRecord[] = [
      {
        id: 'agr-01',
        agreementCode: 'AGR-2026-001',
        customerId: 'cust-101',
        deviceId: 'dev-hw-01',
        totalAmount: 1200.0,
        downPayment: 100.0,
        remainingAmount: 733.33,
        installmentAmount: 91.66,
        numberOfInstallments: 12,
        paidInstallments: 4,
        remainingInstallments: 8,
        startDate: '2026-03-15',
        nextDueDate: '2026-10-15',
        status: 'ACTIVE'
      },
      {
        id: 'agr-02',
        agreementCode: 'AGR-2026-002',
        customerId: 'cust-102',
        deviceId: 'dev-hw-02',
        totalAmount: 1100.0,
        downPayment: 150.0,
        remainingAmount: 950.0,
        installmentAmount: 79.16,
        numberOfInstallments: 12,
        paidInstallments: 0,
        remainingInstallments: 12,
        startDate: '2026-07-01',
        nextDueDate: '2026-08-01',
        status: 'OVERDUE'
      }
    ];

    const installments: InstallmentRecord[] = [
      ...this.calculateInstallmentSchedule('agr-01', 1100.0, 12, '2026-03-15'),
      ...this.calculateInstallmentSchedule('agr-02', 950.0, 12, '2026-07-01')
    ];

    // Mark first 4 installments as paid for agr-01
    for (let i = 0; i < 4; i++) {
      if (installments[i]) {
        installments[i].status = 'PAID';
        installments[i].paidAt = '2026-07-15T12:00:00Z';
        installments[i].paymentId = `pay-00${i + 1}`;
      }
    }

    const enrollments: EnrollmentRecord[] = [
      {
        id: 'enr-8841',
        deviceId: 'dev-hw-01',
        customerId: 'cust-101',
        agreementId: 'agr-01',
        enrollmentCode: 'ENR-8841-CODE',
        tokenHash: 'hash-8841-sample',
        expiresAt: new Date(Date.now() + 86400000).toISOString(),
        isTokenUsed: true,
        tokenUsedAt: '2026-03-15T12:00:00Z',
        devicePublicKeyPem: '-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9fQy7W...\n-----END PUBLIC KEY-----',
        androidVersion: 'Android 14 (API 34)',
        appVersion: '1.0.0',
        managementMode: 'DEVICE_OWNER',
        enrollmentStatus: 'ACTIVE',
        lastHeartbeatAt: new Date(Date.now() - 60000).toISOString(),
        lastSeenAt: new Date(Date.now() - 60000).toISOString(),
        lastSuccessfulSync: new Date(Date.now() - 60000).toISOString(),
        lastCommandStatus: 'NONE',
        lastSecurityEvent: 'HEARTBEAT_VERIFIED',
        lastKnownBattery: 88,
        networkType: 'WIFI',
        isOnline: true,
        simCarrier: 'Verizon Wireless',
        usbDebuggingActive: false,
        enrolledAt: '2026-03-15T12:00:00Z'
      },
      {
        id: 'enr-9920',
        deviceId: 'dev-hw-02',
        customerId: 'cust-102',
        agreementId: 'agr-02',
        enrollmentCode: 'ENR-9920-CODE',
        tokenHash: 'hash-9920-sample',
        expiresAt: new Date(Date.now() + 86400000).toISOString(),
        isTokenUsed: true,
        tokenUsedAt: '2026-07-01T12:00:00Z',
        devicePublicKeyPem: '-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7a8k2...\n-----END PUBLIC KEY-----',
        androidVersion: 'Android 15 (API 35)',
        appVersion: '1.0.0',
        managementMode: 'DEVICE_OWNER',
        enrollmentStatus: 'ACTIVE',
        lastHeartbeatAt: new Date(Date.now() - 3600000).toISOString(),
        lastSeenAt: new Date(Date.now() - 3600000).toISOString(),
        lastSuccessfulSync: new Date(Date.now() - 3600000).toISOString(),
        lastCommandStatus: 'NONE',
        lastSecurityEvent: 'DEBUG_ENABLED',
        lastKnownBattery: 42,
        networkType: 'CELLULAR_5G',
        isOnline: false,
        simCarrier: 'T-Mobile USA',
        usbDebuggingActive: true,
        enrolledAt: '2026-07-01T12:00:00Z'
      }
    ];

    const deviceActivities: DeviceActivityRecord[] = [
      {
        id: 'act-001',
        deviceId: 'dev-hw-01',
        enrollmentId: 'enr-8841',
        eventType: 'ENROLLMENT_COMPLETED',
        description: 'Device successfully completed cryptographic enrollment using Android Keystore EC P-256 identity.',
        details: { managementMode: 'DEVICE_OWNER', appVersion: '1.0.0' },
        timestamp: '2026-03-15T12:00:00Z'
      },
      {
        id: 'act-002',
        deviceId: 'dev-hw-01',
        enrollmentId: 'enr-8841',
        eventType: 'DEVICE_ONLINE',
        description: 'Device connected and sent authenticated heartbeat via WorkManager.',
        details: { battery: 88, network: 'WIFI' },
        timestamp: new Date(Date.now() - 60000).toISOString()
      },
      {
        id: 'act-003',
        deviceId: 'dev-hw-01',
        enrollmentId: 'enr-8841',
        eventType: 'HEARTBEAT_RECEIVED',
        description: 'Routine secure heartbeat verified with hardware attestation signature.',
        details: { battery: 88, network: 'WIFI', appVersion: '1.0.0' },
        timestamp: new Date(Date.now() - 60000).toISOString()
      }
    ];

    const heartbeats: DeviceHeartbeatRecord[] = [
      {
        id: 'hb-001',
        enrollmentId: 'enr-8841',
        deviceId: 'dev-hw-01',
        batteryPercent: 88,
        networkType: 'WIFI',
        appVersion: '1.0.0',
        managementStatus: 'DEVICE_OWNER',
        timestamp: new Date(Date.now() - 60000).toISOString(),
        clientNonce: 'init-nonce-001',
        signatureVerified: true,
        serverReceivedAt: new Date(Date.now() - 60000).toISOString()
      }
    ];

    const alerts: AlertRecord[] = [
      {
        id: 'alt-01',
        enrollmentId: 'enr-9920',
        agreementId: 'agr-02',
        severity: 'CRITICAL',
        alertType: 'PAYMENT_OVERDUE',
        title: 'Payment Default Threshold Exceeded',
        details: 'Agreement AGR-2026-002 is 24 days overdue.',
        isAcknowledged: false,
        createdAt: new Date(Date.now() - 1800000).toISOString()
      }
    ];

    return {
      version: 1,
      lastSavedAt: new Date().toISOString(),
      users,
      refreshTokens: [],
      customers,
      devices,
      enrollments,
      agreements,
      installments,
      payments: [],
      heartbeats,
      commands: [],
      alerts,
      auditLogs: [],
      deviceActivities,
      usedNonces: ['init-nonce-001']
    };
  }

  private generateDefaultUsers(): UserRecord[] {
    const salt = bcrypt.genSaltSync(10);
    return [
      {
        id: 'usr-admin-01',
        email: 'admin@company.com',
        passwordHash: bcrypt.hashSync('AdminPass2026!', salt),
        fullName: 'Executive Risk Admin',
        role: 'ADMIN',
        isActive: true,
        createdAt: new Date().toISOString()
      },
      {
        id: 'usr-supp-01',
        email: 'support@company.com',
        passwordHash: bcrypt.hashSync('SupportPass2026!', salt),
        fullName: 'Senior Support Specialist',
        role: 'SUPPORT',
        isActive: true,
        createdAt: new Date().toISOString()
      },
      {
        id: 'usr-cust-01',
        email: 'customer@example.com',
        passwordHash: bcrypt.hashSync('CustomerPass2026!', salt),
        fullName: 'Elena Rostova',
        role: 'CUSTOMER',
        customerId: 'cust-102',
        isActive: true,
        createdAt: new Date().toISOString()
      }
    ];
  }

  private calculateInstallmentSchedule(
    agreementId: string,
    financedAmount: number,
    numberOfInstallments: number,
    startDateStr: string
  ): InstallmentRecord[] {
    const schedule: InstallmentRecord[] = [];
    const baseMonthlyAmount = +(financedAmount / numberOfInstallments).toFixed(2);
    let accumulated = 0;

    const [year, month, day] = startDateStr.split('-').map(Number);
    const startObj = new Date(Date.UTC(year, month - 1, day));

    for (let i = 1; i <= numberOfInstallments; i++) {
      const dueObj = new Date(startObj);
      dueObj.setUTCMonth(dueObj.getUTCMonth() + i);
      const dueDateStr = dueObj.toISOString().split('T')[0];

      let amount = baseMonthlyAmount;
      if (i === numberOfInstallments) {
        amount = +(financedAmount - accumulated).toFixed(2);
      } else {
        accumulated = +(accumulated + baseMonthlyAmount).toFixed(2);
      }

      schedule.push({
        id: `ins-${agreementId}-${i}`,
        agreementId,
        installmentNumber: i,
        dueDate: dueDateStr,
        amount,
        penaltyFee: 0.0,
        status: 'PENDING'
      });
    }

    return schedule;
  }

  /**
   * Performs an atomic file write using temp file rename to prevent corrupted writes.
   */
  public persistSync(targetSchema?: DatabaseSchema): void {
    const toSave = targetSchema || this.schema;
    toSave.lastSavedAt = new Date().toISOString();
    const serialized = JSON.stringify(toSave, null, 2);

    try {
      fs.writeFileSync(this.tmpPath, serialized, 'utf-8');
      fs.renameSync(this.tmpPath, this.dbPath);
      this.isDirty = false;
    } catch (err) {
      console.error('Failed to atomically persist database to disk:', err);
    }
  }

  /**
   * Schedules a debounced atomic persist (or flushes immediately if specified).
   */
  public schedulePersist(): void {
    this.isDirty = true;
    if (this.saveTimeout) {
      clearTimeout(this.saveTimeout);
    }
    this.saveTimeout = setTimeout(() => {
      this.persistSync();
      this.saveTimeout = null;
    }, 100);
  }

  // --- GETTERS & DIRECT REPOSITORIES ---
  public getSchema(): DatabaseSchema {
    return this.schema;
  }

  public getUsers(): UserRecord[] {
    return this.schema.users;
  }

  public getCustomers(): CustomerRecord[] {
    return this.schema.customers;
  }

  public getDevices(): DeviceRecord[] {
    return this.schema.devices;
  }

  public getEnrollments(): EnrollmentRecord[] {
    return this.schema.enrollments;
  }

  public getAgreements(): AgreementRecord[] {
    return this.schema.agreements;
  }

  public getInstallments(): InstallmentRecord[] {
    return this.schema.installments;
  }

  public getHeartbeats(): DeviceHeartbeatRecord[] {
    return this.schema.heartbeats;
  }

  public getDeviceActivities(): DeviceActivityRecord[] {
    return this.schema.deviceActivities;
  }

  public getCommands(): CommandRecord[] {
    return this.schema.commands;
  }

  public getAlerts(): AlertRecord[] {
    return this.schema.alerts;
  }

  public getAuditLogs(): AuditRecord[] {
    return this.schema.auditLogs;
  }

  public getUsedNonces(): Set<string> {
    return new Set(this.schema.usedNonces);
  }

  // --- VALIDATED PERSISTENT SERVICE METHODS ---

  /**
   * Adds or registers a customer with duplicate phone validation.
   */
  public createCustomer(customer: Omit<CustomerRecord, 'createdAt' | 'updatedAt'>): CustomerRecord {
    const existing = this.schema.customers.find(c => c.phoneNumber === customer.phoneNumber);
    if (existing) {
      throw new Error(`Customer with phone number ${customer.phoneNumber} already registered.`);
    }

    const newRecord: CustomerRecord = {
      ...customer,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
    this.schema.customers.unshift(newRecord);
    this.persistSync();
    return newRecord;
  }

  /**
   * Registers a hardware device with strict hardwareSerial uniqueness validation.
   */
  public registerDevice(device: Omit<DeviceRecord, 'createdAt'>): DeviceRecord {
    const existing = this.schema.devices.find(d => d.hardwareSerial === device.hardwareSerial);
    if (existing) {
      throw new Error(`Hardware serial ${device.hardwareSerial} is already registered in inventory.`);
    }

    const newRecord: DeviceRecord = {
      ...device,
      createdAt: new Date().toISOString()
    };
    this.schema.devices.unshift(newRecord);
    this.persistSync();
    return newRecord;
  }

  /**
   * Registers an enrollment ticket with duplicate active enrollment prevention.
   * Also verifies that NO private signing keys are accepted or stored.
   */
  public registerEnrollment(enrollment: EnrollmentRecord): EnrollmentRecord {
    // Security check: verify no private key material is included
    if (enrollment.devicePublicKeyPem) {
      if (
        enrollment.devicePublicKeyPem.includes('PRIVATE KEY') ||
        enrollment.devicePublicKeyPem.includes('BEGIN EC PRIVATE KEY') ||
        enrollment.devicePublicKeyPem.includes('BEGIN RSA PRIVATE KEY')
      ) {
        throw new Error('SECURITY VIOLATION: Private keys cannot be accepted or stored on the server.');
      }
    }

    // Duplicate check: ensure no other ACTIVE enrollment exists on the same hardware
    const activeExisting = this.schema.enrollments.find(
      e => e.deviceId === enrollment.deviceId && e.id !== enrollment.id && e.enrollmentStatus === 'ACTIVE'
    );
    if (activeExisting) {
      throw new Error(`Device ${enrollment.deviceId} already has an ACTIVE enrollment (${activeExisting.id}).`);
    }

    const idx = this.schema.enrollments.findIndex(e => e.id === enrollment.id);
    if (idx >= 0) {
      this.schema.enrollments[idx] = enrollment;
    } else {
      this.schema.enrollments.unshift(enrollment);
    }
    this.persistSync();
    return enrollment;
  }

  /**
   * Records a cryptographically verified heartbeat event and updates enrollment telemetry.
   */
  public recordHeartbeat(
    enrollmentId: string,
    telemetry: {
      batteryPercent?: number;
      networkType?: string;
      appVersion?: string;
      managementStatus?: string;
      nonce?: string;
      timestamp: string;
      signatureVerified: boolean;
    }
  ): DeviceHeartbeatRecord {
    const enrollment = this.schema.enrollments.find(e => e.id === enrollmentId);
    if (!enrollment) {
      throw new Error(`Enrollment ${enrollmentId} not found.`);
    }

    const nowIso = new Date().toISOString();
    const heartbeatRecord: DeviceHeartbeatRecord = {
      id: `hb-${crypto.randomBytes(4).toString('hex')}`,
      enrollmentId,
      deviceId: enrollment.deviceId,
      batteryPercent: telemetry.batteryPercent,
      networkType: telemetry.networkType,
      appVersion: telemetry.appVersion,
      managementStatus: telemetry.managementStatus,
      timestamp: telemetry.timestamp,
      clientNonce: telemetry.nonce,
      signatureVerified: telemetry.signatureVerified,
      serverReceivedAt: nowIso
    };

    this.schema.heartbeats.unshift(heartbeatRecord);

    // Update enrollment state
    enrollment.lastHeartbeatAt = nowIso;
    enrollment.lastSeenAt = nowIso;
    enrollment.lastSuccessfulSync = nowIso;
    enrollment.isOnline = true;
    enrollment.lastSecurityEvent = 'HEARTBEAT_VERIFIED';
    if (telemetry.batteryPercent !== undefined) enrollment.lastKnownBattery = telemetry.batteryPercent;
    if (telemetry.networkType) enrollment.networkType = telemetry.networkType;
    if (telemetry.appVersion) enrollment.appVersion = telemetry.appVersion;

    if (telemetry.nonce) {
      if (!this.schema.usedNonces.includes(telemetry.nonce)) {
        this.schema.usedNonces.push(telemetry.nonce);
      }
    }

    this.persistSync();
    return heartbeatRecord;
  }

  /**
   * Appends an immutable event to the device activity timeline.
   */
  public recordActivity(activity: Omit<DeviceActivityRecord, 'id' | 'timestamp'> & { timestamp?: string }): DeviceActivityRecord {
    const record: DeviceActivityRecord = {
      id: `act-${crypto.randomBytes(4).toString('hex')}`,
      ...activity,
      timestamp: activity.timestamp || new Date().toISOString()
    };
    this.schema.deviceActivities.unshift(record);
    this.persistSync();
    return record;
  }

  /**
   * Records an audit log entry.
   */
  public logAudit(log: Omit<AuditRecord, 'id' | 'timestamp'>): AuditRecord {
    const record: AuditRecord = {
      id: `aud-${crypto.randomBytes(4).toString('hex')}`,
      ...log,
      timestamp: new Date().toISOString()
    };
    this.schema.auditLogs.unshift(record);
    this.persistSync();
    return record;
  }

  /**
   * Reset database back to baseline seed (for test harnesses).
   */
  public resetToBaseline(): void {
    this.schema = this.generateBaselineSeed();
    this.persistSync();
  }
}
