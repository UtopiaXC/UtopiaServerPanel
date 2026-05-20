import api from './index';

export const authAPI = {
  login(username, password) {
    return api.post('/auth/login', { username, password });
  },
  refresh(refreshToken) {
    return api.post('/auth/refresh', { refreshToken });
  },
  logout(refreshToken) {
    return api.post('/auth/logout', { refreshToken });
  },
  changePassword(oldPassword, newPassword) {
    return api.post('/auth/change-password', { oldPassword, newPassword });
  },
  changeUsername(newUsername) {
    return api.put('/auth/username', { newUsername });
  },
  register(username, password, bindingCode) {
    return api.post('/auth/register', { username, password, bindingCode });
  },
  me() {
    return api.get('/auth/me');
  },
  permissions() {
    return api.get('/auth/permissions');
  },
  guestPermissions() {
    return api.get('/auth/guest-permissions');
  },
  getPlayerData() {
    return api.get('/player/me');
  }
};
