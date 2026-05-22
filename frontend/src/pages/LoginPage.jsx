import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, EyeOff, Layers, Loader2, ShieldCheck, ArrowLeft } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const { login, verifyMfa } = useAuth();
  const navigate = useNavigate();

  // 'credentials' → first step; 'mfa' → TOTP step after successful Keycloak login
  const [stage, setStage] = useState('credentials');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const handleCredentials = async (e) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);
    try {
      const result = await login({ username: username.trim(), password });
      if (result?.mfaRequired) {
        // MFA enabled — move to TOTP step
        setOtp('');
        setStage('mfa');
        return;
      }
      if (result?.mfaSetupRequired) {
        navigate('/mfa-setup', { replace: true, state: { firstLoginSetup: true } });
      } else {
        navigate('/dashboard', { replace: true });
      }
    } catch (err) {
      setError(String(err?.message || 'Sign-in failed. Please check your credentials.'));
    } finally {
      setIsLoading(false);
    }
  };

  const handleMfaVerify = async (e) => {
    e.preventDefault();
    setError('');
    const cleanOtp = otp.replace(/\s/g, '');
    if (cleanOtp.length !== 6) {
      setError('Please enter the 6-digit code from your authenticator app.');
      return;
    }
    setIsLoading(true);
    try {
      const result = await verifyMfa(cleanOtp);
      if (result?.mfaSetupRequired) {
        navigate('/mfa-setup', { replace: true, state: { firstLoginSetup: true } });
      } else {
        navigate('/dashboard', { replace: true });
      }
    } catch (err) {
      setError(String(err?.message || 'Invalid code. Please try again.'));
    } finally {
      setIsLoading(false);
    }
  };

  const BrandPanel = (
    <div className="relative flex flex-col justify-between overflow-hidden bg-gradient-to-br from-finnera-700 via-finnera-800 to-finnera-900 px-10 py-12 text-white lg:min-h-screen lg:rounded-none">
      <div className="pointer-events-none absolute inset-0 opacity-[0.12]">
        <div className="absolute -right-24 top-1/4 h-96 w-96 rounded-full bg-finnera-300 blur-3xl" />
        <div className="absolute -left-20 bottom-0 h-80 w-80 rounded-full bg-teal-400/40 blur-3xl" />
      </div>
      <div className="relative">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/10 ring-1 ring-white/20 backdrop-blur">
            <Layers className="h-6 w-6 text-finnera-100" />
          </div>
          <div>
            <p className="text-lg font-semibold tracking-tight">Finnera</p>
            <p className="text-xs font-medium text-finnera-100/90">Banking security</p>
          </div>
        </div>
        <h1 className="mt-10 max-w-sm text-2xl font-semibold leading-snug tracking-tight text-white lg:text-3xl">
          Secure access to operations, compliance, and core banking controls.
        </h1>
        <p className="mt-4 max-w-sm text-sm leading-relaxed text-finnera-100/85">
          Staff sign-in uses verified credentials, time-based MFA, and optional on-chain identity
          checks aligned with your institution&apos;s policies.
        </p>
      </div>
      <ul className="relative mt-12 space-y-3 text-sm text-finnera-50/95 lg:mt-0">
        <li className="flex gap-2">
          <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-finnera-300" />
          Session traffic is encrypted in transit (TLS).
        </li>
        <li className="flex gap-2">
          <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-finnera-300" />
          Sensitive actions are recorded for audit.
        </li>
        <li className="flex gap-2">
          <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-finnera-300" />
          Use an authenticator app you already trust (e.g. Google Authenticator, Microsoft Authenticator).
        </li>
      </ul>
    </div>
  );

  return (
    <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
      <div className="hidden lg:block">{BrandPanel}</div>

      <div className="flex min-h-screen flex-col justify-center px-4 py-10 sm:px-8 lg:px-12 xl:px-16">
        <div className="mx-auto w-full max-w-md animate-fadeIn">
          <div className="mb-8 lg:hidden">{BrandPanel}</div>

          <div className="rounded-2xl border border-gray-200/80 bg-white p-8 shadow-sm shadow-gray-200/40">
            {stage === 'credentials' ? (
              <>
                <header className="mb-6">
                  <h2 className="text-xl font-semibold tracking-tight text-gray-900">Sign in</h2>
                  <p className="mt-1 text-sm text-gray-500">Enter your staff credentials.</p>
                </header>

                {error && (
                  <div className="mb-5 rounded-xl border border-red-100 bg-red-50 p-3.5">
                    <p className="text-sm text-red-800">{error}</p>
                  </div>
                )}

                <form className="space-y-5" onSubmit={handleCredentials}>
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-gray-700" htmlFor="username">
                      Username
                    </label>
                    <input
                      id="username"
                      name="username"
                      autoComplete="username"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      className="w-full rounded-xl border border-gray-200 bg-white px-3.5 py-3 text-sm text-gray-900 outline-none ring-finnera-200 focus:border-finnera-400 focus:ring-4"
                      placeholder="e.g. admin"
                      required
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium text-gray-700" htmlFor="password">
                      Password
                    </label>
                    <div className="relative">
                      <input
                        id="password"
                        name="password"
                        type={showPassword ? 'text' : 'password'}
                        autoComplete="current-password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        className="w-full rounded-xl border border-gray-200 bg-white px-3.5 py-3 pr-12 text-sm text-gray-900 outline-none ring-finnera-200 focus:border-finnera-400 focus:ring-4"
                        placeholder="••••••••"
                        required
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword((v) => !v)}
                        className="absolute inset-y-0 right-2 my-auto inline-flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-50 hover:text-gray-700"
                        aria-label={showPassword ? 'Hide password' : 'Show password'}
                      >
                        {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={isLoading}
                    className="btn-primary flex w-full items-center justify-center gap-2 rounded-xl py-3.5 text-[15px] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="h-5 w-5 animate-spin" />
                        Signing in...
                      </>
                    ) : (
                      <>Sign in</>
                    )}
                  </button>
                </form>
              </>
            ) : (
              <>
                <header className="mb-6">
                  <div className="flex items-center gap-3 mb-1">
                    <button
                      type="button"
                      onClick={() => { setStage('credentials'); setError(''); setOtp(''); }}
                      className="p-1.5 hover:bg-gray-100 rounded-lg transition-colors text-gray-500"
                      aria-label="Back to sign in"
                    >
                      <ArrowLeft className="h-4 w-4" />
                    </button>
                    <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-finnera-50">
                      <ShieldCheck className="h-5 w-5 text-finnera-600" />
                    </div>
                    <h2 className="text-xl font-semibold tracking-tight text-gray-900">
                      Two-factor authentication
                    </h2>
                  </div>
                  <p className="mt-1 text-sm text-gray-500 pl-1">
                    Open your authenticator app and enter the 6-digit code for{' '}
                    <span className="font-medium text-gray-700">{username}</span>.
                  </p>
                </header>

                {error && (
                  <div className="mb-5 rounded-xl border border-red-100 bg-red-50 p-3.5">
                    <p className="text-sm text-red-800">{error}</p>
                  </div>
                )}

                <form className="space-y-5" onSubmit={handleMfaVerify}>
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-gray-700" htmlFor="otp">
                      Authenticator code
                    </label>
                    <input
                      id="otp"
                      name="otp"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      autoFocus
                      value={otp}
                      onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      className="w-full rounded-xl border border-gray-200 bg-white px-3.5 py-3 text-center text-xl font-mono tracking-[0.4em] text-gray-900 outline-none ring-finnera-200 focus:border-finnera-400 focus:ring-4"
                      placeholder="000000"
                      maxLength={6}
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={isLoading || otp.replace(/\s/g, '').length !== 6}
                    className="btn-primary flex w-full items-center justify-center gap-2 rounded-xl py-3.5 text-[15px] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="h-5 w-5 animate-spin" />
                        Verifying...
                      </>
                    ) : (
                      <>Verify</>
                    )}
                  </button>
                </form>
              </>
            )}

            <p className="mt-8 border-t border-gray-100 pt-6 text-center text-xs text-gray-400">
              Authorized use only. Activity may be monitored and audited.
            </p>
          </div>

          <p className="mt-6 text-center text-xs text-gray-400">
            Finnera · Core banking security interface · © {new Date().getFullYear()}
          </p>
        </div>
      </div>
    </div>
  );
}
