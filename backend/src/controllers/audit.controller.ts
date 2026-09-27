import { Request, Response } from 'express';
import { AuditService } from '../services/audit.service.js';

export class AuditController {
  static async listLogs(req: Request, res: Response): Promise<void> {
    const limit = Number(req.query.limit) || 50;
    const logs = AuditService.getRecentLogs(limit);
    res.json({ success: true, data: logs });
  }
}
