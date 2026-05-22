import { useState } from 'react';
import {
  Search, User, CreditCard, Phone, Mail, History, AlertTriangle,
  Loader2, Server, CheckCircle2, XCircle, RefreshCw, Activity, Wifi
} from 'lucide-react';
import cbcService from '../services/cbcService';
import { useAuth } from '../context/AuthContext';
import { PageShell } from '../components/layout/PageShell';

const HEALTH_ENDPOINTS = [
  { name: 'API Gateway', url: '/actuator/health', port: 8080 },
  { name: 'Auth Service', url: '/actuator/health', port: 9000 },
  { name: 'CBC Service', url: '/actuator/health', port: 8081 },
  { name: 'KYC/AML Service', url: '/actuator/health', port: 8082 },
  { name: 'Fraud Service', url: '/actuator/health', port: 8083 },
  { name: 'Audit Service', url: '/actuator/health', port: 8084 },
];

export default function ServiceDesk() {
  const { user } = useAuth();

  const [searchQuery, setSearchQuery] = useState('');
  const [searchLoading, setSearchLoading] = useState(false);
  const [client, setClient] = useState(null);
  const [clientError, setClientError] = useState(null);
  const [clients, setClients] = useState([]);

  const [healthResults, setHealthResults] = useState([]);
  const [healthLoading, setHealthLoading] = useState(false);

  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    try {
      setSearchLoading(true);
      setClientError(null);
      setClient(null);
      setClients([]);

      const isNumeric = /^\d+$/.test(searchQuery.trim());
      if (isNumeric) {
        const res = await cbcService.getClient(searchQuery.trim());
        const data = res.data || res;
        setClient(data);
      } else {
        const res = await cbcService.getClients();
        const all = Array.isArray(res.data) ? res.data : (Array.isArray(res) ? res : []);
        const filtered = all.filter(c =>
          (c.displayName || c.firstname || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
          (c.externalId || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
          String(c.accountNo || '').includes(searchQuery)
        );
        if (filtered.length === 1) {
          setClient(filtered[0]);
        } else {
          setClients(filtered);
        }
      }
    } catch (err) {
      setClientError(err.response?.data?.message || err.message || 'Client not found');
    } finally {
      setSearchLoading(false);
    }
  };

  const checkHealth = async () => {
    setHealthLoading(true);
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '';

    const results = await Promise.all(
      HEALTH_ENDPOINTS.map(async (ep) => {
        const start = performance.now();
        try {
          const res = await fetch(`${baseUrl}${ep.url}`, {
            signal: AbortSignal.timeout(5000),
            headers: { Authorization: `Bearer ${localStorage.getItem('accessToken') || ''}` },
          });
          const latency = Math.round(performance.now() - start);
          if (res.ok) {
            return { ...ep, status: 'UP', latency };
          }
          return { ...ep, status: 'DOWN', latency, error: `HTTP ${res.status}` };
        } catch (err) {
          const latency = Math.round(performance.now() - start);
          return { ...ep, status: 'DOWN', latency, error: err.message };
        }
      })
    );
    setHealthResults(results);
    setHealthLoading(false);
  };

  return (
    <PageShell
      title="Customer service desk"
      subtitle="Search clients, review account context, and run quick service health checks against the API gateway."
      meta={(
        <div className="text-right">
          <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400">Signed in as</p>
          <p className="text-sm font-semibold text-gray-900">{user?.name}</p>
          <p className="text-xs text-gray-500">{user?.role}</p>
        </div>
      )}
    >

      <div className="flex items-start gap-3 rounded-xl border border-emerald-100 bg-emerald-50 p-4">
        <AlertTriangle className="w-5 h-5 text-emerald-600 mt-0.5 flex-shrink-0" />
        <div>
          <p className="text-sm font-semibold text-emerald-800">
            Customer data is protected. Only authorised support staff may view and assist with accounts.
          </p>
          <p className="text-xs text-emerald-700 mt-0.5">
            All lookups and actions are logged for audit and compliance.
          </p>
        </div>
      </div>

      {/* Search bar */}
      <div className="card">
        <div className="flex items-center gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              className="input-field pl-10"
              placeholder="Search by client ID, account number, or name..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <button
            onClick={handleSearch}
            disabled={searchLoading}
            className="btn-primary rounded-lg text-sm px-5 py-2.5 hover:opacity-90 transition-opacity flex items-center gap-2 disabled:opacity-50"
          >
            {searchLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
            Search
          </button>
        </div>
      </div>

      {/* Search error */}
      {clientError && (
        <div className="flex items-center gap-3 p-4 bg-red-50 border border-red-200 rounded-xl">
          <XCircle className="w-5 h-5 text-red-500" />
          <p className="text-sm text-red-700">{clientError}</p>
        </div>
      )}

      {/* Multiple clients found */}
      {clients.length > 1 && (
        <div className="card">
          <h3 className="text-base font-semibold text-gray-900 mb-3">Search Results ({clients.length} clients)</h3>
          <div className="space-y-2">
            {clients.map(c => (
              <button
                key={c.id}
                onClick={() => { setClient(c); setClients([]); }}
                className="w-full flex items-center justify-between p-3 bg-gray-50 hover:bg-finnera-50 rounded-lg border border-gray-100 transition-colors text-left"
              >
                <div>
                  <p className="text-sm font-semibold text-gray-900">{c.displayName || `${c.firstname || ''} ${c.lastname || ''}`}</p>
                  <p className="text-xs text-gray-500">ID: {c.id} &middot; Account: {c.accountNo || '—'}</p>
                </div>
                <span className={`badge text-xs ${c.active ? 'badge-success' : 'badge-warning'}`}>
                  {c.active ? 'Active' : 'Inactive'}
                </span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Client details */}
      {client && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 card">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-full bg-finnera-100 flex items-center justify-center text-finnera-700 font-bold">
                  {(client.displayName || client.firstname || '?').charAt(0).toUpperCase()}
                </div>
                <div>
                  <p className="text-lg font-semibold text-gray-900">
                    {client.displayName || `${client.firstname || ''} ${client.lastname || ''}`}
                  </p>
                  <p className="text-xs text-gray-500">
                    Client since {client.activationDate ? (Array.isArray(client.activationDate)
                      ? new Date(client.activationDate[0], (client.activationDate[1] || 1) - 1, client.activationDate[2] || 1).toLocaleDateString()
                      : client.activationDate)
                      : '—'}
                  </p>
                </div>
              </div>
              <span className={`badge ${client.active ? 'badge-success' : 'badge-warning'}`}>
                {client.active ? 'Active' : 'Inactive'}
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <InfoRow icon={User} label="Client ID" value={String(client.id)} />
              <InfoRow icon={CreditCard} label="Account Number" value={client.accountNo || '—'} />
              <InfoRow icon={User} label="External ID" value={client.externalId || '—'} />
              <InfoRow icon={User} label="Office" value={client.officeName || '—'} />
              {client.mobileNo && <InfoRow icon={Phone} label="Phone" value={client.mobileNo} />}
              {client.emailAddress && <InfoRow icon={Mail} label="Email" value={client.emailAddress} />}
            </div>

            {client.timeline && (
              <div className="grid grid-cols-3 gap-4 mt-5 pt-4 border-t border-gray-100">
                {client.timeline.submittedOnDate && (
                  <div className="bg-blue-50 rounded-lg p-3">
                    <p className="text-xs text-blue-600 font-medium">Submitted</p>
                    <p className="text-sm font-bold text-blue-700 mt-1">
                      {Array.isArray(client.timeline.submittedOnDate)
                        ? new Date(client.timeline.submittedOnDate[0], (client.timeline.submittedOnDate[1] || 1) - 1, client.timeline.submittedOnDate[2] || 1).toLocaleDateString()
                        : client.timeline.submittedOnDate}
                    </p>
                  </div>
                )}
                {client.timeline.activatedOnDate && (
                  <div className="bg-emerald-50 rounded-lg p-3">
                    <p className="text-xs text-emerald-600 font-medium">Activated</p>
                    <p className="text-sm font-bold text-emerald-700 mt-1">
                      {Array.isArray(client.timeline.activatedOnDate)
                        ? new Date(client.timeline.activatedOnDate[0], (client.timeline.activatedOnDate[1] || 1) - 1, client.timeline.activatedOnDate[2] || 1).toLocaleDateString()
                        : client.timeline.activatedOnDate}
                    </p>
                  </div>
                )}
                <div className="bg-purple-50 rounded-lg p-3">
                  <p className="text-xs text-purple-600 font-medium">Status</p>
                  <p className="text-sm font-bold text-purple-700 mt-1">
                    {client.status?.value || (client.active ? 'Active' : 'Inactive')}
                  </p>
                </div>
              </div>
            )}
          </div>

          {/* Quick info sidebar */}
          <div className="card">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <History className="w-4 h-4 text-finnera-600" />
                <h2 className="text-sm font-semibold text-gray-900">Client Details</h2>
              </div>
            </div>
            <div className="space-y-3">
              <DetailRow label="Client Type" value={client.clientType || client.legalForm?.value || '—'} />
              <DetailRow label="Gender" value={client.gender?.name || '—'} />
              <DetailRow label="Date of Birth" value={
                client.dateOfBirth ? (Array.isArray(client.dateOfBirth)
                  ? new Date(client.dateOfBirth[0], (client.dateOfBirth[1] || 1) - 1, client.dateOfBirth[2] || 1).toLocaleDateString()
                  : client.dateOfBirth) : '—'
              } />
              <DetailRow label="Office ID" value={String(client.officeId || '—')} />
              <DetailRow label="Staff" value={client.staffName || '—'} />
              {client.savingsProductName && (
                <DetailRow label="Savings Product" value={client.savingsProductName} />
              )}
            </div>
          </div>
        </div>
      )}

      {/* Service Health Check */}
      <div className="card">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2">
              <Wifi className="w-5 h-5 text-finnera-600" />
              Service Health Check
            </h3>
            <p className="text-sm text-gray-500">Check connectivity to backend microservices</p>
          </div>
          <button
            onClick={checkHealth}
            disabled={healthLoading}
            className="btn-primary rounded-lg text-sm px-5 py-2 flex items-center gap-2 disabled:opacity-50"
          >
            {healthLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
            Check Health
          </button>
        </div>

        {healthResults.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {healthResults.map((ep) => (
              <div
                key={ep.name}
                className={`flex items-center justify-between p-3 rounded-lg border ${
                  ep.status === 'UP' ? 'bg-emerald-50 border-emerald-100' : 'bg-red-50 border-red-100'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Server className={`w-4 h-4 ${ep.status === 'UP' ? 'text-emerald-600' : 'text-red-500'}`} />
                  <div>
                    <p className="text-sm font-medium text-gray-900">{ep.name}</p>
                    <p className="text-[11px] text-gray-400 font-mono">port {ep.port}</p>
                  </div>
                </div>
                <div className="text-right">
                  <span className={`badge text-[11px] ${ep.status === 'UP' ? 'badge-success' : 'badge-danger'}`}>
                    {ep.status}
                  </span>
                  <p className="text-[11px] text-gray-400 mt-0.5">{ep.latency}ms</p>
                </div>
              </div>
            ))}
          </div>
        )}

        {healthResults.length === 0 && !healthLoading && (
          <p className="text-sm text-gray-400 text-center py-4">Click "Check Health" to test service connectivity</p>
        )}
      </div>
    </PageShell>
  );
}

function InfoRow({ icon: Icon, label, value }) {
  return (
    <div className="flex items-center gap-2">
      <Icon className="w-4 h-4 text-gray-400" />
      <div>
        <p className="text-xs text-gray-500">{label}</p>
        <p className="text-sm font-medium text-gray-900">{value}</p>
      </div>
    </div>
  );
}

function DetailRow({ label, value }) {
  return (
    <div className="flex items-center justify-between py-1.5 border-b border-gray-50">
      <span className="text-xs text-gray-500">{label}</span>
      <span className="text-sm font-medium text-gray-900">{value}</span>
    </div>
  );
}
