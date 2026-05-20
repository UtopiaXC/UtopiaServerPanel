<template>
  <div class="user-layout">
    <header class="user-header">
      <router-link v-if="$route.name === 'UserHome'" to="/dashboard" class="back-link">&larr; {{ $t('admin.backToPanel') }}</router-link>
      <router-link v-else to="/user/home" class="back-link">&larr; {{ $t('auth.goBack') }}</router-link>
      <div class="header-right">
        <router-link v-if="$route.name === 'UserHome' && auth.hasFullAccess('admin')" to="/admin/roles" class="admin-link">
          {{ $t('admin.title') }}
        </router-link>
        <div class="lang-switch-container">
          <LanguageSwitcher />
        </div>
      </div>
    </header>
    <main class="user-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useAuthStore } from '../stores/auth';
import LanguageSwitcher from '../components/LanguageSwitcher.vue';

const auth = useAuthStore();
</script>

<style scoped>
.user-layout { max-width: 1000px; margin: 0 auto; padding: 20px; min-height: 100vh; }
.user-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color); padding-bottom: 15px; margin-bottom: 20px; }
.header-right { display: flex; align-items: center; gap: 16px; }
.admin-link { background: var(--primary-color); color: #fff; padding: 6px 12px; border-radius: 6px; text-decoration: none; font-size: 0.9rem; font-weight: 500; transition: opacity 0.2s; }
.admin-link:hover { opacity: 0.9; }
.back-link { color: var(--primary-color); text-decoration: none; font-size: 0.95rem; font-weight: 500; display: inline-block; }
.back-link:hover { text-decoration: underline; }
.user-content { background: var(--card-bg); border-radius: 12px; box-shadow: 0 4px 24px var(--shadow-color, rgba(0,0,0,0.1)); padding: 32px; border: 1px solid var(--border-color); }
</style>
