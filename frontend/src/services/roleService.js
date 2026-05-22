import api from './api';

const roleService = {
  async getAll() {
    const { data } = await api.get('/api/v1/auth-roles');
    return data;
  },

  async getEnabled() {
    const { data } = await api.get('/api/v1/auth-roles/enabled');
    return data;
  },

  async getById(id) {
    const { data } = await api.get(`/api/v1/auth-roles/${id}`);
    return data;
  },

  async create(roleData) {
    const { data } = await api.post('/api/v1/auth-roles', roleData);
    return data;
  },

  async update(id, roleData) {
    const { data } = await api.put(`/api/v1/auth-roles/${id}`, roleData);
    return data;
  },

  async assignPermissions(id, permissionIds) {
    const { data } = await api.post(`/api/v1/auth-roles/${id}/permissions`, permissionIds);
    return data;
  },

  async getAllPermissions() {
    const { data } = await api.get('/api/v1/auth-roles/permissions');
    return data;
  },

  async getPermissionGroupings() {
    const { data } = await api.get('/api/v1/auth-roles/permissions/groupings');
    return data;
  },

  async deleteRole(id) {
    const { data } = await api.delete(`/api/v1/auth-roles/${id}`);
    return data;
  },
};

export default roleService;
