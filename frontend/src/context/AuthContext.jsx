import { createContext, useContext, useState, useEffect, useRef } from 'react';
import {
  decodeJwtPayload,
  getJwtExpSeconds,
  requestLogout,
  requestPasswordGrant,
  requestRefreshGrant,
} from '../lib/keycloakTokens';
import authService from '../services/authService';

/**
 * Fetches MFA status from the auth-service for the currently stored access
 * token and returns { mfaEnabled, mfaSetupRequired }.
 */
async function fetchMfaStatus() {
  try {
    const res = await authService.getMfaStatus();
    const payload = res?.data ?? res;
    const inner = payload?.data ?? payload;
    if (inner && typeof inner.mfaEnabled === 'boolean') {
      return { mfaEnabled: inner.mfaEnabled, mfaSetupRequired: !inner.mfaEnabled };
    }
  } catch (e) {
    console.warn('MFA status fetch failed', e);
  }
  // Unknown status — treat as setup required but don't block on it
  return { mfaEnabled: false, mfaSetupRequired: true };
}

const AuthContext = createContext(null);

const ROLE_MAP = {
  ROLE_ADMIN: { role: 'Super Admin', type: 'admin', avatar: 'AD', permissions: ['all'] },
  ROLE_SUPER_ADMIN: { role: 'Super Admin', type: 'admin', avatar: 'AD', permissions: ['all'] },
  ROLE_COMPLIANCE: { role: 'Compliance Officer', type: 'compliance', avatar: 'CO', permissions: ['view_audit', 'view_reports', 'view_kyc', 'view_fraud'] },
  ROLE_COMPLIANCE_OFFICER: { role: 'Compliance Officer', type: 'compliance', avatar: 'CO', permissions: ['view_audit', 'view_reports', 'view_kyc', 'view_fraud'] },
  ROLE_MANAGER: { role: 'Bank Manager', type: 'manager', avatar: 'MG', permissions: ['view_accounts', 'approve_transactions', 'manage_staff', 'view_reports', 'manage_loans', 'view_audit'] },
  ROLE_LOAN_OFFICER: { role: 'Loan Officer', type: 'loan_officer', avatar: 'LO', permissions: ['view_accounts', 'process_loans', 'view_risk_analysis', 'approve_loans'] },
  ROLE_USER: { role: 'User', type: 'manager', avatar: 'US', permissions: ['view_accounts', 'view_reports'] },
  ROLE_SYSTEM: { role: 'Super Admin', type: 'admin', avatar: 'SY', permissions: ['all'] },
};

function normalizeRoleName(role) {
  const r = String(role || '')
    .toUpperCase()
    .trim()
    .replace(/\s+/g, '_')
    .replace(/^ROLE_/, '');
  return r ? 'ROLE_' + r : '';
}

/** Backend may list multiple roles; primary UI role is first match in ROLE_MAP, which can hide ADMIN if another role appears first. */
const ADMIN_PRIVILEGE_ROLES = new Set(['ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_SYSTEM']);

export function hasSuperAdminAccess(user) {
  if (!user) return false;
  const displayRole = (user.role || '').trim();
  if (displayRole === 'Super Admin') return true;
  if (user.type === 'admin') return true;
  if (Array.isArray(user.permissions) && user.permissions.includes('all')) return true;
  const raw = user.backendRoles || [];
  for (const item of raw) {
    const name = typeof item === 'string' ? item : item?.authority || item?.name || '';
    const n = normalizeRoleName(name);
    if (n && ADMIN_PRIVILEGE_ROLES.has(n)) return true;
  }
  return false;
}

const PRIMARY_ROLE_PRIORITY = [
  'ROLE_SUPER_ADMIN',
  'ROLE_SYSTEM',
  'ROLE_ADMIN',
  'ROLE_COMPLIANCE',
  'ROLE_COMPLIANCE_OFFICER',
  'ROLE_MANAGER',
  'ROLE_LOAN_OFFICER',
  'ROLE_USER',
];

