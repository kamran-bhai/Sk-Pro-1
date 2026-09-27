import crypto from 'crypto';
import { db, AuditRecord } from './store.js';

export class AuditService {
  static log(entry: {
    actorId?: string;
    actorEmail?: string;
    action: string;
    entityName: string;
    entityId: string;
    changes?: any;
    ipAddress?: string;
    userAgent?: string;
  }): AuditRecord {
    const record: AuditRecord = {
      id: crypto.randomUUID(),
      actorId: entry.actorId,
      actorEmail: entry.actorEmail || 'system',
      action: entry.action,
      entityName: entry.entityName,
      entityId: entry.entityId,
      changes: entry.changes,
      ipAddress: entry.ipAddress,
      userAgent: entry.userAgent,
      createdAt: new Date().toISOString()
    };
    db.auditLogs.unshift(record);
    return record;
  }

  static getRecentLogs(limit: number = 50): AuditRecord[] {
    return db.auditLogs.slice(0, limit);
  }
}
