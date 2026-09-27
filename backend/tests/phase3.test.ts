/**
 * Phase 3 Automated Test Suite
 * Tests Customer, Device, Financing Agreement, Installment Calculations, Payments, and Authorization.
 */

import { db, generateInstallmentSchedule } from '../src/services/store.js';
import { CustomerController } from '../src/controllers/customer.controller.js';
import { DeviceController } from '../src/controllers/device.controller.js';
import { FinanceController } from '../src/controllers/finance.controller.js';
import { CryptoService } from '../src/services/crypto.service.js';

interface TestResult {
  name: string;
  passed: boolean;
  error?: string;
  durationMs: number;
}

const results: TestResult[] = [];

async function runTest(name: string, fn: () => Promise<void> | void) {
  const start = Date.now();
  try {
    await fn();
    results.push({ name, passed: true, durationMs: Date.now() - start });
    console.log(`[PASS] ${name}`);
  } catch (err: any) {
    results.push({ name, passed: false, error: err.message, durationMs: Date.now() - start });
    console.error(`[FAIL] ${name}: ${err.message}`);
  }
}

function mockResponse() {
  const res: any = {
    statusCode: 200,
    body: null,
    status(code: number) {
      this.statusCode = code;
      return this;
    },
    json(data: any) {
      this.body = data;
      return this;
    }
  };
  return res;
}

