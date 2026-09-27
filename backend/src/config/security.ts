import crypto from 'crypto';

// In production, these keys are provisioned via environment variables or secret vaults
export const SECURITY_CONFIG = {
  JWT_ACCESS_SECRET: process.env.JWT_ACCESS_SECRET || 'financed_device_jwt_access_secret_p256_super_secure_key_2026',
  JWT_REFRESH_SECRET: process.env.JWT_REFRESH_SECRET || 'financed_device_jwt_refresh_secret_p256_super_secure_key_2026',
  ACCESS_TOKEN_EXPIRY: '15m',
  REFRESH_TOKEN_EXPIRY_DAYS: 7,
  
  // Rate limiting thresholds
  RATE_LIMIT: {
    AUTH_MAX_REQUESTS: 10,
    AUTH_WINDOW_MS: 60 * 1000, // 10 attempts per minute
    API_MAX_REQUESTS: 120,
    API_WINDOW_MS: 60 * 1000,  // 120 requests per minute
    HEARTBEAT_MAX_REQUESTS: 60,
    HEARTBEAT_WINDOW_MS: 60 * 1000
  },

  // Device status & heartbeat policy
  HEARTBEAT_TIMEOUT_MS: 15 * 60 * 1000, // 15 minutes default before marked OFFLINE
  HEARTBEAT_MAX_SKEW_MS: 5 * 60 * 1000 // 5 minutes max clock drift allowed
};

/**
 * Server-Side ECDSA Authority Keypair (secp256r1)
 * Used to cryptographically sign all outbound commands dispatched to Android devices.
 * Android device verifies the signature using the backend public key.
 */
const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', {
  namedCurve: 'prime256v1', // NIST P-256 (secp256r1)
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
});

export const SERVER_AUTHORITY_KEYS = {
  privateKeyPem: process.env.SERVER_PRIVATE_KEY || privateKey,
  publicKeyPem: process.env.SERVER_PUBLIC_KEY || publicKey
};
