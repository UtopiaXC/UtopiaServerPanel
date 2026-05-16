<template>
  <div class="login-page">
    <h2>{{ $t('auth.loginTitle') }}</h2>
    <form @submit.prevent="handleLogin">
      <div class="form-group">
        <label>{{ $t('auth.username') }}</label>
        <input v-model="username" type="text" autocomplete="username" required />
      </div>
      <div class="form-group">
        <label>{{ $t('auth.password') }}</label>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </div>
      <div v-if="error" class="error-msg">{{ error }}</div>
      <button type="submit" class="btn-primary" :disabled="loading">{{ loading ? '...' : $t('auth.loginBtn') }}</button>
    </form>
    <p class="register-link">
      {{ $t('auth.noAccount') }} <router-link to="/register">{{ $t('auth.registerLink') }}</router-link>
    </p>
    <p class="home-link">
      <router-link to="/dashboard">{{ $t('auth.backHome') }}</router-link>
    </p>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const username = ref('');
const password = ref('');
const loading = ref(false);
const error = ref('');

const handleLogin = async () => {
  error.value = '';
  loading.value = true;
  try {
    await auth.login(username.value, password.value);
    // Router guard will handle redirect based on mustChangePassword / bindingStatus
    router.push(route.query.redirect || '/dashboard');
  } catch (e) {
    error.value = e.response?.data?.message || 'Login failed';
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-page h2 { margin: 0 0 20px; color: var(--text-strong); text-align: center; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 0.9rem; color: var(--text-secondary); font-weight: 500; }
.form-group input { width: 100%; padding: 10px 12px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-color); color: var(--text-strong); font-size: 0.95rem; box-sizing: border-box; }
.form-group input:focus { outline: none; border-color: var(--primary-color); box-shadow: 0 0 0 3px rgba(59,130,246,0.15); }
.btn-primary { width: 100%; padding: 12px; background: var(--primary-color); color: #fff; border: none; border-radius: 8px; font-size: 1rem; font-weight: 600; cursor: pointer; transition: opacity 0.2s; }
.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.6; cursor: default; }
.error-msg { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 12px; }
.register-link { text-align: center; margin-top: 16px; font-size: 0.9rem; color: var(--text-secondary); }
.register-link a { color: var(--primary-color); text-decoration: none; }
.home-link { text-align: center; margin-top: 8px; font-size: 0.85rem; }
.home-link a { color: var(--text-secondary); text-decoration: none; }
.home-link a:hover { color: var(--text-strong); }
</style>
