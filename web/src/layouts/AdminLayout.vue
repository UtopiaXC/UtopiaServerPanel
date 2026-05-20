<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <router-link to="/dashboard" class="back-link">&larr; {{ $t('admin.backToPanel') }}</router-link>
      </div>
      <nav class="sidebar-nav">
        <router-link v-if="auth.hasReadAccess('admin')" to="/admin/roles" class="nav-item" active-class="active">
          <span class="nav-icon"></span> {{ $t('admin.roles') }}
        </router-link>
        <router-link v-if="auth.hasReadAccess('admin')" to="/admin/users" class="nav-item" active-class="active">
          <span class="nav-icon"></span> {{ $t('user.profile.userManagement') }}
        </router-link>
      </nav>
    </aside>
    <main class="admin-content">
      <div class="content-header">
        <LanguageSwitcher />
      </div>
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
.admin-layout { display: flex; min-height: calc(100vh - 80px); gap: 24px; max-width: 1100px; margin: 0 auto; padding: 20px; }
.sidebar { width: 220px; flex-shrink: 0; background: var(--card-bg); border-radius: 10px; padding: 16px; border: 1px solid var(--border-color); }
.sidebar-header { margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--border-color); }
.back-link { color: var(--primary-color); text-decoration: none; font-size: 0.9rem; font-weight: 500; }
.back-link:hover { text-decoration: underline; }
.sidebar-nav { display: flex; flex-direction: column; gap: 4px; }
.nav-item { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-radius: 8px; text-decoration: none; color: var(--text-secondary); font-size: 0.95rem; transition: all 0.15s; }
.nav-item:hover { background: var(--bg-color); color: var(--text-strong); }
.nav-item.active { background: var(--primary-color); color: #fff; }
.nav-icon { font-size: 1.1rem; }
.admin-content { flex: 1; min-width: 0; }
.content-header { display: flex; justify-content: flex-end; margin-bottom: 20px; }
</style>
