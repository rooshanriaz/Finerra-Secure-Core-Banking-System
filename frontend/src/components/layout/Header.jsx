import { Bell, Settings, LogOut } from 'lucide-react';
import { useState, useEffect, useCallback } from 'react';
import { useAuth, hasSuperAdminAccess } from '../../context/AuthContext';
import adminService from '../../services/adminService';

function formatNotificationTime(ts) {
  if (ts == null || typeof ts !== 'string') return '';
  const d = new Date(ts);
  return Number.isNaN(d.getTime())
    ? ''
    : d.toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
}

export default function Header() {
  const { user, logout } = useAuth();
  const [showNotifications, setShowNotifications] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const isAdmin = hasSuperAdminAccess(user);

  const loadSecurityNotifications = useCallback(async () => {
    if (!isAdmin) {
      setNotifications([]);
      return;
    }
    try {
      const res = await adminService.getSecurityLockoutNotifications();
      const inner = res?.data ?? res;
      const raw = Array.isArray(inner) ? inner : [];
      setNotifications(
        raw.map((n) => ({
          id: n.id,
          text: [n.action, n.message].filter(Boolean).join(' — ') || 'Security event',
          sub: n.subjectUsername ? `User: ${n.subjectUsername}` : null,
          time: formatNotificationTime(n.timestamp),
        })),
      );
    } catch {
      setNotifications([]);
    }
  }, [isAdmin]);

  useEffect(() => {
    loadSecurityNotifications();
    if (!isAdmin) return undefined;
    const t = setInterval(loadSecurityNotifications, 90_000);
    return () => clearInterval(t);
  }, [isAdmin, loadSecurityNotifications]);

  useEffect(() => {
    if (showNotifications && isAdmin) loadSecurityNotifications();
  }, [showNotifications, isAdmin, loadSecurityNotifications]);

  return (
    <header className="sticky top-0 z-30 flex items-center justify-between border-b border-gray-200/80 bg-white/90 px-5 py-3 backdrop-blur-md sm:px-6 lg:px-8">
      <div className="flex flex-col">
        <span className="text-[10px] font-semibold uppercase tracking-widest text-gray-400">Current role</span>
        <span className="text-sm font-semibold text-gray-900">{user?.role}</span>
      </div>

      <div className="flex items-center gap-4">
        {/* Security notifications (admin only): lockouts / brute-force from auth audit */}
        {isAdmin && (
        <div className="relative">
          <button
            type="button"
            aria-expanded={showNotifications}
            aria-haspopup="true"
            aria-label="Security notifications"
            onClick={() => { setShowNotifications(!showNotifications); }}
            className="relative p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-50 rounded-lg transition-colors"
          >
            <Bell className="w-5 h-5" />
            {notifications.length > 0 && (
              <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full" aria-hidden />
            )}
          </button>
          {showNotifications && (
            <div className="absolute right-0 mt-2 w-80 bg-white rounded-xl shadow-lg border border-gray-100 py-2 animate-fadeIn">
              <div className="px-4 py-2 border-b border-gray-100">
                <h3 className="text-sm font-semibold text-gray-900">Security notifications</h3>
                <p className="text-xs text-gray-500 mt-0.5">Lockouts and IP brute-force signals</p>
              </div>
              {notifications.length === 0 ? (
                <p className="px-4 py-6 text-sm text-gray-500 text-center">No lockout events recently.</p>
              ) : (
                notifications.map((n) => (
                  <div key={n.id} className="px-4 py-3 hover:bg-gray-50 border-b border-gray-50 last:border-0">
                    <p className="text-sm text-gray-700">{n.text}</p>
                    {n.sub && <p className="text-xs text-gray-500 mt-0.5">{n.sub}</p>}
                    <p className="text-xs text-gray-400 mt-1">{n.time}</p>
                  </div>
                ))
              )}
            </div>
          )}
        </div>
        )}

        {/* Settings */}
        <button className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-50 rounded-lg transition-colors">
          <Settings className="w-5 h-5" />
        </button>

        {/* User Avatar */}
        <div className="flex items-center gap-3 pl-3 border-l border-gray-200">
          <div className="w-9 h-9 bg-finnera-600 rounded-full flex items-center justify-center text-white text-sm font-semibold">
            {user?.avatar}
          </div>
          <div className="hidden md:block">
            <p className="text-sm font-semibold text-gray-900 leading-tight">{user?.name}</p>
            <p className="text-xs text-gray-500">{user?.email}</p>
          </div>

          <button
            onClick={logout}
            className="p-1.5 text-gray-400 hover:text-red-500 transition-colors"
            title="Logout"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  );
}
