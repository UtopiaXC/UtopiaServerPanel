<template>
  <div class="forbidden-page">
    <div class="icon"><svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" fill="currentColor" viewBox="0 0 16 16"><path d="M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14zm0 1A8 8 0 1 0 8 0a8 8 0 0 0 0 16z"/><path d="M4.646 4.646a.5.5 0 0 1 .708 0L8 7.293l2.646-2.647a.5.5 0 0 1 .708.708L8.707 8l2.647 2.646a.5.5 0 0 1-.708.708L8 8.707l-2.646 2.647a.5.5 0 0 1-.708-.708L7.293 8 4.646 5.354a.5.5 0 0 1 0-.708z"/></svg></div>
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
