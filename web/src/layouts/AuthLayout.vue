<template>
  <div class="auth-layout">
    <div class="lang-switch-container">
      <LanguageSwitcher />
    </div>
    <div class="auth-card">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import LanguageSwitcher from '../components/LanguageSwitcher.vue';
</script>

<style scoped>
.auth-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-color);
  padding: 20px;
  position: relative;
}
.lang-switch-container {
  position: absolute;
  top: 20px;
  right: 20px;
}
.auth-card {
  width: 100%;
  max-width: 420px;
  background: var(--card-bg);
  border-radius: 12px;
  box-shadow: 0 4px 24px var(--shadow-color, rgba(0,0,0,0.1));
  padding: 32px;
}
</style>
