import { useState, useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ShieldCheck, Copy, CheckCircle2, AlertCircle, ArrowLeft, Fingerprint, Loader2, ShieldOff } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { useAuth } from '../context/AuthContext';
import authService from '../services/authService';

export default function MfaSetupPage() {
  const { user, markMfaSetupComplete } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isFirstLoginSetup = location.state?.firstLoginSetup === true;
  const autoStartedRef = useRef(false);

  const [step, setStep] = useState('loading');
  const [mfaStatus, setMfaStatus] = useState(null);
  const [setupData, setSetupData] = useState(null);
  const [code, setCode] = useState('');
  const [disableCode, setDisableCode] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    loadStatus();
  }, []);

  useEffect(() => {
    if (
      isFirstLoginSetup &&
      step === 'overview' &&
      mfaStatus?.mfaEnabled === false &&
      !autoStartedRef.current
    ) {
      autoStartedRef.current = true;
      startSetup();
    }
  }, [isFirstLoginSetup, step, mfaStatus]);

  const loadStatus = async () => {
    setError('');
    try {
      const res = await authService.getMfaStatus();
      const payload = res?.data ?? res;
      const data = payload?.data ?? payload;
      if (payload && payload.success === false) {
        setError(payload.error || payload.message || 'Could not load security status');
        setMfaStatus(null);
        setStep('overview');
        return;
      }
      if (data && typeof data.mfaEnabled === 'boolean') {
        setMfaStatus(data);
        setStep(data.mfaEnabled ? 'enabled' : 'overview');
      } else {
        setMfaStatus(null);
        setStep('overview');
        setError('Unexpected response from server. Try again or contact support.');
      }
    } catch (err) {
      const msg =
        err?.response?.data?.error ||
        err?.response?.data?.message ||
        err?.message ||
        'Could not load security status';
      setError(msg);
      setMfaStatus(null);
      setStep('overview');
    }
  };

  const startSetup = async () => {
    setIsLoading(true);
    setError('');
    try {
      const response = await authService.setupMfa();
      const data = response.data?.data || response.data;
      setSetupData(data);
      setStep('scan');
    } catch (err) {
      setError(
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err.message ||
        'Failed to start MFA setup'
      );
    } finally {
      setIsLoading(false);
    }
  };

  const confirmSetup = async () => {
    if (code.length !== 6) {
      setError('Please enter a 6-digit code');
      return;
    }
    setIsLoading(true);
    setError('');
    try {
      await authService.confirmMfaSetup(code);
      markMfaSetupComplete();
      setSuccess('MFA has been enabled successfully!');
      setStep('complete');
      setTimeout(() => {
        if (isFirstLoginSetup) {
          navigate('/dashboard', { replace: true });
        } else {
          loadStatus();
        }
      }, 1200);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err.message ||
        'Invalid code. Please try again.'
      );
      setCode('');
    } finally {
      setIsLoading(false);
    }
  };

  const disableMfa = async () => {
    if (disableCode.length !== 6) {
      setError('Please enter your current 6-digit code');
      return;
    }
    setIsLoading(true);
    setError('');
    try {
      await authService.disableMfa(disableCode);
      setSuccess('MFA has been disabled.');
      setMfaStatus({ ...mfaStatus, mfaEnabled: false });
      setStep('overview');
      setDisableCode('');
    } catch (err) {
      setError(
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err.message ||
        'Invalid code.'
      );
      setDisableCode('');
    } finally {
      setIsLoading(false);
    }
  };

  const copySecret = () => {
    if (setupData?.secret) {
      navigator.clipboard.writeText(setupData.secret);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  if (step === 'loading') {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-finnera-600" />
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        {!isFirstLoginSetup && (
          <button onClick={() => navigate(-1)} className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
            <ArrowLeft className="w-5 h-5 text-gray-600" />
          </button>
        )}
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {isFirstLoginSetup ? 'Set Up Your Authenticator' : 'Security Settings'}
          </h1>
          <p className="text-gray-500 text-sm">
            {isFirstLoginSetup
              ? 'Complete MFA setup now to continue to your dashboard'
              : 'Manage two-factor authentication (TOTP) for your account'}
          </p>
        </div>
      </div>

      {error && (
        <div className="mb-4 flex items-start gap-3 p-3.5 bg-red-50 rounded-lg border border-red-200">
          <AlertCircle className="w-4 h-4 text-red-600 mt-0.5 flex-shrink-0" />
          <p className="text-sm text-red-700 font-medium">{error}</p>
        </div>
      )}
      {success && (
        <div className="mb-4 flex items-start gap-3 p-3.5 bg-green-50 rounded-lg border border-green-200">
          <CheckCircle2 className="w-4 h-4 text-green-600 mt-0.5 flex-shrink-0" />
          <p className="text-sm text-green-700 font-medium">{success}</p>
        </div>
      )}

      {isFirstLoginSetup && (
        <div className="mb-4 flex items-start gap-3 p-3.5 bg-amber-50 rounded-lg border border-amber-200">
          <AlertCircle className="w-4 h-4 text-amber-600 mt-0.5 flex-shrink-0" />
          <p className="text-sm text-amber-700 font-medium">
            Welcome {user?.name || user?.username || 'User'}! MFA setup is required before you can access your dashboard.
          </p>
        </div>
      )}

      {/* MFA Status Card */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-4">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
              mfaStatus?.mfaEnabled ? 'bg-green-100' : 'bg-gray-100'
            }`}>
              <Fingerprint className={`w-5 h-5 ${mfaStatus?.mfaEnabled ? 'text-green-600' : 'text-gray-500'}`} />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900">Two-Factor Authentication (TOTP)</h3>
              <p className="text-sm text-gray-500">
                {mfaStatus?.mfaEnabled
                  ? 'Enabled — your account requires a code from your authenticator app'
                  : 'Add an extra layer of security to your account'}
              </p>
            </div>
          </div>
          <span className={`px-3 py-1 rounded-full text-xs font-bold ${
            mfaStatus?.mfaEnabled
              ? 'bg-green-100 text-green-700'
              : 'bg-gray-100 text-gray-600'
          }`}>
            {mfaStatus?.mfaEnabled ? 'Enabled' : 'Disabled'}
          </span>
        </div>

        {step === 'overview' && (
          <button
            onClick={startSetup}
            disabled={isLoading}
            className="w-full bg-finnera-600 hover:bg-finnera-700 text-white font-semibold py-3 rounded-xl transition-colors flex items-center justify-center gap-2 disabled:opacity-60"
          >
            {isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <ShieldCheck className="w-5 h-5" />}
            Enable Two-Factor Authentication
          </button>
        )}

        {step === 'enabled' && (
          <div className="space-y-3">
            <p className="text-sm text-gray-600 mb-2">Enter your current TOTP code to disable MFA:</p>
            <div className="flex gap-2">
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={disableCode}
                onChange={(e) => setDisableCode(e.target.value.replace(/\D/g, ''))}
                placeholder="6-digit code"
                className="flex-1 px-4 py-3 border-2 border-gray-200 rounded-xl text-center text-lg font-mono tracking-widest focus:border-red-400 focus:ring-2 focus:ring-red-100 outline-none"
              />
              <button
                onClick={disableMfa}
                disabled={isLoading || disableCode.length !== 6}
                className="px-6 py-3 bg-red-600 hover:bg-red-700 text-white font-semibold rounded-xl transition-colors flex items-center gap-2 disabled:opacity-60"
              >
                {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <ShieldOff className="w-4 h-4" />}
                Disable
              </button>
            </div>
          </div>
        )}
      </div>

      {/* QR Code Setup Step */}
      {step === 'scan' && setupData && (
        <div className="bg-white rounded-xl border border-gray-200 p-6 mb-4">
          <h3 className="font-semibold text-gray-900 mb-4">Step 1: Scan QR Code</h3>
          <p className="text-sm text-gray-600 mb-4">
            Open your authenticator app and scan this QR code, or enter the secret key manually.
          </p>

          <div className="flex flex-col items-center gap-4 mb-6">
            <div className="bg-white p-4 rounded-xl border-2 border-gray-100 shadow-sm">
              <QRCodeSVG value={setupData.qrCodeUri} size={200} level="M" />
            </div>

            <div className="w-full">
              <p className="text-xs font-semibold text-gray-500 uppercase mb-1">Manual Entry Key</p>
              <div className="flex items-center gap-2">
                <code className="flex-1 bg-gray-50 px-4 py-2.5 rounded-lg text-sm font-mono tracking-wider border border-gray-200 break-all">
                  {setupData.secret}
                </code>
                <button
                  onClick={copySecret}
                  className="p-2.5 hover:bg-gray-100 rounded-lg transition-colors border border-gray-200"
                  title="Copy secret"
                >
                  {copied ? <CheckCircle2 className="w-4 h-4 text-green-600" /> : <Copy className="w-4 h-4 text-gray-500" />}
                </button>
              </div>
            </div>
          </div>

          <h3 className="font-semibold text-gray-900 mb-2">Step 2: Verify Code</h3>
          <p className="text-sm text-gray-600 mb-3">
            Enter the 6-digit code from your authenticator app to confirm setup.
          </p>
          <div className="flex gap-2">
            <input
              type="text"
              inputMode="numeric"
              maxLength={6}
              value={code}
              onChange={(e) => { setCode(e.target.value.replace(/\D/g, '')); setError(''); }}
              placeholder="6-digit code"
              className="flex-1 px-4 py-3 border-2 border-gray-200 rounded-xl text-center text-lg font-mono tracking-widest focus:border-finnera-500 focus:ring-2 focus:ring-finnera-200 outline-none"
            />
            <button
              onClick={confirmSetup}
              disabled={isLoading || code.length !== 6}
              className="px-6 py-3 bg-finnera-600 hover:bg-finnera-700 text-white font-semibold rounded-xl transition-colors flex items-center gap-2 disabled:opacity-60"
            >
              {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
              Verify
            </button>
          </div>
        </div>
      )}

      {step === 'complete' && (
        <div className="bg-white rounded-xl border border-green-200 p-6 mb-4 text-center">
          <CheckCircle2 className="w-12 h-12 text-green-600 mx-auto mb-3" />
          <h3 className="font-bold text-gray-900 text-lg mb-1">MFA Enabled Successfully</h3>
          <p className="text-sm text-gray-600">
            Your account is now protected with two-factor authentication.
            You'll need your authenticator app for every login.
          </p>
        </div>
      )}

    </div>
  );
}
