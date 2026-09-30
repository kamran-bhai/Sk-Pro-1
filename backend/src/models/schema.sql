-- PROTECT YOUR FINANCED DEVICES - PostgreSQL Schema DDL
-- Production-ready schema with strict constraints, indexes, foreign keys, and audit tracking.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Users / Administrators & Role Authorization
CREATE TABLE IF NOT EXISTS admins (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ADMIN', 'SUPPORT', 'CUSTOMER')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_admins_email ON admins(email);

-- 2. Cryptographic Refresh Token Store (Rotation & Family Revocation)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    token_family UUID NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens(token_family);

-- 3. Customers
CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(30) UNIQUE NOT NULL,
    email VARCHAR(255),
    address TEXT,
    national_identity_hash VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Hardware Inventory
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    manufacturer VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    brand VARCHAR(100),
    hardware_serial VARCHAR(100),
    initial_carrier VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Financing Agreements
CREATE TABLE IF NOT EXISTS agreements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agreement_code VARCHAR(50) UNIQUE NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE RESTRICT,
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount > 0),
    down_payment NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    remaining_amount NUMERIC(12, 2) NOT NULL,
    installment_amount NUMERIC(12, 2) NOT NULL,
    number_of_installments INT NOT NULL CHECK (number_of_installments > 0),
    paid_installments INT NOT NULL DEFAULT 0,
    remaining_installments INT NOT NULL,
    start_date DATE NOT NULL,
    next_due_date DATE NOT NULL,
    grace_period_days INT NOT NULL DEFAULT 5,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' 
        CHECK (status IN ('ACTIVE', 'PAID', 'OVERDUE', 'COMPLETED', 'CANCELLED', 'DEFAULTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_agreements_status ON agreements(status);

-- 6. Cryptographic Device Enrollments
CREATE TABLE IF NOT EXISTS device_enrollments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    agreement_id UUID NOT NULL REFERENCES agreements(id) ON DELETE RESTRICT,
    enrollment_token_hash VARCHAR(128) NOT NULL,
    device_public_key_pem TEXT,
    device_key_algorithm VARCHAR(30) DEFAULT 'EC_SECP256R1',
    android_version VARCHAR(30),
    sdk_int INT,
    app_version VARCHAR(30),
    management_mode VARCHAR(50) NOT NULL 
        CHECK (management_mode IN ('DEVICE_OWNER', 'PROFILE_OWNER', 'UNMANAGED_USER_APP')),
    enrollment_status VARCHAR(40) NOT NULL DEFAULT 'PENDING'
        CHECK (enrollment_status IN ('PENDING', 'ACTIVE', 'LOCKED', 'OVERDUE', 'SUSPENDED', 'COMPLETED', 'UNENROLLED')),
    fcm_registration_token TEXT,
    last_heartbeat_at TIMESTAMPTZ,
    last_known_battery INT,
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    sim_carrier VARCHAR(100),
    usb_debugging_active BOOLEAN NOT NULL DEFAULT FALSE,
    enrolled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_enrollment_device ON device_enrollments(device_id) WHERE enrollment_status != 'UNENROLLED';

-- 7. Installment Schedules
CREATE TABLE IF NOT EXISTS installments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agreement_id UUID NOT NULL REFERENCES agreements(id) ON DELETE CASCADE,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    penalty_fee NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'OVERDUE', 'WAIVED')),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. Payment Ledger
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    agreement_id UUID NOT NULL REFERENCES agreements(id) ON DELETE RESTRICT,
    installment_id UUID REFERENCES installments(id) ON DELETE SET NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    payment_method VARCHAR(50) NOT NULL CHECK (payment_method IN ('BANK_TRANSFER', 'CASH', 'POS', 'MOBILE_MONEY')),
    transaction_reference VARCHAR(100) UNIQUE NOT NULL,
    received_by UUID NOT NULL REFERENCES admins(id),
    receipt_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 9. Device Commands (Nonce Guarded, Replay Protected, Signed)
CREATE TABLE IF NOT EXISTS device_commands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    enrollment_id UUID NOT NULL REFERENCES device_enrollments(id) ON DELETE CASCADE,
    command_type VARCHAR(50) NOT NULL 
        CHECK (command_type IN ('LOCK_DEVICE', 'UNLOCK_DEVICE', 'STATUS_REQUEST', 'LOCATION_REQUEST', 'NOTIFY_USER', 'REFRESH_POLICIES')),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    nonce VARCHAR(64) NOT NULL UNIQUE,
    monotonic_sequence BIGINT NOT NULL,
    server_signature TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'DISPATCHED', 'ACKNOWLEDGED', 'EXECUTED', 'FAILED', 'EXPIRED')),
    issued_by UUID NOT NULL REFERENCES admins(id),
    acknowledged_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_commands_status ON device_commands(enrollment_id, status);

-- 10. Device Events (Telemetry & Audit)
CREATE TABLE IF NOT EXISTS device_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    enrollment_id UUID NOT NULL REFERENCES device_enrollments(id) ON DELETE CASCADE,
    event_type VARCHAR(60) NOT NULL,
    payload JSONB NOT NULL,
    client_timestamp TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 11. Alerts
CREATE TABLE IF NOT EXISTS alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    enrollment_id UUID REFERENCES device_enrollments(id) ON DELETE CASCADE,
    agreement_id UUID REFERENCES agreements(id) ON DELETE CASCADE,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    alert_type VARCHAR(60) NOT NULL,
    title VARCHAR(150) NOT NULL,
    details TEXT NOT NULL,
    is_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_by UUID REFERENCES admins(id),
    acknowledged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 12. Immutable System Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    actor_email VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    entity_name VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    changes JSONB,
    ip_address VARCHAR(50),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_logs(created_at DESC);


-- 13. One-Key / One-Device Control Licenses
-- A control key is a server-issued credential for exactly one enrolled device.
-- Store only a hash of the key; never store the plaintext key.
CREATE TABLE IF NOT EXISTS device_control_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key_hash VARCHAR(128) UNIQUE NOT NULL,
    key_last4 VARCHAR(4) NOT NULL,
    issued_by UUID REFERENCES admins(id) ON DELETE SET NULL,
    retailer_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    device_id UUID UNIQUE REFERENCES devices(id) ON DELETE RESTRICT,
    enrollment_id UUID UNIQUE REFERENCES device_enrollments(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED'
        CHECK (status IN ('ISSUED', 'ACTIVATED', 'SUSPENDED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_device_control_keys_status ON device_control_keys(status);
CREATE INDEX IF NOT EXISTS idx_device_control_keys_retailer ON device_control_keys(retailer_id);

-- 14. Retailer tenancy / resale accounts
CREATE TABLE IF NOT EXISTS retailer_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_id UUID UNIQUE REFERENCES admins(id) ON DELETE CASCADE,
    business_name VARCHAR(200) NOT NULL,
    phone_number VARCHAR(30),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Bind an activated control key to one enrollment only.
CREATE UNIQUE INDEX IF NOT EXISTS uq_control_key_active_enrollment
    ON device_control_keys(enrollment_id)
    WHERE status = 'ACTIVATED';
