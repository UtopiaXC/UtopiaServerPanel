import { defineStore } from 'pinia';
import { authAPI } from '../api/auth';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    accessToken: localStorage.getItem('accessToken') || null,
    refreshToken: localStorage.getItem('refreshToken') || null,
    /** Permission levels: { admin: 0|1|2, dashboard: 0|1|2, terminal: 0|1|2, logs: 0|1|2 } */
    permissions: JSON.parse(localStorage.getItem('permissions') || '{}'),
    isLoggedIn: false,
    isInitialized: false,
    /** Guest permissions for unauthenticated users */
    guestPermissions: {}
  }),

  getters: {
    /** Check if the user (or guest) has at least readonly access (level >= 1) */
    hasReadAccess: (state) => (key) => {
      if (state.isLoggedIn) {
        return (state.permissions[key] || 0) >= 1;
      }
      return (state.guestPermissions[key] || 0) >= 1;
    },
    /** Check if the user has full access (level >= 2) */
    hasFullAccess: (state) => (key) => {
      if (!state.isLoggedIn) return false;
      return (state.permissions[key] || 0) >= 2;
    },
    /** Get the permission level for a key */
    getPermLevel: (state) => (key) => {
      if (state.isLoggedIn) {
        return state.permissions[key] || 0;
      }
      return state.guestPermissions[key] || 0;
    },
    isAdmin: (state) => state.user?.roleId === 1,
    mustChangePassword: (state) => state.user?.mustChangePassword === true,
    bindingStatus: (state) => state.user?.bindingStatus || 'unbound'
  },

  actions: {
    /**
     * Initialize the auth store on app startup.
     * Validates the existing token by calling GET /api/auth/me.
     * If token is invalid, attempts refresh. If both fail, resets to guest.
     */
    async init() {
      // Always load guest permissions for unauthenticated fallback
      await this.loadGuestPermissions();

      if (this.accessToken) {
        try {
          const { data } = await authAPI.me();
          this.user = data.data;
          this.permissions = data.data.permissions || {};
          localStorage.setItem('permissions', JSON.stringify(this.permissions));
          this.isLoggedIn = true;
        } catch {
          // Token invalid, try refresh
          const refreshed = await this.refreshAccessToken();
          if (refreshed) {
            try {
              const { data } = await authAPI.me();
              this.user = data.data;
              this.permissions = data.data.permissions || {};
              localStorage.setItem('permissions', JSON.stringify(this.permissions));
              this.isLoggedIn = true;
            } catch {
              this.resetState();
            }
          } else {
            this.resetState();
          }
        }
      }
      this.isInitialized = true;
    },

    async login(username, password) {
      const { data } = await authAPI.login(username, password);
      const d = data.data;

      this.accessToken = d.accessToken;
      this.refreshToken = d.refreshToken;
      localStorage.setItem('accessToken', d.accessToken);
      localStorage.setItem('refreshToken', d.refreshToken);

      if (d.user) {
        this.user = d.user;
        this.permissions = d.user.permissions || {};
        localStorage.setItem('permissions', JSON.stringify(this.permissions));
      }

      this.isLoggedIn = true;
      return { mustChangePassword: d.mustChangePassword, bindingStatus: d.user?.bindingStatus };
    },

    async fetchUser() {
      try {
        const { data } = await authAPI.me();
        this.user = data.data;
        this.permissions = data.data.permissions || {};
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

    async changeUsername(newUsername) {
      return await authAPI.changeUsername(newUsername);
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

    async loadGuestPermissions() {
      try {
        const { data } = await authAPI.guestPermissions();
        this.guestPermissions = data.data.permissions || {};
      } catch {
        this.guestPermissions = { dashboard: 1 };
      }
    },

    resetState() {
      this.user = null;
      this.accessToken = null;
      this.refreshToken = null;
      this.permissions = {};
      this.isLoggedIn = false;
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('permissions');
    }
  }
});
