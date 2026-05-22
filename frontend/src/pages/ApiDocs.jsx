import { FileText, Server, Shield, ListChecks, Link as LinkIcon } from 'lucide-react';
import { PageShell } from '../components/layout/PageShell';

const API_GROUPS = [
  {
    title: 'Authentication & Access',
    service: 'auth-service',
    basePath: '/api/v1/auth',
    endpoints: [
      'POST /login',
      'POST /refresh',
      'POST /logout',
      'GET /me',
      'GET /mfa/status',
      'POST /mfa/setup',
      'POST /mfa/verify',
    ],
  },
  {
    title: 'KYC / AML',
    service: 'kyc-aml-service',
    basePath: '/api/v1/kyc',
    endpoints: [
      'POST /onboard',
      'GET /status/{referenceId}',
      'GET /did/{referenceId}',
      'GET /did/verify/{didId}',
      'GET /aml/{referenceId}',
    ],
  },
  {
    title: 'Transactions',
    service: 'transaction-service',
    basePath: '/api/v1/transactions',
    endpoints: [
      'POST /savings/{accountId}/deposit',
      'POST /savings/{accountId}/withdrawal',
      'POST /loans/{loanId}/repayment',
      'GET /savings/{accountId}/history',
      'GET /recent',
      'POST /requests',
      'GET /requests',
      'POST /requests/{requestId}/approve',
      'POST /requests/{requestId}/decline',
    ],
  },
  {
    title: 'Fraud Detection & Monitoring',
    service: 'fraud-detection-service',
    basePath: '/api/v1/fraud',
    endpoints: [
      'POST /score',
      'GET /alerts',
      'GET /alerts/stats',
      'GET /alerts/{alertId}',
      'PUT /alerts/{alertId}/status',
      'GET /thresholds',
      'PUT /thresholds',
    ],
  },
  {
    title: 'Audit Trail & Blockchain',
    service: 'audit-service',
    basePath: '/api/v1/audit',
    endpoints: [
      'POST /record',
      'GET /recent',
      'GET /{auditId}',
      'GET /transaction/{transactionId}',
      'GET /account/{accountId}',
      'GET /reports/export',
      'POST /integrity-check',
      'GET /{auditId}/verify',
    ],
  },
  {
    title: 'Core Banking Connector',
    service: 'core-banking-connector',
    basePath: '/api/v1',
    endpoints: [
      'GET /clients',
      'GET /savingsaccounts',
      'GET /loans',
      'POST /loans',
      'POST /loans/{loanId}?command=approve|forward|disburse|reject',
      'GET /loans/{loanId}/transactions',
      'GET /reports',
    ],
  },
];

export default function ApiDocs() {
  return (
    <PageShell
      title="OpenAPI / Swagger-style API docs"
      subtitle="Project API map for quick endpoint discovery, role-aware testing, and Postman collection building."
    >
      <div className="card bg-blue-50 border-blue-100">
        <div className="flex items-start gap-3">
          <Shield className="h-5 w-5 text-blue-600 mt-0.5" />
          <div>
            <p className="text-sm font-semibold text-blue-900">Gateway-first access pattern</p>
            <p className="text-xs text-blue-700 mt-1">
              Use API Gateway as the primary entry point. Protected endpoints require a Bearer token from Keycloak-authenticated login.
            </p>
            <p className="text-xs text-blue-700 mt-1">
              Base URL (local): <code>http://localhost:8080</code>
            </p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {API_GROUPS.map((group) => (
          <div key={group.title} className="card">
            <div className="flex items-start justify-between gap-2 mb-3">
              <div>
                <h3 className="text-base font-semibold text-gray-900">{group.title}</h3>
                <p className="text-xs text-gray-500 mt-0.5">{group.service}</p>
              </div>
              <Server className="h-4 w-4 text-gray-400 mt-1" />
            </div>
            <p className="text-xs text-gray-600 mb-3">
              Base path: <code>{group.basePath}</code>
            </p>
            <ul className="space-y-1.5">
              {group.endpoints.map((endpoint) => (
                <li key={endpoint} className="text-xs text-gray-700 flex items-start gap-2">
                  <ListChecks className="h-3.5 w-3.5 text-finnera-600 mt-0.5 flex-shrink-0" />
                  <code>{endpoint}</code>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      <div className="card">
        <div className="flex items-start gap-3">
          <FileText className="h-5 w-5 text-finnera-600 mt-0.5" />
          <div>
            <h3 className="text-sm font-semibold text-gray-900">Postman final validation</h3>
            <p className="text-xs text-gray-600 mt-1">
              Follow the project test playbook in <code>Postman_Testing.md</code> for end-to-end checks
              (Auth → KYC/DID → Transactions → Fraud → Audit/Blockchain).
            </p>
            <p className="text-xs text-gray-600 mt-2 flex items-center gap-1.5">
              <LinkIcon className="h-3.5 w-3.5" />
              Keep a shared environment in Postman with <code>baseUrl</code>, <code>accessToken</code>, and sampled IDs.
            </p>
          </div>
        </div>
      </div>
    </PageShell>
  );
}
