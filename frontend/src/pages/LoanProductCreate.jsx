import { useState } from 'react';
import { useNavigate, Link, Navigate } from 'react-router-dom';
import { FileText, Loader2, ArrowLeft } from 'lucide-react';
import cbcService from '../services/cbcService';
import { hasSuperAdminAccess, useAuth } from '../context/AuthContext';
import { PageShell } from '../components/layout/PageShell';

export default function LoanProductCreate() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const isSuperAdmin = hasSuperAdminAccess(user);
  const isLoanOfficer = user?.type === 'loan_officer';

  const [form, setForm] = useState({
    name: '',
    shortName: '',
    description: '',
    currencyCode: 'PKR',
    principal: '',
    numberOfRepayments: '',
    repaymentEvery: '',
    interestRatePerPeriod: '',
  });
  const [loading, setLoading] = useState(false);

  if (!isSuperAdmin && !isLoanOfficer) {
    return <Navigate to="/loans" replace />;
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.name.trim() || !form.shortName.trim()) {
      window.alert('Loan product name and short name are required');
      return;
    }

    const principal = Number(form.principal || 0);
    const repayments = Number(form.numberOfRepayments || 0);
    const repaymentEvery = Number(form.repaymentEvery || 0);
    const interestRate = Number(form.interestRatePerPeriod || 0);
    if (principal <= 0 || repayments <= 0 || repaymentEvery <= 0 || interestRate < 0) {
      window.alert('Principal, repayment count and repayment interval must be valid positive numbers');
      return;
    }

    try {
      setLoading(true);
      const payload = {
        name: form.name.trim(),
        shortName: form.shortName.trim(),
        description: form.description?.trim() || undefined,
        currencyCode: form.currencyCode || 'PKR',
        digitsAfterDecimal: 2,
        principal,
        minPrincipal: principal,
        maxPrincipal: principal,
        numberOfRepayments: repayments,
        minNumberOfRepayments: repayments,
        maxNumberOfRepayments: repayments,
        repaymentEvery,
        interestRatePerPeriod: interestRate,
        minInterestRatePerPeriod: interestRate,
        maxInterestRatePerPeriod: interestRate,
        interestRateFrequencyType: 2,
        amortizationType: 1,
        interestType: 0,
        interestCalculationPeriodType: 1,
        transactionProcessingStrategyCode: 'mifos-standard-strategy',
        accountingRule: 1,
      };
      const created = await cbcService.createLoanProduct(payload);
      const productId =
        created?.data?.id ??
        created?.data?.resourceId ??
        created?.data?.productId ??
        created?.id ??
        created?.resourceId ??
        created?.productId;
      window.alert(
        productId
          ? `Loan product created successfully (ID: ${productId})`
          : 'Loan product created successfully'
      );
      navigate('/loans', { replace: true });
    } catch (err) {
      const detail =
        err.response?.data?.error?.message ||
        err.response?.data?.error?.details ||
        err.response?.data?.message ||
        err.message;
      window.alert('Failed to create loan product: ' + detail);
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageShell
      title="Create loan product"
      subtitle="Define a new loan product in Apache Fineract via the core banking connector. This is separate from reviewing individual applications."
      actions={(
        <Link
          to="/loans"
          className="inline-flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition-colors hover:bg-gray-50"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to loan review
        </Link>
      )}
    >
      <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm shadow-gray-200/40">
        <div className="mb-6 flex items-start gap-3 rounded-xl border border-violet-100 bg-violet-50/60 p-4">
          <FileText className="mt-0.5 h-5 w-5 shrink-0 text-violet-600" />
          <p className="text-sm leading-relaxed text-violet-900">
            Products control terms (principal band, tenor, rate) that new applications can select. After saving, officers can submit applications against this product from{' '}
            <Link to="/loans" className="font-semibold underline hover:text-violet-700">Loan review</Link>.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            <div className="md:col-span-1">
              <label className="mb-1 block text-xs font-medium text-gray-600">Product name</label>
              <input
                className="input-field"
                placeholder="Product name"
                value={form.name}
                onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
              />
            </div>
            <div className="md:col-span-1">
              <label className="mb-1 block text-xs font-medium text-gray-600">Short name</label>
              <input
                className="input-field"
                placeholder="Short name (code)"
                value={form.shortName}
                onChange={(e) => setForm((prev) => ({ ...prev, shortName: e.target.value }))}
              />
            </div>
            <div className="md:col-span-2">
              <label className="mb-1 block text-xs font-medium text-gray-600">Description</label>
              <input
                className="input-field"
                placeholder="Description"
                value={form.description}
                onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Currency</label>
              <input
                className="input-field"
                placeholder="e.g. USD, PKR"
                value={form.currencyCode}
                onChange={(e) => setForm((prev) => ({ ...prev, currencyCode: e.target.value.toUpperCase() }))}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Principal</label>
              <input
                type="number"
                className="input-field"
                placeholder="Principal"
                value={form.principal}
                onChange={(e) => setForm((prev) => ({ ...prev, principal: e.target.value }))}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Number of repayments</label>
              <input
                type="number"
                className="input-field"
                placeholder="No. of repayments"
                value={form.numberOfRepayments}
                onChange={(e) => setForm((prev) => ({ ...prev, numberOfRepayments: e.target.value }))}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Repayment every (months)</label>
              <input
                type="number"
                className="input-field"
                placeholder="Repayment interval"
                value={form.repaymentEvery}
                onChange={(e) => setForm((prev) => ({ ...prev, repaymentEvery: e.target.value }))}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Interest rate (%)</label>
              <input
                type="number"
                step="0.01"
                className="input-field"
                placeholder="Interest rate (%)"
                value={form.interestRatePerPeriod}
                onChange={(e) => setForm((prev) => ({ ...prev, interestRatePerPeriod: e.target.value }))}
              />
            </div>
          </div>

          <div className="flex flex-wrap gap-3 pt-2">
            <button
              type="submit"
              disabled={loading}
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-violet-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-violet-700 disabled:opacity-50"
            >
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              Create product
            </button>
            <Link
              to="/loans"
              className="inline-flex items-center rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Cancel
            </Link>
          </div>
        </form>
      </div>
    </PageShell>
  );
}
