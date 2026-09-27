import { Request, Response, NextFunction } from 'express';

interface RateLimitEntry {
  count: number;
  resetTime: number;
}

const rateLimitBuckets = new Map<string, RateLimitEntry>();

export function createRateLimiter(options: { maxRequests: number; windowMs: number }) {
  return (req: Request, res: Response, next: NextFunction): void => {
    const ip = req.ip || req.socket.remoteAddress || 'unknown';
    const key = `${req.baseUrl || req.path}:${ip}`;
    const now = Date.now();

    const record = rateLimitBuckets.get(key);

    if (!record || now > record.resetTime) {
      rateLimitBuckets.set(key, {
        count: 1,
        resetTime: now + options.windowMs
      });
      res.setHeader('X-RateLimit-Limit', options.maxRequests);
      res.setHeader('X-RateLimit-Remaining', options.maxRequests - 1);
      return next();
    }

    if (record.count >= options.maxRequests) {
      const retryAfterSec = Math.ceil((record.resetTime - now) / 1000);
      res.setHeader('Retry-After', retryAfterSec);
      res.status(429).json({
        success: false,
        error: 'RATE_LIMIT_EXCEEDED',
        message: `Too many requests. Please wait ${retryAfterSec} seconds before retrying.`
      });
      return;
    }

    record.count += 1;
    res.setHeader('X-RateLimit-Limit', options.maxRequests);
    res.setHeader('X-RateLimit-Remaining', options.maxRequests - record.count);
    next();
  };
}
