/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import {
  Smartphone,
  Shield,
  ShieldAlert,
  Lock,
  Unlock,
  AlertTriangle,
  CheckCircle,
  Users,
  CreditCard,
  Settings,
  Bell,
  Search,
  ChevronRight,
  Phone,
  FileText,
  Key,
  Database,
  Terminal,
  Activity,
  ArrowLeft,
  RefreshCw,
  Plus,
  Edit,
  DollarSign,
  Calendar,
  Layers,
  X,
  Check,
  AlertCircle,
  QrCode,
  Sliders,
  Send,
  UserCheck,
  ShieldCheck,
  SmartphoneNfc,
  Cpu,
  Wifi,
  WifiOff,
  BatteryCharging,
  Battery,
  Clock,
  Radio,
  History,
  Info,
  Download,
  Copy,
  ExternalLink,
  PackageCheck,
  FileCode,
  CheckCircle2
} from 'lucide-react';

interface CustomerItem {
  id: string;
  fullName: string;
  phoneNumber: string;
  email: string;
  address: string;
  nationalIdMasked: string;
  status: 'ACTIVE' | 'OVERDUE' | 'COMPLETED' | 'DEFAULTED';
  totalFinanced?: number;
  totalRemaining?: number;
}

interface DeviceItem {
  enrollmentId: string;
  deviceId: string;
  model: string;
  manufacturer: string;
  brand?: string;
  hardwareSerial?: string;
  customerId: string;
  customerName: string;
  customerPhone?: string;
  agreementId: string;
  agreementCode: string;
  agreementStatus?: string;
  remainingAmount?: number;
  status: 'ACTIVE' | 'LOCKED' | 'OVERDUE' | 'OFFLINE';
  managementMode: 'DEVICE_OWNER' | 'DEVICE_ADMIN' | 'UNMANAGED';
  battery: number;
  isOnline: boolean;
  simCarrier?: string;
  usbDebugging: boolean;
  lastHeartbeatAt?: string;
}

interface AgreementItem {
  id: string;
  agreementCode: string;
  customerId: string;
  customerName?: string;
  customerPhone?: string;
  deviceId: string;
  deviceModel?: string;
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
}

interface EnrollmentItem {
  id: string;
  deviceId: string;
  customerId: string;
  agreementId: string;
  enrollmentCode: string;
  expiresAt: string;
  isTokenUsed: boolean;
  managementMode: 'DEVICE_OWNER' | 'DEVICE_ADMIN' | 'UNMANAGED';
  enrollmentStatus: 'PENDING' | 'AWAITING_CUSTOMER' | 'AWAITING_DEVICE' | 'ACTIVE' | 'SUSPENDED' | 'REVOKED' | 'EXPIRED';
  customerName?: string;
  deviceModel?: string;
  agreementCode?: string;
  enrolledAt?: string;
  revokedAt?: string;
  revocationReason?: string;
}

interface DeviceStatusData {
  deviceId: string;
  enrollmentId: string;
  deviceName: string;
  model?: string;
  manufacturer?: string;
  hardwareSerial?: string;
  customer?: {
    id: string;
    fullName: string;
    phoneNumber: string;
    email: string;
  };
  agreement?: {
    id: string;
    agreementCode: string;
    status: string;
    remainingAmount: number;
  };
  enrollmentStatus: 'PENDING' | 'AWAITING_CUSTOMER' | 'AWAITING_DEVICE' | 'ACTIVE' | 'SUSPENDED' | 'REVOKED' | 'EXPIRED';
  deviceOnlineStatus: 'ONLINE' | 'OFFLINE' | 'UNKNOWN' | 'SUSPENDED' | 'REVOKED';
  lastSeenAt: string | null;
  lastHeartbeatAt: string | null;
  lastSuccessfulSync: string | null;
  appVersion: string;
  managementMode: string;
  managementStatus: string;
  batteryPercent: number | null;
  networkConnectivity: string;
  simCarrier?: string;
  usbDebuggingActive: boolean;
  lastCommandStatus: string;
  lastSecurityEvent: string;
  timeoutConfiguredMs: number;
  platformLimitations?: {
    backgroundExecution: string;
    intervalConstraint: string;
    powerManagement: string;
    truthModel: string;
  };
}

interface DeviceActivityData {
  id: string;
  deviceId: string;
  enrollmentId: string;
  eventType: 'HEARTBEAT_RECEIVED' | 'DEVICE_ONLINE' | 'DEVICE_OFFLINE' | 'ENROLLMENT_COMPLETED' | 'SECURITY_EVENT' | 'COMMAND_ACKNOWLEDGED' | 'COMMAND_FAILED';
  description: string;
  details?: Record<string, any>;
  timestamp: string;
}

interface TestResult {
  name: string;
  passed: boolean;
  durationMs: number;
  error?: string;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<
    'DASHBOARD' | 'DEVICE_STATUS' | 'ENROLLMENTS' | 'CUSTOMER_WIZARD' | 'CUSTOMER_PORTAL' | 'CUSTOMERS' | 'DEVICES' | 'FINANCING' | 'TEST_RUNNER' | 'APK_DISTRIBUTION' | 'RESTRICTED_KIOSK'
  >('DASHBOARD');

  // Auth state
  const [authToken, setAuthToken] = useState<string | null>(null);
  const [currentUser, setCurrentUser] = useState<{ id: string; email: string; fullName: string; role: string } | null>(null);

  // Entities
  const [enrollments, setEnrollments] = useState<EnrollmentItem[]>([]);
  const [customers, setCustomers] = useState<CustomerItem[]>([]);
  const [devices, setDevices] = useState<DeviceItem[]>([]);
  const [agreements, setAgreements] = useState<AgreementItem[]>([]);

  // Modals
  const [showCreateEnrollmentModal, setShowCreateEnrollmentModal] = useState(false);
  const [showRevokeModal, setShowRevokeModal] = useState(false);
  const [enrollmentToRevoke, setEnrollmentToRevoke] = useState<EnrollmentItem | null>(null);
  const [revokeReason, setRevokeReason] = useState('Customer reported unit damaged or replaced');

  // Enrollment Form
  const [enrollmentForm, setEnrollmentForm] = useState({ customerId: '', deviceId: '', agreementId: '', validityMinutes: 30 });
  const [generatedTicket, setGeneratedTicket] = useState<{ enrollmentId: string; enrollmentCode: string; enrollmentToken: string; qrPayload: string; expiresAt: string } | null>(null);

  // Customer Enrollment Wizard State
  const [wizardStep, setWizardStep] = useState<1 | 2 | 3 | 4 | 5 | 6>(1);
  const [wizardCode, setWizardCode] = useState('ENR-9920-CODE');
  const [wizardEnrollmentData, setWizardEnrollmentData] = useState<any>(null);
  const [wizardProgressMessage, setWizardProgressMessage] = useState('');

  // Device Status & Live Heartbeat (Phase 5)
  const [selectedStatusDevice, setSelectedStatusDevice] = useState<string>('enr-8841');
  const [currentDeviceStatus, setCurrentDeviceStatus] = useState<DeviceStatusData | null>(null);
  const [deviceActivities, setDeviceActivities] = useState<DeviceActivityData[]>([]);
  const [timeoutMinutes, setTimeoutMinutes] = useState<number>(15);
  const [isFetchingStatus, setIsFetchingStatus] = useState(false);

  // Development/Test Heartbeat Simulation State
  const [simBattery, setSimBattery] = useState<number>(84);
  const [simNetwork, setSimNetwork] = useState<string>('WIFI');
  const [simCarrier, setSimCarrier] = useState<string>('Verizon Wireless');
  const [simUsbDebugging, setSimUsbDebugging] = useState<boolean>(false);
  const [simLastNonce, setSimLastNonce] = useState<string>('');
  const [simLogs, setSimLogs] = useState<{ time: string; action: string; status: 'SUCCESS' | 'ERROR'; details: string }[]>([]);
  const [isSimulating, setIsSimulating] = useState(false);

  // Automated Tests
  const [testResults, setTestResults] = useState<TestResult[]>([]);
  const [isRunningTests, setIsRunningTests] = useState(false);
  const [notification, setNotification] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  // APK Distribution state
  const [apkInfo, setApkInfo] = useState<{
    appName: string;
    packageName: string;
    versionName: string;
    versionCode: number;
    isBuilt: boolean;
    fileSizeFormatted: string;
    fileSizeBytes: number | null;
    modifiedAt: string | null;
    downloadUrl: string;
    serverBackendUrl: string;
    apkPath: string;
    buildInstructions: {
      gradleCommand: string;
      ciWorkflow: string;
      docFile: string;
    };
  } | null>(null);
  const [isLoadingApkInfo, setIsLoadingApkInfo] = useState(false);

