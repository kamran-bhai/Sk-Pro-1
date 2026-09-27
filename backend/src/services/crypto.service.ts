import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import { SERVER_AUTHORITY_KEYS } from '../config/security.js';

let monotonicCounter = 1000n;

export class CryptoService {
  /**
   * Hashes a password using bcrypt with salt factor 12.
   */
  static async hashPassword(password: string): Promise<string> {
    return bcrypt.hash(password, 12);
  }

  /**
   * Verifies a password against a hash.
   */
  static async verifyPassword(password: string, hash: string): Promise<boolean> {
    return bcrypt.compare(password, hash);
  }

  /**
   * Generates a unique 64-character hexadecimal nonce for replay attack prevention.
   */
  static generateNonce(): string {
    return crypto.randomBytes(32).toString('hex');
  }

  /**
   * Next atomic monotonic sequence number.
   */
  static getNextSequence(): bigint {
    monotonicCounter += 1n;
    return monotonicCounter;
  }

  /**
   * Signs command data using the server authority ECDSA private key.
   */
  static signCommand(payload: {
    commandId: string;
    enrollmentId: string;
    commandType: string;
    nonce: string;
    sequence: string;
    expiresAt: number;
  }): string {
    const canonicalString = `${payload.commandId}|${payload.enrollmentId}|${payload.commandType}|${payload.nonce}|${payload.sequence}|${payload.expiresAt}`;
    const signer = crypto.createSign('SHA256');
    signer.update(canonicalString);
    signer.end();
    return signer.sign(SERVER_AUTHORITY_KEYS.privateKeyPem, 'base64');
  }

  /**
   * Verifies an ECDSA signature from an Android device using the device's public key PEM.
   */
  static verifyDeviceSignature(data: string, signatureBase64: string, devicePublicKeyPem: string): boolean {
    try {
      const verifier = crypto.createVerify('SHA256');
      verifier.update(data);
      verifier.end();
      return verifier.verify(devicePublicKeyPem, signatureBase64, 'base64');
    } catch (e) {
      return false;
    }
  }

  /**
   * Hashes a token using SHA-256 for secure DB lookup.
   */
  static hashToken(token: string): string {
    return crypto.createHash('sha256').update(token).digest('hex');
  }
}
