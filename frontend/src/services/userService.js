import api from './api';

const userService = {
  async getAll() {
    const { data } = await api.get('/api/v1/users');
    return data;
  },

  async getById(id) {
    const { data } = await api.get(`/api/v1/users/${id}`);
    return data;
  },

  async create(userData) {
    const { data } = await api.post('/api/v1/users', userData);
    return data;
  },

  async update(id, userData) {
    const { data } = await api.put(`/api/v1/users/${id}`, userData);
    return data;
  },

  async changePassword(id, currentPassword, newPassword) {
    const { data } = await api.post(`/api/v1/users/${id}/password`, {
      currentPassword,
      newPassword,
    });
    return data;
  },

  async assignRoles(id, roleIds) {
    const { data } = await api.post(`/api/v1/users/${id}/roles`, { roleIds });
    return data;
  },

  async enable(id) {
    const { data } = await api.post(`/api/v1/users/${id}/enable`);
    return data;
  },

  async disable(id) {
    const { data } = await api.post(`/api/v1/users/${id}/disable`);
    return data;
  },

  async deleteUser(id) {
    const { data } = await api.delete(`/api/v1/users/${id}`);
    return data;
  },
};

export default userService;
