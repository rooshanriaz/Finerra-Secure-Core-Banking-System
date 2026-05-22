import { useState, useEffect } from 'react';
import {
  Search, Filter, Download, ExternalLink, Shield, CheckCircle2,
  AlertTriangle, Clock, Activity, Eye, ChevronDown, Loader2, RefreshCw
} from 'lucide-react';
import auditService from '../services/auditService';
import { formatAuditDate } from '../lib/auditRecordUtils';
import { PageShell } from '../components/layout/PageShell';

const typeColorMap = {
  TRANSACTION: 'bg-blue-100 text-blue-700',
  KYC_VERIFICATION: 'bg-green-100 text-green-700',
  LOAN_ACTION: 'bg-purple-100 text-purple-700',
  ROLE_CHANGE: 'bg-orange-100 text-orange-700',
  FRAUD_ALERT: 'bg-red-100 text-red-700',
  SYSTEM: 'bg-gray-100 text-gray-700',
  LOGIN: 'bg-teal-100 text-teal-700',
};

export default function AuditLogs() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [verifyingId, setVerifyingId] = useState(null);
  const [integrityRunning, setIntegrityRunning] = useState(false);
  const [integrityResult, setIntegrityResult] = useState(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedType, setSelectedType] = useState('All Activities');

  const fetchLogs = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await auditService.getRecent();
      setLogs(res.success ? res.data : []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load audit logs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchLogs(); }, []);

  const handleVerify = async (auditId) => {
    try {
      setVerifyingId(auditId);
      const res = await auditService.verify(auditId);
      if (res.success) {
        const p = res.data || {};
        const ok = p.verified === true || p.overallIntegrity === true;
        const headline = ok ? 'Verification PASSED' : 'Verification FAILED';
        const detail = p.message || (ok ? 'Integrity checks passed.' : 'See details below.');
        const hashLine =
          p.hashValid === false
            ? '\n\nLocal hash: recomputed SHA-256 does not match stored dataHash (fields may differ from anchor time).'
            : p.hashValid === true
              ? '\n\nLocal hash: OK (recomputed hash matches stored dataHash).'
              : '';
        const chain = p.blockchainVerification;
        const chainLine =
          chain && typeof chain.message === 'string'
            ? `\n\nBlockchain: ${chain.verified ? 'OK — ' : ''}${chain.message}`
            : '';
        window.alert(`${headline}\n\n${detail}${hashLine}${chainLine}`);
      }
    } catch (err) {
      window.alert('Verification failed: ' + (err.response?.data?.message || err.message));
    } finally {
      setVerifyingId(null);
    }
  };

  const handleIntegrityCheck = async () => {
    try {
      setIntegrityRunning(true);
      setIntegrityResult(null);
      const res = await auditService.integrityCheck();
      setIntegrityResult(res.success ? res.data : { error: 'Unexpected response' });
    } catch (err) {
      setIntegrityResult({ error: err.response?.data?.message || err.message });
    } finally {
      setIntegrityRunning(false);
    }
  };

  const activityTypes = ['All Activities', ...new Set(logs.map((l) => l.displayAction).filter(Boolean))];

  const filteredLogs = logs.filter((log) => {
    const action = (log.displayAction || '').toLowerCase();
    const performedBy = (log.displayPerformedBy || '').toLowerCase();
    const q = searchQuery.toLowerCase();
    const matchesSearch =
      action.includes(q) ||
      performedBy.includes(q) ||
      (log.dataHash || '').toLowerCase().includes(q) ||
      String(log.transactionId || '').toLowerCase().includes(q) ||
      String(log.accountId || '').toLowerCase().includes(q);
    const matchesType = selectedType === 'All Activities' || log.displayAction === selectedType;
    return matchesSearch && matchesType;
  });

  const summary = {
    totalActivities: logs.length,
    verified: logs.filter((l) => l.isVerified).length,
    onBlockchain: logs.filter((l) => l.isOnChain).length,
    pending: logs.filter((l) => !l.isVerified).length,
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 className="w-8 h-8 text-finnera-500 animate-spin" />
        <span className="ml-3 text-gray-500">Loading audit logs...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-96 gap-4">
        <AlertTriangle className="w-12 h-12 text-red-400" />
        <p className="text-red-600 font-medium">{error}</p>
        <button onClick={fetchLogs} className="btn-primary rounded-lg px-6 py-2 text-sm">Retry</button>
      </div>
    );
  }

  return (
    <PageShell
      title="Activity logs & audit trail"
      subtitle="Portal actions, approvals, and system events as recorded by the audit service. Use verification tools to validate ledger anchors."
    >

      {/* Blockchain Notice */}
      <div className="flex items-start gap-3 bg-finnera-50 border border-finnera-100 rounded-xl p-4">
        <Shield className="w-5 h-5 text-finnera-600 mt-0.5 flex-shrink-0" />
        <div>
          <p className="text-sm font-semibold text-finnera-800">
            Blockchain-Secured Audit Trail: All activities are logged with your DID for complete audit trail and accountability
          </p>
          <p className="text-xs text-finnera-600 mt-0.5">
            Showing recent logs from the audit service. All records are anchored to the Hyperledger Fabric ledger.
          </p>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <SummaryCard label="Total Activities" value={summary.totalActivities} icon={Activity} color="blue" />
        <SummaryCard label="Verified Records" value={summary.verified} icon={CheckCircle2} color="green" />
        <SummaryCard label="Pending Verification" value={summary.pending} icon={Clock} color="purple" />
        <SummaryCard label="On Blockchain" value={summary.onBlockchain} icon={Shield} color="teal" suffix="✓" />
      </div>

      {/* Integrity Check */}
      <div className="card bg-gray-50">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-gray-900">Chain Integrity Check</h3>
            <p className="text-xs text-gray-500">Verify the integrity of the entire audit chain</p>
          </div>
          <button
            onClick={handleIntegrityCheck}
            disabled={integrityRunning}
            className="btn-primary rounded-lg text-sm px-5 py-2 flex items-center gap-2 disabled:opacity-50"
          >
            {integrityRunning ? <Loader2 className="w-4 h-4 animate-spin" /> : <Shield className="w-4 h-4" />}
            Run Integrity Check
          </button>
        </div>
        {integrityResult && (
          <div className={`mt-3 p-3 rounded-lg text-sm ${integrityResult.error ? 'bg-red-50 text-red-700' : 'bg-emerald-50 text-emerald-700'}`}>
            {integrityResult.error
              ? `Error: ${integrityResult.error}`
              : `Integrity check complete. Valid: ${integrityResult.valid ?? 'N/A'}, Total checked: ${integrityResult.totalChecked ?? 'N/A'}`
            }
          </div>
        )}
      </div>

      {/* Filters */}
      <div className="card">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-base font-semibold text-gray-900">Activity Logs</h3>
          <p className="text-sm text-gray-500">Filter and search through recent activities</p>
        </div>
        <div className="flex flex-col md:flex-row items-start md:items-center gap-4">
          <div className="relative flex-1 w-full">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search by action, hash, transaction, or account ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="input-field pl-10"
            />
          </div>
          <div className="flex gap-3">
            <select
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value)}
              className="px-4 py-2.5 border border-gray-200 rounded-lg text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-finnera-500 bg-white min-w-[180px]"
            >
              {activityTypes.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
            <button
              onClick={fetchLogs}
              className="flex items-center gap-2 px-4 py-2.5 bg-white border border-gray-200 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
            >
              <RefreshCw className="w-4 h-4" />
              Refresh
            </button>
          </div>
        </div>
      </div>

      {/* Audit Table */}
      <div className="card p-0 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100">
                <th className="text-left px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Timestamp</th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Action</th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Transaction / Account</th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Performed By</th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Blockchain</th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Verified</th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Data Hash</th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filteredLogs.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-6 py-12 text-center text-sm text-gray-500">
                    {logs.length === 0
                      ? 'No audit records returned from the audit service.'
                      : 'No activities match your filters.'}
                  </td>
                </tr>
              )}
              {filteredLogs.map((log) => (
                <tr key={log.auditId} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-3.5 text-sm text-gray-600 whitespace-nowrap font-mono text-xs">
                    {formatAuditDate(log.displayTime)}
                  </td>
                  <td className="px-6 py-3.5">
                    <span className={`badge ${typeColorMap[log.displayAction] || 'bg-gray-100 text-gray-700'}`}>
                      {log.displayAction}
                    </span>
                  </td>
                  <td className="px-6 py-3.5 text-sm text-gray-700 max-w-xs">
                    <div className="text-xs font-mono">{log.transactionId || '—'}</div>
                    <div className="text-xs text-gray-400">{log.accountId != null ? String(log.accountId) : ''}</div>
                  </td>
                  <td className="px-6 py-3.5 text-sm text-gray-500 text-xs">{log.displayPerformedBy || '—'}</td>
                  <td className="px-6 py-3.5">
                    <span className={`badge text-xs ${
                      log.blockchainStatus === 'CONFIRMED'
                        ? 'badge-success'
                        : log.blockchainStatus === 'SUBMITTED'
                          ? 'badge-warning'
                          : log.blockchainStatus === 'FAILED'
                            ? 'badge-danger'
                            : log.blockchainStatus === 'LOCAL_ONLY'
                              ? 'bg-gray-100 text-gray-600'
                              : 'bg-gray-100 text-gray-600'
                    }`}>
                      {log.blockchainStatus || 'N/A'}
                    </span>
                  </td>
                  <td className="px-6 py-3.5">
                    {log.isVerified ? (
                      <CheckCircle2 className="w-4 h-4 text-emerald-500" />
                    ) : (
                      <Clock className="w-4 h-4 text-gray-300" />
                    )}
                  </td>
                  <td className="px-6 py-3.5">
                    <div className="flex items-center gap-1.5">
                      <code className="text-xs font-mono text-finnera-600 bg-finnera-50 px-2 py-1 rounded">
                        {log.dataHash ? log.dataHash.substring(0, 16) + '...' : '—'}
                      </code>
                    </div>
                  </td>
                  <td className="px-6 py-3.5">
                    <button
                      onClick={() => handleVerify(log.auditId)}
                      disabled={verifyingId === log.auditId}
                      className="p-1.5 rounded-lg hover:bg-finnera-50 text-finnera-600 transition-colors disabled:opacity-50"
                      title="Verify on blockchain"
                    >
                      {verifyingId === log.auditId ? (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      ) : (
                        <Shield className="w-4 h-4" />
                      )}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="px-6 py-4 border-t border-gray-100 flex items-center justify-between bg-gray-50">
          <p className="text-sm text-gray-500">
            Showing {filteredLogs.length} of {logs.length} audit records
          </p>
          <p className="text-sm text-finnera-600 cursor-pointer hover:text-finnera-700 font-medium">
            Need older logs? Contact system administrator for archived records
          </p>
        </div>
      </div>
    </PageShell>
  );
}

function SummaryCard({ label, value, icon: Icon, color, suffix }) {
  const bgColors = {
    blue: 'bg-blue-50 border-blue-100',
    green: 'bg-emerald-50 border-emerald-100',
    purple: 'bg-purple-50 border-purple-100',
    teal: 'bg-teal-50 border-teal-100',
  };
  const iconColors = {
    blue: 'text-blue-600',
    green: 'text-emerald-600',
    purple: 'text-purple-600',
    teal: 'text-teal-600',
  };

  return (
    <div className={`card ${bgColors[color]} border`}>
      <p className="text-sm text-gray-600 font-medium">{label}</p>
      <div className="flex items-center justify-between mt-2">
        <p className="text-3xl font-bold text-gray-900">
          {value} {suffix && <span className="text-emerald-500 text-lg">{suffix}</span>}
        </p>
        <Icon className={`w-6 h-6 ${iconColors[color]}`} />
      </div>
    </div>
  );
}
