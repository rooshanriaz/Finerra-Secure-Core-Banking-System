import { useState, useEffect } from 'react';
import {
  ArrowUpRight, ArrowDownRight, Activity, Users, AlertTriangle, ScrollText,
  Landmark, Clock, Server, TrendingUp, Shield, Loader2
} from 'lucide-react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts';
import { Link } from 'react-router-dom';
import { useAuth, hasSuperAdminAccess } from '../context/AuthContext';
import transactionService from '../services/transactionService';
import fraudService from '../services/fraudService';
import auditService from '../services/auditService';
import cbcService from '../services/cbcService';
import { formatAuditDate } from '../lib/auditRecordUtils';
import { formatPkr } from '../lib/formatCurrency';
const EXCLUDED_ALERT_TYPES = new Set(['LOAN_APPLICATION']);
function StatCard({ icon: Icon, label, value, change, changeType, color }) {
  const colorClasses = {
    blue: 'bg-blue-50 text-blue-600 ring-1 ring-blue-100/80',
    green: 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-100/80',
    red: 'bg-red-50 text-red-600 ring-1 ring-red-100/80',
    purple: 'bg-purple-50 text-purple-600 ring-1 ring-purple-100/80',
    amber: 'bg-amber-50 text-amber-600 ring-1 ring-amber-100/80',
    indigo: 'bg-finnera-50 text-finnera-600 ring-1 ring-finnera-100/80',
  };

  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm shadow-gray-200/40 transition-shadow hover:shadow-md">
      <div className="flex items-start justify-between">
        <div className={`flex h-11 w-11 items-center justify-center rounded-xl ${colorClasses[color]}`}>
          <Icon className="h-5 w-5" />
        </div>
        {change && (
          <span className={`flex items-center gap-0.5 text-xs font-semibold ${
            changeType === 'up' ? 'text-emerald-600' : 'text-red-500'
          }`}>
            {changeType === 'up' ? <ArrowUpRight className="w-3.5 h-3.5" /> : <ArrowDownRight className="w-3.5 h-3.5" />}
            {change}
          </span>
        )}
      </div>
      <div className="mt-4">
        <p className="text-2xl font-semibold tracking-tight text-gray-900">{value}</p>
        <p className="mt-1 text-sm text-gray-500">{label}</p>
      </div>
    </div>
  );
}