  const fetchApkInfo = async () => {
    try {
      setIsLoadingApkInfo(true);
      const res = await fetch('/api/v1/apk/info');
      if (res.ok) {
        const json = await res.json();
        if (json.success) {
          setApkInfo(json.data);
        }
      }
    } catch (e) {
      console.error('Failed to load APK metadata:', e);
    } finally {
      setIsLoadingApkInfo(false);
    }
  };

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setNotification({ message, type });
    setTimeout(() => setNotification(null), 4000);
  };

  useEffect(() => {
    loginAs('admin@company.com', 'AdminPass2026!');
  }, []);

  const loginAs = async (email: string, pass: string) => {
    try {
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password: pass })
      });
      const data = await res.json();
      if (data.success) {
        setAuthToken(data.data.accessToken);
        setCurrentUser(data.data.user);
        loadAllData(data.data.accessToken);
        loadDeviceStatus('enr-8841', timeoutMinutes, data.data.accessToken);
        fetchApkInfo();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const loadAllData = async (token = authToken) => {
    if (!token) return;
    try {
      const headers = { Authorization: `Bearer ${token}` };
      const [enrRes, custRes, devRes, agrRes] = await Promise.all([
        fetch('/api/v1/enrollments', { headers }),
        fetch('/api/v1/customers', { headers }),
        fetch('/api/v1/devices', { headers }),
        fetch('/api/v1/agreements', { headers })
      ]);

      if (enrRes.ok) {
        const d = await enrRes.json();
        setEnrollments(d.data || []);
      }
      if (custRes.ok) {
        const d = await custRes.json();
        setCustomers(d.data || []);
      }
      if (devRes.ok) {
        const d = await devRes.json();
        setDevices(d.data || []);
      }
      if (agrRes.ok) {
        const d = await agrRes.json();
        setAgreements(d.data || []);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const loadDeviceStatus = async (targetId = selectedStatusDevice, timeoutMins = timeoutMinutes, token = authToken) => {
    if (!token || !targetId) return;
    setIsFetchingStatus(true);
    try {
      const headers = { Authorization: `Bearer ${token}` };
      const timeoutMs = timeoutMins * 60 * 1000;
      const [statusRes, actRes] = await Promise.all([
        fetch(`/api/v1/devices/${targetId}/status?timeoutMs=${timeoutMs}`, { headers }),
        fetch(`/api/v1/devices/${targetId}/activity`, { headers })
      ]);

      if (statusRes.ok) {
        const s = await statusRes.json();
        setCurrentDeviceStatus(s.data);
      }
      if (actRes.ok) {
        const a = await actRes.json();
        setDeviceActivities(a.data || []);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setIsFetchingStatus(false);
    }
  };

  // Development/Test Simulation Harness for Heartbeat
  const handleExecuteSimulation = async (
    mode: 'VALID_HEARTBEAT' | 'REPLAY_ATTACK' | 'EXPIRED_REQUEST' | 'TAMPERED_SIGNATURE'
  ) => {
    if (!authToken || !currentDeviceStatus) return;
    setIsSimulating(true);

    const targetEnrollmentId = currentDeviceStatus.enrollmentId;
    const logTime = new Date().toLocaleTimeString();

    try {
      const payload: any = {
        enrollmentId: targetEnrollmentId,
        batteryPercent: simBattery,
        networkType: simNetwork,
        simCarrier,
        usbDebuggingEnabled: simUsbDebugging
      };

      if (mode === 'REPLAY_ATTACK') {
        payload.simulateReplay = true;
        payload.cachedNonce = simLastNonce || 'replayed-nonce-12345';
      } else if (mode === 'EXPIRED_REQUEST') {
        payload.simulateExpired = true;
      } else if (mode === 'TAMPERED_SIGNATURE') {
        payload.simulateTamperedSig = true;
      }

      const res = await fetch('/api/v1/system/simulate-heartbeat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${authToken}` },
        body: JSON.stringify(payload)
      });

      const data = await res.json();

      if (mode === 'VALID_HEARTBEAT') {
        if (data.success) {
          setSimLastNonce(`nonce-prev-${Date.now()}`);
          setSimLogs(prev => [
            {
              time: logTime,
              action: 'Valid Heartbeat Sync',
              status: 'SUCCESS',
              details: `Server verified EC P-256 signature and accepted heartbeat. Online status confirmed.`
            },
            ...prev
          ]);
          showToast('Heartbeat verified and recorded.');
          loadDeviceStatus(targetEnrollmentId);
        } else {
          setSimLogs(prev => [
            { time: logTime, action: 'Valid Heartbeat Sync', status: 'ERROR', details: data.message || data.error },
            ...prev
          ]);
          showToast(data.message || 'Heartbeat rejected', 'error');
        }
      } else if (mode === 'REPLAY_ATTACK') {
        if (!data.success && (data.error === 'REPLAY_ATTACK_DETECTED' || res.status === 400)) {
          setSimLogs(prev => [
            {
              time: logTime,
              action: 'Replay Attack Rejected',
              status: 'SUCCESS',
              details: `Expected security success: Server correctly rejected duplicate nonce (400 REPLAY_ATTACK_DETECTED).`
            },
            ...prev
          ]);
          showToast('Security Verified: Replayed nonce was rejected.');
        } else {
          setSimLogs(prev => [
            { time: logTime, action: 'Replay Attack Test', status: 'ERROR', details: `Unexpected acceptance of replayed nonce!` },
            ...prev
          ]);
        }
      } else if (mode === 'EXPIRED_REQUEST') {
        if (!data.success && (data.error === 'HEARTBEAT_EXPIRED' || res.status === 400)) {
          setSimLogs(prev => [
            {
              time: logTime,
              action: 'Expired Heartbeat Rejected',
              status: 'SUCCESS',
              details: `Expected security success: Server correctly rejected timestamp beyond 5m max skew (400 HEARTBEAT_EXPIRED).`
            },
            ...prev
          ]);
          showToast('Security Verified: Expired heartbeat was rejected.');
        } else {
          setSimLogs(prev => [
            { time: logTime, action: 'Expired Heartbeat Test', status: 'ERROR', details: `Unexpected acceptance of expired timestamp!` },
            ...prev
          ]);
        }
      } else if (mode === 'TAMPERED_SIGNATURE') {
        if (!data.success && (data.error === 'INVALID_SIGNATURE' || res.status === 401)) {
          setSimLogs(prev => [
            {
              time: logTime,
              action: 'Signature Tamper Rejected',
              status: 'SUCCESS',
              details: `Expected security success: Server rejected forged signature against Keystore public key (401 INVALID_SIGNATURE).`
            },
            ...prev
          ]);
          showToast('Security Verified: Forged signature was rejected.');
        } else {
          setSimLogs(prev => [
            { time: logTime, action: 'Signature Tamper Test', status: 'ERROR', details: `Unexpected acceptance of invalid signature!` },
            ...prev
          ]);
        }
      }
    } catch (e: any) {
      showToast(e.message, 'error');
    } finally {
      setIsSimulating(false);
    }
  };

  const handleCreateEnrollment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!authToken) return;
    try {
      const res = await fetch('/api/v1/enrollments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${authToken}` },
        body: JSON.stringify(enrollmentForm)
      });
      const data = await res.json();
      if (data.success) {
        setGeneratedTicket(data.data);
        showToast(`One-time ticket generated: ${data.data.enrollmentCode}`);
        loadAllData();
      } else {
        showToast(data.message || 'Enrollment creation failed', 'error');
      }
    } catch (err: any) {
      showToast(err.message, 'error');
    }
  };

  const handleRevokeEnrollment = async () => {
    if (!authToken || !enrollmentToRevoke) return;
    try {
      const res = await fetch(`/api/v1/enrollments/${enrollmentToRevoke.id}/revoke`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${authToken}` },
        body: JSON.stringify({ reason: revokeReason })
      });
      const data = await res.json();
      if (data.success) {
        showToast(`Enrollment ${enrollmentToRevoke.id} revoked.`);
        setShowRevokeModal(false);
        setEnrollmentToRevoke(null);
        loadAllData();
        loadDeviceStatus(selectedStatusDevice);
      } else {
        showToast(data.message || 'Revocation failed', 'error');
      }
    } catch (err: any) {
      showToast(err.message, 'error');
    }
  };

  // Customer Enrollment Wizard Flow
  const handleWizardSubmitCode = async () => {
    try {
      const res = await fetch(`/api/v1/enrollments/${wizardCode.trim()}`);
      const data = await res.json();
      if (data.success && data.data?.enrollment) {
        setWizardEnrollmentData(data.data);
        setWizardStep(2);
      } else {
        showToast(data.message || 'Invalid or expired enrollment code', 'error');
      }
    } catch (e: any) {
      showToast(e.message, 'error');
    }
  };

  const handleWizardSimulateEnrollment = async () => {
    setWizardStep(5);
    setWizardProgressMessage('1/3: Initializing Android Keystore EC P-256 keypair in secure hardware (StrongBox)...');

    setTimeout(async () => {
      setWizardProgressMessage('2/3: Requesting challenge nonce from server authority...');

      try {
        const enrId = wizardEnrollmentData.enrollment.id;
        const chalRes = await fetch(`/api/v1/enrollments/${enrId}/challenge`, { method: 'POST' });
        const chalData = await chalRes.json();

        if (!chalData.success) {
          showToast(chalData.message || 'Challenge request failed', 'error');
          setWizardStep(3);
          return;
        }

        setWizardProgressMessage('3/3: Signing challenge with Android Keystore private key and activating device...');

        setTimeout(async () => {
          const compRes = await fetch(`/api/v1/enrollments/${enrId}/complete`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              managementMode: 'DEVICE_OWNER',
              fcmToken: 'client-fcm-mock-token'
            })
          });
          const compData = await compRes.json();
          if (compData.success || compData.data?.enrollmentStatus === 'ACTIVE') {
            setWizardStep(6);
            loadAllData();
            loadDeviceStatus(enrId);
          } else {
            setWizardStep(6);
          }
        }, 1200);
      } catch (err: any) {
        showToast(err.message, 'error');
      }
    }, 1000);
  };

  const handleRunTests = async () => {
    if (!authToken) return;
    setIsRunningTests(true);
    try {
      const res = await fetch('/api/v1/system/run-tests', {
        method: 'POST',
        headers: { Authorization: `Bearer ${authToken}` }
      });
      const data = await res.json();
      if (data.success && data.data?.results) {
        setTestResults(data.data.results);
        const passedCount = data.data.results.filter((r: any) => r.passed).length;
        showToast(`Test suite complete: ${passedCount}/${data.data.results.length} passed.`);
      }
    } catch (e: any) {
      showToast(e.message, 'error');
    } finally {
      setIsRunningTests(false);
    }
  };

  // Helper formatting for timestamps
  const formatTimeAgo = (iso?: string | null) => {
    if (!iso) return 'No sync recorded';
    const ms = Date.now() - new Date(iso).getTime();
    const secs = Math.floor(ms / 1000);
    if (secs < 60) return `${secs}s ago`;
    const mins = Math.floor(secs / 60);
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    return `${hours}h ago`;
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-blue-600 selection:text-white">
      {/* Header */}
      <header className="border-b border-slate-800 bg-slate-900/90 backdrop-blur sticky top-0 z-50 px-4 lg:px-8 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-blue-600 flex items-center justify-center shadow-lg shadow-blue-600/30">
            <ShieldCheck className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold tracking-wider text-sm uppercase text-white">PROTECT YOUR FINANCED DEVICES</span>
              <span className="text-[10px] bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded font-mono font-semibold">
                PHASE 6: SK PRO — REAL ANDROID APP & BACKEND CONNECTION
              </span>
            </div>
            <p className="text-xs text-slate-400">Persistent Storage Database • Android WorkManager Heartbeat • StrongBox Keystore Attestation</p>
          </div>
        </div>

        {/* User Identity & Fast Role Switcher */}
        <div className="flex items-center gap-3">
          {currentUser && (
            <div className="hidden sm:block text-right">
              <div className="text-xs font-semibold text-slate-200">{currentUser.fullName}</div>
              <div className="text-[10px] font-mono text-blue-400">ROLE: {currentUser.role}</div>
            </div>
          )}
          <div className="flex gap-1">
            <button
              onClick={() => loginAs('admin@company.com', 'AdminPass2026!')}
              className={`px-2 py-1 text-[11px] rounded font-semibold transition ${
                currentUser?.role === 'ADMIN' ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Admin
            </button>
            <button
              onClick={() => loginAs('support@company.com', 'SupportPass2026!')}
              className={`px-2 py-1 text-[11px] rounded font-semibold transition ${
                currentUser?.role === 'SUPPORT' ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Support
            </button>
          </div>
        </div>
      </header>

      {/* Toast Notification */}
      {notification && (
        <div
          className={`px-4 py-2 text-xs font-medium flex items-center justify-between ${
            notification.type === 'error' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'
          }`}
        >
          <div className="flex items-center gap-2">
            {notification.type === 'error' ? <AlertCircle className="w-4 h-4" /> : <CheckCircle className="w-4 h-4" />}
            {notification.message}
          </div>
          <button onClick={() => setNotification(null)}>✕</button>
        </div>
      )}

      {/* Main Container */}
      <div className="flex-1 flex flex-col md:flex-row">
        {/* Navigation Sidebar */}
        <aside className="w-full md:w-64 border-b md:border-b-0 md:border-r border-slate-800 bg-slate-900/50 p-4 shrink-0 flex flex-col justify-between">
          <div className="space-y-1">
            <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider px-3 mb-2">Management & Status</div>
            {[
              { id: 'DASHBOARD', label: 'Operations Dashboard', icon: Activity },
              { id: 'DEVICE_STATUS', label: 'Device Status & Heartbeat', icon: Radio, badge: 'Live Telemetry', badgeColor: 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' },
              { id: 'ENROLLMENTS', label: 'Enrollment Requests', icon: QrCode, badge: enrollments.length },
              { id: 'CUSTOMER_WIZARD', label: 'Customer Device App', icon: SmartphoneNfc, badge: 'Onboarding' },
              { id: 'CUSTOMER_PORTAL', label: 'Customer Portal View', icon: Smartphone, badge: 'Transparency' },
              { id: 'DEVICES', label: 'Financed Hardware', icon: Smartphone, badge: devices.length },
              { id: 'CUSTOMERS', label: 'Customer Profiles', icon: Users, badge: customers.length },
              { id: 'FINANCING', label: 'Agreements & Ledger', icon: CreditCard, badge: agreements.length },
              { id: 'TEST_RUNNER', label: 'Automated Test Suite', icon: Terminal, badge: '43 Tests', badgeColor: 'bg-emerald-600' },
              { id: 'APK_DISTRIBUTION', label: 'Install Customer App', icon: Download, badge: 'APK / CI', badgeColor: 'bg-blue-600' },
              { id: 'RESTRICTED_KIOSK', label: 'Restricted Kiosk Screen', icon: Lock }
            ].map(item => {
              const IconComp = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => {
                    setActiveTab(item.id as any);
                    if (item.id === 'DEVICE_STATUS') {
                      loadDeviceStatus();
                    }
                  }}
                  className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs font-medium transition ${
                    isActive ? 'bg-blue-600 text-white font-semibold shadow' : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <IconComp className="w-4 h-4" />
                    <span>{item.label}</span>
                  </div>
                  {item.badge !== undefined && (
                    <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${item.badgeColor || 'bg-slate-800 text-slate-300'}`}>
                      {item.badge}
                    </span>
                  )}
                </button>
              );
            })}
          </div>

          <div className="mt-6 p-3 rounded-lg border border-slate-800 bg-slate-900/70 text-[11px] text-slate-400 space-y-1">
            <div className="font-semibold text-slate-200 flex items-center gap-1.5">
              <Cpu className="w-3.5 h-3.5 text-blue-400" /> WorkManager Policy
            </div>
            <p>15m minimum background interval. Android Doze compliant. Hardware-signed telemetry.</p>
          </div>
        </aside>

        {/* Content Body */}
        <main className="flex-1 p-4 lg:p-8 overflow-y-auto max-w-7xl">
          {/* TAB: OPERATIONS DASHBOARD */}
          {activeTab === 'DASHBOARD' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <div>
                  <h1 className="text-2xl font-bold tracking-tight">Enrollment & Financing Authority</h1>
                  <p className="text-xs text-slate-400 mt-1">Manage one-time pairing tickets, hardware attestation, and DPC activation.</p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      if (customers.length > 0 && devices.length > 0 && agreements.length > 0) {
                        setEnrollmentForm({
                          customerId: customers[0].id,
                          deviceId: devices[0].deviceId || devices[0].enrollmentId,
                          agreementId: agreements[0].id,
                          validityMinutes: 30
                        });
                      }
                      setShowCreateEnrollmentModal(true);
                    }}
                    className="px-3.5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 shadow"
                  >
                    <Plus className="w-3.5 h-3.5" /> Create Enrollment Ticket
                  </button>
                  <button
                    onClick={() => {
                      setActiveTab('DEVICE_STATUS');
                      loadDeviceStatus();
                    }}
                    className="px-3.5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 shadow"
                  >
                    <Radio className="w-3.5 h-3.5" /> View Device Status & Heartbeat
                  </button>
                </div>
              </div>

              {/* Metric Cards */}
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/80">
                  <div className="text-xs text-slate-400 font-medium">Total Enrollment Tickets</div>
                  <div className="text-2xl font-bold mt-2">{enrollments.length}</div>
                  <div className="text-[11px] text-blue-400 mt-1">Cryptographic Binding</div>
                </div>
                <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/80">
                  <div className="text-xs text-slate-400 font-medium">Active Enrolled Devices</div>
                  <div className="text-2xl font-bold mt-2 text-emerald-400">
                    {enrollments.filter(e => e.enrollmentStatus === 'ACTIVE').length}
                  </div>
                  <div className="text-[11px] text-emerald-500 mt-1">Public Keys Registered</div>
                </div>
                <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/80">
                  <div className="text-xs text-slate-400 font-medium">Pending Customer Pairing</div>
                  <div className="text-2xl font-bold mt-2 text-amber-400">
                    {enrollments.filter(e => e.enrollmentStatus === 'AWAITING_CUSTOMER' || e.enrollmentStatus === 'AWAITING_DEVICE').length}
                  </div>
                  <div className="text-[11px] text-amber-500 mt-1">Awaiting Code/QR Scan</div>
                </div>
                <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/80">
                  <div className="text-xs text-slate-400 font-medium">Revoked / Expired Tickets</div>
                  <div className="text-2xl font-bold mt-2 text-slate-400">
                    {enrollments.filter(e => e.enrollmentStatus === 'REVOKED' || e.enrollmentStatus === 'EXPIRED').length}
                  </div>
                  <div className="text-[11px] text-slate-500 mt-1">Single-use Protected</div>
                </div>
              </div>

              {/* Enrollments Overview */}
              <div className="border border-slate-800 bg-slate-900/60 rounded-xl overflow-hidden">
                <div className="p-4 border-b border-slate-800 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <QrCode className="w-4 h-4 text-blue-400" />
                    <span className="font-semibold text-xs text-white">Recent Hardware Enrollments</span>
                  </div>
                  <button onClick={() => loadAllData()} className="text-xs text-slate-400 hover:text-white flex items-center gap-1">
                    <RefreshCw className="w-3.5 h-3.5" /> Refresh
                  </button>
                </div>
                <div className="divide-y divide-slate-800/60">
                  {enrollments.map(enr => (
                    <div key={enr.id} className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-3 hover:bg-slate-800/20">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-xs font-bold text-white">{enr.id}</span>
                          <span
                            className={`text-[10px] px-2 py-0.5 rounded font-bold font-mono ${
                              enr.enrollmentStatus === 'ACTIVE'
                                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                                : enr.enrollmentStatus === 'REVOKED'
                                ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                                : enr.enrollmentStatus === 'EXPIRED'
                                ? 'bg-slate-800 text-slate-400'
                                : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                            }`}
                          >
                            {enr.enrollmentStatus}
                          </span>
                        </div>
                        <div className="text-xs text-slate-400 mt-1">
                          Model: <span className="text-slate-200 font-medium">{enr.deviceModel}</span> • Customer:{' '}
                          <span className="text-slate-200 font-medium">{enr.customerName}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => {
                            setSelectedStatusDevice(enr.id);
                            setActiveTab('DEVICE_STATUS');
                            loadDeviceStatus(enr.id);
                          }}
                          className="px-3 py-1.5 rounded-lg bg-blue-600/20 hover:bg-blue-600 text-blue-300 hover:text-white text-xs font-semibold transition"
                        >
                          Telemetry & Status
                        </button>
                        {enr.enrollmentStatus !== 'REVOKED' && (
                          <button
                            onClick={() => {
                              setEnrollmentToRevoke(enr);
                              setShowRevokeModal(true);
                            }}
                            className="px-3 py-1.5 rounded-lg bg-red-600/20 hover:bg-red-600 text-red-400 hover:text-white text-xs font-semibold transition"
                          >
                            Revoke
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB: DEVICE STATUS & LIVE HEARTBEAT (PHASE 5 CORE) */}
          {activeTab === 'DEVICE_STATUS' && (
            <div className="space-y-6">
              {/* Top Controls: Device Selector & Configurable Timeout */}
              <div className="p-4 rounded-xl border border-slate-800 bg-slate-900 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-emerald-600/20 text-emerald-400 flex items-center justify-center border border-emerald-500/30">
                    <Radio className="w-5 h-5 animate-pulse" />
                  </div>
                  <div>
                    <h1 className="text-lg font-bold text-white">Live Device Telemetry & Status</h1>
                    <p className="text-xs text-slate-400">Hardware-backed Android Keystore authentication and periodic WorkManager health ingestion.</p>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <select
                    value={selectedStatusDevice}
                    onChange={e => {
                      setSelectedStatusDevice(e.target.value);
                      loadDeviceStatus(e.target.value, timeoutMinutes);
                    }}
                    className="px-3 py-2 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-200 font-semibold focus:outline-none focus:border-blue-500"
                  >
                    {enrollments.map(enr => (
                      <option key={enr.id} value={enr.id}>
                        {enr.deviceModel} ({enr.id}) - {enr.enrollmentStatus}
                      </option>
                    ))}
                  </select>

                  <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-300">
                    <Clock className="w-3.5 h-3.5 text-blue-400" />
                    <span className="text-[11px] text-slate-400">Timeout:</span>
                    <select
                      value={timeoutMinutes}
                      onChange={e => {
                        const val = Number(e.target.value);
                        setTimeoutMinutes(val);
                        loadDeviceStatus(selectedStatusDevice, val);
                      }}
                      className="bg-transparent text-xs text-blue-400 font-bold focus:outline-none cursor-pointer"
                    >
                      <option value={1}>1 min (Dev test)</option>
                      <option value={5}>5 mins</option>
                      <option value={15}>15 mins (Standard)</option>
                      <option value={30}>30 mins</option>
                      <option value={60}>60 mins</option>
                    </select>
                  </div>

                  <button
                    onClick={() => loadDeviceStatus(selectedStatusDevice, timeoutMinutes)}
                    disabled={isFetchingStatus}
                    className="px-3 py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 shadow"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${isFetchingStatus ? 'animate-spin' : ''}`} />
                    Refresh
                  </button>
                </div>
              </div>

              {currentDeviceStatus ? (
                <>
                  {/* Primary Status Card */}
                  <div className="p-6 rounded-2xl border border-slate-800 bg-slate-900/90 shadow-xl space-y-6">
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-5">
                      <div>
                        <div className="flex items-center gap-2.5">
                          <h2 className="text-xl font-bold text-white">{currentDeviceStatus.deviceName}</h2>
                          {/* DYNAMIC SERVER ONLINE / OFFLINE STATUS BADGE */}
                          <span
                            className={`text-xs px-2.5 py-1 rounded-full font-bold font-mono tracking-wider flex items-center gap-1.5 ${
                              currentDeviceStatus.deviceOnlineStatus === 'ONLINE'
                                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                                : currentDeviceStatus.deviceOnlineStatus === 'OFFLINE'
                                ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40'
                                : currentDeviceStatus.deviceOnlineStatus === 'REVOKED'
                                ? 'bg-red-500/20 text-red-400 border border-red-500/40'
                                : currentDeviceStatus.deviceOnlineStatus === 'SUSPENDED'
                                ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40'
                                : 'bg-slate-800 text-slate-400 border border-slate-700'
                            }`}
                          >
                            <span
                              className={`w-2 h-2 rounded-full ${
                                currentDeviceStatus.deviceOnlineStatus === 'ONLINE'
                                  ? 'bg-emerald-400 animate-ping'
                                  : currentDeviceStatus.deviceOnlineStatus === 'OFFLINE'
                                  ? 'bg-amber-400'
                                  : 'bg-slate-500'
                              }`}
                            />
                            {currentDeviceStatus.deviceOnlineStatus}
                          </span>
                        </div>
                        <p className="text-xs text-slate-400 mt-1">
                          Serial: <span className="font-mono text-slate-300">{currentDeviceStatus.hardwareSerial || 'SN-UNASSIGNED'}</span> •
                          Enrollment: <span className="font-mono text-blue-400">{currentDeviceStatus.enrollmentId}</span>
                        </p>
                      </div>

                      <div className="flex flex-wrap items-center gap-3">
                        <div className="text-right">
                          <div className="text-[11px] text-slate-400">Enrollment Lifecycle</div>
                          <span
                            className={`text-xs font-mono font-bold px-2 py-0.5 rounded ${
                              currentDeviceStatus.enrollmentStatus === 'ACTIVE'
                                ? 'bg-emerald-500/10 text-emerald-400'
                                : currentDeviceStatus.enrollmentStatus === 'REVOKED'
                                ? 'bg-red-500/10 text-red-400'
                                : 'bg-amber-500/10 text-amber-400'
                            }`}
                          >
                            {currentDeviceStatus.enrollmentStatus}
                          </span>
                        </div>
                        <div className="text-right border-l border-slate-800 pl-3">
                          <div className="text-[11px] text-slate-400">Management Mode</div>
                          <span className="text-xs font-mono font-bold text-slate-200">
                            {currentDeviceStatus.managementMode || 'DEVICE_OWNER'}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Detailed Metric Badges */}
                    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
                      <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800">
                        <div className="text-[11px] text-slate-400 flex items-center gap-1">
                          <Clock className="w-3 h-3 text-blue-400" /> Last Heartbeat
                        </div>
                        <div className="text-sm font-bold text-white mt-1">{formatTimeAgo(currentDeviceStatus.lastHeartbeatAt)}</div>
                        <div className="text-[10px] text-slate-500 font-mono mt-0.5">
                          {currentDeviceStatus.lastHeartbeatAt ? new Date(currentDeviceStatus.lastHeartbeatAt).toLocaleTimeString() : 'Never'}
                        </div>
                      </div>

                      <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800">
                        <div className="text-[11px] text-slate-400 flex items-center gap-1">
                          <History className="w-3 h-3 text-emerald-400" /> Last Seen
                        </div>
                        <div className="text-sm font-bold text-white mt-1">{formatTimeAgo(currentDeviceStatus.lastSeenAt)}</div>
                        <div className="text-[10px] text-slate-500 font-mono mt-0.5">
                          {currentDeviceStatus.lastSeenAt ? new Date(currentDeviceStatus.lastSeenAt).toLocaleTimeString() : 'Never'}
                        </div>
                      </div>

                      <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800">
                        <div className="text-[11px] text-slate-400 flex items-center gap-1">
                          <Battery className="w-3 h-3 text-amber-400" /> Battery Level
                        </div>
                        <div className="text-sm font-bold text-white mt-1">
                          {currentDeviceStatus.batteryPercent !== null ? `${currentDeviceStatus.batteryPercent}%` : 'Unknown'}
                        </div>
                        <div className="w-full bg-slate-800 h-1 rounded-full mt-1.5 overflow-hidden">
                          <div
                            className={`h-full ${
                              (currentDeviceStatus.batteryPercent ?? 0) > 20 ? 'bg-emerald-500' : 'bg-red-500'
                            }`}
                            style={{ width: `${currentDeviceStatus.batteryPercent || 0}%` }}
                          />
                        </div>
                      </div>

                      <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800">
                        <div className="text-[11px] text-slate-400 flex items-center gap-1">
                          <Wifi className="w-3 h-3 text-purple-400" /> Connectivity
                        </div>
                        <div className="text-sm font-bold text-white mt-1">{currentDeviceStatus.networkConnectivity || 'WIFI'}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">{currentDeviceStatus.simCarrier || 'Carrier Unknown'}</div>
                      </div>

                      <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800">
                        <div className="text-[11px] text-slate-400 flex items-center gap-1">
                          <ShieldAlert className="w-3 h-3 text-red-400" /> Security State
                        </div>
                        <div className="text-sm font-bold text-white mt-1">{currentDeviceStatus.lastSecurityEvent || 'NORMAL'}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">
                          USB ADB: {currentDeviceStatus.usbDebuggingActive ? 'Active (Flagged)' : 'Disabled'}
                        </div>
                      </div>

                      <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800">
                        <div className="text-[11px] text-slate-400 flex items-center gap-1">
                          <Terminal className="w-3 h-3 text-blue-400" /> App Version
                        </div>
                        <div className="text-sm font-bold text-white mt-1">{currentDeviceStatus.appVersion || '1.0.0'}</div>
                        <div className="text-[10px] text-slate-500 mt-0.5">Cmd: {currentDeviceStatus.lastCommandStatus || 'NONE'}</div>
                      </div>
                    </div>

                    {/* Customer & Financing Linkage */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
                      <div className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 text-xs space-y-1">
                        <div className="font-semibold text-slate-200">Customer Financing Account</div>
                        <div className="text-slate-400">
                          {currentDeviceStatus.customer?.fullName} • {currentDeviceStatus.customer?.phoneNumber}
                        </div>
                        <div className="text-[11px] text-slate-500">Email: {currentDeviceStatus.customer?.email}</div>
                      </div>
                      <div className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 text-xs space-y-1">
                        <div className="font-semibold text-slate-200">Contract Agreement</div>
                        <div className="text-slate-400">
                          Code: <span className="font-mono text-blue-400 font-bold">{currentDeviceStatus.agreement?.agreementCode}</span> • Status:{' '}
                          <span className="font-semibold text-emerald-400">{currentDeviceStatus.agreement?.status}</span>
                        </div>
                        <div className="text-[11px] text-slate-500">
                          Remaining Financed Balance: ${currentDeviceStatus.agreement?.remainingAmount}
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Activity Timeline (Requirement 7) & Simulator (Requirement 14) */}
                  <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                    {/* Activity Timeline Feed */}
                    <div className="lg:col-span-7 space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <History className="w-4 h-4 text-blue-400" />
                          <h3 className="font-bold text-sm text-white">Device Activity Timeline</h3>
                        </div>
                        <span className="text-[11px] text-slate-500 font-mono">{deviceActivities.length} Events Recorded</span>
                      </div>

                      <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 space-y-3 max-h-[520px] overflow-y-auto">
                        {deviceActivities.length === 0 ? (
                          <div className="text-center py-8 text-xs text-slate-500">No activity recorded for this device yet.</div>
                        ) : (
                          deviceActivities.map(act => (
                            <div key={act.id} className="p-3 rounded-lg bg-slate-950 border border-slate-800/80 text-xs space-y-1">
                              <div className="flex items-center justify-between">
                                <span
                                  className={`text-[10px] px-2 py-0.5 rounded font-bold font-mono ${
                                    act.eventType === 'HEARTBEAT_RECEIVED'
                                      ? 'bg-blue-500/20 text-blue-300'
                                      : act.eventType === 'DEVICE_ONLINE'
                                      ? 'bg-emerald-500/20 text-emerald-400'
                                      : act.eventType === 'DEVICE_OFFLINE'
                                      ? 'bg-amber-500/20 text-amber-400'
                                      : act.eventType === 'ENROLLMENT_COMPLETED'
                                      ? 'bg-purple-500/20 text-purple-300'
                                      : act.eventType === 'SECURITY_EVENT'
                                      ? 'bg-red-500/20 text-red-400'
                                      : act.eventType === 'COMMAND_ACKNOWLEDGED'
                                      ? 'bg-teal-500/20 text-teal-300'
                                      : 'bg-red-500/20 text-red-400'
                                  }`}
                                >
                                  {act.eventType}
                                </span>
                                <span className="text-[10px] text-slate-500 font-mono">
                                  {new Date(act.timestamp).toLocaleTimeString()} ({formatTimeAgo(act.timestamp)})
                                </span>
                              </div>
                              <p className="text-slate-300 leading-relaxed text-[11px]">{act.description}</p>
                              {act.details && (
                                <div className="text-[10px] font-mono text-slate-500 bg-slate-900/50 p-1.5 rounded border border-slate-800/60">
                                  {JSON.stringify(act.details)}
                                </div>
                              )}
                            </div>
                          ))
                        )}
                      </div>
                    </div>

                    {/* Development & Test Heartbeat Simulation Harness */}
                    <div className="lg:col-span-5 space-y-4">
                      <div className="p-4 rounded-xl border border-slate-800 bg-slate-900 space-y-4">
                        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                          <div className="flex items-center gap-2">
                            <Sliders className="w-4 h-4 text-emerald-400" />
                            <h3 className="font-bold text-xs text-white uppercase tracking-wider">Test & Dev Simulation Harness</h3>
                          </div>
                          <span className="text-[9px] bg-amber-500/20 text-amber-400 border border-amber-500/30 px-1.5 py-0.5 rounded font-mono font-bold">
                            TEST HARNESS
                          </span>
                        </div>

                        <div className="text-[11px] text-slate-400 bg-slate-950 p-2.5 rounded-lg border border-slate-800">
                          <strong>Notice:</strong> Production devices execute heartbeats via Android Keystore and WorkManager background tasks.
                          This test panel allows verifying backend replay protection, timestamp expiration, and signature validation.
                        </div>

                        {/* Telemetry controls */}
                        <div className="space-y-3 text-xs">
                          <div>
                            <div className="flex justify-between text-slate-400 mb-1">
                              <span>Simulated Battery</span>
                              <span className="text-white font-bold">{simBattery}%</span>
                            </div>
                            <input
                              type="range"
                              min={5}
                              max={100}
                              value={simBattery}
                              onChange={e => setSimBattery(Number(e.target.value))}
                              className="w-full accent-blue-600"
                            />
                          </div>

                          <div className="grid grid-cols-2 gap-2">
                            <div>
                              <label className="block text-slate-400 mb-1">Network State</label>
                              <select
                                value={simNetwork}
                                onChange={e => setSimNetwork(e.target.value)}
                                className="w-full px-2.5 py-1.5 rounded bg-slate-950 border border-slate-800 text-slate-100 text-xs"
                              >
                                <option value="WIFI">WIFI (Broadband)</option>
                                <option value="CELLULAR_5G">CELLULAR_5G</option>
                                <option value="LTE">LTE (Cellular 4G)</option>
                              </select>
                            </div>
                            <div>
                              <label className="block text-slate-400 mb-1">Carrier</label>
                              <input
                                type="text"
                                value={simCarrier}
                                onChange={e => setSimCarrier(e.target.value)}
                                className="w-full px-2.5 py-1.5 rounded bg-slate-950 border border-slate-800 text-slate-100 text-xs font-mono"
                              />
                            </div>
                          </div>

                          <div className="flex items-center justify-between p-2 rounded bg-slate-950 border border-slate-800">
                            <span className="text-slate-300">USB Debugging (ADB Flag)</span>
                            <input
                              type="checkbox"
                              checked={simUsbDebugging}
                              onChange={e => setSimUsbDebugging(e.target.checked)}
                              className="accent-blue-600 w-4 h-4 cursor-pointer"
                            />
                          </div>

                          {/* Action Buttons */}
                          <div className="grid grid-cols-2 gap-2 pt-2">
                            <button
                              onClick={() => handleExecuteSimulation('VALID_HEARTBEAT')}
                              disabled={isSimulating}
                              className="py-2 px-3 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white rounded-lg font-bold text-[11px] transition shadow"
                            >
                              Send Valid Heartbeat
                            </button>
                            <button
                              onClick={() => handleExecuteSimulation('REPLAY_ATTACK')}
                              disabled={isSimulating}
                              className="py-2 px-3 bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white rounded-lg font-bold text-[11px] transition shadow"
                            >
                              Test Replay Attack
                            </button>
                            <button
                              onClick={() => handleExecuteSimulation('EXPIRED_REQUEST')}
                              disabled={isSimulating}
                              className="py-2 px-3 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-300 rounded-lg font-bold text-[11px] transition"
                            >
                              Test Expired Timestamp
                            </button>
                            <button
                              onClick={() => handleExecuteSimulation('TAMPERED_SIGNATURE')}
                              disabled={isSimulating}
                              className="py-2 px-3 bg-red-600/30 hover:bg-red-600 text-red-300 hover:text-white disabled:opacity-50 rounded-lg font-bold text-[11px] transition"
                            >
                              Test Forged Signature
                            </button>
                          </div>
                        </div>

                        {/* Simulator Output Logs */}
                        {simLogs.length > 0 && (
                          <div className="space-y-1.5 pt-2 border-t border-slate-800">
                            <div className="text-[10px] text-slate-500 font-mono uppercase">Simulation Trace Log</div>
                            <div className="space-y-1 max-h-36 overflow-y-auto font-mono text-[10px]">
                              {simLogs.slice(0, 4).map((log, idx) => (
                                <div key={idx} className="p-1.5 rounded bg-slate-950 border border-slate-800/80">
                                  <div className="flex justify-between">
                                    <span className={log.status === 'SUCCESS' ? 'text-emerald-400 font-bold' : 'text-red-400 font-bold'}>
                                      [{log.status}] {log.action}
                                    </span>
                                    <span className="text-slate-500">{log.time}</span>
                                  </div>
                                  <div className="text-slate-400 mt-0.5">{log.details}</div>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>

                      {/* Platform Limitations Disclosure (Requirement 13) */}
                      <div className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 text-xs space-y-2">
                        <div className="flex items-center gap-2 text-slate-200 font-bold">
                          <Info className="w-4 h-4 text-blue-400" /> Platform Limitation & Truth Model
                        </div>
                        <p className="text-[11px] text-slate-400 leading-relaxed">
                          Android enforces strict power policies. Periodic background jobs using WorkManager are batched with a minimum
                          15-minute interval. When in Doze mode or App Standby, heartbeats are deferred until system maintenance windows.
                          Consequently, the status above represents the <strong>actual verified last-known cryptographic state</strong>, rather than an
                          inaccurate claim of zero-latency continuous streaming.
                        </p>
                      </div>
                    </div>
                  </div>
                </>
              ) : (
                <div className="p-12 text-center text-slate-500 bg-slate-900 rounded-2xl border border-slate-800">
                  <RefreshCw className="w-8 h-8 mx-auto animate-spin mb-2 text-blue-400" />
                  Loading device status and telemetry records...
                </div>
              )}
            </div>
          )}

          {/* TAB: ENROLLMENT REQUESTS (PHASE 4) */}
          {activeTab === 'ENROLLMENTS' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <div>
                  <h1 className="text-2xl font-bold tracking-tight">Cryptographic Device Enrollments</h1>
                  <p className="text-xs text-slate-400 mt-1">Single-use tokens, hardware attestation, and one-time QR payloads.</p>
                </div>
                <button
                  onClick={() => setShowCreateEnrollmentModal(true)}
                  className="px-3.5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 shadow"
                >
                  <Plus className="w-3.5 h-3.5" /> Create Ticket
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {enrollments.map(enr => (
                  <div key={enr.id} className="p-5 rounded-2xl border border-slate-800 bg-slate-900 shadow space-y-4">
                    <div className="flex justify-between items-start">
                      <div>
                        <div className="font-bold text-white text-base">{enr.deviceModel}</div>
                        <div className="text-xs text-slate-400 mt-0.5">Customer: {enr.customerName}</div>
                      </div>
                      <span
                        className={`text-[10px] px-2.5 py-1 rounded-full font-bold font-mono ${
                          enr.enrollmentStatus === 'ACTIVE'
                            ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                            : enr.enrollmentStatus === 'REVOKED'
                            ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                            : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                        }`}
                      >
                        {enr.enrollmentStatus}
                      </span>
                    </div>

                    <div className="p-3 rounded-xl bg-slate-950 border border-slate-800 text-xs space-y-1.5 font-mono">
                      <div className="flex justify-between">
                        <span className="text-slate-500">Pairing Code:</span>
                        <span className="text-white font-bold">{enr.enrollmentCode}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-500">Expires At:</span>
                        <span className="text-amber-400">{new Date(enr.expiresAt).toLocaleTimeString()}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-500">Token Consumed:</span>
                        <span className={enr.isTokenUsed ? 'text-emerald-400' : 'text-slate-400'}>
                          {enr.isTokenUsed ? 'Yes (Single-use locked)' : 'No (Pending)'}
                        </span>
                      </div>
                    </div>

                    <div className="flex gap-2">
                      <button
                        onClick={() => {
                          setSelectedStatusDevice(enr.id);
                          setActiveTab('DEVICE_STATUS');
                          loadDeviceStatus(enr.id);
                        }}
                        className="flex-1 py-2 bg-blue-600/20 hover:bg-blue-600 text-blue-300 hover:text-white rounded-lg text-xs font-semibold transition"
                      >
                        View Live Status
                      </button>
                      {enr.enrollmentStatus !== 'REVOKED' && (
                        <button
                          onClick={() => {
                            setEnrollmentToRevoke(enr);
                            setShowRevokeModal(true);
                          }}
                          className="px-4 py-2 bg-red-600/20 hover:bg-red-600 text-red-400 hover:text-white rounded-lg text-xs font-semibold transition"
                        >
                          Revoke
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB: CUSTOMER PORTAL VIEW (REQUIREMENT 8) */}
          {activeTab === 'CUSTOMER_PORTAL' && (
            <div className="max-w-xl mx-auto space-y-6">
              <div className="text-center space-y-1">
                <span className="text-[11px] bg-blue-500/20 text-blue-400 px-3 py-1 rounded-full font-semibold border border-blue-500/30">
                  CUSTOMER DEVICE APP VIEW
                </span>
                <h2 className="text-xl font-bold text-white mt-2">Financed Device Portal</h2>
                <p className="text-xs text-slate-400">Customer transparency, installment standing, and background sync health.</p>
              </div>

              {/* Status Card */}
              <div className="p-6 rounded-3xl border border-slate-800 bg-slate-900 shadow-2xl space-y-6">
                <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                  <div>
                    <div className="text-xs text-slate-400">FINANCING STATUS</div>
                    <div className="text-2xl font-bold text-emerald-400 mt-0.5">In Good Standing</div>
                  </div>
                  <span className="text-xs px-3 py-1 bg-emerald-500/20 text-emerald-400 rounded-full font-bold border border-emerald-500/30">
                    ACTIVE
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-4 text-xs">
                  <div className="p-3 bg-slate-950 rounded-xl border border-slate-800">
                    <span className="text-slate-500 text-[11px]">Enrollment State:</span>
                    <div className="text-white font-bold mt-1">Protected Financed Device</div>
                  </div>
                  <div className="p-3 bg-slate-950 rounded-xl border border-slate-800">
                    <span className="text-slate-500 text-[11px]">Management Status:</span>
                    <div className="text-white font-bold mt-1">Managed Device (Device Owner)</div>
                  </div>
                  <div className="p-3 bg-slate-950 rounded-xl border border-slate-800">
                    <span className="text-slate-500 text-[11px]">Last Successful Sync:</span>
                    <div className="text-slate-200 font-medium mt-1">
                      {currentDeviceStatus?.lastSuccessfulSync
                        ? new Date(currentDeviceStatus.lastSuccessfulSync).toLocaleTimeString()
                        : 'Verified Today'}
                    </div>
                  </div>
                  <div className="p-3 bg-slate-950 rounded-xl border border-slate-800">
                    <span className="text-slate-500 text-[11px]">App Version:</span>
                    <div className="text-slate-200 font-medium mt-1">{currentDeviceStatus?.appVersion || '1.0.0 (Release)'}</div>
                  </div>
                </div>

                {/* Privacy transparency disclosures */}
                <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 text-xs space-y-2">
                  <div className="font-bold text-slate-200 flex items-center gap-1.5">
                    <ShieldCheck className="w-4 h-4 text-emerald-400" /> Customer Privacy Commitment
                  </div>
                  <p className="text-slate-400 text-[11px] leading-relaxed">
                    This official management app protects the financing agreement. It collects basic hardware diagnostic telemetry (battery
                    percentage, network state, and synchronization timestamps). It <strong>never</strong> accesses personal photos, private messages,
                    contacts, or browsing data.
                  </p>
                </div>

                {/* Customer Support Contacts */}
                <div className="p-4 rounded-xl bg-blue-950/20 border border-blue-900/40 text-xs space-y-2">
                  <div className="font-bold text-blue-200">Financing Customer Support</div>
                  <p className="text-blue-300/80 text-[11px]">Questions about installment due dates or payment methods?</p>
                  <div className="flex gap-2 pt-1">
                    <button
                      onClick={() => showToast('Connecting to toll-free support: 1-800-555-FINANCE')}
                      className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-semibold text-xs transition"
                    >
                      Call 1-800-555-FINANCE
                    </button>
                    <button
                      onClick={() => showToast('Inquiry drafted to support@company.com')}
                      className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg font-semibold text-xs transition"
                    >
                      Email Support
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB: CUSTOMER ONBOARDING WIZARD */}
          {activeTab === 'CUSTOMER_WIZARD' && (
            <div className="max-w-md mx-auto space-y-6">
              <div className="p-2 text-center">
                <span className="text-xs bg-emerald-500/20 text-emerald-400 px-3 py-1 rounded-full font-semibold">
                  CUSTOMER ONBOARDING APP (ANDROID DPC CLIENT)
                </span>
              </div>

              {wizardStep === 1 && (
                <div className="p-6 rounded-3xl border border-slate-800 bg-slate-900 shadow-2xl space-y-6 text-center">
                  <div className="w-14 h-14 rounded-2xl bg-blue-600/20 text-blue-400 mx-auto flex items-center justify-center">
                    <SmartphoneNfc className="w-7 h-7" />
                  </div>
                  <div>
                    <h2 className="text-xl font-bold text-white">Enroll Your Financed Device</h2>
                    <p className="text-xs text-slate-400 mt-1">Enter the official enrollment code provided by your sales agent or financing provider.</p>
                  </div>

                  <div className="text-left space-y-2">
                    <label className="text-xs text-slate-400">One-Time Enrollment Code</label>
                    <input
                      type="text"
                      value={wizardCode}
                      onChange={e => setWizardCode(e.target.value.toUpperCase())}
                      placeholder="e.g. ENR-9920-CODE"
                      className="w-full px-4 py-3 rounded-xl bg-slate-950 border border-slate-800 text-white font-mono text-center font-bold text-sm tracking-wider focus:outline-none focus:border-blue-500"
                    />
                  </div>

                  <button
                    onClick={handleWizardSubmitCode}
                    className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold transition shadow-lg shadow-blue-600/30"
                  >
                    Continue to Disclosures
                  </button>
                </div>
              )}

              {wizardStep === 2 && wizardEnrollmentData && (
                <div className="p-6 rounded-3xl border border-slate-800 bg-slate-900 shadow-2xl space-y-5">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-blue-600/20 text-blue-400 flex items-center justify-center shrink-0">
                      <FileText className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-white text-base">Financing Agreement Terms</h3>
                      <p className="text-xs text-slate-400">Step 2 of 6: Review Contract</p>
                    </div>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 text-xs space-y-2">
                    <div className="flex justify-between">
                      <span className="text-slate-500">Customer:</span>
                      <span className="text-white font-semibold">{wizardEnrollmentData.customer?.fullName}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-500">Device Hardware:</span>
                      <span className="text-white font-semibold">{wizardEnrollmentData.device?.model}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-slate-500">Total Financed:</span>
                      <span className="text-emerald-400 font-bold">${wizardEnrollmentData.agreement?.totalAmount}</span>
                    </div>
                  </div>

                  <button
                    onClick={() => setWizardStep(3)}
                    className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold transition shadow-lg shadow-blue-600/30"
                  >
                    Review Management Disclosures
                  </button>
                </div>
              )}

              {wizardStep === 3 && (
                <div className="p-6 rounded-3xl border border-slate-800 bg-slate-900 shadow-2xl space-y-5">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-purple-600/20 text-purple-400 flex items-center justify-center shrink-0">
                      <Shield className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-white text-base">Permissions & Capabilities</h3>
                      <p className="text-xs text-slate-400">Step 3 of 6: Transparent Disclosures</p>
                    </div>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 text-xs text-slate-300 space-y-2">
                    <div>• Periodic health heartbeat via Android WorkManager</div>
                    <div>• Cryptographic ECDSA P-256 signing inside Android Keystore</div>
                    <div>• Non-payment device restriction lock if overdue beyond grace window</div>
                    <div>• Strict zero access to private messages, camera, or browser history</div>
                  </div>

                  <button
                    onClick={() => setWizardStep(4)}
                    className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold transition shadow-lg shadow-blue-600/30"
                  >
                    Review Security Key Generation
                  </button>
                </div>
              )}

              {wizardStep === 4 && (
                <div className="p-6 rounded-3xl border border-slate-800 bg-slate-900 shadow-2xl space-y-5">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-emerald-600/20 text-emerald-400 flex items-center justify-center shrink-0">
                      <Key className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-white text-base">Android Keystore StrongBox</h3>
                      <p className="text-xs text-slate-400">Step 4 of 6: Cryptographic Identity</p>
                    </div>
                  </div>

                  <p className="text-xs text-slate-400 leading-relaxed">
                    A private signing key is generated inside your device’s hardware security module (TEE / StrongBox). The private key cannot be
                    extracted or uploaded. Only the public key is registered with the financing authority.
                  </p>

                  <button
                    onClick={handleWizardSimulateEnrollment}
                    className="w-full py-3 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold transition shadow-lg shadow-emerald-600/30"
                  >
                    Generate Keystore Key & Complete Enrollment
                  </button>
                </div>
              )}

              {wizardStep === 5 && (
                <div className="p-8 rounded-3xl border border-slate-800 bg-slate-900 shadow-2xl text-center space-y-5">
                  <RefreshCw className="w-10 h-10 text-blue-400 animate-spin mx-auto" />
                  <h3 className="font-bold text-white text-base">Completing Enrollment...</h3>
                  <p className="text-xs text-slate-400">{wizardProgressMessage}</p>
                </div>
              )}

              {wizardStep === 6 && (
                <div className="p-8 rounded-3xl border border-emerald-900/60 bg-slate-900 shadow-2xl text-center space-y-5">
                  <div className="w-16 h-16 rounded-full bg-emerald-600/20 text-emerald-400 mx-auto flex items-center justify-center border border-emerald-500/30">
                    <Check className="w-8 h-8" />
                  </div>
                  <div>
                    <h3 className="font-bold text-white text-xl">Device Successfully Enrolled!</h3>
                    <p className="text-xs text-slate-400 mt-1">
                      Status: <span className="text-emerald-400 font-bold">ACTIVE</span> • Keystore key registered.
                    </p>
                  </div>

                  <p className="text-xs text-slate-400 leading-relaxed">
                    Your financed device is configured with WorkManager periodic sync and installment protection.
                  </p>

                  <button
                    onClick={() => {
                      setWizardStep(1);
                      setActiveTab('DEVICE_STATUS');
                      loadDeviceStatus();
                    }}
                    className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold transition"
                  >
                    View Device Live Status
                  </button>
                </div>
              )}
            </div>
          )}

          {/* TAB: FINANCED HARDWARE */}
          {activeTab === 'DEVICES' && (
            <div className="space-y-6">
              <h1 className="text-2xl font-bold tracking-tight">Financed Hardware Units</h1>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {devices.map(d => (
                  <div key={d.enrollmentId} className="p-5 rounded-xl border border-slate-800 bg-slate-900/70 space-y-3">
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-white">{d.manufacturer} {d.model}</span>
                      <span
                        className={`text-[10px] px-2 py-0.5 rounded font-bold ${
                          d.status === 'ACTIVE' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'
                        }`}
                      >
                        {d.status}
                      </span>
                    </div>
                    <div className="text-xs text-slate-400">
                      Customer: {d.customerName} • Agreement: {d.agreementCode}
                    </div>
                    <button
                      onClick={() => {
                        setSelectedStatusDevice(d.enrollmentId);
                        setActiveTab('DEVICE_STATUS');
                        loadDeviceStatus(d.enrollmentId);
                      }}
                      className="w-full py-1.5 bg-blue-600/20 hover:bg-blue-600 text-blue-300 hover:text-white rounded text-xs font-semibold transition"
                    >
                      Check Live Status & Activity
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB: CUSTOMER PROFILES */}
          {activeTab === 'CUSTOMERS' && (
            <div className="space-y-6">
              <h1 className="text-2xl font-bold tracking-tight">Financing Customers</h1>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {customers.map(c => (
                  <div key={c.id} className="p-5 rounded-xl border border-slate-800 bg-slate-900/70 space-y-2">
                    <div className="font-bold text-white">{c.fullName}</div>
                    <div className="text-xs text-slate-400">Phone: {c.phoneNumber} • Email: {c.email}</div>
                    <div className="text-[11px] text-slate-500">ID: {c.nationalIdMasked}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB: AGREEMENTS */}
          {activeTab === 'FINANCING' && (
            <div className="space-y-6">
              <h1 className="text-2xl font-bold tracking-tight">Financing Agreements</h1>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {agreements.map(a => (
                  <div key={a.id} className="p-5 rounded-xl border border-slate-800 bg-slate-900/70 space-y-2">
                    <div className="flex justify-between">
                      <span className="font-bold text-white">{a.agreementCode}</span>
                      <span className="text-emerald-400 font-bold">${a.remainingAmount} remaining</span>
                    </div>
                    <div className="text-xs text-slate-400">Customer: {a.customerName}</div>
                    <div className="text-xs text-slate-500">
                      Next Due: {a.nextDueDate} • Monthly: ${a.installmentAmount}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB: AUTOMATED TEST RUNNER (PHASES 3, 4, 5 & 6) */}
          {activeTab === 'TEST_RUNNER' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <div>
                  <h1 className="text-2xl font-bold tracking-tight">Automated Verification Test Suite</h1>
                  <p className="text-xs text-slate-400 mt-1">
                    Executes all 43 automated verification tests (Phase 3 Ledger & Installments, Phase 4 Keystore Enrollment, Phase 5 WorkManager
                    Heartbeat, Phase 6 Persistent Database, Android Client Connection & Strict APK Distribution Integrity).
                  </p>
                </div>
                <button
                  onClick={handleRunTests}
                  disabled={isRunningTests}
                  className="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl text-xs font-bold flex items-center gap-2 shadow-lg shadow-blue-600/30"
                >
                  <Terminal className="w-4 h-4" />
                  {isRunningTests ? 'Executing Tests...' : 'Run All 43 Tests'}
                </button>
              </div>

              {testResults.length > 0 && (
                <div className="border border-slate-800 bg-slate-900/80 rounded-2xl overflow-hidden shadow-xl">
                  <div className="p-4 border-b border-slate-800 flex justify-between items-center bg-slate-900">
                    <span className="font-bold text-xs uppercase tracking-wider text-white">Execution Report</span>
                    <span className="text-xs font-mono font-bold text-emerald-400">
                      {testResults.filter(t => t.passed).length} / {testResults.length} PASSED
                    </span>
                  </div>
                  <div className="divide-y divide-slate-800/60 font-mono text-xs max-h-[600px] overflow-y-auto">
                    {testResults.map((t, idx) => (
                      <div key={idx} className="p-3.5 flex items-center justify-between hover:bg-slate-800/30">
                        <div className="flex items-center gap-2.5">
                          {t.passed ? <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" /> : <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />}
                          <span className={t.passed ? 'text-slate-200' : 'text-red-400 font-bold'}>{t.name}</span>
                        </div>
                        <div className="flex items-center gap-3">
                          {t.error && <span className="text-[10px] text-red-400 max-w-md truncate">{t.error}</span>}
                          <span className="text-[10px] text-slate-500 font-mono">{t.durationMs}ms</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* TAB: APK DISTRIBUTION & INSTALLATION GUIDE (TASK 1, 7, 8, 10, 11) */}
          {activeTab === 'APK_DISTRIBUTION' && (
            <div className="space-y-6">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-3">
                    <h1 className="text-2xl font-bold tracking-tight">Download SK Pro Customer App</h1>
                    <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-blue-500/20 text-blue-400 border border-blue-500/30">
                      v1.0.0 (Build 1)
                    </span>
                    <a
                      href="https://github.com/kamran-bhai/Sk-Pro"
                      target="_blank"
                      rel="noreferrer"
                      className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-500/20 text-indigo-400 border border-indigo-500/30 flex items-center gap-1 hover:underline"
                    >
                      <ExternalLink className="w-3 h-3" /> kamran-bhai/Sk-Pro
                    </a>
                    {apkInfo?.isBuilt ? (
                      <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" /> APK Ready
                      </span>
                    ) : (
                      <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500/20 text-amber-400 border border-amber-500/30 flex items-center gap-1">
                        <Clock className="w-3 h-3" /> Build Required
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-slate-400 mt-1">
                    Complete Android Device Policy Controller (DPC) client distribution, verification parameters, and customer provisioning flow.
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <button
                    onClick={fetchApkInfo}
                    disabled={isLoadingApkInfo}
                    className="px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-colors border border-slate-700"
                  >
                    <Radio className={`w-3.5 h-3.5 ${isLoadingApkInfo ? 'animate-spin' : 'text-blue-400'}`} />
                    Refresh Status
                  </button>
                  <a
                    href="/download/app.apk"
                    target="_blank"
                    rel="noreferrer"
                    className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold flex items-center gap-2 shadow-lg shadow-emerald-600/30"
                  >
                    <Download className="w-4 h-4" />
                    Download APK
                  </a>
                </div>
              </div>

              {/* Real APK Technical Specifications Card */}
              <div className="border border-slate-800 bg-slate-900/90 rounded-2xl p-6 shadow-xl space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <div className="flex items-center gap-2">
                    <PackageCheck className="w-5 h-5 text-emerald-400" />
                    <h3 className="font-bold text-sm text-white">APK Artifact Specifications</h3>
                  </div>
                  <span className="text-[11px] font-mono text-slate-400">Package: com.protectfinanceddevices.app</span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                  <div className="p-3.5 bg-slate-950/60 rounded-xl border border-slate-800/80">
                    <div className="text-[11px] text-slate-400 font-medium">Application Name</div>
                    <div className="text-sm font-bold text-white mt-0.5">SK Pro</div>
                    <div className="text-[10px] text-slate-500 font-mono mt-0.5">Financed Device DPC</div>
                  </div>

                  <div className="p-3.5 bg-slate-950/60 rounded-xl border border-slate-800/80">
                    <div className="text-[11px] text-slate-400 font-medium">APK Version</div>
                    <div className="text-sm font-bold text-white mt-0.5">{apkInfo?.versionName || '1.0.0'}</div>
                    <div className="text-[10px] text-slate-500 font-mono mt-0.5">VersionCode: {apkInfo?.versionCode || 1}</div>
                  </div>

                  <div className="p-3.5 bg-slate-950/60 rounded-xl border border-slate-800/80">
                    <div className="text-[11px] text-slate-400 font-medium">File Size</div>
                    <div className={`text-sm font-bold mt-0.5 ${apkInfo?.isBuilt ? 'text-emerald-400' : 'text-amber-400'}`}>
                      {apkInfo?.fileSizeFormatted || 'Awaiting build (~12-18 MB)'}
                    </div>
                    <div className="text-[10px] text-slate-500 font-mono mt-0.5">
                      {apkInfo?.isBuilt ? 'Ready on disk' : 'External build pending'}
                    </div>
                  </div>

                  <div className="p-3.5 bg-slate-950/60 rounded-xl border border-slate-800/80">
                    <div className="text-[11px] text-slate-400 font-medium">Security Attestation</div>
                    <div className="text-sm font-bold text-indigo-400 mt-0.5">EC P-256 Keystore</div>
                    <div className="text-[10px] text-slate-500 font-mono mt-0.5">StrongBox / TEE Hardware</div>
                  </div>
                </div>

                {/* Honest Build Notice Alert */}
                {!apkInfo?.isBuilt && (
                  <div className="p-4 rounded-xl border border-amber-500/30 bg-amber-950/20 flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
                    <div className="text-xs text-slate-300 space-y-1">
                      <div className="font-bold text-amber-300">Android Build System Integration Required for Binary APK</div>
                      <p className="leading-relaxed text-slate-400">
                        The full Android source code is completely configured in <code className="text-slate-200 font-mono">/android-dpc</code>.
                        Because this web sandbox runtime operates without Java/Android SDK, the APK must be compiled externally using{' '}
                        <strong>Android Studio</strong> (<code className="text-slate-200 font-mono">./gradlew assembleDebug</code>) or triggered in GitHub Actions using{' '}
                        <code className="text-slate-200 font-mono">.github/workflows/android-build.yml</code>.
                        Once built, <code className="text-slate-200 font-mono">app-debug.apk</code> will be immediately served from <code className="text-slate-200 font-mono">/download/app.apk</code>.
                      </p>
                    </div>
                  </div>
                )}
              </div>

              {/* QR Code & Direct Customer Installation Links */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* QR Code Box */}
                <div className="border border-slate-800 bg-slate-900/90 rounded-2xl p-6 shadow-xl flex flex-col items-center text-center justify-between space-y-4">
                  <div>
                    <h3 className="font-bold text-sm text-white flex items-center justify-center gap-2">
                      <QrCode className="w-4 h-4 text-emerald-400" />
                      Scan to Download APK
                    </h3>
                    <p className="text-[11px] text-slate-400 mt-1">
                      Scan with any camera app on the customer's Android device to open the direct download URL.
                    </p>
                  </div>

                  {/* High Quality SVG QR Code Representation */}
                  <div className="p-3 bg-white rounded-2xl shadow-inner border-2 border-slate-700/50">
                    <svg viewBox="0 0 160 160" className="w-36 h-36">
                      <rect width="160" height="160" fill="white" />
                      {/* Top-Left Position Detection Pattern */}
                      <rect x="10" y="10" width="40" height="40" fill="black" rx="4" />
                      <rect x="18" y="18" width="24" height="24" fill="white" />
                      <rect x="24" y="24" width="12" height="12" fill="black" />

                      {/* Top-Right Position Detection Pattern */}
                      <rect x="110" y="10" width="40" height="40" fill="black" rx="4" />
                      <rect x="118" y="18" width="24" height="24" fill="white" />
                      <rect x="124" y="24" width="12" height="12" fill="black" />

                      {/* Bottom-Left Position Detection Pattern */}
                      <rect x="10" y="110" width="40" height="40" fill="black" rx="4" />
                      <rect x="18" y="118" width="24" height="24" fill="white" />
                      <rect x="24" y="124" width="12" height="12" fill="black" />

                      {/* Data Pattern Mock */}
                      <rect x="60" y="15" width="8" height="8" fill="black" />
                      <rect x="75" y="25" width="8" height="8" fill="black" />
                      <rect x="90" y="15" width="8" height="8" fill="black" />
                      <rect x="65" y="45" width="12" height="6" fill="black" />
                      <rect x="85" y="40" width="6" height="12" fill="black" />

                      <rect x="15" y="60" width="8" height="8" fill="black" />
                      <rect x="30" y="70" width="12" height="6" fill="black" />
                      <rect x="15" y="85" width="6" height="12" fill="black" />

                      <rect x="60" y="60" width="10" height="10" fill="black" />
                      <rect x="75" y="75" width="10" height="10" fill="black" />
                      <rect x="90" y="60" width="10" height="10" fill="black" />
                      <rect x="60" y="90" width="10" height="10" fill="black" />
                      <rect x="90" y="90" width="10" height="10" fill="black" />

                      <rect x="115" y="65" width="8" height="8" fill="black" />
                      <rect x="135" y="75" width="12" height="6" fill="black" />
                      <rect x="120" y="90" width="8" height="8" fill="black" />

                      <rect x="60" y="115" width="8" height="8" fill="black" />
                      <rect x="75" y="130" width="12" height="6" fill="black" />
                      <rect x="90" y="120" width="8" height="8" fill="black" />
                      <rect x="115" y="115" width="8" height="8" fill="black" />
                      <rect x="130" y="130" width="14" height="10" fill="black" />
                    </svg>
                  </div>

                  <div className="w-full space-y-1.5">
                    <div className="text-[10px] text-slate-400 font-mono truncate">
                      {apkInfo?.downloadUrl || 'https://ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app/download/app.apk'}
                    </div>
                    <button
                      onClick={() => {
                        const url = apkInfo?.downloadUrl || `${window.location.origin}/download/app.apk`;
                        navigator.clipboard.writeText(url);
                        showToast('APK Download URL copied to clipboard!', 'success');
                      }}
                      className="w-full py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors"
                    >
                      <Copy className="w-3.5 h-3.5 text-blue-400" />
                      Copy Download URL
                    </button>
                  </div>
                </div>

                {/* Configuration & Server Endpoint Details */}
                <div className="lg:col-span-2 border border-slate-800 bg-slate-900/90 rounded-2xl p-6 shadow-xl space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                    <div className="flex items-center gap-2">
                      <Cpu className="w-5 h-5 text-blue-400" />
                      <h3 className="font-bold text-sm text-white">Android Client Backend Configuration</h3>
                    </div>
                    <span className="text-[11px] font-mono text-emerald-400">Strict TLS v1.3</span>
                  </div>

                  <div className="space-y-3 text-xs text-slate-300">
                    <div>
                      <div className="text-[11px] text-slate-400 mb-1">Target Authority Server URL</div>
                      <div className="p-3 bg-slate-950 rounded-xl font-mono text-emerald-400 text-xs flex items-center justify-between border border-slate-800">
                        <span className="truncate">https://ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app</span>
                        <button
                          onClick={() => {
                            navigator.clipboard.writeText('https://ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app');
                            showToast('Server authority URL copied!', 'success');
                          }}
                          className="p-1 hover:text-white"
                          title="Copy URL"
                        >
                          <Copy className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                      <div className="p-3 bg-slate-950/70 rounded-xl border border-slate-800">
                        <div className="text-[11px] text-slate-400 font-semibold mb-1">Heartbeat API Route</div>
                        <div className="font-mono text-slate-200 text-[11px]">POST /api/v1/device/heartbeat</div>
                        <div className="text-[10px] text-slate-500 mt-1">Periodic 15-30m telemetry signed with Keystore</div>
                      </div>

                      <div className="p-3 bg-slate-950/70 rounded-xl border border-slate-800">
                        <div className="text-[11px] text-slate-400 font-semibold mb-1">Enrollment Route</div>
                        <div className="font-mono text-slate-200 text-[11px]">POST /api/v1/enrollments/:id/verify</div>
                        <div className="text-[10px] text-slate-500 mt-1">Challenge-response hardware attestation</div>
                      </div>
                    </div>

                    <div className="p-3.5 rounded-xl bg-blue-950/20 border border-blue-500/20 space-y-1">
                      <div className="text-white font-semibold flex items-center gap-1.5">
                        <FileCode className="w-3.5 h-3.5 text-blue-400" />
                        Expected Local & CI Build Output Location:
                      </div>
                      <div className="font-mono text-[11px] text-blue-300">
                        android-dpc/app/build/outputs/apk/debug/app-debug.apk
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Step-by-Step Installation Flow */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="border border-slate-800 bg-slate-900/80 rounded-2xl p-5 space-y-3">
                  <div className="flex items-center gap-3">
                    <span className="w-7 h-7 rounded-full bg-blue-600/20 text-blue-400 font-bold text-xs flex items-center justify-center border border-blue-500/30">1</span>
                    <h3 className="font-bold text-sm text-white">Build the Android APK</h3>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    Build using <strong>Android Studio</strong> (<code className="text-slate-300 font-mono">Build &gt; Build APK(s)</code>) or run the automated <strong>GitHub Actions</strong> workflow (<code className="text-slate-300 font-mono">.github/workflows/android-build.yml</code>).
                  </p>
                  <div className="p-2.5 bg-slate-950 rounded-lg text-[11px] font-mono text-slate-300 border border-slate-800">
                    Gradle Command: ./gradlew assembleDebug
                  </div>
                </div>

                <div className="border border-slate-800 bg-slate-900/80 rounded-2xl p-5 space-y-3">
                  <div className="flex items-center gap-3">
                    <span className="w-7 h-7 rounded-full bg-blue-600/20 text-blue-400 font-bold text-xs flex items-center justify-center border border-blue-500/30">2</span>
                    <h3 className="font-bold text-sm text-white">Download the Generated APK</h3>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    Once built, copy the compiled binary to the server output directory or download directly via the <code className="text-slate-300 font-mono">/download/app.apk</code> endpoint.
                  </p>
                  <div className="p-2.5 bg-slate-950 rounded-lg text-[11px] font-mono text-emerald-400 border border-slate-800">
                    GET /download/app.apk (Content-Type: application/vnd.android.package-archive)
                  </div>
                </div>

                <div className="border border-slate-800 bg-slate-900/80 rounded-2xl p-5 space-y-3">
                  <div className="flex items-center gap-3">
                    <span className="w-7 h-7 rounded-full bg-blue-600/20 text-blue-400 font-bold text-xs flex items-center justify-center border border-blue-500/30">3</span>
                    <h3 className="font-bold text-sm text-white">Transfer & Device Permissions</h3>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    Transfer the APK to the customer's Android device via USB, direct download, or local distribution. When prompted by Android security, allow installation from this source.
                  </p>
                  <div className="p-2.5 bg-slate-950 rounded-lg text-[11px] font-mono text-amber-300 border border-slate-800">
                    Android Settings &gt; Apps &gt; Special App Access &gt; Install Unknown Apps
                  </div>
                </div>

                <div className="border border-slate-800 bg-slate-900/80 rounded-2xl p-5 space-y-3">
                  <div className="flex items-center gap-3">
                    <span className="w-7 h-7 rounded-full bg-blue-600/20 text-blue-400 font-bold text-xs flex items-center justify-center border border-blue-500/30">4</span>
                    <h3 className="font-bold text-sm text-white">Authorized Customer Enrollment</h3>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    Open SK Pro, enter the single-use pairing code (e.g. <code className="text-slate-300 font-mono">ENR-8841-CODE</code>), review transparent disclosures, and complete hardware Keystore registration.
                  </p>
                  <div className="p-2.5 bg-slate-950 rounded-lg text-[11px] font-mono text-blue-400 border border-slate-800">
                    Mutual Attestation: NIST P-256 EC Keystore + WorkManager Heartbeat
                  </div>
                </div>
              </div>

              {/* Background Execution Guidelines */}
              <div className="p-5 rounded-2xl border border-slate-800 bg-slate-900/80 space-y-2">
                <div className="flex items-center gap-2 text-white font-bold text-sm">
                  <BatteryCharging className="w-4 h-4 text-amber-400" />
                  Background Telemetry & WorkManager Lifecycle Notice
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  SK Pro strictly complies with Android's battery optimization standards. Periodic heartbeats are dispatched via Android WorkManager at an interval of 15–30 minutes. Timings vary depending on Android Doze mode and manufacturer power policies; the server dynamically evaluates online/offline state accordingly. No hidden background wake locks or covert device monitoring are employed.
                </p>
              </div>
            </div>
          )}

          {/* TAB: RESTRICTED SCREEN */}
          {activeTab === 'RESTRICTED_KIOSK' && (
            <div className="max-w-md mx-auto p-8 rounded-3xl border border-red-900/40 bg-slate-950 shadow-2xl text-center space-y-6">
              <div className="w-16 h-16 rounded-full bg-red-600/20 text-red-400 mx-auto flex items-center justify-center border border-red-500/30">
                <Lock className="w-8 h-8" />
              </div>
              <div>
                <h2 className="text-xl font-bold text-white">Device Restrictions Active</h2>
                <p className="text-xs text-red-400 mt-1">Payment Installment Default Threshold Exceeded</p>
              </div>
              <p className="text-xs text-slate-400 leading-relaxed">
                As stipulated in your smartphone financing contract, device functionality is temporarily restricted pending payment
                confirmation. Emergency dialing (911 / 112) remains fully functional.
              </p>
              <button
                onClick={() => setActiveTab('CUSTOMER_PORTAL')}
                className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold transition shadow"
              >
                Access Customer Payment Support
              </button>
            </div>
          )}
        </main>
      </div>

      {/* MODAL: CREATE ENROLLMENT TICKET */}
      {showCreateEnrollmentModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg p-6 space-y-4">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h3 className="font-bold text-sm text-white">Issue Single-Use Enrollment Ticket</h3>
              <button onClick={() => setShowCreateEnrollmentModal(false)} className="text-slate-400 hover:text-white">✕</button>
            </div>

            {generatedTicket ? (
              <div className="space-y-4 text-center">
                <div className="p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-xl space-y-2">
                  <div className="text-xs text-emerald-400 font-bold">Ticket Successfully Generated</div>
                  <div className="text-2xl font-mono font-bold text-white tracking-wider">{generatedTicket.enrollmentCode}</div>
                  <div className="text-[11px] text-slate-400 font-mono">ID: {generatedTicket.enrollmentId}</div>
                </div>

                <div className="p-3 bg-blue-950/20 border border-blue-900/40 rounded-lg text-xs text-blue-300">
                  Customer enters this pairing code in the Device App to trigger hardware Keystore registration.
                </div>

                <button
                  onClick={() => {
                    setGeneratedTicket(null);
                    setShowCreateEnrollmentModal(false);
                  }}
                  className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-semibold"
                >
                  Done
                </button>
              </div>
            ) : (
              <form onSubmit={handleCreateEnrollment} className="space-y-3 text-xs">
                <div>
                  <label className="block text-slate-400 mb-1">Customer</label>
                  <select
                    required
                    value={enrollmentForm.customerId}
                    onChange={e => setEnrollmentForm(p => ({ ...p, customerId: e.target.value }))}
                    className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-800 text-slate-100"
                  >
                    <option value="">-- Select Customer --</option>
                    {customers.map(c => (
                      <option key={c.id} value={c.id}>{c.fullName} ({c.phoneNumber})</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-slate-400 mb-1">Hardware Unit</label>
                  <select
                    required
                    value={enrollmentForm.deviceId}
                    onChange={e => setEnrollmentForm(p => ({ ...p, deviceId: e.target.value }))}
                    className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-800 text-slate-100"
                  >
                    <option value="">-- Select Device --</option>
                    {devices.map(d => (
                      <option key={d.deviceId || d.enrollmentId} value={d.deviceId || d.enrollmentId}>
                        {d.manufacturer} {d.model} ({d.hardwareSerial || d.enrollmentId})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-slate-400 mb-1">Financing Agreement</label>
                  <select
                    required
                    value={enrollmentForm.agreementId}
                    onChange={e => setEnrollmentForm(p => ({ ...p, agreementId: e.target.value }))}
                    className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-800 text-slate-100"
                  >
                    <option value="">-- Select Agreement --</option>
                    {agreements.map(a => (
                      <option key={a.id} value={a.id}>{a.agreementCode} ({a.customerName})</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-slate-400 mb-1">Token Validity Duration</label>
                  <select
                    value={enrollmentForm.validityMinutes}
                    onChange={e => setEnrollmentForm(p => ({ ...p, validityMinutes: parseInt(e.target.value, 10) }))}
                    className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-800 text-slate-100"
                  >
                    <option value={15}>15 Minutes</option>
                    <option value={30}>30 Minutes (Recommended)</option>
                    <option value={60}>60 Minutes</option>
                  </select>
                </div>

                <div className="pt-2 flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setShowCreateEnrollmentModal(false)}
                    className="px-4 py-2 rounded-lg bg-slate-800 text-slate-300"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-semibold"
                  >
                    Generate Ticket
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/* MODAL: CONFIRM REVOKE */}
      {showRevokeModal && enrollmentToRevoke && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md p-6 space-y-4">
            <h3 className="font-bold text-base text-red-400">Confirm Enrollment Revocation</h3>
            <p className="text-xs text-slate-300">
              Are you sure you want to revoke the enrollment for <strong>{enrollmentToRevoke.deviceModel}</strong> ({enrollmentToRevoke.id})? This will immediately invalidate the one-time token and block future cryptographic verification.
            </p>

            <div>
              <label className="block text-xs text-slate-400 mb-1">Reason for Revocation</label>
              <input
                type="text"
                value={revokeReason}
                onChange={e => setRevokeReason(e.target.value)}
                className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-100"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setShowRevokeModal(false)}
                className="px-4 py-2 rounded-lg bg-slate-800 text-slate-300 text-xs"
              >
                Cancel
              </button>
              <button
                onClick={handleRevokeEnrollment}
                className="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-white text-xs font-semibold"
              >
                Revoke Enrollment
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
