import api from './api';

function toOnboardPayload(clientData = {}) {
  const cnicValue = String(clientData.cnicNumber || clientData.cnic || '').trim();
  const mobileValue = String(clientData.mobileNumber || clientData.phone || '').trim();
  return {
    cnicNumber: cnicValue,
    firstName: clientData.firstName || '',
    lastName: clientData.lastName || '',
    dateOfBirth: clientData.dateOfBirth || '',
    fatherName: clientData.fatherName || '',
    address: clientData.address || '',
    mobileNumber: mobileValue,
    email: clientData.email || '',
    officeId: clientData.officeId || 1,
  };
}

function toCnicPayload(cnicData = {}) {
  const fullName = String(cnicData.name || '').trim();
  const parts = fullName.split(/\s+/).filter(Boolean);
  const firstName = parts[0] || '';
  const lastName = parts.slice(1).join(' ') || 'N/A';
  return {
    cnicNumber: cnicData.cnicNumber || cnicData.cnic || '',
    firstName,
    lastName,
    dateOfBirth: cnicData.dateOfBirth || '',
    fatherName: cnicData.fatherName || '',
    address: cnicData.address || '',
    mobileNumber: cnicData.mobileNumber || '',
    email: cnicData.email || '',
    officeId: cnicData.officeId || 1,
  };
}

const kycService = {
  async onboard(clientData) {
    const { data } = await api.post('/api/v1/kyc/onboard', toOnboardPayload(clientData));
    return data;
  },

  async getStatus(referenceId) {
    const { data } = await api.get(`/api/v1/kyc/${referenceId}/status`);
    return data;
  },

  async getClientStatus(clientId) {
    const { data } = await api.get(`/api/v1/kyc/client/${clientId}/status`);
    return data;
  },

  async verifyCnic(cnicData) {
    const { data } = await api.post('/api/v1/kyc/verify-cnic', toCnicPayload(cnicData));
    return data;
  },

  async amlScreen(screenData) {
    const { data } = await api.post('/api/v1/aml/screen', screenData);
    return data;
  },

  async searchSanctions(query) {
    const { data } = await api.get('/api/v1/aml/sanctions/search', { params: { query } });
    return data;
  },
};

export default kycService;
