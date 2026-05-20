<template>
  <div class="register-page">
    <h2>{{ $t('auth.registerTitle') }}</h2>
    <form @submit.prevent="handleRegister">
      <div class="form-group">
        <label>{{ $t('auth.username') }}</label>
        <input v-model="username" type="text" autocomplete="username" required minlength="3" maxlength="32" pattern="[a-zA-Z0-9_]+" />
      </div>
      <div class="form-group">
        <label>{{ $t('auth.password') }}</label>
        <input v-model="password" type="password" autocomplete="new-password" required minlength="4" />
      </div>
      <div class="form-group">
        <label>{{ $t('auth.confirmPassword') }}</label>
        <input v-model="confirmPassword" type="password" autocomplete="new-password" required />
      </div>
      <div class="form-group">
        <label>{{ $t('auth.bindingCode') }}</label>
        <input v-model="bindingCode" type="text" placeholder="" required maxlength="6" style="text-transform:uppercase;letter-spacing:4px;text-align:center;font-size:1.2rem;font-family:monospace" />
      </div>
      <div v-if="error" class="error-msg">{{ error }}</div>
      <div v-if="success" class="success-msg">{{ success }}</div>
      <button type="submit" class="btn-primary" :disabled="loading">{{ loading ? '...' : $t('auth.registerBtn') }}</button>
    </form>
    <p class="login-link">
      {{ $t('auth.haveAccount') }} <router-link to="/login">{{ $t('auth.loginLink') }}</router-link>
    </p>
    <p class="home-link">
      <a href="#" @click.prevent="$router.back()">{{ $t('auth.goBack') }}</a>
    </p>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { authAPI } from '../api/auth';

const username = ref('');
const password = ref('');
const confirmPassword = ref('');
const bindingCode = ref('');
const loading = ref(false);
const error = ref('');
const success = ref('');

const handleRegister = async () => {
  error.value = '';
  success.value = '';
  if (password.value !== confirmPassword.value) {
    error.value = 'Passwords do not match';
    return;
  }
  loading.value = true;
  try {
    await authAPI.register(username.value, password.value, bindingCode.value.toUpperCase());
    success.value = 'Registration successful! You can now login.';
  } catch (e) {
    error.value = e.response?.data?.message || 'Registration failed';
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.register-page h2 { margin: 0 0 20px; color: var(--text-strong); text-align: center; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 0.9rem; color: var(--text-secondary); font-weight: 500; }
.form-group input { width: 100%; padding: 10px 12px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-color); color: var(--text-strong); font-size: 0.95rem; box-sizing: border-box; }
.form-group input:focus { outline: none; border-color: var(--primary-color); box-shadow: 0 0 0 3px rgba(59,130,246,0.15); }
.btn-primary { width: 100%; padding: 12px; background: var(--primary-color); color: #fff; border: none; border-radius: 8px; font-size: 1rem; font-weight: 600; cursor: pointer; }
.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.6; cursor: default; }
.error-msg { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 12px; }
.success-msg { background: #f0fdf4; border: 1px solid #bbf7d0; color: #16a34a; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 12px; }
.login-link { text-align: center; margin-top: 16px; font-size: 0.9rem; color: var(--text-secondary); }
.login-link a { color: var(--primary-color); text-decoration: none; }
.home-link { text-align: center; margin-top: 8px; font-size: 0.85rem; }
.home-link a { color: var(--text-secondary); text-decoration: none; }
.home-link a:hover { color: var(--text-strong); }
</style>
