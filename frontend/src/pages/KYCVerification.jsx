import { useState } from 'react';
import {
  UserCheck, Shield, Search, CheckCircle2, XCircle, Clock,
  AlertTriangle, Eye, FileText, Radio, ChevronRight, RefreshCw,
  Database, Globe, Filter, Loader2
} from 'lucide-react';
import kycService from '../services/kycService';
import { useAuth } from '../context/AuthContext';
import { PageShell } from '../components/layout/PageShell';

export default function KYCVerification() {
  const { user } = useAuth();
  const CNIC_REGEX = /^\d{5}-\d{7}-\d$/;
  const PHONE_REGEX = /^\+92\d{10}$/;

  // Onboard form state
  const [onboardForm, setOnboardForm] = useState({
    firstName: '', lastName: '', cnic: '', dateOfBirth: '', phone: '', email: '', address: '',
  });
  const [onboardLoading, setOnboardLoading] = useState(false);
  const [onboardResult, setOnboardResult] = useState(null);

  // CNIC verification state
  const [cnicForm, setCnicForm] = useState({ cnic: '', name: '', dateOfBirth: '' });
  const [cnicLoading, setCnicLoading] = useState(false);
  const [cnicResult, setCnicResult] = useState(null);

  // Status check state
  const [referenceId, setReferenceId] = useState('');
  const [statusLoading, setStatusLoading] = useState(false);
  const [statusResult, setStatusResult] = useState(null);

  // Active tab
  const [activeTab, setActiveTab] = useState('onboard');

  const extractErrorMessage = (err) => {
    const data = err?.response?.data;
    const nested = data?.error;
    const fieldErrors = data?.errors;
    if (typeof fieldErrors === 'string' && fieldErrors.trim()) return fieldErrors;
    if (Array.isArray(fieldErrors) && fieldErrors.length > 0) return fieldErrors.join(', ');
    return nested?.message || nested?.details || data?.detail || data?.message || err?.message || 'Request failed';
  };

  const handleOnboard = async (e) => {
    e.preventDefault();
    const cnic = String(onboardForm.cnic || '').trim();
    const phone = String(onboardForm.phone || '').trim();
    if (!CNIC_REGEX.test(cnic)) {
      setOnboardResult({ success: false, error: 'CNIC must match #####-#######-#' });
      return;
    }
    if (phone && !PHONE_REGEX.test(phone)) {
      setOnboardResult({ success: false, error: 'Phone must match +92##########' });
      return;
    }
    try {
      setOnboardLoading(true);
      setOnboardResult(null);
      const res = await kycService.onboard({
        ...onboardForm,
        cnic,
        phone,
      });
      setOnboardResult({ success: true, data: res.data || res });
    } catch (err) {
      setOnboardResult({ success: false, error: extractErrorMessage(err) });
    } finally {
      setOnboardLoading(false);
    }
  };

  const handleCnicVerify = async (e) => {
    e.preventDefault();
    try {
      setCnicLoading(true);
      setCnicResult(null);
      const res = await kycService.verifyCnic(cnicForm);
      setCnicResult({ success: true, data: res.data || res });
    } catch (err) {
      setCnicResult({ success: false, error: extractErrorMessage(err) });
    } finally {
      setCnicLoading(false);
    }
  };

  const handleStatusCheck = async (e) => {
    e.preventDefault();
    try {
      setStatusLoading(true);
      setStatusResult(null);
      const res = await kycService.getStatus(referenceId);
      setStatusResult({ success: true, data: res.data || res });
    } catch (err) {
      setStatusResult({ success: false, error: extractErrorMessage(err) });
    } finally {
      setStatusLoading(false);
    }
  };

  const tabs = [
    { key: 'onboard', label: 'Client Onboarding', icon: UserCheck },
    { key: 'verify', label: 'CNIC Verification', icon: Shield },
    { key: 'status', label: 'Status Check', icon: Search },
  ];

  return (
    <PageShell
      title="KYC / AML verification"
      subtitle="Identity intake, CNIC checks against NADRA, and AML screening aligned with institutional policy."
    >

      {/* Info Banner */}
      <div className="flex items-start gap-3 bg-finnera-50 border border-finnera-100 rounded-xl p-4">
        <Shield className="w-5 h-5 text-finnera-600 mt-0.5 flex-shrink-0" />
        <div>
          <p className="text-sm font-semibold text-finnera-800">
            KYC/AML Compliance: All verifications are cross-checked with NADRA and international sanctions lists
          </p>
          <p className="text-xs text-finnera-600 mt-0.5">
            Decentralized Identity (DID) verification ensures tamper-proof identity records anchored to blockchain
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-gray-200 pb-0">
        {tabs.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium rounded-t-lg transition-colors ${
              activeTab === tab.key
                ? 'bg-white border border-b-white border-gray-200 text-finnera-700 -mb-px'
                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-50'
            }`}
          >
            <tab.icon className="w-4 h-4" />
            {tab.label}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Content */}
        <div className="lg:col-span-2">
          {/* Onboarding Tab */}
          {activeTab === 'onboard' && (
            <div className="card">
              <h3 className="text-lg font-semibold text-gray-900 mb-1">Client Onboarding</h3>
              <p className="text-sm text-gray-500 mb-6">Submit client details to initiate KYC verification and account creation</p>

              <form onSubmit={handleOnboard} className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">First Name</label>
                    <input
                      type="text" required
                      value={onboardForm.firstName}
                      onChange={e => setOnboardForm(f => ({ ...f, firstName: e.target.value }))}
                      className="input-field"
                      placeholder="Legal first name"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
                    <input
                      type="text" required
                      value={onboardForm.lastName}
                      onChange={e => setOnboardForm(f => ({ ...f, lastName: e.target.value }))}
                      className="input-field"
                      placeholder="Legal last name"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">CNIC Number</label>
                    <input
                      type="text" required
                      value={onboardForm.cnic}
                      onChange={e => setOnboardForm(f => ({ ...f, cnic: e.target.value }))}
                      className="input-field font-mono"
                      placeholder="#####-#######-#"
                      pattern="\d{5}-\d{7}-\d{1}"
                      title="CNIC format: #####-#######-#"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Date of Birth</label>
                    <input
                      type="date" required
                      value={onboardForm.dateOfBirth}
                      onChange={e => setOnboardForm(f => ({ ...f, dateOfBirth: e.target.value }))}
                      className="input-field"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Phone</label>
                    <input
                      type="tel"
                      value={onboardForm.phone}
                      onChange={e => setOnboardForm(f => ({ ...f, phone: e.target.value }))}
                      className="input-field"
                      placeholder="+92##########"
                      pattern="\+92\d{10}"
                      title="Phone format: +92##########"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                    <input
                      type="email"
                      value={onboardForm.email}
                      onChange={e => setOnboardForm(f => ({ ...f, email: e.target.value }))}
                      className="input-field"
                      placeholder="name@organization.com"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Address</label>
                  <input
                    type="text"
                    value={onboardForm.address}
                    onChange={e => setOnboardForm(f => ({ ...f, address: e.target.value }))}
                    className="input-field"
                    placeholder="Street, city, postal code"
                  />
                </div>

                <button
                  type="submit"
                  disabled={onboardLoading}
                  className="btn-primary rounded-xl py-2.5 px-6 flex items-center gap-2 disabled:opacity-50"
                >
                  {onboardLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <UserCheck className="w-4 h-4" />}
                  Onboard
                </button>
              </form>

              {onboardResult && (
                <div className={`mt-4 p-4 rounded-xl ${onboardResult.success ? 'bg-emerald-50 border border-emerald-200' : 'bg-red-50 border border-red-200'}`}>
                  {onboardResult.success ? (
                    <div>
                      <p className="text-sm font-semibold text-emerald-800 flex items-center gap-2">
                        <CheckCircle2 className="w-4 h-4" /> Onboarding request submitted successfully
                      </p>
                      {onboardResult.data?.referenceId && (
                        <p className="text-xs text-emerald-600 mt-1 font-mono">Reference ID: {onboardResult.data.referenceId}</p>
                      )}
                      {onboardResult.data?.clientId && (
                        <p className="text-xs text-emerald-600 mt-1 font-mono">Client ID: {onboardResult.data.clientId}</p>
                      )}
                      <pre className="text-xs text-emerald-700 mt-2 bg-emerald-100 p-2 rounded overflow-auto max-h-40">
                        {JSON.stringify(onboardResult.data, null, 2)}
                      </pre>
                    </div>
                  ) : (
                    <p className="text-sm text-red-700 flex items-center gap-2">
                      <XCircle className="w-4 h-4" /> {onboardResult.error}
                    </p>
                  )}
                </div>
              )}
            </div>
          )}

          {/* CNIC Verification Tab */}
          {activeTab === 'verify' && (
            <div className="card">
              <h3 className="text-lg font-semibold text-gray-900 mb-1">CNIC Verification</h3>
              <p className="text-sm text-gray-500 mb-6">Verify a CNIC against NADRA records</p>

              <form onSubmit={handleCnicVerify} className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">CNIC Number</label>
                    <input
                      type="text" required
                      value={cnicForm.cnic}
                      onChange={e => setCnicForm(f => ({ ...f, cnic: e.target.value }))}
                      className="input-field font-mono"
                      placeholder="#####-#######-#"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Full Name</label>
                    <input
                      type="text" required
                      value={cnicForm.name}
                      onChange={e => setCnicForm(f => ({ ...f, name: e.target.value }))}
                      className="input-field"
                      placeholder="Full name as on CNIC"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Date of Birth</label>
                    <input
                      type="date" required
                      value={cnicForm.dateOfBirth}
                      onChange={e => setCnicForm(f => ({ ...f, dateOfBirth: e.target.value }))}
                      className="input-field"
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={cnicLoading}
                  className="btn-primary rounded-xl py-2.5 px-6 flex items-center gap-2 disabled:opacity-50"
                >
                  {cnicLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Shield className="w-4 h-4" />}
                  Verify CNIC
                </button>
              </form>

              {cnicResult && (
                <div className={`mt-4 p-4 rounded-xl ${cnicResult.success ? 'bg-emerald-50 border border-emerald-200' : 'bg-red-50 border border-red-200'}`}>
                  {cnicResult.success ? (
                    <div>
                      <p className="text-sm font-semibold text-emerald-800 flex items-center gap-2">
                        <CheckCircle2 className="w-4 h-4" /> CNIC Verification Complete
                      </p>
                      {cnicResult.data?.status && (
                        <span className={`inline-block mt-2 badge ${
                          cnicResult.data.status === 'VERIFIED' ? 'badge-success' : 'badge-danger'
                        }`}>
                          {cnicResult.data.status}
                        </span>
                      )}
                      {cnicResult.data?.message && (
                        <p className={`text-xs mt-2 ${
                          cnicResult.data.status === 'VERIFIED' ? 'text-emerald-700' : 'text-red-700'
                        }`}>
                          {cnicResult.data.message}
                        </p>
                      )}
                      <pre className="text-xs text-emerald-700 mt-2 bg-emerald-100 p-2 rounded overflow-auto max-h-40">
                        {JSON.stringify(cnicResult.data, null, 2)}
                      </pre>
                    </div>
                  ) : (
                    <p className="text-sm text-red-700 flex items-center gap-2">
                      <XCircle className="w-4 h-4" /> {cnicResult.error}
                    </p>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Status Check Tab */}
          {activeTab === 'status' && (
            <div className="card">
              <h3 className="text-lg font-semibold text-gray-900 mb-1">KYC Status Check</h3>
              <p className="text-sm text-gray-500 mb-6">Look up the status of a KYC onboarding request by reference ID</p>

              <form onSubmit={handleStatusCheck} className="flex gap-4">
                <div className="flex-1">
                  <input
                    type="text" required
                    value={referenceId}
                    onChange={e => setReferenceId(e.target.value)}
                    className="input-field font-mono"
                    placeholder="Reference ID from onboarding response"
                  />
                </div>
                <button
                  type="submit"
                  disabled={statusLoading}
                  className="btn-primary rounded-xl py-2.5 px-6 flex items-center gap-2 disabled:opacity-50"
                >
                  {statusLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
                  Check Status
                </button>
              </form>

              {statusResult && (
                <div className={`mt-4 p-4 rounded-xl ${statusResult.success ? 'bg-blue-50 border border-blue-200' : 'bg-red-50 border border-red-200'}`}>
                  {statusResult.success ? (
                    <div>
                      <p className="text-sm font-semibold text-blue-800 flex items-center gap-2">
                        <Eye className="w-4 h-4" /> KYC Status Retrieved
                      </p>
                      {statusResult.data?.status && (
                        <span className={`inline-block mt-2 badge ${
                          statusResult.data.status === 'VERIFIED' || statusResult.data.status === 'APPROVED' ? 'badge-success' :
                          statusResult.data.status === 'PENDING' ? 'badge-warning' : 'badge-danger'
                        }`}>
                          {statusResult.data.status}
                        </span>
                      )}
                      <pre className="text-xs text-blue-700 mt-2 bg-blue-100 p-2 rounded overflow-auto max-h-40">
                        {JSON.stringify(statusResult.data, null, 2)}
                      </pre>
                    </div>
                  ) : (
                    <p className="text-sm text-red-700 flex items-center gap-2">
                      <XCircle className="w-4 h-4" /> {statusResult.error}
                    </p>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Right Sidebar */}
        <div className="space-y-6">
          {/* NADRA Integration */}
          <div className="card bg-green-50 border-green-100">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center">
                <UserCheck className="w-5 h-5 text-green-700" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-green-900">NADRA Integration</h3>
                <p className="text-xs text-green-600">Identity verification service</p>
              </div>
            </div>
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-green-700">Service Status</span>
                <span className="badge-success">Connected</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-green-700">Verification Method</span>
                <span className="font-semibold text-green-900">CNIC + Biometric</span>
              </div>
            </div>
          </div>

          {/* DID Verification */}
          <div className="card bg-finnera-50 border-finnera-100">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-10 h-10 bg-finnera-100 rounded-lg flex items-center justify-center">
                <Radio className="w-5 h-5 text-finnera-700" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-finnera-900">Decentralized Identity</h3>
                <p className="text-xs text-finnera-600">Blockchain-backed DID verification</p>
              </div>
            </div>
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-finnera-700">Network</span>
                <span className="font-semibold text-finnera-900">Hyperledger Fabric</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-finnera-700">Protocol</span>
                <span className="font-semibold text-finnera-900">W3C DID Standard</span>
              </div>
            </div>
          </div>

          {/* AML Notice */}
          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-semibold text-gray-900">AML Screening</h3>
              <Database className="w-4 h-4 text-finnera-500" />
            </div>
            <div className="space-y-3">
              <div className="p-3 bg-gray-50 rounded-lg">
                <p className="text-sm font-medium text-gray-900">Sanctions Lists</p>
                <p className="text-xs text-gray-500 mt-1">OFAC, UN, EU, FATF, and local watchlists are checked during onboarding</p>
              </div>
            </div>
            <div className="mt-4 pt-4 border-t border-gray-100">
              <div className="flex items-center gap-2 text-xs text-gray-500">
                <Globe className="w-3.5 h-3.5" />
                <span>Screening uses Levenshtein distance matching for fuzzy name comparison</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </PageShell>
  );
}
