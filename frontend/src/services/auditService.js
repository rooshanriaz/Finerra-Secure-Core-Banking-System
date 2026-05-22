import api from './api';
import { normalizeAuditRecords } from '../lib/auditRecordUtils';

const auditService = {
  async getRecent() {
    const { data } = await api.get('/api/v1/audit/recent');
    if (data && Array.isArray(data.data)) {
      return { ...data, data: normalizeAuditRecords(data.data) };
    }
    return data;
  },

  async getById(auditId) {
    const { data } = await api.get(`/api/v1/audit/${auditId}`);
    return data;
  },

  async getByTransaction(transactionId) {
    const { data } = await api.get(`/api/v1/audit/transaction/${transactionId}`);
    return data;
  },

  async getByAccount(accountId) {
    const { data } = await api.get(`/api/v1/audit/account/${accountId}`);
    return data;
  },

  async getByUser(username) {
    const { data } = await api.get(`/api/v1/audit/user/${username}`);
    return data;
  },

  async verify(auditId) {
    const { data } = await api.get(`/api/v1/audit/${auditId}/verify`);
    return data;
  },

  async integrityCheck() {
    const { data } = await api.post('/api/v1/audit/integrity-check');
    return data;
  },

  async exportReport({ format = 'csv', from, to } = {}) {
    const params = { format };
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await api.get('/api/v1/audit/reports/export', {
      params,
      responseType: 'blob',
    });
    return response;
  },
};

export default auditService;
