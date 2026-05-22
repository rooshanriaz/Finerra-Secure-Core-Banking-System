import { useState, useEffect } from 'react';
import { Users, Shield, CheckCircle2, Edit3, Plus, Info, Lock, Unlock, Loader2, AlertTriangle, UserPlus, Mail, KeyRound, Trash2 } from 'lucide-react';
import roleService from '../services/roleService';
import userService from '../services/userService';
import { useAuth, hasSuperAdminAccess } from '../context/AuthContext';
import { PageShell } from '../components/layout/PageShell';

const PASSWORD_RULES = [
  { id: 'length', label: '8+ characters', test: (p) => p.length >= 8 },
  { id: 'upper', label: 'Uppercase letter', test: (p) => /[A-Z]/.test(p) },
  { id: 'lower', label: 'Lowercase letter', test: (p) => /[a-z]/.test(p) },
  { id: 'number', label: 'Number', test: (p) => /\d/.test(p) },
  { id: 'special', label: 'Special character', test: (p) => /[^A-Za-z0-9]/.test(p) },
];

export default function RBACManagement() {
  const { user } = useAuth();
  const isSuperAdmin = hasSuperAdminAccess(user);
  const [roles, setRoles] = useState([]);
  const [users, setUsers] = useState([]);
  const [allPermissions, setAllPermissions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  const [selectedRole, setSelectedRole] = useState(null);
  const [editingPermissions, setEditingPermissions] = useState(false);
  const [selectedPermissionCodes, setSelectedPermissionCodes] = useState([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newRoleForm, setNewRoleForm] = useState({ name: '', description: '' });
  const [newRolePermissions, setNewRolePermissions] = useState([]);
  const [showCreateUserModal, setShowCreateUserModal] = useState(false);
  const [newUserForm, setNewUserForm] = useState({
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    password: '',
    roleName: '',
  });
  const [showUserPassword, setShowUserPassword] = useState(false);
  const [selectedRoleFilter, setSelectedRoleFilter] = useState('ALL');
  const [activeSection, setActiveSection] = useState('users');
  const [showChangePasswordModal, setShowChangePasswordModal] = useState(false);
  const [passwordTargetUser, setPasswordTargetUser] = useState(null);
  const [showResetPassword, setShowResetPassword] = useState(false);
  const [passwordForm, setPasswordForm] = useState({
    newPassword: '',
    confirmPassword: '',
  });

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [rolesRes, usersRes, permsRes] = await Promise.all([
        roleService.getAll(),
        userService.getAll(),
        roleService.getAllPermissions().catch(() => ({ success: true, data: [] })),
      ]);
      setRoles(rolesRes.success !== false ? (Array.isArray(rolesRes.data) ? rolesRes.data : (Array.isArray(rolesRes) ? rolesRes : [])) : []);
      setUsers(usersRes.success !== false ? (Array.isArray(usersRes.data) ? usersRes.data : (Array.isArray(usersRes) ? usersRes : [])) : []);
      setAllPermissions(permsRes.success !== false ? (Array.isArray(permsRes.data) ? permsRes.data : (Array.isArray(permsRes) ? permsRes : [])) : []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load RBAC data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const handleCreateRole = async () => {
    if (!newRoleForm.name.trim()) return;
    try {
      setActionLoading(true);
      await roleService.create({
        ...newRoleForm,
        permissionCodes: newRolePermissions,
      });
      setShowCreateModal(false);
      setNewRoleForm({ name: '', description: '' });
      setNewRolePermissions([]);
      await fetchData();
    } catch (err) {
      window.alert('Failed to create role: ' + (err.response?.data?.message || err.message));
    } finally {
      setActionLoading(false);
    }
  };

  const handleAssignPermissions = async (roleId, permissionIds) => {
    try {
      setActionLoading(true);
      await roleService.assignPermissions(roleId, permissionIds);
      setEditingPermissions(false);
      setSelectedPermissionCodes([]);
      await fetchData();
    } catch (err) {
      window.alert('Failed to assign permissions: ' + (err.response?.data?.message || err.message));
    } finally {
      setActionLoading(false);
    }
  };

  const openPermissionEditor = (role) => {
    setSelectedRole(role);
    setSelectedPermissionCodes((role.permissions || []).map((p) =>
      typeof p === 'string' ? p : (p.code || p.id)
    ).filter(Boolean));
    setEditingPermissions(true);
  };

  const togglePermission = (permissionCode) => {
    setSelectedPermissionCodes((prev) =>
      prev.includes(permissionCode)
        ? prev.filter((code) => code !== permissionCode)
        : [...prev, permissionCode]
    );
  };

  const toggleNewRolePermission = (permissionCode) => {
    setNewRolePermissions((prev) =>
      prev.includes(permissionCode)
        ? prev.filter((code) => code !== permissionCode)
        : [...prev, permissionCode]
    );
  };

  const handleCreateUser = async () => {
    if (!isSuperAdmin) {
      window.alert('Only Super Admin can create users.');
      return;
    }

    const selectedRole = newUserForm.roleName?.trim();
    if (!newUserForm.username.trim() || !newUserForm.password || !selectedRole) {
      window.alert('Please provide username, password, and role.');
      return;
    }

    const failedRule = PASSWORD_RULES.find((rule) => !rule.test(newUserForm.password));
    if (failedRule) {
      window.alert('Password does not meet strong policy requirements.');
      return;
    }

    try {
      setActionLoading(true);
      await userService.create({
        username: newUserForm.username.trim(),
        email: newUserForm.email.trim() || null,
        firstName: newUserForm.firstName.trim() || null,
        lastName: newUserForm.lastName.trim() || null,
        password: newUserForm.password,
        roleNames: [selectedRole],
      });

      setShowCreateUserModal(false);
      setNewUserForm({
        username: '',
        email: '',
        firstName: '',
        lastName: '',
        password: '',
        roleName: '',
      });

      window.alert('User created successfully. On first login, the user will be directed to MFA setup.');
      await fetchData();
    } catch (err) {
      window.alert('Failed to create user: ' + (err.response?.data?.message || err.message));
    } finally {
      setActionLoading(false);
    }
  };

  const getUserCountForRole = (roleName) => {
    return users.filter(u => (u.roles || []).some(r => r.name === roleName || r === roleName)).length;
  };

  const getRoleNames = (u) => {
    const source = u?.roles || u?.authorities || [];
    return source
      .map((r) => (typeof r === 'string' ? r : r?.name || r?.authority || ''))
      .filter(Boolean);
  };

  const handleDeleteUser = async (targetUser) => {
    if (!isSuperAdmin) {
      window.alert('Only Super Admin can remove users.');
      return;
    }
    const userId = targetUser?.id || targetUser?.userId;
    if (!userId) {
      window.alert('Unable to delete this user: missing user ID.');
      return;
    }

    const targetName = targetUser?.username || targetUser?.name || targetUser?.email || 'this user';
    const confirmed = window.confirm(`Are you sure you want to remove ${targetName}? This action cannot be undone.`);
    if (!confirmed) return;

    try {
      setActionLoading(true);
      await userService.deleteUser(userId);
      window.alert('User removed successfully.');
      await fetchData();
    } catch (err) {
      window.alert('Failed to remove user: ' + (err.response?.data?.message || err.message));
    } finally {
      setActionLoading(false);
    }
  };

  const openChangePasswordModal = (targetUser) => {
    setPasswordTargetUser(targetUser);
    setPasswordForm({ newPassword: '', confirmPassword: '' });
    setShowResetPassword(false);
    setShowChangePasswordModal(true);
  };

  const handleChangeUserPassword = async () => {
    if (!isSuperAdmin) {
      window.alert('Only Super Admin can change user passwords.');
      return;
    }
    const userId = passwordTargetUser?.id || passwordTargetUser?.userId;
    if (!userId) {
      window.alert('Unable to change password: missing user ID.');
      return;
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      window.alert('Password confirmation does not match.');
      return;
    }
    const failedRule = PASSWORD_RULES.find((rule) => !rule.test(passwordForm.newPassword || ''));
    if (failedRule) {
      window.alert('Password does not meet strong policy requirements.');
      return;
    }

    try {
      setActionLoading(true);
      await userService.changePassword(userId, 'ADMIN_OVERRIDE', passwordForm.newPassword);
      window.alert('Password updated successfully.');
      setShowChangePasswordModal(false);
      setPasswordTargetUser(null);
      setPasswordForm({ newPassword: '', confirmPassword: '' });
    } catch (err) {
      window.alert('Failed to update password: ' + (err.response?.data?.message || err.message));
    } finally {
      setActionLoading(false);
    }
  };

  const filteredUsers = users.filter((u) => {
    if (selectedRoleFilter === 'ALL') return true;
    return getRoleNames(u).includes(selectedRoleFilter);
  });

  const totalUsers = users.length;

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 className="w-8 h-8 text-finnera-500 animate-spin" />
        <span className="ml-3 text-gray-500">Loading RBAC data...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-96 gap-4">
        <AlertTriangle className="w-12 h-12 text-red-400" />
        <p className="text-red-600 font-medium">{error}</p>
        <button onClick={fetchData} className="btn-primary rounded-lg px-6 py-2 text-sm">Retry</button>
      </div>
    );
  }

  return (
    <PageShell
      title="Role-based access control"
      subtitle="Manage roles, entitlements, and privileged users. Changes propagate through the auth service and are audit-logged."
      meta={(
        <div className="text-right">
          <p className="text-sm text-gray-500">Directory size</p>
          <p className="text-lg font-semibold text-gray-900">{totalUsers} users · {roles.length} roles</p>
        </div>
      )}
      actions={
        activeSection === 'roles' ? (
          <button
            type="button"
            onClick={() => {
              setNewRoleForm({ name: '', description: '' });
              setNewRolePermissions([]);
              setShowCreateModal(true);
            }}
            className="btn-primary inline-flex items-center gap-2 whitespace-nowrap rounded-xl px-4 py-2 text-sm shadow-sm transition-opacity hover:opacity-90"
          >
            <Plus className="h-4 w-4 shrink-0" />
            New role
          </button>
        ) : null
      }
    >

      {/* Admin Notice */}
      <div className="flex items-start gap-3 bg-finnera-50 border border-finnera-100 rounded-xl p-4">
        <Shield className="w-5 h-5 text-finnera-600 mt-0.5 flex-shrink-0" />
        <div>
          <p className="text-sm font-semibold text-finnera-800">
            {isSuperAdmin ? 'Super Admin' : user?.role} Access: You can {isSuperAdmin ? 'modify role permissions across all branches' : 'view role configurations'}
          </p>
          <p className="text-xs text-finnera-600 mt-0.5">
            All changes are logged on the blockchain with your DID for complete audit trail
          </p>
        </div>
      </div>

      <div className="card p-3">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => setActiveSection('users')}
            className={`rounded-lg px-4 py-2.5 text-sm font-semibold transition-colors ${
              activeSection === 'users'
                ? 'bg-finnera-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            Manage Users
          </button>
          <button
            type="button"
            onClick={() => setActiveSection('roles')}
            className={`rounded-lg px-4 py-2.5 text-sm font-semibold transition-colors ${
              activeSection === 'roles'
                ? 'bg-finnera-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            Role Permissions
          </button>
        </div>
      </div>

      {/* Manage Users */}
      {activeSection === 'users' && <div className="card">
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <h2 className="text-lg font-bold text-gray-900">Manage Users</h2>
            <p className="text-sm text-gray-500 mt-1">
              Add or remove users, assign roles, and enforce strong password policy.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <select
              value={selectedRoleFilter}
              onChange={(e) => setSelectedRoleFilter(e.target.value)}
              className="input-field text-sm min-w-[180px]"
            >
              <option value="ALL">All roles</option>
              {roles.map((r) => (
                <option key={r.id} value={r.name}>{r.name}</option>
              ))}
            </select>
            {isSuperAdmin && (
              <button
                type="button"
                onClick={() => {
                  setNewUserForm({
                    username: '',
                    email: '',
                    firstName: '',
                    lastName: '',
                    password: '',
                    roleName: '',
                  });
                  setShowCreateUserModal(true);
                }}
                className="btn-primary inline-flex items-center gap-2 rounded-lg text-sm px-4 py-2 hover:opacity-90 transition-opacity whitespace-nowrap"
              >
                <UserPlus className="w-4 h-4" />
                Add User
              </button>
            )}
          </div>
        </div>

        <div className="mt-4 rounded-xl border border-gray-200 overflow-hidden">
          <div className="bg-gray-50 px-4 py-2.5 grid grid-cols-12 text-xs font-semibold text-gray-500 uppercase tracking-wide">
            <div className="col-span-3">Username</div>
            <div className="col-span-3">Email</div>
            <div className="col-span-3">Role(s)</div>
            <div className="col-span-3 text-right">Actions</div>
          </div>
          <div className="divide-y divide-gray-100">
            {filteredUsers.length === 0 ? (
              <div className="px-4 py-8 text-sm text-gray-500 text-center">
                No users found for selected role.
              </div>
            ) : (
              filteredUsers.map((u, index) => {
                const roleNames = getRoleNames(u);
                const userId = u?.id || u?.userId || `row-${index}`;
                return (
                  <div key={userId} className="px-4 py-3 grid grid-cols-12 items-center gap-3">
                    <div className="col-span-3">
                      <p className="text-sm font-semibold text-gray-900">{u.username || u.name || '-'}</p>
                      {(u.firstName || u.lastName) && (
                        <p className="text-xs text-gray-500">{`${u.firstName || ''} ${u.lastName || ''}`.trim()}</p>
                      )}
                    </div>
                    <div className="col-span-3 text-sm text-gray-700">{u.email || '-'}</div>
                    <div className="col-span-3">
                      <div className="flex flex-wrap gap-1">
                        {roleNames.length ? roleNames.map((name) => (
                          <span key={`${userId}-${name}`} className="badge-info">{name}</span>
                        )) : <span className="text-sm text-gray-400">-</span>}
                      </div>
                    </div>
                    <div className="col-span-3 flex justify-end gap-2">
                      {isSuperAdmin ? (
                        <>
                          <button
                            type="button"
                            onClick={() => openChangePasswordModal(u)}
                            disabled={actionLoading}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md border border-amber-200 text-amber-700 hover:bg-amber-50 disabled:opacity-50"
                          >
                            <KeyRound className="w-4 h-4" />
                            Change Password
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDeleteUser(u)}
                            disabled={actionLoading}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md border border-red-200 text-red-600 hover:bg-red-50 disabled:opacity-50"
                          >
                            <Trash2 className="w-4 h-4" />
                            Remove
                          </button>
                        </>
                      ) : (
                        <span className="text-xs text-gray-400">View only</span>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>}

      {/* Role Cards Grid */}
      {activeSection === 'roles' && <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {roles.map((role) => {
          const rolePermissions = role.permissions || [];
          const userCount = role.userCount ?? getUserCountForRole(role.name);
          return (
            <div
              key={role.id}
              className={`card hover:shadow-md transition-all cursor-pointer ${
                selectedRole?.id === role.id ? 'ring-2 ring-finnera-500 shadow-md' : ''
              }`}
              onClick={() => setSelectedRole(role)}
            >
              {/* Role Header */}
              <div className="flex items-start justify-between mb-4">
                <div className="w-11 h-11 bg-finnera-50 rounded-xl flex items-center justify-center">
                  <Users className="w-6 h-6 text-finnera-600" />
                </div>
                <span className="badge-info">{userCount} users</span>
              </div>

              <h3 className="text-lg font-bold text-gray-900">{role.name}</h3>
              <p className="text-sm text-gray-500 mt-1 leading-relaxed">{role.description || 'No description'}</p>

              {/* Permissions */}
              <div className="mt-4 pt-4 border-t border-gray-100">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Permissions</span>
                  <span className="text-xs text-gray-400">{rolePermissions.length} total</span>
                </div>
                <div className="space-y-2">
                  {rolePermissions.slice(0, 2).map((perm, idx) => {
                    const permName = typeof perm === 'string' ? perm : perm.name || perm.code || 'Permission';
                    const permDesc = typeof perm === 'string' ? '' : perm.description || '';
                    return (
                      <div key={idx} className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-500 mt-0.5 flex-shrink-0" />
                        <div>
                          <p className="text-sm font-medium text-gray-900">{permName}</p>
                          {permDesc && <p className="text-xs text-gray-400">{permDesc}</p>}
                        </div>
                      </div>
                    );
                  })}
                  {rolePermissions.length > 2 && (
                    <p className="text-xs text-finnera-600 font-medium pl-6">
                      +{rolePermissions.length - 2} more permissions
                    </p>
                  )}
                </div>
              </div>

              {/* Edit Button */}
              <button
                className="mt-4 w-full flex items-center justify-center gap-2 py-2.5 bg-gray-50 hover:bg-finnera-50 text-gray-700 hover:text-finnera-700 rounded-lg text-sm font-medium transition-colors"
                onClick={(e) => {
                  e.stopPropagation();
                  openPermissionEditor(role);
                }}
              >
                <Edit3 className="w-4 h-4" />
                Modify Role Permissions
              </button>
            </div>
          );
        })}
      </div>}

      {/* Permission Detail Modal */}
      {selectedRole && editingPermissions && (
        <div className="fixed inset-0 bg-black/30 z-50 flex items-center justify-center p-4" onClick={() => { setEditingPermissions(false); setSelectedPermissionCodes([]); }}>
          <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full p-6 animate-fadeIn" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-6">
              <div>
                <h2 className="text-xl font-bold text-gray-900">{selectedRole.name}</h2>
                <p className="text-sm text-gray-500">Manage permissions for this role</p>
              </div>
              <span className="badge-info">{selectedRole.userCount ?? getUserCountForRole(selectedRole.name)} users</span>
            </div>

            <div className="space-y-3 max-h-[400px] overflow-y-auto">
              {(allPermissions.length > 0 ? allPermissions : (selectedRole.permissions || [])).map((perm, idx) => {
                const permCode = typeof perm === 'string' ? perm : (perm.code || perm.name || '');
                const permName = typeof perm === 'string' ? perm : (perm.code || perm.name || 'Permission');
                const permDesc = typeof perm === 'string' ? '' : (perm.description || '');
                const enabled = selectedPermissionCodes.includes(permCode);
                return (
                  <div key={idx} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                    <div className="flex items-start gap-3">
                      <CheckCircle2 className={`w-5 h-5 mt-0.5 flex-shrink-0 ${enabled ? 'text-emerald-500' : 'text-gray-300'}`} />
                      <div>
                        <p className="text-sm font-medium text-gray-900">{permName}</p>
                        {permDesc && <p className="text-xs text-gray-500">{permDesc}</p>}
                      </div>
                    </div>
                    <button
                      disabled={selectedRole.systemRole}
                      onClick={() => togglePermission(permCode)}
                      className="p-1.5 rounded-lg hover:bg-gray-200 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {enabled ? <Unlock className="w-4 h-4 text-emerald-500" /> : <Lock className="w-4 h-4 text-gray-400" />}
                    </button>
                  </div>
                );
              })}
              {(allPermissions.length === 0 && (selectedRole.permissions || []).length === 0) && (
                <p className="text-sm text-gray-400 text-center py-6">No permissions assigned to this role</p>
              )}
            </div>

            <div className="flex items-center gap-2 mt-6 pt-4 border-t border-gray-100">
              <Info className="w-4 h-4 text-finnera-500 flex-shrink-0" />
              <p className="text-xs text-gray-500">Changes will be logged on the blockchain with your DID signature</p>
            </div>

            <div className="flex gap-3 mt-4">
              <button
                onClick={() => { setEditingPermissions(false); setSelectedPermissionCodes([]); }}
                className="flex-1 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                disabled={actionLoading || selectedRole.systemRole}
                onClick={() => {
                  handleAssignPermissions(selectedRole.id, selectedPermissionCodes);
                }}
                className="flex-1 btn-primary rounded-lg hover:opacity-90 transition-opacity flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {actionLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                {selectedRole.systemRole ? 'System Role (Read-only)' : 'Save Changes'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create Role Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/30 z-50 flex items-center justify-center p-4" onClick={() => setShowCreateModal(false)}>
          <div className="bg-white rounded-2xl shadow-2xl max-w-xl w-full p-6 animate-fadeIn" onClick={e => e.stopPropagation()}>
            <h2 className="text-xl font-bold text-gray-900 mb-4">Create New Role</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Role Name</label>
                <input
                  type="text"
                  value={newRoleForm.name}
                  onChange={e => setNewRoleForm(f => ({ ...f, name: e.target.value }))}
                  className="input-field"
                  placeholder="e.g. Loan Officer"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <input
                  type="text"
                  value={newRoleForm.description}
                  onChange={e => setNewRoleForm(f => ({ ...f, description: e.target.value }))}
                  className="input-field"
                  placeholder="Brief role description"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="block text-sm font-medium text-gray-700">Permissions</label>
                  <span className="text-xs text-gray-500">{newRolePermissions.length} selected</span>
                </div>
                <div className="max-h-56 overflow-y-auto rounded-lg border border-gray-200 p-2 bg-gray-50 space-y-1.5">
                  {allPermissions.length > 0 ? allPermissions.map((perm) => {
                    const permCode = perm.code || perm.name;
                    const isSelected = newRolePermissions.includes(permCode);
                    return (
                      <button
                        type="button"
                        key={permCode}
                        onClick={() => toggleNewRolePermission(permCode)}
                        className={`w-full text-left px-3 py-2 rounded-md border transition-colors ${
                          isSelected
                            ? 'bg-finnera-50 border-finnera-300 text-finnera-800'
                            : 'bg-white border-gray-200 text-gray-700 hover:border-finnera-200'
                        }`}
                      >
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <p className="text-sm font-semibold">{permCode}</p>
                            {perm.description && (
                              <p className="text-xs text-gray-500 mt-0.5">{perm.description}</p>
                            )}
                          </div>
                          {isSelected ? (
                            <CheckCircle2 className="w-4 h-4 text-finnera-600 flex-shrink-0" />
                          ) : (
                            <Lock className="w-4 h-4 text-gray-300 flex-shrink-0" />
                          )}
                        </div>
                      </button>
                    );
                  }) : (
                    <p className="text-sm text-gray-400 text-center py-4">No permissions available</p>
                  )}
                </div>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button
                onClick={() => {
                  setShowCreateModal(false);
                  setNewRolePermissions([]);
                }}
                className="flex-1 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                disabled={actionLoading || !newRoleForm.name.trim()}
                onClick={handleCreateRole}
                className="flex-1 btn-primary rounded-lg hover:opacity-90 transition-opacity flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {actionLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                Create Role
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create User Modal */}
      {showCreateUserModal && (
        <div className="fixed inset-0 bg-black/30 z-50 flex items-center justify-center p-4" onClick={() => setShowCreateUserModal(false)}>
          <div className="bg-white rounded-2xl shadow-2xl max-w-xl w-full p-6 animate-fadeIn" onClick={e => e.stopPropagation()}>
            <h2 className="text-xl font-bold text-gray-900 mb-1">Create User</h2>
            <p className="text-sm text-gray-500 mb-4">
              Super Admin can create user accounts and assign a role. New users will configure MFA on first login.
            </p>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Username</label>
                <input
                  type="text"
                  value={newUserForm.username}
                  onChange={(e) => setNewUserForm((f) => ({ ...f, username: e.target.value }))}
                  className="input-field"
                  placeholder="e.g. loan.officer1"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
                <select
                  value={newUserForm.roleName}
                  onChange={(e) => setNewUserForm((f) => ({ ...f, roleName: e.target.value }))}
                  className="input-field"
                >
                  <option value="">Select role</option>
                  {roles.map((role) => (
                    <option key={role.id} value={role.name}>{role.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email (optional)</label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="email"
                    value={newUserForm.email}
                    onChange={(e) => setNewUserForm((f) => ({ ...f, email: e.target.value }))}
                    className="input-field pl-9"
                    placeholder="name@bank.com"
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
                <div className="relative">
                  <KeyRound className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type={showUserPassword ? 'text' : 'password'}
                    value={newUserForm.password}
                    onChange={(e) => setNewUserForm((f) => ({ ...f, password: e.target.value }))}
                    className="input-field pl-9 pr-20"
                    placeholder="Strong password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowUserPassword((s) => !s)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-finnera-600 font-semibold"
                  >
                    {showUserPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">First Name (optional)</label>
                <input
                  type="text"
                  value={newUserForm.firstName}
                  onChange={(e) => setNewUserForm((f) => ({ ...f, firstName: e.target.value }))}
                  className="input-field"
                  placeholder="First name"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Last Name (optional)</label>
                <input
                  type="text"
                  value={newUserForm.lastName}
                  onChange={(e) => setNewUserForm((f) => ({ ...f, lastName: e.target.value }))}
                  className="input-field"
                  placeholder="Last name"
                />
              </div>
            </div>

            <div className="mt-4 p-3 rounded-lg border border-finnera-100 bg-finnera-50">
              <p className="text-xs font-semibold text-finnera-700 uppercase tracking-wide mb-1.5">Strong Password Policy</p>
              <div className="grid grid-cols-2 gap-x-3 gap-y-1.5">
                {PASSWORD_RULES.map((rule) => {
                  const passed = rule.test(newUserForm.password || '');
                  return (
                    <div key={rule.id} className="flex items-center gap-1.5">
                      <CheckCircle2 className={`w-3.5 h-3.5 ${passed ? 'text-emerald-600' : 'text-gray-300'}`} />
                      <span className={`text-xs ${passed ? 'text-emerald-700' : 'text-gray-500'}`}>{rule.label}</span>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="flex gap-3 mt-6">
              <button
                onClick={() => setShowCreateUserModal(false)}
                className="flex-1 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                disabled={
                  actionLoading ||
                  !newUserForm.username.trim() ||
                  !newUserForm.roleName.trim() ||
                  PASSWORD_RULES.some((r) => !r.test(newUserForm.password || ''))
                }
                onClick={handleCreateUser}
                className="flex-1 btn-primary rounded-lg hover:opacity-90 transition-opacity flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {actionLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                Create User
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Change Password Modal */}
      {showChangePasswordModal && (
        <div className="fixed inset-0 bg-black/30 z-50 flex items-center justify-center p-4" onClick={() => setShowChangePasswordModal(false)}>
          <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full p-6 animate-fadeIn" onClick={e => e.stopPropagation()}>
            <h2 className="text-xl font-bold text-gray-900 mb-1">Change User Password</h2>
            <p className="text-sm text-gray-500 mb-4">
              Updating password for <span className="font-semibold text-gray-700">{passwordTargetUser?.username || passwordTargetUser?.name || 'selected user'}</span>.
            </p>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">New Password</label>
                <div className="relative">
                  <KeyRound className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type={showResetPassword ? 'text' : 'password'}
                    value={passwordForm.newPassword}
                    onChange={(e) => setPasswordForm((f) => ({ ...f, newPassword: e.target.value }))}
                    className="input-field pl-9 pr-20"
                    placeholder="Strong password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowResetPassword((s) => !s)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-finnera-600 font-semibold"
                  >
                    {showResetPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Confirm New Password</label>
                <input
                  type={showResetPassword ? 'text' : 'password'}
                  value={passwordForm.confirmPassword}
                  onChange={(e) => setPasswordForm((f) => ({ ...f, confirmPassword: e.target.value }))}
                  className="input-field"
                  placeholder="Re-enter new password"
                />
              </div>
            </div>

            <div className="mt-4 p-3 rounded-lg border border-finnera-100 bg-finnera-50">
              <p className="text-xs font-semibold text-finnera-700 uppercase tracking-wide mb-1.5">Strong Password Policy</p>
              <div className="grid grid-cols-2 gap-x-3 gap-y-1.5">
                {PASSWORD_RULES.map((rule) => {
                  const passed = rule.test(passwordForm.newPassword || '');
                  return (
                    <div key={rule.id} className="flex items-center gap-1.5">
                      <CheckCircle2 className={`w-3.5 h-3.5 ${passed ? 'text-emerald-600' : 'text-gray-300'}`} />
                      <span className={`text-xs ${passed ? 'text-emerald-700' : 'text-gray-500'}`}>{rule.label}</span>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="flex gap-3 mt-6">
              <button
                onClick={() => setShowChangePasswordModal(false)}
                className="flex-1 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                disabled={
                  actionLoading ||
                  !passwordForm.newPassword ||
                  passwordForm.newPassword !== passwordForm.confirmPassword ||
                  PASSWORD_RULES.some((r) => !r.test(passwordForm.newPassword || ''))
                }
                onClick={handleChangeUserPassword}
                className="flex-1 btn-primary rounded-lg hover:opacity-90 transition-opacity flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {actionLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                Update Password
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Blockchain Notice */}
      <div className="card bg-gray-50 border-dashed border-gray-200">
        <div className="flex items-center gap-3 text-gray-500">
          <Shield className="w-5 h-5 text-finnera-500" />
          <p className="text-sm">
            All role modifications are cryptographically signed with your DID and anchored to the Hyperledger Fabric blockchain for immutable audit trail.
          </p>
        </div>
      </div>
    </PageShell>
  );
}
