<template>
  <div class="main-layout">
    <header>
      <div class="header-top">
        <h1>{{ $t('app.title') }}</h1>
        <div class="header-controls">
          <button class="theme-toggle" @click="cycleTheme" :title="$t('app.theme')">
            <svg v-if="theme === 'auto'" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16"><path d="M8 15A7 7 0 1 0 8 1v14zm0 1A8 8 0 1 1 8 0a8 8 0 0 1 0 16z"/></svg>
            <svg v-else-if="theme === 'light'" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16"><path d="M8 11a3 3 0 1 1 0-6 3 3 0 0 1 0 6zm0 1a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM8 0a.5.5 0 0 1 .5.5v2a.5.5 0 0 1-1 0v-2A.5.5 0 0 1 8 0zm0 13a.5.5 0 0 1 .5.5v2a.5.5 0 0 1-1 0v-2A.5.5 0 0 1 8 13zm8-5a.5.5 0 0 1-.5.5h-2a.5.5 0 0 1 0-1h2a.5.5 0 0 1 .5.5zM3 8a.5.5 0 0 1-.5.5h-2a.5.5 0 0 1 0-1h2A.5.5 0 0 1 3 8zm10.657-5.657a.5.5 0 0 1 0 .707l-1.414 1.415a.5.5 0 1 1-.707-.708l1.414-1.414a.5.5 0 0 1 .707 0zm-9.193 9.193a.5.5 0 0 1 0 .707L3.05 13.657a.5.5 0 0 1-.707-.707l1.414-1.414a.5.5 0 0 1 .707 0zm9.193 2.121a.5.5 0 0 1-.707 0l-1.414-1.414a.5.5 0 0 1 .707-.707l1.414 1.414a.5.5 0 0 1 0 .707zM4.464 4.465a.5.5 0 0 1-.707 0L2.343 3.05a.5.5 0 1 1 .707-.707l1.414 1.414a.5.5 0 0 1 0 .708z"/></svg>
            <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16"><path d="M6 .278a.768.768 0 0 1 .08.858 7.208 7.208 0 0 0-.878 3.46c0 4.021 3.278 7.277 7.318 7.277.527 0 1.04-.055 1.533-.16a.787.787 0 0 1 .81.316.733.733 0 0 1-.031.893A8.349 8.349 0 0 1 8.344 16C3.734 16 0 12.286 0 7.71 0 4.266 2.114 1.312 5.124.06A.752.752 0 0 1 6 .278z"/></svg>
          </button>
          <LanguageSwitcher />

          <!-- Auth button -->
          <router-link v-if="!auth.isLoggedIn" to="/login" class="auth-btn" title="Sign in">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M10 3.5a.5.5 0 0 0-.5-.5h-8a.5.5 0 0 0-.5.5v9a.5.5 0 0 0 .5.5h8a.5.5 0 0 0 .5-.5v-2a.5.5 0 0 1 1 0v2A1.5 1.5 0 0 1 9.5 14h-8A1.5 1.5 0 0 1 0 12.5v-9A1.5 1.5 0 0 1 1.5 2h8A1.5 1.5 0 0 1 11 3.5v2a.5.5 0 0 1-1 0v-2z"/><path fill-rule="evenodd" d="M4.146 8.354a.5.5 0 0 1 0-.708l3-3a.5.5 0 1 1 .708.708L5.707 7.5H14.5a.5.5 0 0 1 0 1H5.707l2.147 2.146a.5.5 0 0 1-.708.708l-3-3z"/></svg>
          </router-link>
          <router-link v-else to="/user/home" class="auth-btn logged-in" title="Account">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16"><path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm2-3a2 2 0 1 1-4 0 2 2 0 0 1 4 0zm4 8c0 1-1 1-1 1H3s-1 0-1-1 1-4 6-4 6 3 6 4zm-1-.004c-.001-.246-.154-.986-.832-1.664C11.516 10.68 10.289 10 8 10c-2.29 0-3.516.68-4.168 1.332-.678.678-.83 1.418-.832 1.664h10z"/></svg>
            <span class="auth-label">{{ auth.user?.username }}</span>
          </router-link>
        </div>
      </div>
      <nav class="tabs">
        <router-link v-if="auth.hasReadAccess('dashboard')" to="/dashboard" class="tab-link" active-class="active">{{ $t('app.tabs.summary') }}</router-link>
        <router-link v-if="auth.hasReadAccess('terminal')" to="/console" class="tab-link" active-class="active">{{ $t('app.tabs.terminal') }}</router-link>
        <router-link v-if="auth.hasReadAccess('logs')" to="/logs" class="tab-link" active-class="active">{{ $t('app.tabs.logs') }}</router-link>
      </nav>
    </header>
    <main>
      <router-view v-slot="{ Component }">
        <keep-alive>
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import LanguageSwitcher from '../components/LanguageSwitcher.vue';

const router = useRouter();
const auth = useAuthStore();

const theme = ref(localStorage.getItem('theme') || 'auto');

const applyTheme = (t) => {
  if (t === 'dark' || (t === 'auto' && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    document.documentElement.classList.add('dark');
  } else {
    document.documentElement.classList.remove('dark');
  }
};

watch(theme, (v) => { localStorage.setItem('theme', v); applyTheme(v); });
onMounted(() => {
  applyTheme(theme.value);
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (theme.value === 'auto') applyTheme('auto');
  });
});

const cycleTheme = () => {
  if (theme.value === 'auto') theme.value = 'light';
  else if (theme.value === 'light') theme.value = 'dark';
  else theme.value = 'auto';
};
</script>

<style scoped>
.main-layout { max-width: 1000px; margin: 0 auto; padding: 20px; }
header { margin-bottom: 20px; }
.header-top { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color); padding-bottom: 10px; margin-bottom: 15px; }
.header-top h1 { margin: 0; color: var(--text-strong); }
.header-controls { display: flex; align-items: center; gap: 10px; }
.tabs { display: flex; gap: 10px; }
.tab-link { padding: 10px 20px; border: none; background: var(--tab-bg); color: var(--tab-text); cursor: pointer; border-radius: 6px; font-size: 1rem; font-weight: 500; transition: all 0.2s; text-decoration: none; }
.tab-link:hover { background: var(--tab-hover); }
.tab-link.active { background: var(--tab-active); color: var(--tab-active-text); }
.auth-btn { display: flex; align-items: center; gap: 6px; padding: 6px 12px; border: 1px solid var(--border-color); border-radius: 6px; color: var(--text-secondary); text-decoration: none; transition: all 0.2s; }
.auth-btn:hover { border-color: var(--primary-color); color: var(--primary-color); }
.auth-btn.logged-in { background: var(--bg-color); }
.auth-label { font-size: 0.85rem; font-weight: 500; max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.theme-toggle { background: none; border: 1px solid var(--border-color); border-radius: 6px; padding: 4px 8px; cursor: pointer; color: var(--text-secondary); display: flex; align-items: center; }
.theme-toggle:hover { border-color: var(--border-hover); color: var(--text-strong); }
</style>
