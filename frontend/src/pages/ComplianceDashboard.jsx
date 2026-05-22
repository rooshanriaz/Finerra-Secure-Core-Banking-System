import { useState, useEffect } from 'react';
import {
  FileText, ScrollText, Shield, AlertTriangle, BarChart3, Download, Loader2,
} from 'lucide-react';
import fraudService from '../services/fraudService';
import auditService from '../services/auditService';
import { useAuth } from '../context/AuthContext';
import { formatAuditDate } from '../lib/auditRecordUtils';
import { formatPkr } from '../lib/formatCurrency';
import { PageShell } from '../components/layout/PageShell';

const EXCLUDED_ALERT_TYPES = new Set(['LOAN_APPLICATION']);

export default function ComplianceDashboard() {
  const { user } = useAuth();

  const [fraudStats, setFraudStats] = useState({
    total: 0, open: 0, investigating: 0, resolved: 0, false_positive: 0, critical: 0, high: 0,
  });
  const [fraudAlerts, setFraudAlerts] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [exportingName, setExportingName] = useState(null);

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [statsRes, alertsRes, logsRes] = await Promise.all([
        fraudService.getStats().catch(() => ({ success: true, data: {} })),
        fraudService.getAlerts().catch(() => ({ success: true, data: [] })),
        auditService.getRecent().catch(() => ({ success: true, data: [] })),
      ]);

      const allAlerts = alertsRes.success ? (alertsRes.data || []) : [];
      const filteredAlerts = allAlerts.filter((a) => !EXCLUDED_ALERT_TYPES.has(String(a.transactionType || '').toUpperCase()));
      setFraudAlerts(filteredAlerts);
      if (statsRes.success) {
        setFraudStats({
          ...(statsRes.data || {}),
          total: filteredAlerts.length,
          open: filteredAlerts.filter((a) => String(a.status || '').toUpperCase() === 'OPEN').length,
          investigating: filteredAlerts.filter((a) => String(a.status || '').toUpperCase() === 'INVESTIGATING').length,
          resolved: filteredAlerts.filter((a) => String(a.status || '').toUpperCase() === 'RESOLVED').length,
          false_positive: filteredAlerts.filter((a) => String(a.status || '').toUpperCase() === 'FALSE_POSITIVE').length,
          critical: filteredAlerts.filter((a) => String(a.riskLevel || '').toUpperCase() === 'CRITICAL').length,
          high: filteredAlerts.filter((a) => String(a.riskLevel || '').toUpperCase() === 'HIGH').length,
        });
      }
      setAuditLogs(logsRes.success ? (logsRes.data || []) : []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load compliance data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const triggerDownload = (blob, filename) => {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  };

  const resolveQuarterRange = () => {
    const now = new Date();
    const quarterStartMonth = Math.floor(now.getMonth() / 3) * 3;
    const start = new Date(now.getFullYear(), quarterStartMonth, 1, 0, 0, 0, 0);
    const end = new Date();
    return {
      from: start.toISOString().slice(0, 19),
      to: end.toISOString().slice(0, 19),
    };
  };

  const getFilenameFromHeader = (contentDisposition) => {
    const raw = String(contentDisposition || '');
    const match = raw.match(/filename="?([^";]+)"?/i);
    return match ? match[1] : null;
  };

  const handleReportExport = async (name, format) => {
    try {
      setExportingName(name);
      const range = name === 'Quarterly Audit Trail Export' ? resolveQuarterRange() : {};
      const response = await auditService.exportReport({ format, ...range });
      const ext = format === 'pdf' ? 'pdf' : 'csv';
      const stamp = new Date().toISOString().slice(0, 10);
      const serverFilename = getFilenameFromHeader(response?.headers?.['content-disposition']);
      const fallbackName = `${name.toLowerCase().replace(/\s+/g, '-')}-${stamp}.${ext}`;
      triggerDownload(response.data, serverFilename || fallbackName);
    } catch (err) {
      window.alert(err?.response?.data?.message || 'Failed to export report');
    } finally {
      setExportingName(null);
    }
  };

  const pendingAlerts = fraudAlerts.filter((a) => a.riskLevel === 'CRITICAL' || a.riskLevel === 'HIGH');
  const anchoredOnChain = auditLogs.filter((l) => l.isOnChain);

  if (loading) {
    return (
      <div className="flex h-[min(28rem,60vh)] items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-sm">
        <Loader2 className="h-9 w-9 animate-spin text-finnera-600" />
        <span className="ml-3 text-sm font-medium text-gray-500">Loading compliance workspace…</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 rounded-2xl border border-red-100 bg-red-50/50 py-24">
        <AlertTriangle className="h-12 w-12 text-red-400" />
        <p className="font-medium text-red-700">{error}</p>
        <button type="button" onClick={fetchData} className="btn-primary rounded-xl px-6 py-2 text-sm">Retry</button>
      </div>
    );
  }

  return (
    <PageShell
      title="Compliance & regulatory desk"
      subtitle="Monitor audit trails, fraud posture, and regulatory exports. Aligned with PCI DSS, AML, and KYC programme expectations."
      meta={(
        <div className="text-right">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-400">Signed in as</p>
          <p className="text-sm font-semibold text-gray-900">{user?.name}</p>
          <p className="text-xs text-gray-500">{user?.role}</p>
        </div>
      )}
    >
      <div className="relative overflow-hidden rounded-2xl border border-finnera-100 bg-gradient-to-br from-finnera-50 via-white to-slate-50 p-5 shadow-sm">
        <div className="pointer-events-none absolute -right-8 top-0 h-40 w-40 rounded-full bg-finnera-200/30 blur-2xl" />
        <div className="relative flex gap-3">
          <Shield className="mt-0.5 h-5 w-5 shrink-0 text-finnera-600" />
          <div>
            <p className="text-sm font-semibold text-finnera-900">Compliance officer workspace</p>
            <p className="mt-1 text-xs leading-relaxed text-finnera-800/80">
              Exports and threshold reviews are written to the immutable audit service. Use this view for daily attestation and escalation.
            </p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <SummaryCard label="Total audit records" value={auditLogs.length} icon={ScrollText} />
        <SummaryCard label="Fraud alerts" value={fraudStats.total ?? fraudAlerts.length} icon={FileText} />
        <SummaryCard label="Pending fraud reviews" value={pendingAlerts.length} icon={AlertTriangle} highlight={pendingAlerts.length > 0} />
        <SummaryCard label="Anchored on blockchain" value={anchoredOnChain.length} icon={Shield} suffix="✓" />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
          <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold tracking-tight text-gray-900">Recent audit trail</h2>
              <p className="mt-0.5 text-sm text-gray-500">Latest compliance-relevant events from the audit service.</p>
            </div>
            <button
              type="button"
              onClick={fetchData}
              className="inline-flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition-colors hover:bg-gray-50"
            >
              <Download className="h-4 w-4" />
              Refresh
            </button>
          </div>
          <div className="max-h-[320px] space-y-2 overflow-y-auto">
            {auditLogs.length === 0 ? (
              <div className="rounded-xl border border-dashed border-gray-200 bg-gray-50/80 py-12 text-center text-sm text-gray-500">
                No audit records returned from the audit service yet.
              </div>
            ) : (
              auditLogs.slice(0, 15).map((log) => (
                <div
                  key={log.auditId}
                  className="flex items-start justify-between rounded-xl border border-gray-100 p-3 transition-colors hover:bg-gray-50/80"
                >
                  <div>
                    <p className="font-mono text-xs text-gray-400">{formatAuditDate(log.displayTime)}</p>
                    <p className="text-sm font-medium text-gray-900">{log.displayAction}</p>
                    <p className="text-xs text-gray-500">
                      {log.displayPerformedBy || '—'} &middot; Txn: {log.transactionId || '—'} &middot; Account:{' '}
                      {log.accountId != null ? log.accountId : '—'}
                    </p>
                  </div>
                  <div className="text-right">
                    <span className={`badge text-xs ${log.isOnChain ? 'badge-success' : 'badge-warning'}`}>
                      {log.isOnChain ? 'On chain' : log.blockchainStatus || 'Pending'}
                    </span>
                    {log.dataHash && (
                      <p className="mt-1 rounded bg-finnera-50 px-2 py-0.5 font-mono text-[11px] text-finnera-700">
                        {log.dataHash.substring(0, 16)}…
                      </p>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
          <div className="mb-4 flex items-center gap-2">
            <BarChart3 className="h-5 w-5 text-finnera-600" />
            <h2 className="text-sm font-semibold tracking-tight text-gray-900">Regulatory reports</h2>
          </div>

          <div className="space-y-2">
            {[
              { name: 'Daily Suspicious Activity Report', format: 'csv' },
              { name: 'Weekly High-Value Transactions', format: 'csv' },
              { name: 'Monthly KYC Exceptions', format: 'csv' },
              { name: 'Quarterly Audit Trail Export', format: 'pdf' },
            ].map(({ name, format }) => (
              <button
                key={name}
                type="button"
                onClick={() => handleReportExport(name, format)}
                disabled={exportingName !== null}
                className="flex w-full items-center justify-between rounded-xl border border-gray-100 bg-gray-50/80 px-3 py-2.5 text-left text-sm text-gray-800 transition-colors hover:bg-gray-100 disabled:opacity-60"
              >
                <span>{name}</span>
                {exportingName === name ? (
                  <Loader2 className="h-4 w-4 animate-spin text-gray-400" />
                ) : (
                  <Download className="h-4 w-4 text-gray-400" />
                )}
              </button>
            ))}
          </div>

          <p className="mt-4 border-t border-gray-100 pt-4 text-xs leading-relaxed text-gray-500">
            Reports use tamper-evident audit payloads. Export actions are themselves logged for non-repudiation.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
          <div className="mb-4 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-5 w-5 text-red-500" />
              <h2 className="text-sm font-semibold tracking-tight text-gray-900">High-priority fraud alerts</h2>
            </div>
            <p className="text-xs text-gray-400">{pendingAlerts.length} alerts</p>
          </div>

          <div className="max-h-[320px] space-y-2 overflow-y-auto">
            {pendingAlerts.length === 0 ? (
              <div className="rounded-xl border border-dashed border-gray-200 bg-gray-50 py-10 text-center text-sm text-gray-500">
                No critical or high-severity items in queue.
              </div>
            ) : (
              pendingAlerts.map((alert) => (
                <div key={alert.alertId} className="rounded-xl border border-red-100 bg-red-50/60 p-3">
                  <div className="mb-1.5 flex items-center justify-between">
                    <span className={`badge text-[11px] ${alert.riskLevel === 'CRITICAL' ? 'badge-danger' : 'bg-orange-100 text-orange-700'}`}>
                      {alert.riskLevel}
                    </span>
                    <span className="text-[11px] text-gray-400">
                      {alert.createdAt ? new Date(alert.createdAt).toLocaleString() : ''}
                    </span>
                  </div>
                  <p className="text-sm font-medium text-gray-900">
                    {alert.transactionType || 'Transaction'} — Score: {Number(alert.riskScore ?? 0).toFixed(2)}
                  </p>
                  <p className="text-xs text-gray-500">
                    Account: {alert.accountId} &middot; Amount: {formatPkr(alert.transactionAmount || 0)}
                  </p>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {(alert.riskFactors || []).slice(0, 3).map((f, i) => (
                      <span key={i} className="rounded bg-red-100 px-1.5 py-0.5 text-[10px] text-red-700">{f}</span>
                    ))}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
          <div className="mb-4 flex items-center gap-2">
            <ScrollText className="h-5 w-5 text-finnera-600" />
            <h2 className="text-sm font-semibold tracking-tight text-gray-900">Fraud statistics</h2>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <StatBox label="Total alerts" value={fraudStats.total} />
            <StatBox label="Open" value={fraudStats.open} />
            <StatBox label="Investigating" value={fraudStats.investigating} />
            <StatBox label="Resolved" value={fraudStats.resolved} />
            <StatBox label="False positive" value={fraudStats.false_positive} />
            <StatBox label="Critical" value={fraudStats.critical} color="red" />
          </div>

          <p className="mt-4 border-t border-gray-100 pt-3 text-[11px] leading-relaxed text-gray-500">
            Counts reflect the fraud service. Cross-check with the main dashboard and Service Desk for client context.
          </p>
        </div>
      </div>
    </PageShell>
  );
}

function SummaryCard({ label, value, icon: Icon, suffix, highlight }) {
  return (
    <div
      className={`rounded-2xl border p-5 shadow-sm transition-shadow ${
        highlight ? 'border-red-100 bg-red-50/80 shadow-red-100/30' : 'border-gray-100 bg-white shadow-gray-200/40'
      }`}
    >
      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{label}</p>
      <div className="mt-2 flex items-center justify-between">
        <p className="text-2xl font-semibold tracking-tight text-gray-900">
          {value}
          {suffix && <span className="ml-1 text-base text-emerald-600">{suffix}</span>}
        </p>
        <Icon className="h-5 w-5 text-finnera-600" />
      </div>
    </div>
  );
}

function StatBox({ label, value, color }) {
  return (
    <div className={`rounded-xl p-3 ${color === 'red' ? 'bg-red-50/90' : 'bg-gray-50/90'}`}>
      <p className="text-xs text-gray-500">{label}</p>
      <p className={`mt-1 text-xl font-semibold ${color === 'red' ? 'text-red-700' : 'text-gray-900'}`}>{value ?? 0}</p>
    </div>
  );
}
