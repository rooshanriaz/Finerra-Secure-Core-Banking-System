import { useState, useEffect } from 'react';
import {
  ServerCog, Plus, AlertTriangle, Shield, Loader2, Trash2
} from 'lucide-react';
import adminService from '../services/adminService';
import { useAuth } from '../context/AuthContext';
import { PageShell } from '../components/layout/PageShell';

export default function AdminConsole() {
  const { user } = useAuth();

  const [ipWhitelist, setIpWhitelist] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  const [showAddIpModal, setShowAddIpModal] = useState(false);
  const [newIpForm, setNewIpForm] = useState({ ip: '', description: '' });

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      const ipRes = await adminService.getIpWhitelist().catch(() => ({ success: true, data: [] }));
      const ipData = ipRes?.data || ipRes;
      setIpWhitelist(Array.isArray(ipData) ? ipData : []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load admin data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const handleAddIp = async () => {
    if (!newIpForm.ip.trim()) return;
    try {
      setActionLoading(true);
      await adminService.addIpToWhitelist(newIpForm.ip, newIpForm.description);
      setShowAddIpModal(false);
      setNewIpForm({ ip: '', description: '' });
      await fetchData();
    } catch (err) {
      window.alert('Failed to add IP: ' + (err.response?.data?.message || err.message));
    } finally {
      setActionLoading(false);
    }
  };

  const handleRemoveIp = async (id) => {
    if (!window.confirm('Remove this IP from the whitelist?')) return;
    try {
      setActionLoading(true);
      await adminService.removeIpFromWhitelist(id);
      await fetchData();
    } catch (err) {
      window.alert('Failed to remove IP: ' + (err.response?.data?.message || err.message));
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 className="w-8 h-8 text-finnera-500 animate-spin" />
        <span className="ml-3 text-gray-500">Loading admin console...</span>
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
      title="Core banking admin console"
      subtitle="Manage trusted IP allow-list for privileged API access."
      meta={(
        <div className="text-right">
          <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400">Signed in as</p>
          <p className="text-sm font-semibold text-gray-900">{user?.name}</p>
          <p className="text-xs text-gray-500">{user?.role}</p>
        </div>
      )}
    >

      {/* Info Banner */}
      <div className="flex items-start gap-3 bg-finnera-50 border border-finnera-100 rounded-xl p-4">
        <ServerCog className="w-5 h-5 text-finnera-600 mt-0.5 flex-shrink-0" />
        <div>
          <p className="text-sm font-semibold text-finnera-800">
            System Administrator Access: Manage IP whitelist entries.
          </p>
          <p className="text-xs text-finnera-600 mt-0.5">
            All configuration changes are logged on the Hyperledger Fabric audit trail.
          </p>
        </div>
      </div>

      <div className="space-y-6">
        {/* IP Whitelist */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">IP Whitelist</h2>
              <p className="text-sm text-gray-500">Manage allowed IP addresses for API access</p>
            </div>
            <button
              onClick={() => setShowAddIpModal(true)}
              className="btn-primary flex items-center gap-2 rounded-lg text-sm px-4 py-2 hover:opacity-90 transition-opacity"
            >
              <Plus className="w-4 h-4" />
              Add IP
            </button>
          </div>

          {ipWhitelist.length === 0 ? (
            <div className="text-center py-8 text-gray-400">
              <Shield className="w-10 h-10 mx-auto mb-2 opacity-40" />
              <p className="text-sm">No IPs in whitelist</p>
            </div>
          ) : (
            <div className="space-y-2">
              {ipWhitelist.map((entry) => (
                <div
                  key={entry.id}
                  className="flex items-center justify-between p-3 bg-gray-50 rounded-lg border border-gray-100"
                >
                  <div>
                    <p className="text-sm font-mono font-semibold text-gray-900">{entry.ip || entry.ipAddress}</p>
                    <p className="text-xs text-gray-500">{entry.description || '—'}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="badge-success text-[11px]">Active</span>
                    <button
                      onClick={() => handleRemoveIp(entry.id)}
                      disabled={actionLoading}
                      className="p-1.5 rounded-lg hover:bg-red-50 text-red-400 hover:text-red-600 transition-colors disabled:opacity-50"
                      title="Remove IP"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="card bg-gray-50 border-dashed border-gray-200">
          <div className="flex items-start gap-3">
            <Shield className="w-4 h-4 text-finnera-500 mt-0.5" />
            <p className="text-xs text-gray-600">
              All admin actions are recorded on the Hyperledger Fabric audit ledger for
              non-repudiation and regulatory compliance.
            </p>
          </div>
        </div>
      </div>

      {/* Add IP Modal */}
      {showAddIpModal && (
        <div className="fixed inset-0 bg-black/30 z-50 flex items-center justify-center p-4" onClick={() => setShowAddIpModal(false)}>
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 animate-fadeIn" onClick={e => e.stopPropagation()}>
            <h2 className="text-xl font-bold text-gray-900 mb-4">Add IP to Whitelist</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">IP Address</label>
                <input
                  type="text"
                  value={newIpForm.ip}
                  onChange={e => setNewIpForm(f => ({ ...f, ip: e.target.value }))}
                  className="input-field font-mono"
                  placeholder="e.g. 192.168.1.100"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <input
                  type="text"
                  value={newIpForm.description}
                  onChange={e => setNewIpForm(f => ({ ...f, description: e.target.value }))}
                  className="input-field"
                  placeholder="e.g. Office network"
                />
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button
                onClick={() => setShowAddIpModal(false)}
                className="flex-1 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                disabled={actionLoading || !newIpForm.ip.trim()}
                onClick={handleAddIp}
                className="flex-1 btn-primary rounded-lg hover:opacity-90 transition-opacity flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {actionLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                Add IP
              </button>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}
