import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Shield,
  FileText,
  History,
  ScrollText,
  UserCheck,
  AlertTriangle,
  Layers,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Landmark,
  FilePlus2,
  ServerCog,
  FileBarChart,
  Fingerprint,
} from 'lucide-react';
import { useAuth, hasSuperAdminAccess } from '../../context/AuthContext';

const allNavItems = [
  { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/rbac', label: 'Access Control', icon: Shield },
  { path: '/loans', label: 'Loan Review', icon: Landmark },
  { path: '/loans/create-product', label: 'Loan product', icon: FilePlus2 },
  { path: '/transactions', label: 'Transactions', icon: History },
  { path: '/kyc', label: 'KYC / AML', icon: UserCheck },
  { path: '/fraud', label: 'Fraud Detection', icon: AlertTriangle },
  { path: '/audit', label: 'Audit Logs', icon: ScrollText },
  { path: '/compliance', label: 'Compliance Desk', icon: FileBarChart },
  { path: '/api-docs', label: 'API Docs', icon: FileText },
];

/** Every role gets Dashboard; Security Settings is appended below for everyone. */
const CORE_PATHS = ['/dashboard'];

/**
 * Role-specific routes (merged with CORE_PATHS). `user` = fallback when type is missing.
 */
const allowedPathsByType = {
  user: ['/api-docs'],
  compliance: ['/kyc', '/fraud', '/audit', '/compliance', '/transactions', '/api-docs'],
  loan_officer: ['/loans', '/loans/create-product', '/transactions', '/api-docs'],
  manager: ['/loans', '/transactions', '/service-desk', '/api-docs'],
};

export default function Sidebar({ collapsed, onToggle }) {
  const { user, logout } = useAuth();

  const isSuperAdmin = hasSuperAdminAccess(user);

  const userType = user?.type || 'user';

  const pathsForRole = () => {
    if (isSuperAdmin) {
      return allNavItems.map((i) => i.path);
    }
    const extra = allowedPathsByType[userType] ?? allowedPathsByType.user;
    return [...new Set([...CORE_PATHS, ...extra])];
  };

  const allowedPathSet = new Set(pathsForRole());

  const baseNavItems = isSuperAdmin
    ? allNavItems
    : allNavItems.filter((item) => allowedPathSet.has(item.path));

  const securityItem = { path: '/mfa-setup', label: 'Security Settings', icon: Fingerprint };

  const navItems = isSuperAdmin
    ? [
        ...baseNavItems,
        securityItem,
        { path: '/admin', label: 'Admin Console', icon: ServerCog },
      ]
    : [...baseNavItems, securityItem];

  return (
    <aside
      className={`fixed left-0 top-0 z-40 flex h-screen flex-col border-r border-gray-200/90 bg-white shadow-sm shadow-gray-200/30 transition-all duration-300 ${
        collapsed ? 'w-[72px]' : 'w-[260px]'
      }`}
    >
      <div className="flex items-center gap-3 border-b border-gray-100 bg-gradient-to-br from-finnera-600 to-finnera-800 px-4 py-5">
        <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl bg-white/15 ring-1 ring-white/25">
          <Layers className="h-5 w-5 text-white" />
        </div>
        {!collapsed && (
          <div className="animate-fadeIn min-w-0">
            <h1 className="text-lg font-semibold tracking-tight text-white">Finnera</h1>
            <p className="text-[10px] font-medium uppercase tracking-widest text-finnera-100/90">Banking security</p>
          </div>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-4 px-3 space-y-1 overflow-y-auto">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-all duration-200 ${
                isActive
                  ? 'bg-finnera-50 text-finnera-800 shadow-sm ring-1 ring-finnera-100'
                  : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
              } ${collapsed ? 'justify-center' : ''}`
            }
            title={collapsed ? item.label : ''}
          >
            <item.icon className={`w-5 h-5 flex-shrink-0`} />
            {!collapsed && <span className="animate-fadeIn">{item.label}</span>}
          </NavLink>
        ))}
      </nav>

      {/* Bottom Section */}
      <div className="border-t border-gray-100 p-3 space-y-1">
        <button
          onClick={logout}
          className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-gray-600 hover:bg-red-50 hover:text-red-600 w-full transition-all ${
            collapsed ? 'justify-center' : ''
          }`}
          title={collapsed ? 'Logout' : ''}
        >
          <LogOut className="w-5 h-5 flex-shrink-0" />
          {!collapsed && <span>Logout</span>}
        </button>

        <button
          onClick={onToggle}
          className="flex items-center justify-center w-full py-2 rounded-lg text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition-all"
        >
          {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
        </button>
      </div>
    </aside>
  );
}
