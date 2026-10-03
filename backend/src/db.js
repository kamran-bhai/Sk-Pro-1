const { Pool } = require("pg");

const databaseUrl = String(process.env.DATABASE_URL || "").trim();
const pool = databaseUrl ? new Pool({
  connectionString: databaseUrl,
  ssl: process.env.PGSSLMODE === "disable" ? false : { rejectUnauthorized: false }
}) : null;

async function initDb() {
  if (!pool) {
    if (process.env.NODE_ENV === "production" || process.env.RENDER) throw new Error("DATABASE_URL is required on the production server.");
    console.warn("DATABASE_URL is not configured; using in-memory storage for local development.");
    return;
  }
  await pool.query(`
    CREATE TABLE IF NOT EXISTS devices (
      id TEXT PRIMARY KEY, device_id TEXT UNIQUE NOT NULL, imei TEXT NOT NULL,
      model TEXT NOT NULL DEFAULT '', customer_name TEXT NOT NULL DEFAULT '',
      customer_phone TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'ACTIVE',
      control_key TEXT UNIQUE NOT NULL, created_at TIMESTAMPTZ NOT NULL, last_seen_at TIMESTAMPTZ,
      agent_version TEXT NOT NULL DEFAULT '', agent_status TEXT NOT NULL DEFAULT 'UNKNOWN'
    );
    ALTER TABLE devices ADD COLUMN IF NOT EXISTS agent_version TEXT NOT NULL DEFAULT '';
    ALTER TABLE devices ADD COLUMN IF NOT EXISTS agent_status TEXT NOT NULL DEFAULT 'UNKNOWN';
    CREATE TABLE IF NOT EXISTS commands (
      id TEXT PRIMARY KEY, device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
      command TEXT NOT NULL, payload JSONB NOT NULL DEFAULT '{}'::jsonb, status TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, result TEXT,
      dedupe_key TEXT
    );
    ALTER TABLE commands ADD COLUMN IF NOT EXISTS dedupe_key TEXT;
    CREATE INDEX IF NOT EXISTS idx_commands_queue ON commands (device_id, status, created_at);
    CREATE UNIQUE INDEX IF NOT EXISTS idx_commands_dedupe_key ON commands (dedupe_key) WHERE dedupe_key IS NOT NULL;
    CREATE TABLE IF NOT EXISTS customers (
      id TEXT PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL
    );
    CREATE TABLE IF NOT EXISTS agreements (
      id TEXT PRIMARY KEY, customer_id TEXT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
      device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE, total_amount NUMERIC NOT NULL,
      down_payment NUMERIC NOT NULL DEFAULT 0, installment_amount NUMERIC NOT NULL,
      number_of_installments INTEGER NOT NULL, paid_installments INTEGER NOT NULL DEFAULT 0,
      remaining_amount NUMERIC NOT NULL, next_due_date TEXT NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL
    );
    CREATE TABLE IF NOT EXISTS enach (
      id TEXT PRIMARY KEY, customer_id TEXT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
      agreement_id TEXT NOT NULL REFERENCES agreements(id) ON DELETE CASCADE,
      mandate_ref TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_agreements_customer ON agreements(customer_id);
    CREATE INDEX IF NOT EXISTS idx_enach_agreement ON enach(agreement_id);
  `);
  console.log("Postgres persistence initialized.");
}
module.exports = { pool, initDb, isPersistent: () => Boolean(pool) };
