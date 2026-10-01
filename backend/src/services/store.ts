/**
 * Persistent Storage Layer for Protect Financed Devices
 * Implements persistent database delegation with in-memory caching and backward compatibility.
 */

import { DatabaseService } from './database.service.js';
import {
  UserRecord,
  RefreshTokenRecord,
  CustomerRecord,
  DeviceRecord,
  EnrollmentRecord,
  DeviceHeartbeatRecord,
  DeviceActivityRecord,
  DeviceControlKeyRecord,
  RetailerAccountRecord,
  AgreementRecord,
  InstallmentRecord,
  PaymentRecord,
  CommandRecord,
  AlertRecord,
  AuditRecord
} from '../models/schema.js';

export * from '../models/schema.js';

export function generateInstallmentSchedule(
  agreementId: string,
  arg2: number,
  arg3: number,
  arg4?: number | string,
  arg5?: string,
  firstDueDateStr?: string
): InstallmentRecord[] {
  let financedAmount: number;
  let numberOfInstallments: number;
  let startDateStr: string;

  if (typeof arg4 === 'number') {
    // 5 args: (agreementId, totalAmount, downPayment, numberOfInstallments, startDateStr)
    const totalAmount = arg2;
    const downPayment = arg3;
    numberOfInstallments = arg4;
    startDateStr = typeof arg5 === 'string' ? arg5 : new Date().toISOString().split('T')[0];
    financedAmount = +(totalAmount - downPayment).toFixed(2);
  } else {
    // 4 args: (agreementId, financedAmount, numberOfInstallments, startDateStr)
    financedAmount = arg2;
    numberOfInstallments = arg3;
    startDateStr = typeof arg4 === 'string' ? arg4 : new Date().toISOString().split('T')[0];
  }

  const schedule: InstallmentRecord[] = [];
  const baseMonthlyAmount = Math.floor((financedAmount / numberOfInstallments) * 100) / 100;
  let accumulated = 0;

  const baseDate = firstDueDateStr || startDateStr || '2026-01-01';
  const [year, month, day] = baseDate.split('-').map(Number);
  const startObj = new Date(Date.UTC(year || 2026, (month || 1) - 1, day || 1));
  const useFirstDueDateAsAnchor = Boolean(firstDueDateStr);

  for (let i = 1; i <= numberOfInstallments; i++) {
    const dueObj = new Date(startObj);
    if (!useFirstDueDateAsAnchor) {
      dueObj.setUTCMonth(dueObj.getUTCMonth() + i);
    } else if (i > 1) {
      dueObj.setUTCMonth(dueObj.getUTCMonth() + (i - 1));
    }
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

export const calculateInstallmentSchedule = generateInstallmentSchedule;

export class PersistentDatabase {
  public usedNonces: Set<string>;

  constructor() {
    const ds = DatabaseService.getInstance();
    const schema = ds.getSchema();

    this.usedNonces = new Set(schema.usedNonces);
    const origAdd = this.usedNonces.add.bind(this.usedNonces);
    this.usedNonces.add = (nonce: string) => {
      origAdd(nonce);
      if (!schema.usedNonces.includes(nonce)) {
        schema.usedNonces.push(nonce);
        ds.schedulePersist();
      }
      return this.usedNonces;
    };
  }

  get users(): UserRecord[] {
    return DatabaseService.getInstance().getUsers();
  }
  set users(val: UserRecord[]) {
    DatabaseService.getInstance().getSchema().users = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get refreshTokens(): RefreshTokenRecord[] {
    return DatabaseService.getInstance().getSchema().refreshTokens;
  }
  set refreshTokens(val: RefreshTokenRecord[]) {
    DatabaseService.getInstance().getSchema().refreshTokens = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get customers(): CustomerRecord[] {
    return DatabaseService.getInstance().getCustomers();
  }
  set customers(val: CustomerRecord[]) {
    DatabaseService.getInstance().getSchema().customers = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get devices(): DeviceRecord[] {
    return DatabaseService.getInstance().getDevices();
  }
  set devices(val: DeviceRecord[]) {
    DatabaseService.getInstance().getSchema().devices = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get enrollments(): EnrollmentRecord[] {
    return DatabaseService.getInstance().getEnrollments();
  }
  set enrollments(val: EnrollmentRecord[]) {
    DatabaseService.getInstance().getSchema().enrollments = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get agreements(): AgreementRecord[] {
    return DatabaseService.getInstance().getAgreements();
  }
  set agreements(val: AgreementRecord[]) {
    DatabaseService.getInstance().getSchema().agreements = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get installments(): InstallmentRecord[] {
    return DatabaseService.getInstance().getInstallments();
  }
  set installments(val: InstallmentRecord[]) {
    DatabaseService.getInstance().getSchema().installments = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get payments(): PaymentRecord[] {
    return DatabaseService.getInstance().getSchema().payments;
  }
  set payments(val: PaymentRecord[]) {
    DatabaseService.getInstance().getSchema().payments = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get heartbeats(): DeviceHeartbeatRecord[] {
    return DatabaseService.getInstance().getHeartbeats();
  }
  set heartbeats(val: DeviceHeartbeatRecord[]) {
    DatabaseService.getInstance().getSchema().heartbeats = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get commands(): CommandRecord[] {
    return DatabaseService.getInstance().getCommands();
  }
  set commands(val: CommandRecord[]) {
    DatabaseService.getInstance().getSchema().commands = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get alerts(): AlertRecord[] {
    return DatabaseService.getInstance().getAlerts();
  }
  set alerts(val: AlertRecord[]) {
    DatabaseService.getInstance().getSchema().alerts = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get auditLogs(): AuditRecord[] {
    return DatabaseService.getInstance().getAuditLogs();
  }
  set auditLogs(val: AuditRecord[]) {
    DatabaseService.getInstance().getSchema().auditLogs = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get deviceControlKeys(): DeviceControlKeyRecord[] {
    return DatabaseService.getInstance().getSchema().deviceControlKeys;
  }
  set deviceControlKeys(val: DeviceControlKeyRecord[]) {
    DatabaseService.getInstance().getSchema().deviceControlKeys = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get retailerAccounts(): RetailerAccountRecord[] {
    return DatabaseService.getInstance().getSchema().retailerAccounts;
  }
  set retailerAccounts(val: RetailerAccountRecord[]) {
    DatabaseService.getInstance().getSchema().retailerAccounts = val;
    DatabaseService.getInstance().schedulePersist();
  }

  get deviceActivities(): DeviceActivityRecord[] {
    return DatabaseService.getInstance().getDeviceActivities();
  }
  set deviceActivities(val: DeviceActivityRecord[]) {
    DatabaseService.getInstance().getSchema().deviceActivities = val;
    DatabaseService.getInstance().schedulePersist();
  }

  public save(): void {
    const ds = DatabaseService.getInstance();
    ds.getSchema().usedNonces = Array.from(this.usedNonces);
    ds.persistSync();
  }

  public resetToBaseline(): void {
    const ds = DatabaseService.getInstance();
    ds.resetToBaseline();
    const schema = ds.getSchema();
    this.usedNonces = new Set(schema.usedNonces);
    const origAdd = this.usedNonces.add.bind(this.usedNonces);
    this.usedNonces.add = (nonce: string) => {
      origAdd(nonce);
      if (!schema.usedNonces.includes(nonce)) {
        schema.usedNonces.push(nonce);
        ds.schedulePersist();
      }
      return this.usedNonces;
    };
  }
}

// Export for full backwards compatibility
export const InMemoryDatabase = PersistentDatabase;
export const db = new PersistentDatabase();

/**
 * Calculates current server-side online/offline/suspended/revoked/unknown status.
 * Server status is dynamic and evaluated based on actual cryptographic heartbeat timeliness.
 */
export function calculateDeviceOnlineStatus(
  enrollment: EnrollmentRecord,
  timeoutMs: number = 15 * 60 * 1000
): 'ONLINE' | 'OFFLINE' | 'UNKNOWN' | 'SUSPENDED' | 'REVOKED' {
  if (enrollment.enrollmentStatus === 'REVOKED') {
    return 'REVOKED';
  }
  if (enrollment.enrollmentStatus === 'SUSPENDED') {
    return 'SUSPENDED';
  }
  if (!['ACTIVE', 'LOCKED'].includes(enrollment.enrollmentStatus)) {
    return 'UNKNOWN';
  }
  if (!enrollment.lastHeartbeatAt) {
    return 'UNKNOWN';
  }

  const lastHeartbeatTime = new Date(enrollment.lastHeartbeatAt).getTime();
  const now = Date.now();

  // If last heartbeat is within the timeout window, device is actively ONLINE
  if (now - lastHeartbeatTime <= timeoutMs) {
    return 'ONLINE';
  } else {
    return 'OFFLINE';
  }
}
