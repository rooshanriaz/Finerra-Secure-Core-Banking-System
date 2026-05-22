import api from './api';

/**
 * Auth/MFA service.
 *
 * All requests go through the API gateway (same-origin via nginx /api proxy)
 * so we get JWT validation, role propagation, and CORS-safe responses.
 * Calling auth-service directly (http://localhost:8082) from the browser
 * triggers a CORS preflight that the service does not answer, surfacing
 * to the user as a generic "Network Error".
 */
const authService = {
  async login(username, password) {
    const { data } = await api.post('/api/v1/auth/login', { username, password });
    return data;
  },

  async logout() {
    const refreshToken = localStorage.getItem('refreshToken');
    try {
      await api.post('/api/v1/auth/logout', { refreshToken });
    } finally {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('user');
    }
  },

  async refresh() {
    const refreshToken = localStorage.getItem('refreshToken');
    const { data } = await api.post('/api/v1/auth/refresh', { refreshToken });
    return data;
  },

  async validate() {
    const { data } = await api.post('/api/v1/auth/validate');
    return data;
  },

  async getMe() {
    const { data } = await api.get('/api/v1/auth/me');
    return data;
  },

  async verifyMfa(mfaToken, code) {
    const { data } = await api.post('/api/v1/auth/mfa/verify', { mfaToken, code });
    return data;
  },

  /**
   * Verify a TOTP code for the currently authenticated user.
   * Called after a successful Keycloak password grant when the user has
   * MFA enabled in the auth-service — the Keycloak access token must already
   * be stored in localStorage so the api interceptor can attach it.
   */
  async verifyTotpForSession(code) {
    const { data } = await api.post('/api/v1/auth/mfa/verify-totp', { code });
    return data;
  },

  async setupMfa() {
    return api.get('/api/v1/auth/mfa/setup');
  },

  async confirmMfaSetup(code) {
    return api.post('/api/v1/auth/mfa/confirm-setup', { code });
  },

  async disableMfa(code) {
    return api.post('/api/v1/auth/mfa/disable', { code });
  },

  async getMfaStatus() {
    return api.get('/api/v1/auth/mfa/status');
  },
};

export default authService;
