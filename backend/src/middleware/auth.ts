import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { SECURITY_CONFIG } from '../config/security.js';

export interface AuthenticatedUser {
  userId: string;
  email: string;
  role: 'ADMIN' | 'SUPPORT' | 'CUSTOMER';
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthenticatedUser;
    }
  }
}

export function authenticateToken(req: Request, res: Response, next: NextFunction): void {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({
      success: false,
      error: 'UNAUTHORIZED',
      message: 'Missing or malformed Authorization header with Bearer token'
    });
    return;
  }

  const token = authHeader.split(' ')[1];

  try {
    const decoded = jwt.verify(token, SECURITY_CONFIG.JWT_ACCESS_SECRET) as AuthenticatedUser;
    req.user = decoded;
    next();
  } catch (error: any) {
    if (error.name === 'TokenExpiredError') {
      res.status(401).json({
        success: false,
        error: 'TOKEN_EXPIRED',
        message: 'Access token has expired. Request a new token using /auth/refresh'
      });
      return;
    }

    res.status(403).json({
      success: false,
      error: 'FORBIDDEN',
      message: 'Invalid access token signature'
    });
  }
}

export function requireRole(...allowedRoles: Array<'ADMIN' | 'SUPPORT' | 'CUSTOMER'>) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!req.user) {
      res.status(401).json({ success: false, error: 'UNAUTHORIZED', message: 'Authentication required' });
      return;
    }

    if (!allowedRoles.includes(req.user.role)) {
      res.status(403).json({
        success: false,
        error: 'INSUFFICIENT_PERMISSIONS',
        message: `Forbidden: This resource requires one of the following roles: [${allowedRoles.join(', ')}]`
      });
      return;
    }

    next();
  };
}
