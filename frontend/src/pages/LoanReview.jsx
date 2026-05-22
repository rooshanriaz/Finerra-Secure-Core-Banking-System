import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  DollarSign, FileText, Calendar, Clock, CheckCircle2, XCircle,
  TrendingUp, Shield, ArrowUpRight, ArrowDownLeft, Sparkles, AlertTriangle,
  ThumbsUp, Eye, ChevronRight, Info, Home, Building2, ShoppingCart,
  UtensilsCrossed, Fuel, Heart, Zap, MapPin, Phone, Mail, CreditCard,
  Radio, User, Loader2
} from 'lucide-react';
import cbcService from '../services/cbcService';
import transactionService from '../services/transactionService';
import { hasSuperAdminAccess, useAuth } from '../context/AuthContext';
import { PageShell } from '../components/layout/PageShell';
import seededKycCustomers from '../data/seededKycCustomers.json';

/** Lookup maps from the KYC seed manifest */
const KYC_BY_LOAN_ID  = Object.fromEntries(seededKycCustomers.map((c) => [Number(c.loanId), c]));
const KYC_LOAN_IDS    = new Set(seededKycCustomers.map((c) => Number(c.loanId)));

/** Return only loans whose id is in the KYC manifest, sorted by id. */
function pickKycLoans(allRows) {
  if (!Array.isArray(allRows) || allRows.length === 0) return { visible: [], total: allRows?.length ?? 0 };
  const total = allRows.length;
  const visible = allRows
    .filter((l) => KYC_LOAN_IDS.has(Number(l.id)))
    .sort((a, b) => Number(a.id) - Number(b.id));
  return { visible, total };
}

// ─── AI Risk Scoring ────────────────────────────────────────────────────────

/** Normalise a raw savings-history payload to an array of transaction objects. */
function normaliseTxHistory(raw) {
  if (Array.isArray(raw)) return raw;
  if (Array.isArray(raw?.data)) return raw.data;
  if (Array.isArray(raw?.data?.data)) return raw.data.data;
  return [];
}

/**
 * Pure function: derive a risk score (0–100) and breakdown from transaction
 * history + KYC seed metadata. Higher = better creditworthiness.
 */
function computeRiskScore(transactions, kycMeta) {
  const totalTxns = transactions.length;
  if (totalTxns === 0) {
    return {
      score: 0,
      grade: 'INSUFFICIENT_DATA',
      label: 'Insufficient data',
      colour: 'gray',
      breakdown: [],
    };
  }

  const deposits    = transactions.filter((t) => t.transactionType?.toUpperCase() === 'DEPOSIT');
  const withdrawals = transactions.filter((t) => t.transactionType?.toUpperCase() === 'WITHDRAWAL');
  const repayments  = transactions.filter((t) =>
    ['LOAN_REPAYMENT', 'REPAYMENT'].includes(t.transactionType?.toUpperCase()),
  );

  const completed = transactions.filter((t) => t.status?.toUpperCase() === 'COMPLETED');
  const completionRate = totalTxns > 0 ? (completed.length / totalTxns) * 100 : 0;

  const totalDeposited  = deposits.reduce((s, t) => s + Math.abs(Number(t.amount) || 0), 0);
  const totalWithdrawn  = withdrawals.reduce((s, t) => s + Math.abs(Number(t.amount) || 0), 0);
  const withdrawalRatio = totalDeposited > 0 ? totalWithdrawn / totalDeposited : 1;

  const repaymentScore  = Math.min(repayments.length * 8, 25);
  const activityScore   = Math.min(totalTxns * 1.5, 20);
  const completionScore = (completionRate / 100) * 20;
  const savingsScore    = Math.min(Math.max(20 - withdrawalRatio * 20, 0), 20);

  // Static profile adjustment from seed manifest
  const profileAdjust = kycMeta?.riskProfile === 'LOW_RISK' ? 10
    : kycMeta?.riskProfile === 'MEDIUM_RISK' ? 0 : -8;

  const profileBaseline = Math.min(Math.max(15 + profileAdjust, 0), 25);
  const rawScore  = repaymentScore + activityScore + completionScore + savingsScore + profileBaseline;
  const score     = Math.round(Math.min(Math.max(rawScore, 0), 100));

  const grade  = score >= 75 ? 'LOW_RISK' : score >= 50 ? 'MEDIUM_RISK' : 'HIGH_RISK';
  const label  = score >= 75 ? 'Low Risk' : score >= 50 ? 'Medium Risk' : 'High Risk';
  const colour = score >= 75 ? 'emerald' : score >= 50 ? 'amber' : 'red';

  const breakdown = [
    { label: 'Repayment activity',   value: repaymentScore,               max: 25, hint: `${repayments.length} repayment(s) found` },
    { label: 'Transaction activity', value: activityScore,                 max: 20, hint: `${totalTxns} total transactions` },
    { label: 'Transaction success',  value: Math.round(completionScore),  max: 20, hint: `${Math.round(completionRate)}% completed` },
    { label: 'Savings discipline',   value: Math.round(savingsScore),     max: 20, hint: `Withdrawal ratio ${(withdrawalRatio * 100).toFixed(0)}%` },
    { label: 'Profile baseline',     value: profileBaseline,               max: 25, hint: kycMeta?.riskProfile ?? 'Unknown' },
  ];

  return { score, grade, label, colour, breakdown };
}

const RISK_COLOUR_CLASSES = {
  emerald: {
    ring: 'border-emerald-300 bg-emerald-50',
    text: 'text-emerald-700',
    badge: 'bg-emerald-100 text-emerald-700',
    bar: 'bg-emerald-400',
  },
  amber: {
    ring: 'border-amber-300 bg-amber-50',
    text: 'text-amber-700',
    badge: 'bg-amber-100 text-amber-700',
    bar: 'bg-amber-400',
  },
  red: {
    ring: 'border-red-300 bg-red-50',
    text: 'text-red-700',
    badge: 'bg-red-100 text-red-700',
    bar: 'bg-red-400',
  },
  gray: {
    ring: 'border-gray-300 bg-gray-50',
    text: 'text-gray-700',
    badge: 'bg-gray-100 text-gray-700',
    bar: 'bg-gray-400',
  },
};

