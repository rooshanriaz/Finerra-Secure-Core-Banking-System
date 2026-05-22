/**
 * Maps audit-service API fields (recordedAt, transactionType, initiatedBy, integrityVerified, fabricTxId)
 * to stable display fields. Handles Jackson LocalDateTime as ISO string or [y,m,d,h,m,s,nano] array.
 */

export function parseAuditTimestamp(value) {
  if (value == null) return null;
  if (typeof value === 'string') {
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  if (Array.isArray(value) && value.length >= 3) {
    const [y, mo, day, h = 0, min = 0, s = 0, nano = 0] = value;
    return new Date(y, (mo || 1) - 1, day || 1, h, min, s, Math.floor(Number(nano) / 1e6));
  }
  if (typeof value === 'number') {
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  return null;
}

export function formatAuditDate(value) {
  const d = parseAuditTimestamp(value);
  if (!d) return '—';
  return d.toLocaleString();
}

/** True when the record is anchored or confirmed on the ledger (not LOCAL_ONLY / FAILED without tx). */
export function isAuditOnChain(raw) {
  if (!raw || typeof raw !== 'object') return false;
  if (raw.fabricTxId || raw.blockchainTxId) return true;
  const st = String(raw.blockchainStatus || '').toUpperCase();
  return st === 'CONFIRMED' || st === 'SUBMITTED';
}

export function normalizeAuditRecord(raw) {
  if (!raw || typeof raw !== 'object') return raw;
  const displayTime = raw.recordedAt ?? raw.createdAt ?? raw.timestamp;
  const displayAction = raw.transactionType || raw.action || raw.type || '—';
  const displayPerformedBy = raw.initiatedBy ?? raw.performedBy ?? null;
  const isVerified = Boolean(raw.integrityVerified ?? raw.verified);
  const isOnChain = isAuditOnChain(raw);

  return {
    ...raw,
    displayTime,
    displayAction,
    displayPerformedBy,
    isVerified,
    isOnChain,
  };
}

export function normalizeAuditRecords(records) {
  if (!Array.isArray(records)) return [];
  return records.map(normalizeAuditRecord);
}
