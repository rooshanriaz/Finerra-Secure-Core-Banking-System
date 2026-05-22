import { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Layers, ShieldCheck, AlertCircle, Fingerprint, CheckCircle2, Loader2, Link2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function MfaVerifyPage() {
  const { verifyMfa } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const mfaToken = location.state?.mfaToken;

  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [didStatus, setDidStatus] = useState(null);
  const [success, setSuccess] = useState(false);
  const inputRefs = useRef([]);

  useEffect(() => {
    if (!mfaToken) {
      navigate('/login');
      return;
    }
    inputRefs.current[0]?.focus();
  }, [mfaToken, navigate]);

  const handleChange = (index, value) => {
    if (!/^\d*$/.test(value)) return;

    const newCode = [...code];
    newCode[index] = value.slice(-1);
    setCode(newCode);
    setError('');

    if (value && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }

    const fullCode = newCode.join('');
    if (fullCode.length === 6 && newCode.every(d => d !== '')) {
      handleVerify(fullCode);
    }
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !code[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (pasted.length === 6) {
      const newCode = pasted.split('');
      setCode(newCode);
      inputRefs.current[5]?.focus();
      handleVerify(pasted);
    }
  };

  const handleVerify = async (fullCode) => {
    if (!/^\d{6}$/.test(fullCode)) {
      setError('Please enter a valid 6-digit MFA code.');
      return;
    }
    setIsLoading(true);
    setError('');
    try {
      const result = await verifyMfa(mfaToken, fullCode);
      if (result?.mfaSetupRequired || result?.firstLogin) {
        navigate('/mfa-setup', {
          state: { firstLoginSetup: true },
          replace: true,
        });
        return;
      }
      setDidStatus(result?.didVerified);
      setSuccess(true);
      setTimeout(() => navigate('/dashboard'), 1200);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err.message ||
        'Incorrect MFA code. Please try again.'
      );
      setCode(['', '', '', '', '', '']);
      inputRefs.current[0]?.focus();
    } finally {
      setIsLoading(false);
    }
  };

  if (!mfaToken) return null;

  return (
    <div className="min-h-screen bg-gradient-to-br from-finnera-50 via-finnera-100 to-finnera-200 flex items-center justify-center p-4">
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-40 -right-40 w-80 h-80 bg-finnera-300 rounded-full opacity-30 blur-3xl" />
        <div className="absolute -bottom-40 -left-40 w-80 h-80 bg-finnera-200 rounded-full opacity-30 blur-3xl" />
      </div>

      <div className="relative w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center gap-3 mb-3">
            <div className="w-12 h-12 bg-finnera-600 rounded-xl flex items-center justify-center shadow-lg shadow-finnera-200">
              <Layers className="w-7 h-7 text-white" />
            </div>
            <div className="text-left">
              <h1 className="text-3xl font-bold text-gray-900">Finnera</h1>
              <p className="text-xs font-bold text-finnera-600 tracking-[0.2em] uppercase">Banking Security</p>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-2xl shadow-xl shadow-gray-200/50 p-8 border border-gray-100">
          {!success ? (
            <>
              <div className="flex items-center gap-3 mb-6">
                <div className="w-10 h-10 bg-finnera-100 rounded-lg flex items-center justify-center">
                  <Fingerprint className="w-5 h-5 text-finnera-600" />
                </div>
                <div>
                  <h2 className="text-xl font-bold text-gray-900">TOTP Verification</h2>
                  <p className="text-gray-500 text-sm">Enter the 6-digit code from your authenticator</p>
                </div>
              </div>

              {error && (
                <div className="mb-5 flex items-start gap-3 p-3.5 bg-red-50 rounded-lg border border-red-200">
                  <AlertCircle className="w-4 h-4 text-red-600 mt-0.5 flex-shrink-0" />
                  <p className="text-sm text-red-700 font-medium">{error}</p>
                </div>
              )}

              <div className="flex justify-center gap-2.5 mb-6" onPaste={handlePaste}>
                {code.map((digit, index) => (
                  <input
                    key={index}
                    ref={el => inputRefs.current[index] = el}
                    type="text"
                    inputMode="numeric"
                    maxLength={1}
                    value={digit}
                    onChange={(e) => handleChange(index, e.target.value)}
                    onKeyDown={(e) => handleKeyDown(index, e)}
                    disabled={isLoading}
                    className="w-12 h-14 text-center text-2xl font-bold border-2 border-gray-200 rounded-xl
                               focus:border-finnera-500 focus:ring-2 focus:ring-finnera-200 outline-none
                               transition-all bg-gray-50 disabled:opacity-60"
                  />
                ))}
              </div>

              {isLoading && (
                <div className="flex items-center justify-center gap-2 mb-4 text-finnera-600">
                  <Loader2 className="w-5 h-5 animate-spin" />
                  <span className="text-sm font-medium">Verifying code & DID...</span>
                </div>
              )}

              <div className="flex items-start gap-3 p-3.5 bg-finnera-50 rounded-lg border border-finnera-200">
                <ShieldCheck className="w-4 h-4 text-finnera-600 mt-0.5 flex-shrink-0" />
                <p className="text-xs text-finnera-700 leading-relaxed">
                  Open <span className="font-semibold">Google Authenticator</span> or <span className="font-semibold">Authy</span> and enter the current code for Finnera Banking.
                </p>
              </div>
            </>
          ) : (
            <div className="text-center py-2">
              <div className="w-14 h-14 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <CheckCircle2 className="w-7 h-7 text-green-600" />
              </div>
              <h2 className="text-lg font-bold text-gray-900 mb-1">Authentication Complete</h2>
              <p className="text-gray-500 text-sm mb-4">All security checks passed</p>

              <div className="space-y-2 text-left mb-4">
                <div className="flex items-center gap-2.5 p-2.5 bg-green-50 rounded-lg border border-green-200">
                  <CheckCircle2 className="w-4 h-4 text-green-600 flex-shrink-0" />
                  <span className="text-sm text-green-700 font-medium">TOTP MFA code verified</span>
                </div>
                <div className={`flex items-center gap-2.5 p-2.5 rounded-lg border ${
                  didStatus ? 'bg-green-50 border-green-200' : 'bg-gray-50 border-gray-200'
                }`}>
                  {didStatus ? (
                    <CheckCircle2 className="w-4 h-4 text-green-600 flex-shrink-0" />
                  ) : (
                    <Link2 className="w-4 h-4 text-gray-400 flex-shrink-0" />
                  )}
                  <span className={`text-sm font-medium ${didStatus ? 'text-green-700' : 'text-gray-500'}`}>
                    {didStatus ? 'DID verified on blockchain' : 'DID not linked — complete KYC to enable'}
                  </span>
                </div>
              </div>

              <div className="flex items-center justify-center gap-2 text-finnera-600">
                <Loader2 className="w-4 h-4 animate-spin" />
                <span className="text-sm font-medium">Redirecting to dashboard...</span>
              </div>
            </div>
          )}

          <button
            onClick={() => navigate('/login')}
            className="w-full mt-4 text-sm text-gray-500 hover:text-gray-700 font-medium py-2 transition-colors"
          >
            Back to Login
          </button>
        </div>

        <p className="text-center text-xs text-gray-400 mt-6">
          FinTech API for Core Banking Security &copy; 2025
        </p>
      </div>
    </div>
  );
}
