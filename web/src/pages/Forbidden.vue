<template>
  <div class="forbidden-page">
    <div class="icon">🚫</div>
    <h2>{{ $t('errors.forbidden') }}</h2>
    <p>{{ $t('errors.noPermission') }}</p>
    <router-link to="/dashboard" class="back-btn">{{ $t('errors.backHome') }}</router-link>
  </div>
</template>

<style scoped>
.forbidden-page { text-align: center; padding: 60px 20px; }
.icon { font-size: 4rem; margin-bottom: 16px; }
h2 { color: var(--text-strong); margin-bottom: 8px; }
p { color: var(--text-secondary); margin-bottom: 24px; }
.back-btn { display: inline-block; padding: 10px 24px; background: var(--primary-color); color: #fff; border-radius: 8px; text-decoration: none; }
.back-btn:hover { opacity: 0.9; }
</style>