function TransactionVolumePanel({ data }) {
  const hasData = Array.isArray(data) && data.length > 0;
  return (
    <div className="lg:col-span-2 rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
      <div className="mb-4">
        <h3 className="text-lg font-semibold tracking-tight text-gray-900">Transaction volume</h3>
        <p className="mt-0.5 text-sm text-gray-500">Deposits, withdrawals, and transfers grouped by weekday from loaded transactions</p>
      </div>
      {!hasData ? (
        <div className="flex min-h-[280px] items-center justify-center rounded-xl border border-dashed border-gray-200 bg-gray-50/80 px-4 py-12 text-center text-sm text-gray-500">
          No transaction history loaded yet. Data appears when savings transactions are available from the connector.
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={data} barGap={4}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
            <XAxis dataKey="date" tick={{ fontSize: 12 }} stroke="#94a3b8" />
            <YAxis tick={{ fontSize: 12 }} stroke="#94a3b8" />
            <Tooltip
              contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.08)' }}
            />
            <Legend iconType="circle" iconSize={8} />
            <Bar dataKey="deposits" fill="#108589" radius={[4, 4, 0, 0]} name="Deposits" />
            <Bar dataKey="withdrawals" fill="#d97706" radius={[4, 4, 0, 0]} name="Withdrawals" />
            <Bar dataKey="transfers" fill="#0d9488" radius={[4, 4, 0, 0]} name="Transfers" />
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}

function RecentActivityPanel({ items }) {
  const activityTypeColors = {
    success: 'bg-emerald-500',
    info: 'bg-finnera-500',
    danger: 'bg-red-500',
    warning: 'bg-amber-500',
  };
  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-semibold tracking-tight text-gray-900">Recent activity</h3>
        <Clock className="h-4 w-4 text-gray-400" />
      </div>
      {items.length === 0 ? (
        <p className="rounded-xl border border-dashed border-gray-200 bg-gray-50/80 py-10 text-center text-sm text-gray-500">
          No recent fraud alerts, transactions, or audit events to show yet.
        </p>
      ) : (
      <div className="space-y-4">
        {items.map((item, idx) => (
          <div key={idx} className="flex items-start gap-3">
            <div className={`mt-2 h-2 w-2 flex-shrink-0 rounded-full ${activityTypeColors[item.type] || 'bg-gray-300'}`} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-gray-900">{item.action}</p>
              <p className="truncate text-xs text-gray-500">{item.detail}</p>
            </div>
            <span className="whitespace-nowrap text-xs text-gray-400">{item.time}</span>
          </div>
        ))}
      </div>
      )}
    </div>
  );
}

function FraudAlertsPanel({ alerts }) {
  const openCount = alerts.filter(
    (a) => String(a.status || '').toLowerCase() !== 'resolved',
  ).length;
  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold tracking-tight text-gray-900">Recent fraud alerts</h3>
          <p className="text-sm text-gray-500">{openCount} open or in progress</p>
        </div>
        <AlertTriangle className="h-5 w-5 text-gray-400" />
      </div>
      {alerts.length === 0 ? (
        <p className="rounded-xl border border-dashed border-gray-200 bg-gray-50/80 py-10 text-center text-sm text-gray-500">
          No fraud alerts returned from the fraud service.
        </p>
      ) : (
      <div className="space-y-3">
        {alerts.map((alert) => (
          <div
            key={alert.id || alert.alertId}
            className="flex items-center justify-between rounded-xl border border-gray-100 bg-gray-50/90 p-3 transition-colors hover:bg-gray-100"
          >
            <div>
              <p className="text-sm font-medium text-gray-900">{alert.type}</p>
              <p className="text-xs text-gray-500">
                {alert.id || alert.alertId} &middot; {alert.account || alert.accountId || '—'}
              </p>
            </div>
            <div className="text-right">
              <p className="text-sm font-semibold text-gray-900">
                {formatPkr(alert.amount ?? alert.transactionAmount ?? 0)}
              </p>
              <span
                className={`text-xs font-medium ${
                  alert.severity === 'Low'
                    ? 'text-emerald-600'
                    : alert.severity === 'Medium'
                      ? 'text-amber-600'
                      : 'text-red-600'
                }`}
              >
                {alert.severity || '—'}
              </span>
            </div>
          </div>
        ))}
      </div>
      )}
    </div>
  );
}