function mapBackendUser(backendUser) {
  const roles = backendUser.roles || backendUser.authorities || [];
  const roleNames = roles.map(r => (typeof r === 'string' ? r : r.authority || r.name || ''));
  const normalizedNames = roleNames.map(normalizeRoleName).filter(Boolean);
  const privilegedFirst =
    PRIMARY_ROLE_PRIORITY.find((pr) => normalizedNames.includes(pr)) ||
    normalizedNames.find((r) => ROLE_MAP[r]) ||
    roleNames.map(normalizeRoleName).find((r) => r && ROLE_MAP[r]);
  const primaryRole = privilegedFirst || 'ROLE_USER';
  const mapped = ROLE_MAP[primaryRole] || ROLE_MAP.ROLE_USER;

  return {
    id: backendUser.id || backendUser.userId || '',
    name: backendUser.fullName || backendUser.username || backendUser.name || 'User',
    email: backendUser.email || '',
    username: backendUser.username || '',
    ...mapped,
    backendRoles: roleNames,
  };
}

function persistUser(user, setUser, setIsAuthenticated) {
  localStorage.setItem('user', JSON.stringify(user));
  setUser(user);
  setIsAuthenticated(true);
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const refreshTimerRef = useRef(null);
  // Holds the raw Keycloak token response between password-grant success and
  // TOTP verification.  Never written to localStorage until TOTP passes.
  const pendingMfaTokensRef = useRef(null);

  const mapFromToken = (tokenParsed) => {
    const rawRoles = [
      ...(tokenParsed?.realm_access?.roles || []),
      ...(tokenParsed?.roles || []),
    ];
    return mapBackendUser({
      id: tokenParsed?.sub || '',
      username: tokenParsed?.preferred_username || tokenParsed?.sub || 'User',
      email: tokenParsed?.email || '',
      fullName: tokenParsed?.name || tokenParsed?.preferred_username || 'User',
      roles: rawRoles,
    });
  };

  const clearRefreshTimer = () => {
    if (refreshTimerRef.current) {
      window.clearTimeout(refreshTimerRef.current);
      refreshTimerRef.current = null;
    }
  };

  const scheduleAccessTokenRefresh = () => {
    clearRefreshTimer();
    const accessToken = localStorage.getItem('accessToken');
    const refreshToken = localStorage.getItem('refreshToken');
    if (!accessToken || !refreshToken) return;

    const exp = getJwtExpSeconds(accessToken);
    if (!exp) return;

    const now = Math.floor(Date.now() / 1000);
    const skew = 30;
    const ms = Math.max((exp - now - skew) * 1000, 5_000);
    refreshTimerRef.current = window.setTimeout(async () => {
      try {
        const refreshed = await requestRefreshGrant(refreshToken);
        if (refreshed.access_token) localStorage.setItem('accessToken', refreshed.access_token);
        if (refreshed.refresh_token) localStorage.setItem('refreshToken', refreshed.refresh_token);
        scheduleAccessTokenRefresh();
      } catch {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        setUser(null);
        setIsAuthenticated(false);
      }
    }, ms);
  };

  useEffect(() => {
    (async () => {
      try {
        let accessToken = localStorage.getItem('accessToken');
        const refreshToken = localStorage.getItem('refreshToken');
        const storedUser = localStorage.getItem('user');

        if (accessToken) {
          const exp = getJwtExpSeconds(accessToken);
          const now = Math.floor(Date.now() / 1000);
          const isExpired = exp ? exp <= now + 5 : false;

          if (isExpired && refreshToken) {
            const refreshed = await requestRefreshGrant(refreshToken);
            accessToken = refreshed.access_token || '';
            if (accessToken) localStorage.setItem('accessToken', accessToken);
            if (refreshed.refresh_token) localStorage.setItem('refreshToken', refreshed.refresh_token);
          } else if (isExpired) {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            localStorage.removeItem('user');
            return;
          }

          if (accessToken) {
            const { mfaSetupRequired } = await fetchMfaStatus();
            let mappedUser = mapFromToken(decodeJwtPayload(accessToken));
            mappedUser = { ...mappedUser, mfaSetupRequired };
            persistUser(mappedUser, setUser, setIsAuthenticated);
            scheduleAccessTokenRefresh();
          }
        } else if (storedUser) {
          // Avoid a half-broken session: user object without a token can't call APIs.
          localStorage.removeItem('user');
        }
      } catch {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        setUser(null);
        setIsAuthenticated(false);
      } finally {
        setLoading(false);
      }
    })();

    return () => {
      clearRefreshTimer();
    };
  }, []);

  const login = async ({ username, password } = {}) => {
    // Step 1 — Keycloak OIDC password grant (no OTP; OTP is handled separately
    // against auth-service's TOTP system after the grant succeeds).
    const tokens = await requestPasswordGrant({ username, password });

    // Temporarily store the access token so the MFA status API call (which goes
    // through the gateway and requires a Bearer token) can be made.
    localStorage.setItem('accessToken', tokens.access_token);
    if (tokens.refresh_token) localStorage.setItem('refreshToken', tokens.refresh_token);

    // Step 2 — Check whether the user has MFA set up in the auth-service.
    const { mfaEnabled, mfaSetupRequired } = await fetchMfaStatus();

    if (mfaEnabled) {
      // The user has MFA configured.  Stash the raw tokens in memory (NOT in
      // localStorage) and remove what we temporarily stored, so the session is
      // not considered valid until TOTP is verified.
      pendingMfaTokensRef.current = tokens;
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      return { ok: true, mfaRequired: true };
    }

    // No MFA — complete login immediately.
    let mappedUser = mapFromToken(decodeJwtPayload(tokens.access_token));
    mappedUser = { ...mappedUser, mfaSetupRequired };
    persistUser(mappedUser, setUser, setIsAuthenticated);
    scheduleAccessTokenRefresh();
    return { ok: true, mfaRequired: false, mfaSetupRequired };
  };

  /**
   * Second step of login when MFA is required.
   * Verifies the TOTP code against auth-service using the pending Keycloak token,
   * then finalises the session.
   */
  const verifyMfa = async (code) => {
    const tokens = pendingMfaTokensRef.current;
    if (!tokens) {
      throw new Error('No pending authentication. Please sign in again.');
    }

    // Temporarily expose the token so the api interceptor can attach it to the
    // verify-totp request (the gateway requires a valid Bearer token).
    localStorage.setItem('accessToken', tokens.access_token);
    try {
      await authService.verifyTotpForSession(code);
    } catch (err) {
      localStorage.removeItem('accessToken');
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        err?.message ||
        'Invalid verification code';
      throw new Error(msg);
    }

    // TOTP verified — finalise authentication.
    if (tokens.refresh_token) localStorage.setItem('refreshToken', tokens.refresh_token);
    pendingMfaTokensRef.current = null;

    let mappedUser = mapFromToken(decodeJwtPayload(tokens.access_token));
    // mfaEnabled = true so setup is complete
    mappedUser = { ...mappedUser, mfaSetupRequired: false };
    persistUser(mappedUser, setUser, setIsAuthenticated);
    scheduleAccessTokenRefresh();
    return { ok: true, mfaSetupRequired: false };
  };

  const logout = async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    clearRefreshTimer();
    pendingMfaTokensRef.current = null;
    await requestLogout(refreshToken);
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    setUser(null);
    setIsAuthenticated(false);
  };

  const switchRole = (roleType) => {
    if (!user) return;
    const mapped = Object.values(ROLE_MAP).find(r => r.type === roleType);
    if (mapped) {
      const updated = { ...user, ...mapped };
      setUser(updated);
      localStorage.setItem('user', JSON.stringify(updated));
    }
  };

  const markMfaSetupComplete = () => {
    if (!user) return;
    const updated = {
      ...user,
      mfaSetupRequired: false,
      firstLogin: false,
    };
    persistUser(updated, setUser, setIsAuthenticated);
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-900">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, login, logout, verifyMfa, switchRole, markMfaSetupComplete, loading }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
