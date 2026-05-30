import { defineStore } from 'pinia';
import api from '../api/index';

export const useSiteStore = defineStore('site', {
  state: () => ({
    siteName: ''
  }),
  actions: {
    async fetchSiteName() {
      try {
        const { data } = await api.get('/settings/site');
        if (data.data?.name) {
          this.siteName = data.data.name;
          document.title = this.siteName;
        }
      } catch (e) {
        // ignore
      }
    },
    setSiteName(name) {
      this.siteName = name;
      document.title = name;
    }
  }
});
