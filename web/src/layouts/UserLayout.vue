<template>
  <div class="user-layout">
    <div class="user-card">
      <router-link to="/dashboard" class="back-link">&larr; {{ $t('admin.backToPanel') }}</router-link>
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.user-layout { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--bg-color); padding: 20px; }
.user-card { width: 100%; max-width: 560px; background: var(--card-bg); border-radius: 12px; box-shadow: 0 4px 24px var(--shadow-color, rgba(0,0,0,0.1)); padding: 32px; position: relative; }
.back-link { color: var(--primary-color); text-decoration: none; font-size: 0.9rem; font-weight: 500; display: inline-block; margin-bottom: 20px; }
.back-link:hover { text-decoration: underline; }
</style>
