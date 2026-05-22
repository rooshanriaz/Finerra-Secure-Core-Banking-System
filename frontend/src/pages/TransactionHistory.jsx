import { useState, useEffect } from 'react';
import {
  Search, Download, ArrowUpRight, ArrowDownLeft,
  TrendingUp, Shield, AlertTriangle, Loader2, RefreshCw
} from 'lucide-react';
import transactionService from '../services/transactionService';
import { hasSuperAdminAccess, useAuth } from '../context/AuthContext';
import { PageShell } from '../components/layout/PageShell';
import { formatPkr } from '../lib/formatCurrency';
import seededKycCustomers from '../data/seededKycCustomers.json';

const typeConfig = {
  deposit:    { label: 'Deposit',        colorClass: 'bg-emerald-50 text-emerald-600', icon: ArrowDownLeft },
  withdrawal: { label: 'Withdrawal',     colorClass: 'bg-red-50 text-red-600',        icon: ArrowUpRight },
  repayment:  { label: 'Loan Repayment', colorClass: 'bg-blue-50 text-blue-600',      icon: ArrowUpRight },
  loan_repayment: { label: 'Loan Repayment', colorClass: 'bg-blue-50 text-blue-600', icon: ArrowUpRight },
};

const statusBadgeClass = {
  COMPLETED: 'bg-emerald-50 text-emerald-700',
  PENDING:   'bg-amber-50 text-amber-700',
  FAILED:    'bg-red-50 text-red-700',
  FLAGGED:   'bg-red-50 text-red-700',
};

/** Normalize ApiResponse / gateway shapes so history always yields an array when present. */
function normalizeHistoryPayload(raw) {
  if (raw == null) return { rows: [], explicitFail: true, message: 'Empty response' };
  if (Array.isArray(raw)) return { rows: raw, explicitFail: false, message: '' };
  const inner = raw.data;
  const rows = Array.isArray(inner)
    ? inner
    : Array.isArray(inner?.data)
      ? inner.data
      : [];
  const explicitFail = raw.success === false;
  const message = raw.message || inner?.message || '';
  return { rows, explicitFail, message };
}

function normalizePendingRequestsPayload(raw) {
  if (raw == null) return [];
  if (Array.isArray(raw)) return raw;
  if (Array.isArray(raw.data)) return raw.data;
  if (Array.isArray(raw.data?.data)) return raw.data.data;
  return [];
}

