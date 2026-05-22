const clientId = import.meta.env.VITE_OIDC_CLIENT_ID || 'finnera-web';

/**
 * Issuer URL for browser calls. Default is same-origin `/realms/finnera` so the SPA hits the Vite or nginx
 * proxy (avoids cross-origin CORS on Keycloak’s token endpoint). Override with absolute `VITE_OIDC_ISSUER`
 * only if you have Keycloak CORS configured for your SPA origin.
 */
export function getOidcIssuer() {
  const raw = import.meta.env.VITE_OIDC_ISSUER;
  const s = raw != null ? String(raw).trim() : '';
  if (s.startsWith('http')) return s.replace(/\/+$/, '');
  if (s.startsWith('/')) {
    if (typeof window !== 'undefined') {
      return `${window.location.origin}${s}`.replace(/\/+$/, '');
    }
    return 'http://localhost:8090/realms/finnera';
  }
  if (typeof window !== 'undefined') {
    return `${window.location.origin}/realms/finnera`;
  }
  return 'http://localhost:8090/realms/finnera';
}

export function getOidcClientId() {
  return clientId;
}

export function getTokenEndpoint() {
  return `${getOidcIssuer()}/protocol/openid-connect/token`;
}

export function getLogoutEndpoint() {
  return `${getOidcIssuer()}/protocol/openid-connect/logout`;
}

function base64UrlToBase64(input) {
  let s = String(input || '').replace(/-/g, '+').replace(/_/g, '/');
  const pad = s.length % 4;
  if (pad) s += '='.repeat(4 - pad);
  return s;
}

export function decodeJwtPayload(jwt) {
  if (!jwt || typeof jwt !== 'string') return null;
  const parts = jwt.split('.');
  if (parts.length < 2) return null;
  try {
    const json = atob(base64UrlToBase64(parts[1]));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function getJwtExpSeconds(jwt) {
  const payload = decodeJwtPayload(jwt);
  const exp = payload?.exp;
  return typeof exp === 'number' ? exp : null;
}

async function readOAuth2Error(response) {
  const text = await response.text().catch(() => '');
  try {
    const json = JSON.parse(text);
    return json?.error_description || json?.error || text || `HTTP ${response.status}`;
  } catch {
    if (/<title>\s*502\s*Bad Gateway/i.test(text) || /502 Bad Gateway/i.test(text)) {
      return 'Identity service unavailable (502). Ensure the Keycloak container is running and reachable.';
    }
    if (/<!DOCTYPE html>/i.test(text) || /<html[\s>]/i.test(text)) {
      return `Sign-in service returned HTTP ${response.status}. Check Keycloak and the /realms proxy.`;
    }
    return text || `HTTP ${response.status}`;
  }
}

export async function requestPasswordGrant({ username, password, otp, totp }) {
  const body = new URLSearchParams();
  body.set('grant_type', 'password');
  body.set('client_id', getOidcClientId());
  body.set('username', username);
  body.set('password', password);
  body.set('scope', 'openid');
  if (otp) body.set('otp', otp);
  if (totp) body.set('totp', totp);

  let res;
  try {
    res = await fetch(getTokenEndpoint(), {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  } catch (e) {
    const hint =
      typeof window !== 'undefined' && getOidcIssuer().startsWith(window.location.origin)
        ? ' Check that Keycloak is running and /realms is proxied (Vite dev server or nginx).'
        : ' Check that Keycloak is reachable and CORS allows this origin, or use same-origin /realms proxy.';
    throw new Error(`Network error while contacting the identity service.${hint} (${e?.message || 'failed to fetch'})`);
  }

  if (!res.ok) {
    const message = await readOAuth2Error(res);
    const err = new Error(message);
    err.status = res.status;
    throw err;
  }

  return res.json();
}

export async function requestRefreshGrant(refreshToken) {
  const body = new URLSearchParams();
  body.set('grant_type', 'refresh_token');
  body.set('client_id', getOidcClientId());
  body.set('refresh_token', refreshToken);

  let res;
  try {
    res = await fetch(getTokenEndpoint(), {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  } catch (e) {
    const hint =
      typeof window !== 'undefined' && getOidcIssuer().startsWith(window.location.origin)
        ? ' Check that Keycloak is running and /realms is proxied.'
        : ' Check Keycloak reachability or use same-origin /realms proxy.';
    throw new Error(`Network error while refreshing the session.${hint} (${e?.message || 'failed to fetch'})`);
  }

  if (!res.ok) {
    const message = await readOAuth2Error(res);
    const err = new Error(message);
    err.status = res.status;
    throw err;
  }

  return res.json();
}

export async function requestLogout(refreshToken) {
  if (!refreshToken) return;
  const body = new URLSearchParams();
  body.set('client_id', getOidcClientId());
  body.set('refresh_token', refreshToken);

  await fetch(getLogoutEndpoint(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  }).catch(() => {});
}

export function persistTokensFromTokenResponse(tokenResponse) {
  const accessToken = tokenResponse?.access_token || '';
  const refreshToken = tokenResponse?.refresh_token || localStorage.getItem('refreshToken') || '';
  if (accessToken) localStorage.setItem('accessToken', accessToken);
  if (refreshToken) localStorage.setItem('refreshToken', refreshToken);
}

export async function tryRefreshAccessToken() {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) return null;
  const refreshed = await requestRefreshGrant(refreshToken);
  persistTokensFromTokenResponse(refreshed);
  return localStorage.getItem('accessToken');
}