export async function runPhase3Tests(): Promise<{ passed: boolean; results: TestResult[] }> {
  results.length = 0;
  db.resetToBaseline();
  console.log('--- STARTING PHASE 3 AUTOMATED TESTS ---');

  // Test 1: Customer Creation & Validation
  await runTest('1. Customer Creation & Validation', async () => {
    const req: any = {
      body: {
        fullName: 'Test Customer A',
        phoneNumber: '+15559998877',
        email: 'test.a@example.com',
        address: '100 Silicon Way'
      },
      user: { userId: 'usr-admin-01', role: 'ADMIN' },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await CustomerController.createCustomer(req, res);

    if (res.statusCode !== 201 || !res.body?.success) {
      throw new Error(`Failed to create customer: ${res.statusCode} ${JSON.stringify(res.body)}`);
    }
    const customerId = res.body.data.id;
    const created = db.customers.find(c => c.id === customerId);
    if (!created || created.fullName !== 'Test Customer A') {
      throw new Error('Created customer not found in database');
    }
  });

  // Test 2: Customer Duplicate Phone Prevention
  await runTest('2. Customer Duplicate Phone Prevention', async () => {
    const req: any = {
      body: {
        fullName: 'Duplicate Customer',
        phoneNumber: '+15559998877' // Same as Test 1
      },
      user: { userId: 'usr-admin-01', role: 'ADMIN' },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await CustomerController.createCustomer(req, res);

    if (res.statusCode !== 409) {
      throw new Error(`Expected 409 Conflict, received ${res.statusCode}`);
    }
  });

  // Test 3: Device Addition & Registration
  await runTest('3. Device Inventory Registration', async () => {
    const req: any = {
      body: {
        manufacturer: 'Motorola',
        model: 'Edge 50 Ultra',
        brand: 'Motorola',
        hardwareSerial: `MOT-TEST-${Date.now()}`,
        initialCarrier: 'Unlocked'
      },
      user: { userId: 'usr-admin-01', role: 'ADMIN' },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await DeviceController.registerDeviceHardware(req, res);

    if (res.statusCode !== 201 || !res.body?.success) {
      throw new Error(`Failed to register device hardware: ${res.statusCode}`);
    }
  });

  // Test 4: Installment Calculation & Cent Rounding Precision
  await runTest('4. Installment Schedule Calculation & Exact Cent Precision', () => {
    const totalAmount = 1000.00;
    const downPayment = 100.00;
    const financed = 900.00;
    const numberOfInstallments = 7; // 900 / 7 = 128.5714...

    const schedule = generateInstallmentSchedule('agr-test-rounding', totalAmount, downPayment, numberOfInstallments, '2026-01-01');
    if (schedule.length !== 7) {
      throw new Error(`Expected 7 installments, got ${schedule.length}`);
    }

    const sumInstallments = schedule.reduce((sum, item) => +(sum + item.amount).toFixed(2), 0);
    if (sumInstallments !== financed) {
      throw new Error(`Installment sum mismatch: expected ${financed}, got ${sumInstallments}`);
    }
  });

  // Test 5: Agreement Creation & Device Binding
  let testAgreementId = '';
  await runTest('5. Financing Agreement Creation & Device Association', async () => {
    const customer = db.customers[0];
    const device = db.devices.find(d => !db.agreements.some(a => a.deviceId === d.id && (a.status === 'ACTIVE' || a.status === 'OVERDUE')));
    if (!device) throw new Error('No available device without an active agreement');

    const req: any = {
      body: {
        customerId: customer.id,
        deviceId: device.id,
        totalAmount: 1200.00,
        downPayment: 200.00,
        numberOfInstallments: 10,
        startDate: '2026-09-01'
      },
      user: { userId: 'usr-admin-01', role: 'ADMIN' },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await FinanceController.createAgreement(req, res);

    if (res.statusCode !== 201 || !res.body?.success) {
      throw new Error(`Failed to create agreement: ${res.statusCode} ${JSON.stringify(res.body)}`);
    }

    testAgreementId = res.body.data.agreement.id;
    const agr = db.agreements.find(a => a.id === testAgreementId);
    if (!agr || agr.remainingAmount !== 1000.00 || agr.numberOfInstallments !== 10) {
      throw new Error('Agreement values inconsistent in database');
    }
  });

  // Test 6: Duplicate Active Agreement on Same Device Rejection
  await runTest('6. Duplicate Active Financing on Same Device Rejected', async () => {
    const agr = db.agreements.find(a => a.id === testAgreementId);
    if (!agr) throw new Error('Test agreement not found');

    const req: any = {
      body: {
        customerId: agr.customerId,
        deviceId: agr.deviceId, // same device
        totalAmount: 800.00,
        downPayment: 100.00,
        numberOfInstallments: 6
      },
      user: { userId: 'usr-admin-01', role: 'ADMIN' },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await FinanceController.createAgreement(req, res);

    if (res.statusCode !== 409) {
      throw new Error(`Expected 409 Conflict for duplicate device financing, received ${res.statusCode}`);
    }
  });

  // Test 7: Payment Recording & Remaining Balance Recalculation
  await runTest('7. Payment Recording & Remaining Balance Recalculation', async () => {
    const agr = db.agreements.find(a => a.id === testAgreementId);
    if (!agr) throw new Error('Test agreement not found');

    const initialRemaining = agr.remainingAmount;
    const initialPaidCount = agr.paidInstallments;
    const paymentAmount = 100.00;

    const req: any = {
      body: {
        agreementId: testAgreementId,
        amount: paymentAmount,
        paymentMethod: 'BANK_TRANSFER',
        reference: 'TXN-TEST-100'
      },
      user: { userId: 'usr-admin-01', role: 'ADMIN' },
      ip: '127.0.0.1'
    };
    const res = mockResponse();
    await FinanceController.recordPayment(req, res);

    if (res.statusCode !== 201 || !res.body?.success) {
      throw new Error(`Payment failed: ${res.statusCode} ${JSON.stringify(res.body)}`);
    }

    const updatedAgr = db.agreements.find(a => a.id === testAgreementId);
    if (!updatedAgr) throw new Error('Agreement disappeared');

    if (updatedAgr.remainingAmount !== +(initialRemaining - paymentAmount).toFixed(2)) {
      throw new Error(`Remaining amount mismatch: expected ${initialRemaining - paymentAmount}, got ${updatedAgr.remainingAmount}`);
    }
    if (updatedAgr.paidInstallments !== initialPaidCount + 1) {
      throw new Error(`Paid installments count not incremented: expected ${initialPaidCount + 1}, got ${updatedAgr.paidInstallments}`);
    }
  });

  // Test 8: IDOR Security Access Control Check
  await runTest('8. IDOR Prevention: Customer Role Cannot Access Other Customer Records', async () => {
    // usr-cust-01 has customerId: cust-102
    const otherCustomerId = 'cust-101';

    const req: any = {
      params: { id: otherCustomerId },
      user: { userId: 'usr-cust-01', role: 'CUSTOMER' }
    };
    const res = mockResponse();
    await CustomerController.getCustomerDetails(req, res);

    if (res.statusCode !== 403) {
      throw new Error(`Expected 403 Forbidden for IDOR violation, received ${res.statusCode}`);
    }
  });

  const allPassed = results.every(r => r.passed);
  console.log(`--- PHASE 3 TESTS COMPLETE: ${results.filter(r => r.passed).length}/${results.length} PASSED ---`);
  return { passed: allPassed, results };
}

// If run directly from CLI
if (process.argv[1]?.endsWith('phase3.test.ts') || process.argv[1]?.endsWith('phase3.test.js')) {
  runPhase3Tests().then(({ passed }) => {
    process.exit(passed ? 0 : 1);
  });
}