function formatTxnWhen(txn) {
  const iso = txn.processedAt;
  if (iso) {
    const d = new Date(iso);
    if (!Number.isNaN(d.getTime())) {
      return d.toLocaleDateString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
      });
    }
  }
  const dateStr = txn.transactionDate;
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  if (!Number.isNaN(d.getTime())) {
    return d.toLocaleDateString('en-US', {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  }
  return String(dateStr);
}

function isCredit(txn) {
  return txn.transactionType?.toLowerCase() === 'deposit';
}

function isDisplayableTxn(txn) {
  const status = String(txn?.status || '').toUpperCase();
  return status !== 'FAILED' && status !== 'REJECTED';
}

function resolveAuditStatus(txn) {
  const status = String(txn?.status || '').toUpperCase();
  const auditStatus = String(txn?.auditStatus || '').toUpperCase();
  if (status === 'FAILED' || status === 'REJECTED') return 'N/A';
  if (!auditStatus) return null;
  if (auditStatus === 'AUDIT_FAILED') return 'PENDING_RETRY';
  return auditStatus;
}

/** Roles that may look up savings history for KYC-onboarded synthetic / seeded customers. */
function canViewKycCustomerDirectory(user) {
  if (!user) return false;
  if (hasSuperAdminAccess(user)) return true;
  const t = user.type;
  return t === 'manager' || t === 'compliance' || t === 'loan_officer';
}

export default function TransactionHistory() {
  const { user } = useAuth();
  const isSuperAdmin = hasSuperAdminAccess(user);
  const showKycCustomerPicker = canViewKycCustomerDirectory(user);
  const canReviewRequests = isSuperAdmin || user?.type === 'manager';
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedType, setSelectedType] = useState('All');
  const [accountIdInput, setAccountIdInput] = useState('');
  const [activeAccountId, setActiveAccountId] = useState('');
  const [seededPickerValue, setSeededPickerValue] = useState('');

  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [requestForm, setRequestForm] = useState({
    transactionType: 'DEPOSIT',
    accountId: '',
    loanId: '',
    transactionAmount: '',
    transactionDate: '',
    note: '',
  });
  const [requestSubmitLoading, setRequestSubmitLoading] = useState(false);
  const [requestSubmitResult, setRequestSubmitResult] = useState(null);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewError, setReviewError] = useState(null);
  const [pendingRequests, setPendingRequests] = useState([]);

  const fetchTransactions = async (accountId) => {
    if (!accountId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await transactionService.getHistory(accountId);
      const { rows, explicitFail, message } = normalizeHistoryPayload(result);
      if (explicitFail && rows.length === 0) {
        setError(message || 'Failed to fetch transactions.');
        setTransactions([]);
      } else {
        setTransactions(rows.map((t) => ({ ...t, auditStatus: resolveAuditStatus(t) })));
        setError(null);
      }
    } catch (err) {
      const apiMsg =
        err?.response?.data?.message ||
        err?.response?.data?.error?.message ||
        err?.response?.data?.error;
      setError(
        apiMsg ||
        'Unable to load transactions. Please check the account ID and try again.',
      );
      setTransactions([]);
    } finally {
      setLoading(false);
    }
  };

  const handleFetch = () => {
    const id = accountIdInput.trim();
    if (id) {
      setActiveAccountId(id);
      fetchTransactions(id);
    }
  };

  const handleSeededCustomerChange = (e) => {
    const v = e.target.value;
    setSeededPickerValue(v);
    if (!v) return;
    setAccountIdInput(v);
    setActiveAccountId(v);
    fetchTransactions(v);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleFetch();
  };

  const fetchPendingRequests = async () => {
    if (!canReviewRequests) return;
    try {
      setReviewLoading(true);
      setReviewError(null);
      const res = await transactionService.listRequests('PENDING');
      setPendingRequests(normalizePendingRequestsPayload(res));
    } catch (err) {
      setReviewError(err?.response?.data?.error?.message || err?.response?.data?.message || err.message);
      setPendingRequests([]);
    } finally {
      setReviewLoading(false);
    }
  };

  useEffect(() => {
    fetchPendingRequests();
  }, [canReviewRequests]);

  const handleSubmitRequest = async (e) => {
    e.preventDefault();
    try {
      setRequestSubmitLoading(true);
      setRequestSubmitResult(null);
      const payload = {
        transactionType: requestForm.transactionType,
        accountId: requestForm.transactionType === 'LOAN_REPAYMENT' ? null : Number(requestForm.accountId),
        loanId: requestForm.transactionType === 'LOAN_REPAYMENT' ? Number(requestForm.loanId) : null,
        transactionAmount: requestForm.transactionAmount,
        transactionDate: requestForm.transactionDate,
        note: requestForm.note || undefined,
      };
      const res = await transactionService.createRequest(payload);
      setRequestSubmitResult({ ok: true, message: res?.message || 'Request submitted', data: res?.data || res });
      setRequestForm((f) => ({ ...f, transactionAmount: '', note: '' }));
      fetchPendingRequests();
    } catch (err) {
      setRequestSubmitResult({
        ok: false,
        message: err?.response?.data?.error?.message || err?.response?.data?.message || err.message,
      });
    } finally {
      setRequestSubmitLoading(false);
    }
  };

  const handleReviewAction = async (requestId, action) => {
    try {
      setReviewLoading(true);
      if (action === 'approve') {
        await transactionService.approveRequest(requestId);
      } else {
        await transactionService.declineRequest(requestId);
      }
      await fetchPendingRequests();
      if (activeAccountId) fetchTransactions(activeAccountId);
    } catch (err) {
      setReviewError(err?.response?.data?.error?.message || err?.response?.data?.message || err.message);
    } finally {
      setReviewLoading(false);
    }
  };

  const displayTransactions = transactions.filter(isDisplayableTxn);

  const filteredTransactions = displayTransactions.filter((t) => {
    const q = searchQuery.toLowerCase();
    const matchesSearch =
      t.transactionId?.toLowerCase().includes(q) ||
      t.transactionType?.toLowerCase().includes(q) ||
      t.status?.toLowerCase().includes(q) ||
      t.riskLevel?.toLowerCase().includes(q);
    const matchesType =
      selectedType === 'All' ||
      (selectedType === 'Credit' && isCredit(t)) ||
      (selectedType === 'Debit' && !isCredit(t));
    return matchesSearch && matchesType;
  });

  const totalCredit = displayTransactions
    .filter(isCredit)
    .reduce((sum, t) => sum + (Number(t.amount) || 0), 0);
  const totalDebit = displayTransactions
    .filter((t) => !isCredit(t))
    .reduce((sum, t) => sum + (Number(t.amount) || 0), 0);

  const escapeCsvCell = (val) => {
    const s = String(val ?? '');
    if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
    return s;
  };

  const handleExportCsv = () => {
    if (!activeAccountId || filteredTransactions.length === 0) return;
    const cols = [
      'transactionId',
      'transactionType',
      'amount',
      'status',
      'transactionDate',
      'riskLevel',
      'riskScore',
      'auditStatus',
    ];
    const header = cols.join(',');
    const lines = filteredTransactions.map((row) => {
      const exportRow = {
        ...row,
        // Keep CSV aligned with on-screen normalization.
        auditStatus: resolveAuditStatus(row),
      };
      return cols.map((key) => escapeCsvCell(exportRow[key])).join(',');
    });
    const csv = `\uFEFF${[header, ...lines].join('\r\n')}`;
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const stamp = new Date().toISOString().slice(0, 10);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `transactions-account-${activeAccountId}-${stamp}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  };

  const canExport = Boolean(activeAccountId) && filteredTransactions.length > 0;

  return (
    <PageShell
      title="Transaction history"
      subtitle="Look up savings activity by account, or pick a KYC-onboarded customer from the directory (when available for your role). Filter, then export CSV."
      meta={
        activeAccountId ? (
          <p className="text-xs text-gray-500">
            Active account{' '}
            <span className="font-mono font-semibold text-gray-800">{activeAccountId}</span>
          </p>
        ) : null
      }
      actions={(
        <button
          type="button"
          onClick={handleExportCsv}
          disabled={!canExport}
          title={canExport ? 'Download the filtered list as CSV' : 'Load transactions first; export uses the current filters'}
          className="inline-flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Download className="h-4 w-4" />
          Export CSV
        </button>
      )}
    >

      <div className="card">
        <h3 className="text-base font-semibold text-gray-900 mb-1">Customer transaction request intake</h3>
        <p className="text-xs text-gray-500 mb-4">Capture customer-initiated requests when customer UI is unavailable.</p>
        <form onSubmit={handleSubmitRequest} className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <select
            value={requestForm.transactionType}
            onChange={(e) => setRequestForm((f) => ({ ...f, transactionType: e.target.value }))}
            className="input-field"
          >
            <option value="DEPOSIT">Deposit</option>
            <option value="WITHDRAWAL">Withdrawal</option>
            <option value="LOAN_REPAYMENT">Loan repayment</option>
          </select>
          {requestForm.transactionType === 'LOAN_REPAYMENT' ? (
            <input
              className="input-field"
              placeholder="Loan ID"
              required
              value={requestForm.loanId}
              onChange={(e) => setRequestForm((f) => ({ ...f, loanId: e.target.value }))}
            />
          ) : (
            <input
              className="input-field"
              placeholder="Account ID"
              required
              value={requestForm.accountId}
              onChange={(e) => setRequestForm((f) => ({ ...f, accountId: e.target.value }))}
            />
          )}
          <input
            className="input-field"
            placeholder="Amount"
            type="number"
            step="0.01"
            required
            value={requestForm.transactionAmount}
            onChange={(e) => setRequestForm((f) => ({ ...f, transactionAmount: e.target.value }))}
          />
          <input
            className="input-field"
            type="text"
            placeholder="Date (dd MMMM yyyy)"
            required
            value={requestForm.transactionDate}
            onChange={(e) => setRequestForm((f) => ({ ...f, transactionDate: e.target.value }))}
          />
          <input
            className="input-field md:col-span-2"
            placeholder="Request note"
            value={requestForm.note}
            onChange={(e) => setRequestForm((f) => ({ ...f, note: e.target.value }))}
          />
          <button type="submit" disabled={requestSubmitLoading} className="btn-primary disabled:opacity-50">
            {requestSubmitLoading ? 'Submitting...' : 'Submit Request'}
          </button>
        </form>
        {requestSubmitResult && (
          <p className={`mt-3 text-sm ${requestSubmitResult.ok ? 'text-emerald-600' : 'text-red-600'}`}>
            {requestSubmitResult.message}
            {requestSubmitResult.ok && requestSubmitResult.data?.requestId ? ` (${requestSubmitResult.data.requestId})` : ''}
          </p>
        )}
      </div>

      {canReviewRequests && (
        <div className="card">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-base font-semibold text-gray-900">Pending transaction approvals</h3>
            <button onClick={fetchPendingRequests} className="text-xs text-finnera-600">Refresh</button>
          </div>
          {reviewError && <p className="text-sm text-red-600 mb-2">{reviewError}</p>}
          {reviewLoading ? (
            <p className="text-sm text-gray-500">Loading requests...</p>
          ) : pendingRequests.length === 0 ? (
            <p className="text-sm text-gray-500">No pending requests.</p>
          ) : (
            <div className="space-y-2">
              {pendingRequests.map((req) => (
                <div key={req.requestId} className="rounded-lg border border-gray-100 p-3 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-semibold text-gray-900">{req.requestId} · {req.transactionType}</p>
                    <p className="text-xs text-gray-500">
                      {req.accountId ? `Account ${req.accountId}` : `Loan ${req.loanId}`} · {formatPkr(Number(req.transactionAmount || 0))} · by {req.requestedBy}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button onClick={() => handleReviewAction(req.requestId, 'approve')} className="px-3 py-1.5 rounded bg-emerald-600 text-white text-xs">Approve</button>
                    <button onClick={() => handleReviewAction(req.requestId, 'decline')} className="px-3 py-1.5 rounded bg-red-600 text-white text-xs">Decline</button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Account ID Selector */}
      <div className="card space-y-4">
        {showKycCustomerPicker && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              KYC-completed customers (10)
            </label>
            <select
              value={seededPickerValue}
              onChange={handleSeededCustomerChange}
              className="input-field w-full max-w-2xl"
            >
              <option value="">Choose customer to load savings transaction history…</option>
              {seededKycCustomers.map((c) => (
                <option key={c.syntheticUserRef} value={String(c.savingsAccountId)}>
                  {c.syntheticUserRef} · Savings #{c.savingsAccountId} · {c.kycReferenceId} · Loan #{c.loanId} ({c.riskProfile})
                </option>
              ))}
            </select>
            <p className="mt-1.5 text-xs text-gray-500">
              Directory matches the last successful synthetic KYC seed. After re-seeding with new IDs, update{' '}
              <span className="font-mono">frontend/src/data/seededKycCustomers.json</span>.
            </p>
          </div>
        )}
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
          <label className="text-sm font-medium text-gray-700 whitespace-nowrap">
            Account ID
          </label>
          <div className="relative flex-1 w-full">
            <input
              type="text"
              placeholder="Savings account ID from core banking"
              value={accountIdInput}
              onChange={(e) => {
                setAccountIdInput(e.target.value);
                setSeededPickerValue('');
              }}
              onKeyDown={handleKeyDown}
              className="input-field w-full"
            />
          </div>
          <button
            onClick={handleFetch}
            disabled={loading || !accountIdInput.trim()}
            className="btn-primary flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Search className="w-4 h-4" />
            )}
            Fetch
          </button>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-red-500 mt-0.5 shrink-0" />
          <div>
            <p className="text-sm font-medium text-red-800">Error loading transactions</p>
            <p className="text-sm text-red-600 mt-0.5">{error}</p>
          </div>
        </div>
      )}

      {/* Loading Spinner */}
      {loading && (
        <div className="card flex items-center justify-center py-16">
          <Loader2 className="w-8 h-8 text-finnera-500 animate-spin" />
          <p className="ml-3 text-gray-500 font-medium">Loading transactions…</p>
        </div>
      )}

      {/* Main Content — visible after a fetch */}
      {!loading && activeAccountId && (
        <>
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="card bg-emerald-50 border-emerald-100">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-emerald-600 font-medium">Total Credits</p>
                  <p className="text-2xl font-bold text-emerald-700 mt-1">
                    +{formatPkr(totalCredit)}
                  </p>
                </div>
                <ArrowDownLeft className="w-8 h-8 text-emerald-300" />
              </div>
            </div>
            <div className="card bg-red-50 border-red-100">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-red-600 font-medium">Total Debits</p>
                  <p className="text-2xl font-bold text-red-700 mt-1">
                    -{formatPkr(totalDebit)}
                  </p>
                </div>
                <ArrowUpRight className="w-8 h-8 text-red-300" />
              </div>
            </div>
            <div className="card bg-finnera-50 border-finnera-100">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-finnera-600 font-medium">Net Balance Change</p>
                  <p className="text-2xl font-bold text-finnera-700 mt-1">
                    {totalCredit - totalDebit >= 0 ? '+' : '-'} {formatPkr(Math.abs(totalCredit - totalDebit))}
                  </p>
                </div>
                <TrendingUp className="w-8 h-8 text-finnera-300" />
              </div>
            </div>
          </div>

          {/* Filters */}
          <div className="card">
            <div className="flex flex-col md:flex-row items-start md:items-center gap-4">
              <div className="relative flex-1 w-full">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input
                  type="text"
                  placeholder="Search by transaction ID, type, status, risk…"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="input-field pl-10"
                />
              </div>
              <div className="flex flex-wrap gap-3">
                <select
                  value={selectedType}
                  onChange={(e) => setSelectedType(e.target.value)}
                  className="px-4 py-2.5 border border-gray-200 rounded-lg text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-finnera-500 bg-white"
                >
                  <option value="All">All Types</option>
                  <option value="Credit">Credits (Deposits)</option>
                  <option value="Debit">Debits (Withdrawals)</option>
                </select>
                <button
                  onClick={() => fetchTransactions(activeAccountId)}
                  className="px-4 py-2.5 border border-gray-200 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors flex items-center gap-2"
                >
                  <RefreshCw className="w-4 h-4" />
                  Refresh
                </button>
              </div>
            </div>
          </div>

          {/* Transaction List */}
          <div className="card p-0">
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
              <div>
                <h3 className="text-base font-semibold text-gray-900">
                  Recent Transaction History
                </h3>
                <p className="text-xs text-gray-500">
                  Account {activeAccountId} activity
                </p>
              </div>
              <span className="badge-info">{filteredTransactions.length} transactions</span>
            </div>

            <div className="divide-y divide-gray-50">
              {filteredTransactions.map((txn) => {
                const credit = isCredit(txn);
                const typ = String(txn.transactionType || '').toLowerCase();
                const cfg = typeConfig[typ] ?? {
                  label: txn.transactionType || 'Transaction',
                  colorClass: 'bg-gray-50 text-gray-600',
                  icon: Shield,
                };
                const TxnIcon = cfg.icon;

                return (
                  <div
                    key={txn.transactionId}
                    className="flex items-center justify-between px-6 py-4 hover:bg-gray-50 transition-colors"
                  >
                    <div className="flex items-center gap-4">
                      <div
                        className={`w-10 h-10 rounded-lg flex items-center justify-center ${cfg.colorClass}`}
                      >
                        <TxnIcon className="w-5 h-5" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="text-sm font-semibold text-gray-900">{cfg.label}</p>
                          {credit ? (
                            <ArrowDownLeft className="w-3.5 h-3.5 text-emerald-500" />
                          ) : (
                            <ArrowUpRight className="w-3.5 h-3.5 text-gray-400" />
                          )}
                          {txn.status && (
                            <span
                              className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                                statusBadgeClass[txn.status.toUpperCase()] ||
                                'bg-gray-100 text-gray-600'
                              }`}
                            >
                              {txn.status}
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-gray-500 mt-0.5">
                          {formatTxnWhen(txn)}
                          {txn.riskLevel && (
                            <>
                              {' '}&middot;{' '}
                              <span
                                className={`font-medium ${
                                  txn.riskLevel === 'HIGH'
                                    ? 'text-red-500'
                                    : txn.riskLevel === 'MEDIUM'
                                      ? 'text-amber-500'
                                      : 'text-emerald-500'
                                }`}
                              >
                                Risk: {txn.riskLevel}
                                {txn.riskScore != null && ` (${txn.riskScore})`}
                              </span>
                            </>
                          )}
                          {txn.auditStatus && <> &middot; Audit: {txn.auditStatus}</>}
                        </p>
                      </div>
                    </div>

                    <div className="text-right">
                      <p
                        className={`text-sm font-bold ${
                          credit ? 'text-emerald-600' : 'text-gray-900'
                        }`}
                      >
                        {credit ? '+' : '-'} {formatPkr(Math.abs(Number(txn.amount) || 0))}
                      </p>
                      <p className="text-xs text-gray-400 mt-0.5">{txn.transactionId}</p>
                    </div>
                  </div>
                );
              })}
            </div>

            {filteredTransactions.length === 0 && (
              <div className="text-center py-12">
                <Search className="w-8 h-8 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-500 font-medium">No transactions found</p>
                <p className="text-sm text-gray-400 mt-1">
                  {displayTransactions.length === 0
                    ? 'No transactions exist for this account'
                    : 'Try adjusting your search or filters'}
                </p>
              </div>
            )}
          </div>
        </>
      )}

      {/* Initial empty state before any fetch */}
      {!loading && !activeAccountId && (
        <div className="card text-center py-16">
          <Shield className="w-12 h-12 text-gray-300 mx-auto mb-4" />
          <p className="text-gray-500 font-medium text-lg">No account selected</p>
          <p className="text-sm text-gray-400 mt-1">
            Enter an account ID above and click Fetch to view transaction history
          </p>
        </div>
      )}
    </PageShell>
  );
}
