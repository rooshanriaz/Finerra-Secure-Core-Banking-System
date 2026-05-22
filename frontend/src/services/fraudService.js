import api from './api';

const fraudService = {
  async getAlerts() {
    const { data } = await api.get('/api/v1/fraud/alerts');
    return data;
  },

  async getAlertById(alertId) {
    const { data } = await api.get(`/api/v1/fraud/alerts/${alertId}`);
    return data;
  },

  async getAlertsByStatus(status) {
    const { data } = await api.get(`/api/v1/fraud/alerts/status/${status}`);
    return data;
  },

  async getAlertsByAccount(accountId) {
    const { data } = await api.get(`/api/v1/fraud/alerts/account/${accountId}`);
    return data;
  },

  async getStats() {
    const { data } = await api.get('/api/v1/fraud/alerts/stats');
    return data;
  },

  async updateAlertStatus(alertId, status, note) {
    const { data } = await api.put(
      `/api/v1/fraud/alerts/${alertId}/status`,
      null,
      { params: { status, note } }
    );
    return data;
  },

  async getThresholds() {
    const { data } = await api.get('/api/v1/fraud/thresholds');
    return data;
  },

  async updateThreshold(thresholdName, value, description) {
    const { data } = await api.put('/api/v1/fraud/thresholds', {
      thresholdName, value, description,
    });
    return data;
  },

  async scoreTransaction(transactionId, accountId, transactionType, amount) {
    const { data } = await api.post('/api/v1/fraud/score', {
      transactionId, accountId, transactionType, amount,
    });
    return data;
  },
};

export default fraudService;
