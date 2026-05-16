<template>
  <div class="auth-layout">
    <div class="auth-card">
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.auth-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-color);
  padding: 20px;
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
