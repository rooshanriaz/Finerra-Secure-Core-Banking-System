import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth, hasSuperAdminAccess } from './context/AuthContext';
import Layout from './components/layout/Layout';
import LoginPage from './pages/LoginPage';
import MfaSetupPage from './pages/MfaSetupPage';
import Dashboard from './pages/Dashboard';
import RBACManagement from './pages/RBACManagement';
import LoanReview from './pages/LoanReview';
import LoanProductCreate from './pages/LoanProductCreate';
import TransactionHistory from './pages/TransactionHistory';
import AuditLogs from './pages/AuditLogs';
import KYCVerification from './pages/KYCVerification';
import FraudDetection from './pages/FraudDetection';
import AdminConsole from './pages/AdminConsole';
import ComplianceDashboard from './pages/ComplianceDashboard';
import ApiDocs from './pages/ApiDocs';

function ProtectedRoute({ children, requireSystemAdmin = false }) {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.mfaSetupRequired && location.pathname !== '/mfa-setup') {
    return <Navigate to="/mfa-setup" replace state={{ firstLoginSetup: true }} />;
  }

  if (requireSystemAdmin && !hasSuperAdminAccess(user)) {
    return <Navigate to="/dashboard" replace />;
  }

  return <Layout>{children}</Layout>;
}

function LoginRoute() {
  const { isAuthenticated, user } = useAuth();
  if (isAuthenticated) {
    if (user?.mfaSetupRequired) {
      return <Navigate to="/mfa-setup" replace state={{ firstLoginSetup: true }} />;
    }
    return <Navigate to="/dashboard" replace />;
  }
  return <LoginPage />;
}

function AppRoutes() {
  const { isAuthenticated, user } = useAuth();
  const fallbackPath = !isAuthenticated ? '/login' : (user?.mfaSetupRequired ? '/mfa-setup' : '/dashboard');

  return (
    <Routes>
      <Route path="/login" element={<LoginRoute />} />
      <Route
        path="/mfa-verify"
        element={<Navigate to="/login" replace />}
      />
      <Route
        path="/mfa-setup"
        element={<ProtectedRoute><MfaSetupPage /></ProtectedRoute>}
      />
      <Route
        path="/dashboard"
        element={<ProtectedRoute><Dashboard /></ProtectedRoute>}
      />
      <Route
        path="/rbac"
        element={<ProtectedRoute><RBACManagement /></ProtectedRoute>}
      />
      <Route
        path="/loans"
        element={<ProtectedRoute><LoanReview /></ProtectedRoute>}
      />
      <Route
        path="/loans/create-product"
        element={<ProtectedRoute><LoanProductCreate /></ProtectedRoute>}
      />
      <Route
        path="/transactions"
        element={<ProtectedRoute><TransactionHistory /></ProtectedRoute>}
      />
      <Route
        path="/audit"
        element={<ProtectedRoute><AuditLogs /></ProtectedRoute>}
      />
      <Route
        path="/kyc"
        element={<ProtectedRoute><KYCVerification /></ProtectedRoute>}
      />
      <Route
        path="/fraud"
        element={<ProtectedRoute><FraudDetection /></ProtectedRoute>}
      />
      <Route
        path="/compliance"
        element={<ProtectedRoute><ComplianceDashboard /></ProtectedRoute>}
      />
      <Route
        path="/api-docs"
        element={<ProtectedRoute><ApiDocs /></ProtectedRoute>}
      />
      <Route
        path="/admin"
        element={<ProtectedRoute requireSystemAdmin={true}><AdminConsole /></ProtectedRoute>}
      />
      <Route path="*" element={<Navigate to={fallbackPath} replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <Router>
        <AppRoutes />
      </Router>
    </AuthProvider>
  );
}
