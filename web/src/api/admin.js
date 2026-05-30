import api from './index';

export const adminAPI = {
  // Users
  listUsers() { return api.get('/admin/users'); },
  createUser(data) { return api.post('/admin/users', data); },
  updateUser(id, data) { return api.put(`/admin/users/${id}`, data); },
  deleteUser(id) { return api.delete(`/admin/users/${id}`); },

  // Roles
  listRoles() { return api.get('/admin/roles'); },
  getRole(id) { return api.get(`/admin/roles/${id}`); },
  createRole(data) { return api.post('/admin/roles', data); },
  updateRole(id, data) { return api.put(`/admin/roles/${id}`, data); },
  deleteRole(id) { return api.delete(`/admin/roles/${id}`); },

  // Permissions
  listPermissions() { return api.get('/admin/permissions'); },

  // Settings
  getSiteName() { return api.get('/settings/site'); },
  setSiteName(name) { return api.put('/settings/site', { name }); },
  getMonitorConfig() { return api.get('/settings/monitor'); },
  setMonitorConfig(data) { return api.put('/settings/monitor', data); }
};

export const bindingAPI = {
  bind(code) { return api.post('/binding/bind', { code }); },
  unbind(userId) { return api.post('/binding/unbind', userId ? { userId } : {}); }
};

export const monitorAPI = {
  queryPerfLogs(start, end) { return api.get('/monitor/perf', { params: { start, end } }); },
  queryPlayerEvents(start, end) { return api.get('/monitor/players', { params: { start, end } }); },
  getDisplayConfig() { return api.get('/monitor/config'); }
};
