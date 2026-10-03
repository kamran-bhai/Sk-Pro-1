const { Pool } = require("pg");

const databaseUrl = String(process.env.DATABASE_URL || "").trim();
const pool = databaseUrl ? new Pool({
  connectionString: databaseUrl,
  ssl: process.env.PGSSLMODE === "disable" ? false : { rejectUnauthorized: false }
}) : null;

async function initDb() {
  if (!pool) {
    if (process.env.NODE_ENV === "production" || process.env.RENDER) {
      throw new Error("DATABASE_URL is required on the production server.");
    }
    console.warn("DATABASE_URL is not configured; using in-memory storage for local development.");
    return;
  }
  await pool.query(`
    CREATE TABLE IF NOT EXISTS devices (
      id TEXT PRIMARY KEY,
      device_id TEXT UNIQUE NOT NULL,
      imei TEXT NOT NULL,
      model TEXT NOT NULL DEFAULT '',
      customer_name TEXT NOT NULL DEFAULT '',
      customer_phone TEXT NOT NULL DEFAULT '',
      status TEXT NOT NULL DEFAULT 'ACTIVE',
      control_key TEXT UNIQUE NOT NULL,
      created_at TIMESTAMPTZ NOT NULL,
      last_seen_at TIMESTAMPTZ
    );
    CREATE TABLE IF NOT EXISTS commands (
      id TEXT PRIMARY KEY,
      device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
      command TEXT NOT NULL,
      payload JSONB NOT NULL DEFAULT '{}'::jsonb,
      status TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL,
      result TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_commands_queue
      ON commands (device_id, status, created_at);
  `);
  console.log("Postgres persistence initialized.");
}

module.exports = { pool, initDb, isPersistent: () => Boolean(pool) };
