import api from './api';

const adminService = {
  async getSystemInfo() {
    const { data } = await api.get('/api/v1/admin/system/info');
    return data;
  },

  async getIpWhitelist() {
    const { data } = await api.get('/api/v1/admin/ip-whitelist');
    return data;
  },

  async addIpToWhitelist(ip, description) {
    const { data } = await api.post('/api/v1/admin/ip-whitelist', {
      ipAddress: ip,
      description,
      type: (ip || '').includes('/') ? 'CIDR' : ((ip || '').includes('-') ? 'RANGE' : 'SINGLE'),
    });
    return data;
  },

  async removeIpFromWhitelist(id) {
    const { data } = await api.delete(`/api/v1/admin/ip-whitelist/${id}`);
    return data;
  },

  async getSecurityAuditLogs() {
    const { data } = await api.get('/api/v1/admin/audit/security');
    return data;
  },

  async getFailedActions() {
    const { data } = await api.get('/api/v1/admin/audit/failed');
    return data;
  },

  async getUserAuditLogs(userId) {
    const { data } = await api.get(`/api/v1/admin/audit/user/${userId}`);
    return data;
  },

  async getRevocationStats() {
    const { data } = await api.get('/api/v1/admin/revocation/stats');
    return data;
  },

  /** Admin-only: account lockouts and IP brute-force signals for dashboard bell. */
  async getSecurityLockoutNotifications() {
    const { data } = await api.get('/api/v1/admin/notifications/security-lockouts', {
      params: { page: 0, size: 25 },
    });
    return data;
  },
};

export default adminService;