export default function LoanReview() {
  const { user } = useAuth();
  const isSuperAdmin = hasSuperAdminAccess(user);
  const isLoanOfficer = user?.type === 'loan_officer';
  const isBranchManager = user?.type === 'manager';
  const [loans, setLoans] = useState([]);
  const [loansTotalInFineract, setLoansTotalInFineract] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  // ── AI Risk Scoring ──
  const [riskScore, setRiskScore] = useState(null);       // { score, grade, label, colour, breakdown }
  const [riskLoading, setRiskLoading] = useState(false);
  const [riskError, setRiskError] = useState(null);
  const [loanAiRiskById, setLoanAiRiskById] = useState({});

  const [selectedLoanId, setSelectedLoanId] = useState(null);
  const [showApprovalModal, setShowApprovalModal] = useState(false);
  const [approvalDecision, setApprovalDecision] = useState(null);
  const [decisionNote, setDecisionNote] = useState('');

  const [fineractDown, setFineractDown] = useState(false);
  /** Convert ISO date string (yyyy-MM-dd) to Fineract format (dd MMMM yyyy). */
  const isoToFineract = (iso) => {
    if (!iso) return '';
    const months = ['January', 'February', 'March', 'April', 'May', 'June',
                    'July', 'August', 'September', 'October', 'November', 'December'];
    const [y, m, d] = iso.split('-');
    return `${String(d).padStart(2, '0')} ${months[parseInt(m, 10) - 1]} ${y}`;
  };

  const todayISO = () => new Date().toISOString().split('T')[0];
  const twoWeeksISO = () => {
    const d = new Date(); d.setDate(d.getDate() + 14);
    return d.toISOString().split('T')[0];
  };

  const [requestForm, setRequestForm] = useState({
    clientId: '',
    productId: '',
    principal: '',
    tenureMonths: '12',
    interestRate: '15',
    externalId: '',
    customerNote: '',
    submittedOnDate: todayISO(),
    expectedDisbursementDate: twoWeeksISO(),
  });
  const [requestLoading, setRequestLoading] = useState(false);
  const [requestResult, setRequestResult] = useState(null);
  const [loanProducts, setLoanProducts] = useState([]);
  const [clients, setClients] = useState([]);
  const [clientsLoading, setClientsLoading] = useState(false);

  const fetchLoans = async () => {
    try {
      setLoading(true);
      setError(null);
      setFineractDown(false);
      const res = await cbcService.getLoans();
      // Unwrap various API shapes (direct array, wrapped data, paged pageItems)
      let raw = [];
      if (Array.isArray(res)) raw = res;
      else if (Array.isArray(res?.data)) raw = res.data;
      else if (Array.isArray(res?.data?.pageItems)) raw = res.data.pageItems;
      else if (Array.isArray(res?.pageItems)) raw = res.pageItems;
      const { visible, total } = pickKycLoans(raw);
      setLoans(visible);
      setLoansTotalInFineract(total);
      if (visible.length > 0) {
        setSelectedLoanId((prev) =>
          prev != null && visible.some((l) => l.id === prev) ? prev : visible[0].id,
        );
      } else {
        setSelectedLoanId(null);
      }
    } catch (err) {
      const status = err.response?.status;
      if (status === 502 || status === 503 || status === 500 || !err.response) {
        setFineractDown(true);
      } else {
        setError(err.response?.data?.message || err.message || 'Failed to load loans');
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchLoanProducts = async () => {
    try {
      const res = await cbcService.getLoanProducts();
      const rows = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
      const normalized = rows
        .map((p) => ({
          id: Number(p?.id ?? p?.productId ?? p?.resourceId ?? 0),
          name: p?.name || p?.loanProductName || `Product ${p?.id ?? p?.productId ?? ''}`,
        }))
        .filter((p) => Number.isFinite(p.id) && p.id > 0);
      setLoanProducts(normalized);
      if (normalized.length > 0 && !requestForm.productId) {
        setRequestForm((f) => ({ ...f, productId: String(normalized[0].id) }));
      }
    } catch {
      // Keep intake form usable with manual product ID entry fallback.
      setLoanProducts([]);
    }
  };

  const fetchClients = async () => {
    try {
      setClientsLoading(true);
      const res = await cbcService.getClients();
      const rows = Array.isArray(res?.data)
        ? res.data
        : (Array.isArray(res) ? res : (Array.isArray(res?.pageItems) ? res.pageItems : []));

      const normalized = rows
        .map((c) => {
          const id = Number(c?.id ?? c?.clientId ?? c?.resourceId ?? 0);
          const name =
            c?.displayName ||
            [c?.firstname, c?.middlename, c?.lastname].filter(Boolean).join(' ').trim() ||
            (id > 0 ? `Client ${id}` : '');
          return {
            id,
            name,
            accountNo: c?.accountNo || '',
            externalId: c?.externalId || '',
            mobileNo: c?.mobileNo || '',
            emailAddress: c?.emailAddress || '',
            officeName: c?.officeName || '',
            active: c?.active,
          };
        })
        .filter((c) => Number.isFinite(c.id) && c.id > 0)
        .sort((a, b) => a.name.localeCompare(b.name));

      setClients(normalized);
      if (normalized.length > 0 && !requestForm.clientId) {
        setRequestForm((f) => ({ ...f, clientId: String(normalized[0].id) }));
      }
    } catch {
      setClients([]);
    } finally {
      setClientsLoading(false);
    }
  };

  useEffect(() => {
    fetchLoans();
    fetchLoanProducts();
    fetchClients();
  }, []);

  useEffect(() => {
    let cancelled = false;
    const loadLoanCardRisk = async () => {
      if (!Array.isArray(loans) || loans.length === 0) {
        setLoanAiRiskById({});
        return;
      }
      const entries = await Promise.all(
        loans.map(async (loan) => {
          const kyc = KYC_BY_LOAN_ID[Number(loan.id)];
          if (!kyc?.savingsAccountId) return [Number(loan.id), null];
          try {
            const txRaw = await transactionService.getHistory(Number(kyc.savingsAccountId));
            const txns = normaliseTxHistory(txRaw);
            return [Number(loan.id), computeRiskScore(txns, kyc)];
          } catch {
            return [Number(loan.id), null];
          }
        }),
      );
      if (!cancelled) setLoanAiRiskById(Object.fromEntries(entries));
    };
    loadLoanCardRisk();
    return () => { cancelled = true; };
  }, [loans]);

  // Load AI risk score whenever a different loan is selected
  useEffect(() => {
    if (selectedLoanId == null) {
      setRiskScore(null);
      return;
    }
    const kyc = KYC_BY_LOAN_ID[Number(selectedLoanId)];
    if (!kyc?.savingsAccountId) {
      setRiskScore(null);
      return;
    }
    let cancelled = false;
    setRiskLoading(true);
    setRiskError(null);
    setRiskScore(null);
    transactionService.getHistory(kyc.savingsAccountId)
      .then((raw) => {
        if (cancelled) return;
        const txns = normaliseTxHistory(raw);
        setRiskScore(computeRiskScore(txns, kyc));
      })
      .catch(() => {
        if (cancelled) return;
        // No transaction-service history: fall back to profile-only score
        setRiskScore(computeRiskScore([], kyc));
      })
      .finally(() => {
        if (!cancelled) setRiskLoading(false);
      });
    return () => { cancelled = true; };
  }, [selectedLoanId]);

  const selectedLoan = loans.find(l => l.id === selectedLoanId) || loans[0] || null;
  const selectedClient = clients.find((c) => String(c.id) === String(requestForm.clientId)) || null;

  /** Fineract date format for approve/disburse/reject (matches CBC LoansClient: dd MMMM yyyy). */
  const fineractToday = () => {
    const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    const now = new Date();
    const d = now.getDate();
    const day = d < 10 ? `0${d}` : `${d}`;
    return `${day} ${months[now.getMonth()]} ${now.getFullYear()}`;
  };

  const handleApprove = async () => {
    if (!selectedLoan) return;
    if (!canApproveLoan(selectedLoan)) {
      window.alert(`Loan cannot be approved in current state: ${getLoanStatusText(selectedLoan)}`);
      return;
    }
    try {
      setActionLoading(true);
      await cbcService.approveLoan(selectedLoan.id, fineractToday(), null, decisionNote || undefined);
      setShowApprovalModal(false);
      setDecisionNote('');
      await fetchLoans();
    } catch (err) {
      window.alert('Failed to approve loan: ' + (err.response?.data?.message || err.message));
    } finally {
      setActionLoading(false);
    }
  };

  const handleForward = async () => {
    if (!selectedLoan) return;
    if (!canApproveLoan(selectedLoan)) {
      window.alert(`Loan cannot be forwarded in current state: ${getLoanStatusText(selectedLoan)}`);
      return;
    }
    try {
      setActionLoading(true);
      const note = decisionNote?.trim()
        ? `[Forwarded to Branch Manager] ${decisionNote.trim()}`
        : 'Forwarded to Branch Manager for final decision';
      // True two-step flow: Forward is persisted in CBC workflow without final Fineract approval.
      await cbcService.forwardLoan(selectedLoan.id, fineractToday(), note);
      // Optimistic update so status/action state changes immediately in UI.
      setLoans((prev) => prev.map((l) => (
        l.id === selectedLoan.id
          ? { ...l, workflowStatus: 'FORWARDED_TO_MANAGER', forwardedToManager: true }
          : l
      )));
      // Pull latest single-loan detail to avoid stale list payload edge cases.
      try {
        const latest = await cbcService.getLoan(selectedLoan.id);
        const loanData = latest?.data ?? latest;
        if (loanData && loanData.id) {
          setLoans((prev) => prev.map((l) => (l.id === loanData.id ? { ...l, ...loanData } : l)));
        }
      } catch {
        // Keep optimistic state if detail refresh fails.
      }
      setShowApprovalModal(false);
      setDecisionNote('');
      await fetchLoans();
    } catch (err) {
      const detail =
        err.response?.data?.error?.message ||
        err.response?.data?.error?.details ||
        err.response?.data?.message ||
        err.message;
      window.alert('Failed to forward loan: ' + detail);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDisburse = async () => {
    if (!selectedLoan) return;
    if (!canDisburseLoan(selectedLoan)) {
      window.alert(`Loan cannot be disbursed in current state: ${getLoanStatusText(selectedLoan)}`);
      return;
    }
    try {
      setActionLoading(true);
      await cbcService.disburseLoan(selectedLoan.id, fineractToday());
      setShowApprovalModal(false);
      setDecisionNote('');
      await fetchLoans();
    } catch (err) {
      window.alert('Failed to disburse loan: ' + (err.response?.data?.message || err.message));
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!selectedLoan) return;
    if (!canRejectLoan(selectedLoan)) {
      window.alert(`Loan cannot be rejected in current state: ${getLoanStatusText(selectedLoan)}`);
      return;
    }
    try {
      setActionLoading(true);
      await cbcService.rejectLoan(
        selectedLoan.id,
        fineractToday(),
        decisionNote || 'Rejected during manual review'
      );
      setShowApprovalModal(false);
      setDecisionNote('');
      await fetchLoans();
    } catch (err) {
      const detail =
        err.response?.data?.error?.message ||
        err.response?.data?.error?.details ||
        err.response?.data?.message ||
        err.message;
      window.alert('Failed to reject loan: ' + detail);
    } finally {
      setActionLoading(false);
    }
  };

  const getStatusBadge = (status) => {
    if (!status) return 'badge-info';

    if (typeof status === 'object') {
      if (status.pendingApproval) return 'badge-warning';
      if (status.waitingForDisbursal) return 'badge-success';
      if (status.active) return 'badge-info';
      if (status.closed || status.closedWrittenOff || status.closedRescheduled) return 'badge-danger';
    }

    const s = typeof status === 'object' ? (status.value || status.code || '') : status;
    const lower = String(s).toLowerCase();
    if (lower.includes('pending') || lower.includes('submitted') || lower.includes('review')) return 'badge-warning';
    if (lower.includes('waiting') && lower.includes('disbursal')) return 'badge-success';
    if (lower.includes('approved')) return 'badge-success';
    if (lower.includes('active') || lower.includes('disburse')) return 'badge-info';
    if (lower.includes('declin') || lower.includes('reject') || lower.includes('closed')) return 'badge-danger';
    return 'badge-info';
  };

  const isForwardedToManager = (loan) => {
    if (!loan) return false;
    if (loan.forwardedToManager === true) return true;
    const wf = String(loan.workflowStatus || '').toUpperCase();
    return wf === 'FORWARDED_TO_MANAGER';
  };

  const getStatusText = (status) => {
    if (!status) return 'Unknown';
    if (typeof status === 'object') {
      if (status.pendingApproval) return 'Pending Approval';
      if (status.waitingForDisbursal) return 'Approved (Awaiting Disbursal)';
      if (status.active) return 'Active';
      if (status.closed) return 'Closed';
      return status.value || status.code || 'Unknown';
    }
    return String(status);
  };

  const getLoanStatusText = (loan) => {
    if (isForwardedToManager(loan) && loan?.status?.pendingApproval) {
      return 'Forwarded to Branch Manager';
    }
    return getStatusText(loan?.status);
  };

  const getLoanStatusBadge = (loan) => {
    if (isForwardedToManager(loan) && loan?.status?.pendingApproval) {
      return 'badge-info';
    }
    return getStatusBadge(loan?.status);
  };

  const canRejectLoan = (loan) => {
    const st = loan?.status;
    if (!st) return true;
    if (isBranchManager || isSuperAdmin) {
      return isForwardedToManager(loan);
    }
    if (typeof st === 'object') {
      // Fineract reject is valid only while loan is pending approval.
      return !!st.pendingApproval;
    }
    const lower = String(st).toLowerCase();
    return lower.includes('pending') || lower.includes('submitted') || lower.includes('in progress');
  };

  const canApproveLoan = (loan) => {
    const st = loan?.status;
    if (!st) return true;
    if (typeof st === 'object') {
      if (!st.pendingApproval) return false;
      if (isBranchManager && !isForwardedToManager(loan)) return false;
      return true;
    }
    const lower = String(st).toLowerCase();
    const pending = lower.includes('pending') || lower.includes('submitted');
    if (!pending) return false;
    if (isBranchManager && !isForwardedToManager(loan)) return false;
    return true;
  };

  const canDisburseLoan = (loan) => {
    const st = loan?.status;
    if (!st) return false;
    if (typeof st === 'object') return !!st.waitingForDisbursal;
    const lower = String(st).toLowerCase();
    return lower.includes('approved') || lower.includes('waiting');
  };

  const getForwardDisabledReason = (loan) => {
    if (!loan) return 'Select a loan first.';
    if (actionLoading) return 'Action in progress.';
    if (canApproveLoan(loan)) return '';
    return `Loan must be in pending/submitted state for forwarding. Current state: ${getLoanStatusText(loan)}.`;
  };

  const getRejectDisabledReason = (loan) => {
    if (!loan) return 'Select a loan first.';
    if (actionLoading) return 'Action in progress.';
    if (canRejectLoan(loan)) return '';
    if (isBranchManager || isSuperAdmin) {
      return `Loan must be forwarded to Branch Manager before rejection. Current state: ${getLoanStatusText(loan)}.`;
    }
    return `Only pending/submitted loans can be rejected. Current state: ${getLoanStatusText(loan)}.`;
  };

  const handleCreateLoanRequest = async (e) => {
    e.preventDefault();
    try {
      setRequestLoading(true);
      setRequestResult(null);
      const principal = Number(requestForm.principal);
      const tenureMonths = Number(requestForm.tenureMonths || 12);
      const interestRate = Number(requestForm.interestRate || 15);
      if (!Number.isFinite(principal) || principal <= 0) {
        throw new Error('Principal amount must be greater than 0.');
      }
      if (!Number.isFinite(tenureMonths) || tenureMonths <= 0) {
        throw new Error('Tenure (months) must be greater than 0.');
      }
      if (!Number.isFinite(interestRate) || interestRate < 0) {
        throw new Error('Interest rate must be 0 or greater.');
      }
      if (requestForm.expectedDisbursementDate < requestForm.submittedOnDate) {
        throw new Error('Expected disbursement date cannot be before submitted date.');
      }

      const payload = {
        clientId: Number(requestForm.clientId),
        productId: Number(requestForm.productId),
        loanType: 'individual',
        principal,
        loanTermFrequency: tenureMonths,
        loanTermFrequencyType: 2,
        numberOfRepayments: tenureMonths,
        repaymentEvery: 1,
        repaymentFrequencyType: 2,
        interestRatePerPeriod: interestRate,
        interestType: 0,
        interestCalculationPeriodType: 1,
        amortizationType: 1,
        transactionProcessingStrategyCode: 'mifos-standard-strategy',
        submittedOnDate: isoToFineract(requestForm.submittedOnDate),
        expectedDisbursementDate: isoToFineract(requestForm.expectedDisbursementDate),
        locale: 'en',
        dateFormat: 'dd MMMM yyyy',
      };
      if (requestForm.externalId?.trim()) {
        payload.externalId = requestForm.externalId.trim();
      }
      const res = await cbcService.createLoan(payload);
      setRequestResult({ ok: true, message: res?.message || 'Loan request created' });
      await fetchLoans();
    } catch (err) {
      setRequestResult({ ok: false, message: err.response?.data?.error?.message || err.response?.data?.message || err.message });
    } finally {
      setRequestLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 className="w-8 h-8 text-finnera-500 animate-spin" />
        <span className="ml-3 text-gray-500">Loading loan applications...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-96 gap-4">
        <AlertTriangle className="w-12 h-12 text-red-400" />
        <p className="text-red-600 font-medium">{error}</p>
        <button onClick={fetchLoans} className="btn-primary rounded-lg px-6 py-2 text-sm">Retry</button>
      </div>
    );
  }

  return (
    <PageShell
      title="Loan application review"
      subtitle="Review and manage loan applications from Apache Fineract via the core banking connector."
    >
      {fineractDown && (
        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-5">
          <AlertTriangle className="mt-0.5 h-6 w-6 flex-shrink-0 text-amber-500" />
          <div>
            <p className="text-sm font-semibold text-amber-800">Apache Fineract is not running</p>
            <p className="mt-1 text-sm text-amber-700">
              The Loan Review module requires Apache Fineract (core banking engine) to be running externally.
              Fineract is not included in the Docker Compose stack. Once Fineract is started, loan data will appear here automatically.
            </p>
            <button type="button" onClick={fetchLoans} className="mt-3 text-sm font-medium text-amber-700 underline hover:text-amber-900">
              Retry connection
            </button>
          </div>
        </div>
      )}

      {/* Access Notice */}
      <div className="flex items-start gap-3 bg-blue-50 border border-blue-100 rounded-xl p-4">
        <Info className="w-5 h-5 text-blue-600 mt-0.5 flex-shrink-0" />
        <div>
          <p className="text-sm font-semibold text-blue-800">
            {isLoanOfficer
              ? 'Loan Officer Access: Perform preliminary review and forward recommended loans to Branch Manager'
              : isBranchManager
                ? 'Branch Manager Access: Complete final decisioning on forwarded loan applications'
                : `${user?.role} Access: You can review and process loan applications`}
          </p>
          <p className="text-xs text-blue-600 mt-0.5">
            All actions are logged on the blockchain with your DID for complete audit trail and accountability
          </p>
        </div>
      </div>

      {(isSuperAdmin || isLoanOfficer || isBranchManager) && (
        <div className="card">
          <h3 className="text-base font-semibold text-gray-900 mb-1">Customer loan request intake</h3>
          <p className="text-xs text-gray-500 mb-4">Capture customer loan requests here when customer UI is unavailable.</p>
          <form onSubmit={handleCreateLoanRequest} className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {clients.length > 0 ? (
              <div className="flex flex-col gap-1">
                <div className="flex items-center justify-between">
                  <label className="text-xs text-gray-500 font-medium">Customer (Client)</label>
                  <button
                    type="button"
                    onClick={fetchClients}
                    className="text-xs font-medium text-finnera-600 hover:text-finnera-700 underline disabled:opacity-50"
                    disabled={requestLoading || clientsLoading}
                  >
                    {clientsLoading ? 'Refreshing...' : 'Refresh clients'}
                  </button>
                </div>
                <select
                  className="input-field min-h-[44px]"
                  required
                  value={requestForm.clientId}
                  onChange={(e) => setRequestForm((f) => ({ ...f, clientId: e.target.value }))}
                >
                  {clients.map((c) => (
                    <option key={c.id} value={String(c.id)}>
                      {c.name} (ID: {c.id})
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <input className="input-field min-h-[44px]" placeholder="Client ID" required value={requestForm.clientId}
                onChange={(e) => setRequestForm((f) => ({ ...f, clientId: e.target.value }))} />
            )}
            {loanProducts.length > 0 ? (
              <div className="flex flex-col gap-1">
                <div className="flex items-center justify-between">
                  <label className="text-xs text-gray-500 font-medium">Loan Product</label>
                  <button
                    type="button"
                    onClick={fetchLoanProducts}
                    className="text-xs font-medium text-finnera-600 hover:text-finnera-700 underline disabled:opacity-50"
                    disabled={requestLoading}
                  >
                    Refresh products
                  </button>
                </div>
                <select
                  className="input-field min-h-[44px]"
                  required
                  value={requestForm.productId}
                  onChange={(e) => setRequestForm((f) => ({ ...f, productId: e.target.value }))}
                >
                  {loanProducts.map((p) => (
                    <option key={p.id} value={String(p.id)}>
                      {p.name} (ID: {p.id})
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <input className="input-field min-h-[44px]" placeholder="Loan Product ID" required value={requestForm.productId}
                onChange={(e) => setRequestForm((f) => ({ ...f, productId: e.target.value }))} />
            )}
            <input className="input-field" placeholder="Principal Amount" type="number" min="1" step="0.01" required value={requestForm.principal}
              onChange={(e) => setRequestForm((f) => ({ ...f, principal: e.target.value }))} />
            <input className="input-field" placeholder="Tenure (months)" type="number" min="1" step="1" required value={requestForm.tenureMonths}
              onChange={(e) => setRequestForm((f) => ({ ...f, tenureMonths: e.target.value }))} />
            <input className="input-field" placeholder="Interest rate (% per period)" type="number" min="0" step="0.01" required value={requestForm.interestRate}
              onChange={(e) => setRequestForm((f) => ({ ...f, interestRate: e.target.value }))} />
            <input className="input-field" placeholder="External Reference ID (optional)" value={requestForm.externalId}
              onChange={(e) => setRequestForm((f) => ({ ...f, externalId: e.target.value }))} />
            <div className="flex flex-col gap-1">
              <label className="text-xs text-gray-500 font-medium">Submitted Date</label>
              <input className="input-field" type="date" required value={requestForm.submittedOnDate}
                onChange={(e) => setRequestForm((f) => ({ ...f, submittedOnDate: e.target.value }))} />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs text-gray-500 font-medium">Expected Disbursement</label>
              <input className="input-field" type="date" required value={requestForm.expectedDisbursementDate}
                onChange={(e) => setRequestForm((f) => ({ ...f, expectedDisbursementDate: e.target.value }))} />
            </div>
            <div className="md:col-span-3 flex flex-col gap-1">
              <label className="text-xs text-gray-500 font-medium">Officer Intake Note (optional)</label>
              <textarea
                className="input-field min-h-[84px]"
                placeholder="Add brief customer context (income source, repayment confidence, branch note, etc.)"
                value={requestForm.customerNote}
                onChange={(e) => setRequestForm((f) => ({ ...f, customerNote: e.target.value }))}
              />
            </div>
            {selectedClient && (
              <div className="md:col-span-3 rounded-xl border border-gray-100 bg-gray-50 p-3">
                <p className="text-xs font-semibold uppercase tracking-wider text-gray-500">Selected customer details</p>
                <div className="mt-2 grid grid-cols-1 gap-2 text-sm text-gray-700 md:grid-cols-2 lg:grid-cols-4">
                  <p><span className="font-medium text-gray-900">Name:</span> {selectedClient.name}</p>
                  <p><span className="font-medium text-gray-900">Client ID:</span> {selectedClient.id}</p>
                  <p><span className="font-medium text-gray-900">Office:</span> {selectedClient.officeName || '—'}</p>
                  <p><span className="font-medium text-gray-900">Status:</span> {selectedClient.active === false ? 'Inactive' : 'Active'}</p>
                  <p><span className="font-medium text-gray-900">Account No:</span> {selectedClient.accountNo || '—'}</p>
                  <p><span className="font-medium text-gray-900">External ID:</span> {selectedClient.externalId || '—'}</p>
                  <p><span className="font-medium text-gray-900">Phone:</span> {selectedClient.mobileNo || '—'}</p>
                  <p><span className="font-medium text-gray-900">Email:</span> {selectedClient.emailAddress || '—'}</p>
                </div>
              </div>
            )}
            <button type="submit" disabled={requestLoading} className="btn-primary disabled:opacity-50">
              {requestLoading ? 'Submitting...' : 'Create Loan Request'}
            </button>
          </form>
          {requestResult && (
            <p className={`mt-3 text-sm ${requestResult.ok ? 'text-emerald-600' : 'text-red-600'}`}>{requestResult.message}</p>
          )}
        </div>
      )}

      {loans.length === 0 ? (
        <div className="card text-center py-12">
          <FileText className="w-12 h-12 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 font-medium">No loan applications found</p>
          <p className="text-sm text-gray-400 mt-1">Loan applications from Fineract will appear here</p>
          {(isSuperAdmin || isLoanOfficer) && (
            <p className="mt-6 text-sm text-gray-500">
              To define a new product for applications, use{' '}
              <Link to="/loans/create-product" className="font-semibold text-violet-600 underline hover:text-violet-700">
                Loan product
              </Link>{' '}
              in the sidebar.
            </p>
          )}
        </div>
      ) : (
        <>
          {/* Loan Applications List */}
          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-semibold text-gray-900">
                KYC-Verified Loan Applications
                <span className="ml-2 rounded-full bg-finnera-100 px-2.5 py-0.5 text-xs font-semibold text-finnera-700">
                  {loans.length} of {loansTotalInFineract}
                </span>
              </h3>
              <span className="flex items-center gap-1 text-xs text-gray-400">
                <Shield className="w-3.5 h-3.5" /> KYC verified cohort only
              </span>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
              {loans.map((loan) => {
                const kyc = KYC_BY_LOAN_ID[Number(loan.id)];
                const cardRisk = loanAiRiskById[Number(loan.id)];
                const riskColour = cardRisk?.colour || (kyc?.riskProfile === 'LOW_RISK' ? 'emerald'
                  : kyc?.riskProfile === 'MEDIUM_RISK' ? 'amber' : 'red');
                const riskColourClass = riskColour === 'emerald'
                  ? 'bg-emerald-100 text-emerald-700'
                  : riskColour === 'amber'
                    ? 'bg-amber-100 text-amber-700'
                    : 'bg-red-100 text-red-700';
                const riskLabel = cardRisk?.label
                  ? cardRisk.label.replace(' Risk', '')
                  : (kyc?.riskProfile === 'LOW_RISK' ? 'Low'
                    : kyc?.riskProfile === 'MEDIUM_RISK' ? 'Med' : 'High');
                return (
                  <div
                    key={loan.id}
                    className={`p-4 rounded-xl border cursor-pointer transition-all hover:shadow-md ${
                      selectedLoanId === loan.id
                        ? 'border-finnera-300 bg-finnera-50 shadow-sm'
                        : 'border-gray-100 bg-white hover:border-gray-200'
                    }`}
                    onClick={() => setSelectedLoanId(loan.id)}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-mono text-gray-400">ID: {loan.id}</span>
                        {kyc && (
                          <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-700 flex items-center gap-0.5">
                            <Shield className="w-2.5 h-2.5" /> KYC
                          </span>
                        )}
                      </div>
                      <span className={`badge ${getLoanStatusBadge(loan)}`}>
                        {getLoanStatusText(loan)}
                      </span>
                    </div>
                    <p className="font-semibold text-gray-900">
                      {loan.clientName || loan.clientId || 'Client'}
                    </p>
                    <p className="text-sm text-gray-500">
                      {loan.loanProductName || loan.productName || 'Loan'} &middot;{' '}
                      {loan.currency?.code || loan.currencyCode || ''}{' '}
                      {Number(loan.principal || loan.approvedPrincipal || loan.proposedPrincipal || 0).toLocaleString()}
                    </p>
                    <div className="flex items-center justify-between mt-2 pt-2 border-t border-gray-100">
                      <span className="text-xs text-gray-400">
                        {loan.timeline?.submittedOnDate
                          ? new Date(loan.timeline.submittedOnDate).toLocaleDateString()
                          : loan.submittedOnDate || ''}
                      </span>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${riskColourClass}`}>
                        {riskLabel} risk
                      </span>
                    </div>
                    {kyc && (
                      <p className="mt-1.5 text-[10px] text-gray-400 font-mono truncate">{kyc.kycReferenceId}</p>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Detailed Review Section */}
          {selectedLoan && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* Left Column: Loan Details */}
              <div className="lg:col-span-2 space-y-6">
                <div className="card">
                  <div className="flex items-center justify-between mb-6">
                    <div>
                      <h3 className="text-base font-semibold text-gray-900">Loan Details</h3>
                      {KYC_BY_LOAN_ID[Number(selectedLoan.id)] && (
                        <p className="text-xs text-emerald-600 font-medium mt-0.5 flex items-center gap-1">
                          <Shield className="w-3 h-3" />
                          KYC Verified &mdash; {KYC_BY_LOAN_ID[Number(selectedLoan.id)].kycReferenceId}
                          &nbsp;&middot;&nbsp;{KYC_BY_LOAN_ID[Number(selectedLoan.id)].syntheticUserRef}
                        </p>
                      )}
                    </div>
                    <span className={`badge ${getLoanStatusBadge(selectedLoan)}`}>
                      {getLoanStatusText(selectedLoan)}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <InfoRow icon={User} label="Client" value={selectedLoan.clientName || `Client ${selectedLoan.clientId}`} />
                    <InfoRow icon={CreditCard} label="Loan ID" value={String(selectedLoan.id)} />
                    <InfoRow icon={FileText} label="Product" value={selectedLoan.loanProductName || selectedLoan.productName || '—'} />
                    <InfoRow icon={DollarSign} label="Principal" value={`${selectedLoan.currency?.code || ''} ${Number(selectedLoan.principal || 0).toLocaleString()}`} />
                    <InfoRow icon={Calendar} label="Term" value={`${selectedLoan.termFrequency || selectedLoan.numberOfRepayments || '—'} ${selectedLoan.termPeriodFrequencyType?.value || 'months'}`} />
                    <InfoRow icon={TrendingUp} label="Interest Rate" value={`${selectedLoan.interestRatePerPeriod || selectedLoan.annualInterestRate || '—'}%`} />
                  </div>

                  {/* Financial Summary */}
                  <div className="grid grid-cols-3 gap-4 mt-6 pt-6 border-t border-gray-100">
                    <div className="bg-emerald-50 rounded-xl p-4">
                      <p className="text-xs text-emerald-600 font-medium">Principal</p>
                      <p className="text-xl font-bold text-emerald-700 mt-1">
                        {Number(selectedLoan.principal || 0).toLocaleString()}
                      </p>
                    </div>
                    <div className="bg-blue-50 rounded-xl p-4">
                      <p className="text-xs text-blue-600 font-medium">Interest</p>
                      <p className="text-xl font-bold text-blue-700 mt-1">
                        {selectedLoan.interestRatePerPeriod || selectedLoan.annualInterestRate || '—'}%
                      </p>
                    </div>
                    <div className="bg-purple-50 rounded-xl p-4">
                      <p className="text-xs text-purple-600 font-medium">Repayments</p>
                      <p className="text-xl font-bold text-purple-700 mt-1">
                        {selectedLoan.numberOfRepayments || '—'}
                      </p>
                    </div>
                  </div>
                </div>

                {/* Timeline */}
                {selectedLoan.timeline && (
                  <div className="card">
                    <h3 className="text-base font-semibold text-gray-900 mb-4">Loan Timeline</h3>
                    <div className="space-y-3">
                      {selectedLoan.timeline.submittedOnDate && (
                        <TimelineItem label="Submitted" date={selectedLoan.timeline.submittedOnDate} />
                      )}
                      {selectedLoan.timeline.approvedOnDate && (
                        <TimelineItem label="Approved" date={selectedLoan.timeline.approvedOnDate} />
                      )}
                      {selectedLoan.timeline.expectedDisbursementDate && (
                        <TimelineItem label="Expected Disbursement" date={selectedLoan.timeline.expectedDisbursementDate} />
                      )}
                      {selectedLoan.timeline.actualDisbursementDate && (
                        <TimelineItem label="Disbursed" date={selectedLoan.timeline.actualDisbursementDate} />
                      )}
                      {selectedLoan.timeline.expectedMaturityDate && (
                        <TimelineItem label="Expected Maturity" date={selectedLoan.timeline.expectedMaturityDate} />
                      )}
                    </div>
                  </div>
                )}
              </div>

              {/* Right Column: Actions */}
              <div className="space-y-6">
                <div className="card">
                  <h3 className="text-base font-semibold text-gray-900 mb-4">Loan Actions</h3>

                  <div className="space-y-3">
                    {isLoanOfficer ? (
                      <>
                        <button
                          onClick={() => { setApprovalDecision('forward'); setDecisionNote(''); setShowApprovalModal(true); }}
                          disabled={!canApproveLoan(selectedLoan) || actionLoading}
                          className="w-full bg-finnera-600 hover:bg-finnera-700 text-white flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                          title={getForwardDisabledReason(selectedLoan)}
                        >
                          <ArrowUpRight className="w-5 h-5" />
                          Forward to Branch Manager
                        </button>
                        <button
                          onClick={() => { setApprovalDecision('reject'); setDecisionNote(''); setShowApprovalModal(true); }}
                          disabled={!canRejectLoan(selectedLoan) || actionLoading}
                          className="w-full bg-red-500 hover:bg-red-600 text-white flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                          title={getRejectDisabledReason(selectedLoan)}
                        >
                          <XCircle className="w-5 h-5" />
                          Reject Application
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          onClick={() => { setApprovalDecision('approve'); setDecisionNote(''); setShowApprovalModal(true); }}
                          disabled={!canApproveLoan(selectedLoan) || actionLoading}
                          className="w-full btn-success flex items-center justify-center gap-2 py-3 rounded-xl disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          <CheckCircle2 className="w-5 h-5" />
                          Approve Loan
                        </button>
                        <button
                          onClick={() => { setApprovalDecision('disburse'); setDecisionNote(''); setShowApprovalModal(true); }}
                          disabled={!canDisburseLoan(selectedLoan) || actionLoading}
                          className="w-full bg-blue-500 hover:bg-blue-600 text-white flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          <DollarSign className="w-5 h-5" />
                          Disburse Loan
                        </button>
                        <button
                          onClick={() => { setApprovalDecision('reject'); setDecisionNote(''); setShowApprovalModal(true); }}
                          disabled={!canRejectLoan(selectedLoan) || actionLoading}
                          className="w-full bg-red-500 hover:bg-red-600 text-white flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          <XCircle className="w-5 h-5" />
                          Reject Application
                        </button>
                      </>
                    )}
                  </div>

                  <div className="flex items-center gap-2 mt-4 pt-4 border-t border-gray-100">
                    <Shield className="w-3.5 h-3.5 text-gray-400" />
                    <p className="text-xs text-gray-400">Your decision will be recorded on the blockchain with timestamp and DID signature</p>
                  </div>
                  {(getForwardDisabledReason(selectedLoan) || getRejectDisabledReason(selectedLoan)) && (
                    <p className="mt-2 text-[11px] text-amber-700">
                      {getForwardDisabledReason(selectedLoan) || getRejectDisabledReason(selectedLoan)}
                    </p>
                  )}
                </div>

                {/* AI Risk Score Panel */}
                <div className="card">
                  <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
                    <Sparkles className="w-5 h-5 text-finnera-500" />
                    AI Risk Score
                    <span className="text-xs font-normal text-gray-400">(based on transaction history)</span>
                  </h3>

                  {riskLoading ? (
                    <div className="flex items-center gap-2 py-6 justify-center">
                      <Loader2 className="w-5 h-5 animate-spin text-finnera-500" />
                      <span className="text-sm text-gray-500">Analysing transactions…</span>
                    </div>
                  ) : riskError ? (
                    <p className="text-sm text-red-500 py-4 text-center">{riskError}</p>
                  ) : !riskScore ? (
                    <p className="text-sm text-gray-400 py-4 text-center">Select a loan to compute score</p>
                  ) : (
                    <>
                      {(() => {
                        const riskClasses = RISK_COLOUR_CLASSES[riskScore.colour] || RISK_COLOUR_CLASSES.gray;
                        return (
                          <>
                      {/* Score dial */}
                      <div className="flex items-center gap-5 mb-5">
                        <div className={`relative flex h-20 w-20 items-center justify-center rounded-full border-4 ${riskClasses.ring} flex-shrink-0`}>
                          <span className={`text-2xl font-bold ${riskClasses.text}`}>{riskScore.score}</span>
                        </div>
                        <div>
                          <p className={`text-lg font-bold ${riskClasses.text}`}>{riskScore.label}</p>
                          <p className="text-xs text-gray-500 mt-0.5">
                            {riskScore.grade === 'INSUFFICIENT_DATA'
                              ? 'No transaction history available — score is profile-based.'
                              : 'Computed from savings transaction history + KYC profile.'}
                          </p>
                          <div className="mt-2">
                            <span className={`inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full ${riskClasses.badge}`}>
                              {riskScore.grade === 'LOW_RISK' ? '✓ Recommend Approval'
                                : riskScore.grade === 'MEDIUM_RISK' ? '⚠ Manual Review'
                                : '✕ High Risk — Caution'}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Score breakdown */}
                      <div className="space-y-2">
                        {riskScore.breakdown.map((row) => (
                          <div key={row.label}>
                            <div className="flex justify-between text-xs mb-0.5">
                              <span className="text-gray-600">{row.label}</span>
                              <span className="font-semibold text-gray-800">{row.value} / {row.max}</span>
                            </div>
                            <div className="h-1.5 w-full rounded-full bg-gray-100">
                              <div
                                className={`h-1.5 rounded-full ${riskClasses.bar}`}
                                style={{ width: `${Math.min(100, Math.max(0, Math.round((row.value / row.max) * 100)))}%` }}
                              />
                            </div>
                            <p className="text-[10px] text-gray-400 mt-0.5">{row.hint}</p>
                          </div>
                        ))}
                      </div>

                      <div className="mt-4 flex items-start gap-1.5 rounded-lg bg-gray-50 p-2.5">
                        <Shield className="w-3.5 h-3.5 text-finnera-400 mt-0.5 flex-shrink-0" />
                        <p className="text-[10px] text-gray-500 leading-relaxed">
                          Score is computed locally from savings account transaction history.
                          Higher scores indicate better repayment behaviour and savings discipline.
                        </p>
                      </div>
                          </>
                        );
                      })()}
                    </>
                  )}
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {/* Approval / Disburse Modal */}
      {showApprovalModal && selectedLoan && (
        <div className="fixed inset-0 bg-black/30 z-50 flex items-center justify-center p-4" onClick={() => setShowApprovalModal(false)}>
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 animate-fadeIn" onClick={e => e.stopPropagation()}>
            <div className={`w-14 h-14 rounded-full flex items-center justify-center mx-auto mb-4 ${
              approvalDecision === 'approve' || approvalDecision === 'forward'
                ? 'bg-emerald-100'
                : approvalDecision === 'reject'
                  ? 'bg-red-100'
                  : 'bg-blue-100'
            }`}>
              {approvalDecision === 'approve' || approvalDecision === 'forward' ? (
                <CheckCircle2 className="w-7 h-7 text-emerald-600" />
              ) : approvalDecision === 'reject' ? (
                <XCircle className="w-7 h-7 text-red-600" />
              ) : (
                <DollarSign className="w-7 h-7 text-blue-600" />
              )}
            </div>
            <h2 className="text-xl font-bold text-gray-900 text-center">
              {approvalDecision === 'forward'
                ? 'Forward to Branch Manager?'
                : approvalDecision === 'approve'
                  ? 'Approve Loan?'
                  : approvalDecision === 'reject'
                    ? 'Reject Loan?'
                    : 'Disburse Loan?'}
            </h2>
            <p className="text-sm text-gray-500 text-center mt-2">
              Loan #{selectedLoan.id} &middot; {selectedLoan.clientName || `Client ${selectedLoan.clientId}`} &middot;{' '}
              {Number(selectedLoan.principal || 0).toLocaleString()}
            </p>

            {(approvalDecision === 'forward' || approvalDecision === 'reject' || approvalDecision === 'approve') && (
              <div className="mt-4">
                <label className="block text-xs font-semibold text-gray-500 mb-1.5 uppercase tracking-wide">
                  Review Note
                </label>
                <textarea
                  value={decisionNote}
                  onChange={(e) => setDecisionNote(e.target.value)}
                  rows={3}
                  className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-finnera-400"
                  placeholder={approvalDecision === 'reject' ? 'Reason for rejection' : 'Optional note for audit and manager review'}
                />
              </div>
            )}

            <div className="flex items-center gap-2 mt-4 p-3 bg-gray-50 rounded-lg">
              <Shield className="w-4 h-4 text-finnera-500 flex-shrink-0" />
              <p className="text-xs text-gray-500">This action will be recorded on the blockchain with your DID signature</p>
            </div>

            <div className="flex gap-3 mt-6">
              <button
                onClick={() => setShowApprovalModal(false)}
                className="flex-1 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-xl text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                disabled={actionLoading}
                onClick={
                  approvalDecision === 'forward'
                    ? handleForward
                    : approvalDecision === 'approve'
                    ? handleApprove
                    : approvalDecision === 'reject'
                      ? handleReject
                      : handleDisburse
                }
                className={`flex-1 py-2.5 rounded-xl text-sm font-semibold text-white transition-colors flex items-center justify-center gap-2 disabled:opacity-50 ${
                  approvalDecision === 'approve' || approvalDecision === 'forward'
                    ? 'bg-emerald-500 hover:bg-emerald-600'
                    : approvalDecision === 'reject'
                      ? 'bg-red-500 hover:bg-red-600'
                      : 'bg-blue-500 hover:bg-blue-600'
                }`}
              >
                {actionLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                {approvalDecision === 'forward'
                  ? 'Confirm Forward'
                  : approvalDecision === 'approve'
                    ? 'Confirm Approval'
                    : approvalDecision === 'reject'
                      ? 'Confirm Rejection'
                      : 'Confirm Disbursement'}
              </button>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}

function InfoRow({ icon: Icon, label, value }) {
  return (
    <div className="flex items-center gap-2">
      <Icon className="w-4 h-4 text-gray-400 flex-shrink-0" />
      <div>
        <p className="text-xs text-gray-500">{label}</p>
        <p className="text-sm font-medium text-gray-900">{value}</p>
      </div>
    </div>
  );
}

function TimelineItem({ label, date }) {
  const formatted = Array.isArray(date)
    ? new Date(date[0], (date[1] || 1) - 1, date[2] || 1).toLocaleDateString()
    : typeof date === 'string' ? date : String(date);
  return (
    <div className="flex items-center gap-3">
      <div className="w-2 h-2 bg-finnera-500 rounded-full flex-shrink-0" />
      <div className="flex items-center justify-between flex-1">
        <p className="text-sm font-medium text-gray-700">{label}</p>
        <p className="text-sm text-gray-500 font-mono">{formatted}</p>
      </div>
    </div>
  );
}
