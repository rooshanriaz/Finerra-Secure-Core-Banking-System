import api from './api';

const transactionService = {
  async deposit(accountId, transactionAmount, transactionDate, note) {
    const { data } = await api.post(`/api/v1/transactions/savings/${accountId}/deposit`, {
      transactionAmount, transactionDate, note,
    });
    return data;
  },

  async withdraw(accountId, transactionAmount, transactionDate, note) {
    const { data } = await api.post(`/api/v1/transactions/savings/${accountId}/withdrawal`, {
      transactionAmount, transactionDate, note,
    });
    return data;
  },

  async loanRepayment(loanId, transactionAmount, transactionDate, note) {
    const { data } = await api.post(`/api/v1/transactions/loans/${loanId}/repayment`, {
      transactionAmount, transactionDate, note,
    });
    return data;
  },

  async getById(transactionId) {
    const { data } = await api.get(`/api/v1/transactions/${transactionId}`);
    return data;
  },

  async getHistory(accountId) {
    const { data } = await api.get(`/api/v1/transactions/savings/${accountId}/history`);
    return data;
  },

  /** Admin / branch manager: aggregate recent rows for dashboards (requires ADMIN or MANAGER role). */
  async getRecent(limit = 500) {
    const { data } = await api.get('/api/v1/transactions/recent', {
      params: { limit },
    });
    return data;
  },

  async createRequest(payload) {
    const { data } = await api.post('/api/v1/transactions/requests', payload);
    return data;
  },

  async listRequests(status) {
    const { data } = await api.get('/api/v1/transactions/requests', {
      params: status ? { status } : {},
    });
    return data;
  },

  async approveRequest(requestId, note) {
    const { data } = await api.post(`/api/v1/transactions/requests/${requestId}/approve`, null, {
      params: note ? { note } : {},
    });
    return data;
  },

  async declineRequest(requestId, note) {
    const { data } = await api.post(`/api/v1/transactions/requests/${requestId}/decline`, null, {
      params: note ? { note } : {},
    });
    return data;
  },
};

export default transactionService;
