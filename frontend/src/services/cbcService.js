import api from './api';

const cbcService = {
  async getClients() {
    const { data } = await api.get('/api/v1/clients');
    return data;
  },

  async getClient(clientId) {
    const { data } = await api.get(`/api/v1/clients/${clientId}`);
    return data;
  },

  async createClient(clientData) {
    const { data } = await api.post('/api/v1/clients', clientData);
    return data;
  },

  async getSavingsAccounts() {
    const { data } = await api.get('/api/v1/savingsaccounts');
    return data;
  },

  async getSavingsAccount(accountId) {
    const { data } = await api.get(`/api/v1/savingsaccounts/${accountId}`);
    return data;
  },

  async getLoans() {
    const { data } = await api.get('/api/v1/loans');
    return data;
  },

  async getLoan(loanId) {
    const { data } = await api.get(`/api/v1/loans/${loanId}`);
    return data;
  },

  async createLoan(loanData) {
    const { data } = await api.post('/api/v1/loans', loanData);
    return data;
  },

  async approveLoan(loanId, date, amount, note) {
    const { data } = await api.post(`/api/v1/loans/${loanId}`, null, {
      params: { command: 'approve', date, amount, note },
    });
    return data;
  },

  async forwardLoan(loanId, date, note) {
    const { data } = await api.post(`/api/v1/loans/${loanId}`, null, {
      params: { command: 'forward', date, note },
    });
    return data;
  },

  async disburseLoan(loanId, date) {
    const { data } = await api.post(`/api/v1/loans/${loanId}`, null, {
      params: { command: 'disburse', date },
    });
    return data;
  },

  async rejectLoan(loanId, date, note) {
    const { data } = await api.post(`/api/v1/loans/${loanId}`, null, {
      params: { command: 'reject', date, note },
    });
    return data;
  },

  async getLoanTransactions(loanId) {
    const { data } = await api.get(`/api/v1/loans/${loanId}/transactions`);
    return data;
  },

  async getLoanProducts() {
    const { data } = await api.get('/api/v1/loans/products');
    return data;
  },

  async createLoanProduct(productData) {
    const { data } = await api.post('/api/v1/loans/products', productData);
    return data;
  },

  async getReports() {
    const { data } = await api.get('/api/v1/reports');
    return data;
  },

  async getReport(reportName) {
    const { data } = await api.get(`/api/v1/reports/${reportName}`);
    return data;
  },
};

export default cbcService;