export default function Dashboard() {
  const { user } = useAuth();
  const canOpenAdmin = hasSuperAdminAccess(user);
  const isComplianceOfficer = user?.type === 'compliance';
  const isLoanOfficer = user?.type === 'loan_officer';
  const isBranchManager = user?.type === 'manager';

  const [loading, setLoading] = useState(true);
  const [transactions, setTransactions] = useState([]);
  const [fraudAlerts, setFraudAlerts] = useState([]);
  const [fraudStats, setFraudStats] = useState({});
  const [auditLogs, setAuditLogs] = useState([]);
  const [loanOfficerLoans, setLoanOfficerLoans] = useState([]);
  const [loanOfficerRiskScores, setLoanOfficerRiskScores] = useState({});
  const [loanOfficerTransactions, setLoanOfficerTransactions] = useState([]);
  const [selectedLoanTxId, setSelectedLoanTxId] = useState(null);
  const [error, setError] = useState(null);

  const normalizeLoanTransactionsPayload = (raw) => {
    if (Array.isArray(raw)) return raw;
    if (Array.isArray(raw?.data)) return raw.data;
    if (Array.isArray(raw?.data?.data)) return raw.data.data;
    if (Array.isArray(raw?.data?.pageItems)) return raw.data.pageItems;
    if (Array.isArray(raw?.pageItems)) return raw.pageItems;
    if (Array.isArray(raw?.transactions)) return raw.transactions;
    if (Array.isArray(raw?.data?.transactions)) return raw.data.transactions;
    return [];
  };

  useEffect(() => {
    async function fetchDashboardData() {
      setLoading(true);
      setError(null);
      try {
        const txPromise =
          canOpenAdmin || isBranchManager
            ? transactionService.getRecent(500).catch(() => transactionService.getHistory(1))
            : transactionService.getHistory(1);

        const [txRes, alertsRes, statsRes, auditRes] = await Promise.allSettled([
          txPromise,
          fraudService.getAlerts(),
          fraudService.getStats(),
          auditService.getRecent(),
        ]);

        if (txRes.status === 'fulfilled') {
          const txData = txRes.value?.data ?? txRes.value ?? [];
          setTransactions(Array.isArray(txData) ? txData : []);
        }
        if (alertsRes.status === 'fulfilled') {
          const alertData = alertsRes.value?.data ?? alertsRes.value ?? [];
          const filteredAlerts = (Array.isArray(alertData) ? alertData : [])
            .filter((a) => !EXCLUDED_ALERT_TYPES.has(String(a.transactionType || '').toUpperCase()));
          setFraudAlerts(filteredAlerts);
        }
        if (statsRes.status === 'fulfilled') {
          setFraudStats(statsRes.value?.data ?? statsRes.value ?? {});
        }
        if (auditRes.status === 'fulfilled') {
          const auditData = auditRes.value?.data ?? auditRes.value ?? [];
          setAuditLogs(Array.isArray(auditData) ? auditData : []);
        }

        if (isLoanOfficer) {
          const loansRes = await cbcService.getLoans();
          const loanData = loansRes?.data ?? loansRes ?? [];
          const normalizedLoans = Array.isArray(loanData) ? loanData : [];
          setLoanOfficerLoans(normalizedLoans);
          setSelectedLoanTxId(normalizedLoans[0]?.id ?? null);

          const candidateLoans = normalizedLoans.slice(0, 8);
          const riskEntries = await Promise.all(
            candidateLoans.map(async (loan) => {
              try {
                const amount = Number(loan.principal || loan.approvedPrincipal || loan.proposedPrincipal || 0);
                const scoreRes = await fraudService.scoreTransaction(
                  String(loan.id),
                  String(loan.clientId || loan.clientName || 'loan-applicant'),
                  'LOAN_APPLICATION',
                  amount
                );
                const scoreData = scoreRes?.data ?? scoreRes ?? {};
                return [loan.id, scoreData];
              } catch {
                return [loan.id, null];
              }
            })
          );
          setLoanOfficerRiskScores(Object.fromEntries(riskEntries));

          if (normalizedLoans.length > 0) {
            // Prefer showing a loan that already has real transaction lines.
            let seeded = false;
            for (const loan of normalizedLoans.slice(0, 8)) {
              try {
                const txRes = await cbcService.getLoanTransactions(loan.id);
                const txRows = normalizeLoanTransactionsPayload(txRes);
                if (txRows.length > 0) {
                  setSelectedLoanTxId(loan.id);
                  setLoanOfficerTransactions(txRows);
                  seeded = true;
                  break;
                }
              } catch {
                // Try next loan.
              }
            }
            if (!seeded) {
              try {
                const txRes = await cbcService.getLoanTransactions(normalizedLoans[0].id);
                setLoanOfficerTransactions(normalizeLoanTransactionsPayload(txRes));
              } catch {
                setLoanOfficerTransactions([]);
              }
            }
          } else {
            setLoanOfficerTransactions([]);
          }
        }
      } catch (err) {
        console.error('Dashboard fetch error:', err);
        setError('Failed to load dashboard data');
      } finally {
        setLoading(false);
      }
    }

    fetchDashboardData();
  }, [isLoanOfficer, canOpenAdmin, isBranchManager]);

  useEffect(() => {
    const fetchSelectedLoanTransactions = async () => {
      if (!isLoanOfficer || !selectedLoanTxId) return;
      try {
        const txRes = await cbcService.getLoanTransactions(selectedLoanTxId);
        setLoanOfficerTransactions(normalizeLoanTransactionsPayload(txRes));
      } catch {
        setLoanOfficerTransactions([]);
      }
    };
    fetchSelectedLoanTransactions();
  }, [isLoanOfficer, selectedLoanTxId]);

  const dashboardStats = {
    totalTransactions: transactions.length,
    activeAccounts: new Set(transactions.map(t => t.accountId || t.account_id)).size,
    fraudAlerts: fraudStats.open ?? fraudAlerts.length,
    auditRecords: auditLogs.length,
  };

  const recentActivity = [
    ...fraudAlerts.map((a) => {
      const timestamp = a.timestamp ? new Date(a.timestamp).getTime() : 0;
      return {
        action: `Fraud Alert: ${a.type}`,
        detail: `${a.account || a.accountId || '—'} — ${formatPkr(a.amount ?? a.transactionAmount ?? 0)}`,
        time: a.timestamp ? new Date(a.timestamp).toLocaleString() : '',
        type: 'danger',
        _sortTime: Number.isNaN(timestamp) ? 0 : timestamp,
      };
    }),
    ...transactions.map((t) => {
      const when = t.date || t.transactionDate || t.processedAt;
      const timestamp = when ? new Date(when).getTime() : 0;
      return {
        action: t.description || t.transactionType || t.type || 'Transaction',
        detail: `${t.accountId || t.account_id || '—'} — ${formatPkr(Math.abs(Number(t.amount)))}`,
        time: when ? new Date(when).toLocaleString() : '',
        type: Number(t.amount) >= 0 ? 'success' : 'info',
        _sortTime: Number.isNaN(timestamp) ? 0 : timestamp,
      };
    }),
    ...auditLogs.map((a) => {
      const timestamp = a.displayTime ? new Date(a.displayTime).getTime() : 0;
      return {
        action: `Audit: ${a.displayAction || a.transactionType || 'Record'}`,
        detail: `${a.transactionId || '—'} · Account ${a.accountId ?? '—'}`,
        time: formatAuditDate(a.displayTime),
        type: 'info',
        _sortTime: Number.isNaN(timestamp) ? 0 : timestamp,
      };
    }),
  ]
    .sort((left, right) => right._sortTime - left._sortTime)
    .slice(0, 8)
    .map(({ _sortTime, ...item }) => item);

  const resolveTxnDate = (t) => {
    const raw = t.date || t.transactionDate || t.processedAt;
    if (!raw) return null;
    const d = new Date(raw);
    return Number.isNaN(d.getTime()) ? null : d;
  };

  const volumeByDay = transactions.reduce((acc, t) => {
    const d = resolveTxnDate(t);
    const day = d ? d.toLocaleDateString('en-US', { weekday: 'short' }) : 'N/A';
    if (!acc[day]) acc[day] = { date: day, deposits: 0, withdrawals: 0, transfers: 0 };
    const amt = Math.abs(Number(t.amount) || 0);
    const typ = (t.type || t.category || t.transactionType || '').toLowerCase();
    // Align with transaction-service: DEPOSIT, WITHDRAWAL, LOAN_REPAYMENT (shown as transfers), TRANSFER.
    if (typ.includes('transfer')) {
      acc[day].transfers += amt;
    } else if (typ.includes('loan_repayment') || typ.includes('repayment')) {
      acc[day].transfers += amt;
    } else if (typ.includes('withdrawal') || typ.includes('withdraw')) {
      acc[day].withdrawals += amt;
    } else if (typ.includes('deposit') || typ.includes('salary') || typ.includes('income')) {
      acc[day].deposits += amt;
    } else if (Number(t.amount) > 0) {
      acc[day].deposits += amt;
    } else {
      acc[day].withdrawals += amt;
    }
    return acc;
  }, {});
  const transactionVolumeData = Object.values(volumeByDay);

  const loanOfficerPending = loanOfficerLoans.filter((loan) => {
    const status = String(loan?.status?.value || loan?.status || '').toLowerCase();
    return status.includes('pending') || status.includes('submitted') || status.includes('review');
  });
  const loanOfficerRiskValues = Object.values(loanOfficerRiskScores)
    .map((risk) => Number(risk?.riskScore || 0))
    .filter((v) => !Number.isNaN(v));
  const averageLoanRisk = loanOfficerRiskValues.length
    ? (loanOfficerRiskValues.reduce((acc, score) => acc + score, 0) / loanOfficerRiskValues.length).toFixed(2)
    : '0.00';
  const highRiskLoans = Object.values(loanOfficerRiskScores)
    .filter((risk) => {
      const level = String(risk?.riskLevel || '').toUpperCase();
      return level === 'HIGH' || Number(risk?.riskScore || 0) >= 0.75;
    }).length;
  const selectedLoan = loanOfficerLoans.find((loan) => loan.id === selectedLoanTxId) || loanOfficerLoans[0] || null;

  if (loading) {
    return (
      <div className="flex h-[min(28rem,60vh)] animate-fadeIn items-center justify-center rounded-2xl border border-gray-100 bg-white shadow-sm">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-9 w-9 animate-spin text-finnera-600" />
          <p className="text-sm font-medium text-gray-500">Loading workspace…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="animate-fadeIn space-y-8">
      <div className="relative overflow-hidden rounded-2xl border border-finnera-700/20 bg-gradient-to-br from-finnera-700 via-finnera-800 to-finnera-900 p-8 text-white shadow-lg shadow-finnera-900/10">
        <div className="pointer-events-none absolute inset-0 opacity-[0.15]">
          <div className="absolute -right-16 top-0 h-72 w-72 rounded-full bg-finnera-300 blur-3xl" />
          <div className="absolute -left-10 bottom-0 h-56 w-56 rounded-full bg-teal-300/40 blur-3xl" />
        </div>
        <div className="relative">
          <p className="text-sm font-medium text-finnera-100/90">Welcome back</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">{user?.name}</h1>
          <p className="mt-3 flex items-start gap-2 text-sm text-finnera-50/95">
            <Shield className="mt-0.5 h-4 w-4 flex-shrink-0 text-finnera-200" />
            <span className="min-w-0 break-words">
              {user?.did ? (
                <>
                  <span className="font-medium">{user?.role}</span>
                  <span className="text-finnera-100/80"> · DID: </span>
                  <span className="font-mono text-xs sm:text-sm">{user.did}</span>
                </>
              ) : (
                <span className="font-medium">{user?.role}</span>
              )}
            </span>
          </p>
        </div>
      </div>

      {error && (
        <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-800">
          {error} — partial data may be shown.
        </div>
      )}

      {isComplianceOfficer && (
        <>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
            <StatCard
              icon={Activity}
              label="Total transactions"
              value={(dashboardStats.totalTransactions ?? 0).toLocaleString()}
              color="blue"
            />
            <StatCard
              icon={Users}
              label="Active accounts"
              value={(dashboardStats.activeAccounts ?? 0).toLocaleString()}
              color="green"
            />
            <StatCard
              icon={AlertTriangle}
              label="Fraud alerts"
              value={dashboardStats.fraudAlerts ?? 0}
              color="red"
            />
            <StatCard
              icon={ScrollText}
              label="Audit records"
              value={(dashboardStats.auditRecords ?? 0).toLocaleString()}
              color="purple"
            />
          </div>

          <RecentActivityPanel items={recentActivity} />
        </>
      )}

      {isBranchManager && (
        <>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
            <StatCard
              icon={Activity}
              label="Total transactions"
              value={(dashboardStats.totalTransactions ?? 0).toLocaleString()}
              color="blue"
            />
            <StatCard
              icon={Users}
              label="Active accounts"
              value={(dashboardStats.activeAccounts ?? 0).toLocaleString()}
              color="green"
            />
            <StatCard
              icon={AlertTriangle}
              label="Fraud alerts"
              value={dashboardStats.fraudAlerts ?? 0}
              color="red"
            />
            <StatCard
              icon={ScrollText}
              label="Audit records"
              value={(dashboardStats.auditRecords ?? 0).toLocaleString()}
              color="purple"
            />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <TransactionVolumePanel data={transactionVolumeData} />
            <RecentActivityPanel items={recentActivity} />
          </div>
        </>
      )}

      {isLoanOfficer ? (
        <div className="space-y-8">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <StatCard
              icon={Landmark}
              label="Applications in queue"
              value={loanOfficerPending.length}
              color="indigo"
            />
            <StatCard
              icon={AlertTriangle}
              label="High-risk applications"
              value={highRiskLoans}
              color="red"
            />
            <StatCard
              icon={TrendingUp}
              label="Average AI risk score"
              value={averageLoanRisk}
              color="amber"
            />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
              <div className="mb-4 flex items-center justify-between">
                <h3 className="text-lg font-semibold tracking-tight text-gray-900">Loan recommendations</h3>
                <p className="text-xs text-gray-500">AI-assisted triage</p>
              </div>
              <div className="max-h-80 space-y-3 overflow-y-auto">
                {loanOfficerLoans.length === 0 && (
                  <p className="rounded-lg border border-dashed border-gray-200 bg-gray-50 py-8 text-center text-sm text-gray-500">
                    No loan applications loaded from Fineract yet.
                  </p>
                )}
                {loanOfficerLoans.slice(0, 8).map((loan) => {
                  const risk = loanOfficerRiskScores[loan.id];
                  const riskLevel = String(risk?.riskLevel || 'UNKNOWN').toUpperCase();
                  const recommendation = risk?.recommendation || (
                    riskLevel === 'HIGH' ? 'REJECT' : riskLevel === 'MEDIUM' ? 'FORWARD_WITH_CONDITIONS' : 'FORWARD_TO_MANAGER'
                  );
                  return (
                    <button
                      key={loan.id}
                      type="button"
                      onClick={() => setSelectedLoanTxId(loan.id)}
                      className={`w-full text-left rounded-xl border p-3.5 transition-colors ${
                        selectedLoanTxId === loan.id
                          ? 'border-finnera-200 bg-finnera-50/80'
                          : 'border-gray-100 bg-gray-50/90 hover:bg-gray-100'
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <p className="text-sm font-semibold text-gray-900">
                          Loan #{loan.id} &middot; {(loan.clientName || loan.clientId || 'Client')}
                        </p>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                          riskLevel === 'HIGH'
                            ? 'bg-red-100 text-red-700'
                            : riskLevel === 'MEDIUM'
                              ? 'bg-amber-100 text-amber-700'
                              : 'bg-emerald-100 text-emerald-700'
                        }`}>
                          {riskLevel}
                        </span>
                      </div>
                      <p className="mt-1 text-xs text-gray-500">
                        Amount: {loan.currency?.code || ''} {Number(loan.principal || 0).toLocaleString()}
                      </p>
                      <p className="mt-1 text-xs text-gray-600">
                        Recommendation: <span className="font-semibold">{recommendation}</span>
                      </p>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
              <div className="mb-4 flex items-center justify-between">
                <h3 className="text-lg font-semibold tracking-tight text-gray-900">Loan transaction history</h3>
                <p className="text-xs text-gray-500">
                  {selectedLoan?.id ? `Loan #${selectedLoan.id}` : '—'}
                </p>
              </div>
              <div className="max-h-80 space-y-2 overflow-y-auto">
                {loanOfficerTransactions.length === 0 && (
                  <p className="rounded-lg border border-dashed border-gray-200 bg-gray-50 py-8 text-center text-sm text-gray-500">
                    No disbursement or repayment lines yet for the selected loan.
                  </p>
                )}
                {loanOfficerTransactions.slice(0, 10).map((txn, idx) => (
                  <div key={idx} className="rounded-xl border border-gray-100 bg-white p-3">
                    <div className="flex items-center justify-between">
                      <p className="text-sm font-medium text-gray-900">{txn.type?.value || txn.type || 'Transaction'}</p>
                      <p className="text-sm font-semibold text-gray-900">
                        {selectedLoan?.currency?.code || ''} {Number(txn.amount || txn.transactionAmount || 0).toLocaleString()}
                      </p>
                    </div>
                    <p className="mt-1 text-xs text-gray-500">
                      {txn.date || txn.submittedOnDate || txn.createdDate || 'Date unavailable'}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-finnera-100 bg-gradient-to-r from-finnera-50 to-white p-5">
            <p className="text-sm leading-relaxed text-gray-700">
              Review applications in <span className="font-semibold text-finnera-800">Loan Review</span>.
              Use <span className="font-semibold">Forward to Branch Manager</span> for accepted preliminary reviews,
              and <span className="font-semibold">Reject Application</span> to close high-risk or non-compliant requests.
            </p>
          </div>
        </div>
      ) : (!isBranchManager && !isComplianceOfficer) ? (
        <>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <TransactionVolumePanel data={transactionVolumeData} />
            <RecentActivityPanel items={recentActivity} />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <FraudAlertsPanel alerts={fraudAlerts.slice(0, 5)} />

            {!isBranchManager && (
              <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
                <div className="mb-4 flex items-start justify-between gap-3">
                  <div>
                    <h3 className="text-lg font-semibold tracking-tight text-gray-900">Infrastructure</h3>
                    <p className="mt-1 text-sm leading-relaxed text-gray-500">
                      Live health checks run from Service Desk and the admin console. This panel keeps the overview focused on your operational data.
                    </p>
                  </div>
                  <Server className="h-5 w-5 shrink-0 text-gray-400" />
                </div>
                {canOpenAdmin ? (
                  <Link
                    to="/admin"
                    className="inline-flex items-center gap-2 rounded-xl bg-finnera-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-finnera-700"
                  >
                    Open Core Banking Admin Console
                    <span aria-hidden>→</span>
                  </Link>
                ) : (
                  <p className="text-sm text-gray-600">
                    Ask a system administrator for infrastructure status, IP whitelisting, and security logs.
                  </p>
                )}
              </div>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
}
