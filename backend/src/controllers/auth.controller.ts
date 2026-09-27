import { Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import { db, RefreshTokenRecord } from '../services/store.js';
import { CryptoService } from '../services/crypto.service.js';
import { AuditService } from '../services/audit.service.js';
import { SECURITY_CONFIG } from '../config/security.js';

export class AuthController {
  /**
   * POST /api/v1/auth/login
   */
  static async login(req: Request, res: Response): Promise<void> {
    const { email, password } = req.body;

    const user = db.users.find(u => u.email.toLowerCase() === email.toLowerCase());
    if (!user || !user.isActive) {
      res.status(401).json({
        success: false,
        error: 'INVALID_CREDENTIALS',
        message: 'Invalid email or password'
      });
      return;
    }

    const isMatch = await CryptoService.verifyPassword(password, user.passwordHash);
    if (!isMatch) {
      res.status(401).json({
        success: false,
        error: 'INVALID_CREDENTIALS',
        message: 'Invalid email or password'
      });
      return;
    }

    // Generate short-lived Access Token (15 min)
    const accessToken = jwt.sign(
      { userId: user.id, email: user.email, role: user.role },
      SECURITY_CONFIG.JWT_ACCESS_SECRET,
      { expiresIn: '15m' }
    );

    // Generate cryptographically random Refresh Token with Family Tracking
    const rawRefreshToken = crypto.randomBytes(40).toString('hex');
    const tokenHash = CryptoService.hashToken(rawRefreshToken);
    const tokenFamily = crypto.randomUUID();

    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + SECURITY_CONFIG.REFRESH_TOKEN_EXPIRY_DAYS);

    const refreshRecord: RefreshTokenRecord = {
      id: crypto.randomUUID(),
      userId: user.id,
      tokenHash,
      tokenFamily,
      isRevoked: false,
      expiresAt: expiresAt.toISOString()
    };
    db.refreshTokens.push(refreshRecord);

    AuditService.log({
      actorId: user.id,
      actorEmail: user.email,
      action: 'ADMIN_LOGIN_SUCCESS',
      entityName: 'admins',
      entityId: user.id,
      ipAddress: req.ip
    });

    res.json({
      success: true,
      data: {
        accessToken,
        refreshToken: rawRefreshToken,
        expiresInSeconds: 900,
        user: {
          id: user.id,
          email: user.email,
          fullName: user.fullName,
          role: user.role
        }
      }
    });
  }

  /**
   * POST /api/v1/auth/refresh
   * Implements strict refresh token rotation with token family revocation upon reuse.
   */
  static async refresh(req: Request, res: Response): Promise<void> {
    const { refreshToken } = req.body;
    if (!refreshToken) {
      res.status(400).json({ success: false, error: 'BAD_REQUEST', message: 'refreshToken is required' });
      return;
    }

    const tokenHash = CryptoService.hashToken(refreshToken);
    const record = db.refreshTokens.find(r => r.tokenHash === tokenHash);

    if (!record) {
      res.status(401).json({
        success: false,
        error: 'INVALID_REFRESH_TOKEN',
        message: 'Refresh token not recognized'
      });
      return;
    }

    // Token Reuse Detection: If this token was already revoked, someone is replaying it!
    if (record.isRevoked) {
      // Revoke all tokens in this family immediately!
      db.refreshTokens.forEach(t => {
        if (t.tokenFamily === record.tokenFamily) {
          t.isRevoked = true;
        }
      });

      AuditService.log({
        actorId: record.userId,
        action: 'REFRESH_TOKEN_REUSE_DETECTED',
        entityName: 'refresh_tokens',
        entityId: record.tokenFamily,
        ipAddress: req.ip
      });

      res.status(403).json({
        success: false,
        error: 'TOKEN_COMPROMISED',
        message: 'Security violation: Refresh token reuse detected. Family revoked.'
      });
      return;
    }

    // Check expiration
    if (new Date() > new Date(record.expiresAt)) {
      record.isRevoked = true;
      res.status(401).json({ success: false, error: 'TOKEN_EXPIRED', message: 'Refresh token expired' });
      return;
    }

    // Invalidate old token (Single-use rotation)
    record.isRevoked = true;

    const user = db.users.find(u => u.id === record.userId);
    if (!user || !user.isActive) {
      res.status(401).json({ success: false, error: 'USER_INACTIVE', message: 'User account disabled' });
      return;
    }

    // Issue new Access Token and rotated Refresh Token in same family
    const newAccessToken = jwt.sign(
      { userId: user.id, email: user.email, role: user.role },
      SECURITY_CONFIG.JWT_ACCESS_SECRET,
      { expiresIn: '15m' }
    );

    const newRawRefreshToken = crypto.randomBytes(40).toString('hex');
    const newTokenHash = CryptoService.hashToken(newRawRefreshToken);

    const newExpiresAt = new Date();
    newExpiresAt.setDate(newExpiresAt.getDate() + SECURITY_CONFIG.REFRESH_TOKEN_EXPIRY_DAYS);

    db.refreshTokens.push({
      id: crypto.randomUUID(),
      userId: user.id,
      tokenHash: newTokenHash,
      tokenFamily: record.tokenFamily,
      isRevoked: false,
      expiresAt: newExpiresAt.toISOString()
    });

    res.json({
      success: true,
      data: {
        accessToken: newAccessToken,
        refreshToken: newRawRefreshToken,
        expiresInSeconds: 900
      }
    });
  }

  /**
   * GET /api/v1/auth/me
   */
  static async getProfile(req: Request, res: Response): Promise<void> {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'UNAUTHORIZED' });
      return;
    }

    const user = db.users.find(u => u.id === req.user?.userId);
    if (!user) {
      res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'User not found' });
      return;
    }

    res.json({
      success: true,
      data: {
        id: user.id,
        email: user.email,
        fullName: user.fullName,
        role: user.role,
        permissions: {
          canLockDevices: user.role === 'ADMIN',
          canUnlockDevices: user.role === 'ADMIN',
          canRecordPayments: ['ADMIN', 'SUPPORT'].includes(user.role),
          canViewAuditLogs: user.role === 'ADMIN'
        }
      }
    });
  }
}
