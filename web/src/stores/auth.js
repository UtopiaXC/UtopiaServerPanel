import { defineStore } from 'pinia';
import { authAPI } from '../api/auth';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    accessToken: localStorage.getItem('accessToken') || null,
    refreshToken: localStorage.getItem('refreshToken') || null,
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]'),
    isLoggedIn: !!localStorage.getItem('accessToken')
  }),

  getters: {
    hasPermission: (state) => (key) => {
      if (!state.isLoggedIn) return false;
      return state.permissions.includes(key);
    },
    isAdmin: (state) => state.user?.roleId === 1,
    mustChangePassword: (state) => state.user?.mustChangePassword === true,
    bindingStatus: (state) => state.user?.bindingStatus || 'unbound'
  },

  actions: {
    async login(username, password) {
      const { data } = await authAPI.login(username, password);
      const d = data.data;

      this.accessToken = d.accessToken;
      this.refreshToken = d.refreshToken;
      localStorage.setItem('accessToken', d.accessToken);
      localStorage.setItem('refreshToken', d.refreshToken);

      if (d.user) {
        this.user = d.user;
        this.permissions = d.user.permissions || [];
        localStorage.setItem('permissions', JSON.stringify(this.permissions));
      }

      this.isLoggedIn = true;
      return { mustChangePassword: d.mustChangePassword, bindingStatus: d.user?.bindingStatus };
    },

    async fetchUser() {
      try {
        const { data } = await authAPI.me();
        this.user = data.data;
        this.permissions = data.data.permissions || [];
        localStorage.setItem('permissions', JSON.stringify(this.permissions));
      } catch {
        this.logout();
      }
    },

    async logout() {
      try {
        if (this.refreshToken) {
          await authAPI.logout(this.refreshToken);
        }
      } catch { /* ignore */ }
      this.resetState();
    },

    async changePassword(oldPassword, newPassword) {
      return await authAPI.changePassword(oldPassword, newPassword);
    },

    async refreshAccessToken() {
      if (!this.refreshToken) return false;
      try {
        const { data } = await authAPI.refresh(this.refreshToken);
        this.accessToken = data.data.accessToken;
        this.refreshToken = data.data.refreshToken;
        localStorage.setItem('accessToken', data.data.accessToken);
        localStorage.setItem('refreshToken', data.data.refreshToken);
        return true;
      } catch {
        this.resetState();
        return false;
      }
    },

    resetState() {
      this.user = null;
      this.accessToken = null;
      this.refreshToken = null;
      this.permissions = [];
      this.isLoggedIn = false;
      localStorage.clear();
    }
  }
});
