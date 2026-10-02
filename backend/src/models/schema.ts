/**
 * Persistent Database Models and Schema Definitions
 * Phase 6: SK Pro Enterprise Financing Storage Layer
 */

export interface UserRecord {
  id: string;
  email: string;
  passwordHash: string;
  fullName: string;
  role: 'ADMIN' | 'SUPPORT' | 'CUSTOMER';
  customerId?: string; // Links customer role users to customer profile
  isActive: boolean;
  createdAt: string;
}

export interface RefreshTokenRecord {
  id: string;
  userId: string;
  tokenHash: string;
  tokenFamily: string;
  isRevoked: boolean;
  expiresAt: string;
}

export interface CustomerRecord {
  id: string;
  fullName: string;
  phoneNumber: string;
  email: string;
  address: string;
  nationalIdMasked: string;
  status: 'ACTIVE' | 'OVERDUE' | 'COMPLETED' | 'DEFAULTED';
  createdAt: string;
  updatedAt: string;
}

export interface DeviceRecord {
  id: string;
  manufacturer: string;
  model: string;
  brand: string;
  hardwareSerial: string;
  initialCarrier: string;
  createdAt: string;
}

export interface EnrollmentRecord {
  id: string;
  deviceId: string;
  customerId: string;
  agreementId: string;
  enrollmentCode: string;
  tokenHash: string;
  expiresAt: string;
  isTokenUsed: boolean;
  tokenUsedAt?: string;
  devicePublicKeyPem?: string;
  androidVersion?: string;
  appVersion?: string;
  managementMode: 'DEVICE_OWNER' | 'DEVICE_ADMIN' | 'UNMANAGED';
  enrollmentStatus: 'PENDING' | 'AWAITING_CUSTOMER' | 'AWAITING_DEVICE' | 'ACTIVE' | 'LOCKED' | 'SUSPENDED' | 'REVOKED' | 'EXPIRED';
  fcmToken?: string;
  lastHeartbeatAt?: string;
  lastSeenAt?: string;
  lastSuccessfulSync?: string;
  lastCommandStatus?: string;
  lastSecurityEvent?: string;
  lastKnownBattery?: number;
  networkType?: string;
  isOnline: boolean;
  simCarrier?: string;
  simFingerprint?: string;
  simSubscriptionCount?: number;
  usbDebuggingActive: boolean;
  /** Server-controlled protection policies for this enrolled device. */
  autoLockEnabled?: boolean;
  antiTheftEnabled?: boolean;
  lockOnSimChange?: boolean;
  lockOnUsbDebugging?: boolean;
  enrolledAt?: string;
  revokedAt?: string;
  revocationReason?: string;
  revokedBy?: string;
  challengeNonce?: string;
  challengeExpiresAt?: number;
}

export interface DeviceHeartbeatRecord {
  id: string;
  enrollmentId: string;
  deviceId: string;
  batteryPercent?: number;
  networkType?: string;
  appVersion?: string;
  managementStatus?: string;
  timestamp: string;
  clientNonce?: string;
  signatureVerified: boolean;
  serverReceivedAt: string;
}

export interface DeviceActivityRecord {
  id: string;
  deviceId: string;
  enrollmentId: string;
  eventType: 'HEARTBEAT_RECEIVED' | 'DEVICE_ONLINE' | 'DEVICE_OFFLINE' | 'ENROLLMENT_COMPLETED' | 'SECURITY_EVENT' | 'COMMAND_ACKNOWLEDGED' | 'COMMAND_FAILED';
  description: string;
  details?: Record<string, any>;
  timestamp: string;
}

export interface AgreementRecord {
  id: string;
  agreementCode: string;
  customerId: string;
  deviceId: string;
  totalAmount: number;
  downPayment: number;
  remainingAmount: number;
  installmentAmount: number;
  numberOfInstallments: number;
  paidInstallments: number;
  remainingInstallments: number;
  startDate: string;
  nextDueDate: string;
  status: 'ACTIVE' | 'PAID' | 'OVERDUE' | 'COMPLETED' | 'CANCELLED';
  createdAt: string;
  updatedAt: string;
}

export interface InstallmentRecord {
  id: string;
  agreementId: string;
  installmentNumber: number;
  dueDate: string;
  amount: number;
  penaltyFee: number;
  status: 'PENDING' | 'PAID' | 'OVERDUE' | 'WAIVED';
  paidAt?: string;
  paymentId?: string;
}

export interface PaymentRecord {
  id: string;
  agreementId: string;
  installmentId: string;
  amount: number;
  paymentMethod: 'CASH' | 'CARD' | 'BANK_TRANSFER' | 'MOBILE_MONEY' | 'POS';
  transactionReference: string;
  receivedBy: string;
  receiptNotes?: string;
  createdAt: string;
}

export interface CommandRecord {
  id: string;
  enrollmentId: string;
  commandType: 'LOCK_DEVICE' | 'UNLOCK_DEVICE' | 'STATUS_REQUEST' | 'LOCATION_REQUEST' | 'REFRESH_POLICIES';
  payload: Record<string, any>;
  nonce: string;
  monotonicSequence: string;
  serverSignature: string;
  status: 'PENDING' | 'SENT' | 'ACKNOWLEDGED' | 'FAILED' | 'EXPIRED';
  issuedBy: string;
  expiresAt: string;
  acknowledgedAt?: string;
  failureReason?: string;
  createdAt: string;
}

export interface AlertRecord {
  id: string;
  enrollmentId: string;
  agreementId?: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  alertType: 'PAYMENT_OVERDUE' | 'SIM_CHANGE' | 'DEBUG_ENABLED' | 'TAMPER_ATTEMPT' | 'HEARTBEAT_TIMEOUT';
  title: string;
  details: string;
  isAcknowledged: boolean;
  acknowledgedBy?: string;
  acknowledgedAt?: string;
  createdAt: string;
}

export interface AuditRecord {
  id: string;
  timestamp: string;
  actorId?: string;
  actorEmail?: string;
  action: string;
  entityName: string;
  entityId: string;
  changes?: Record<string, any>;
  ipAddress?: string;
}



export interface DeviceControlKeyRecord {
  id: string;
  keyHash: string;
  keyLast4: string;
  issuedBy?: string;
  retailerId?: string;
  deviceId?: string;
  enrollmentId?: string;
  status: 'ISSUED' | 'ACTIVATED' | 'SUSPENDED' | 'REVOKED' | 'EXPIRED';
  expiresAt?: string;
  activatedAt?: string;
  revokedAt?: string;
  createdAt: string;
}

export interface RetailerAccountRecord {
  id: string;
  adminId?: string;
  businessName: string;
  phoneNumber?: string;
  isActive: boolean;
  createdAt: string;
}

export interface DatabaseSchema {
  version: number;
  lastSavedAt: string;
  users: UserRecord[];
  refreshTokens: RefreshTokenRecord[];
  customers: CustomerRecord[];
  devices: DeviceRecord[];
  enrollments: EnrollmentRecord[];
  agreements: AgreementRecord[];
  installments: InstallmentRecord[];
  payments: PaymentRecord[];
  heartbeats: DeviceHeartbeatRecord[];
  commands: CommandRecord[];
  alerts: AlertRecord[];
  auditLogs: AuditRecord[];
  deviceActivities: DeviceActivityRecord[];
  deviceControlKeys: DeviceControlKeyRecord[];
  retailerAccounts: RetailerAccountRecord[];
  usedNonces: string[];
}
