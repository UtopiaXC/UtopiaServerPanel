<template>
  <router-view />
</template>

<script setup>
const theme = localStorage.getItem('theme') || 'auto';
const applyTheme = (t) => {
  if (t === 'dark' || (t === 'auto' && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    document.documentElement.classList.add('dark');
  } else { document.documentElement.classList.remove('dark'); }
};
applyTheme(theme);
</script>

<style>
:root { --bg-color:#f3f4f6;--card-bg:#fff;--text-primary:#333;--text-secondary:#64748b;--text-strong:#1e293b;--border-color:#e2e8f0;--border-hover:#cbd5e0;--primary-color:#3b82f6;--tab-bg:#e2e8f0;--tab-text:#4a5568;--tab-hover:#cbd5e0;--tab-active:#3182ce;--tab-active-text:#fff;--tooltip-bg:#fff;--tooltip-shadow:rgba(0,0,0,.12);--shadow-color:rgba(0,0,0,.06);--input-text:#1e293b; }
html.dark { --bg-color:#0f172a;--card-bg:#1e293b;--text-primary:#e2e8f0;--text-secondary:#94a3b8;--text-strong:#f1f5f9;--border-color:#334155;--border-hover:#475569;--primary-color:#3b82f6;--tab-bg:#1e293b;--tab-text:#94a3b8;--tab-hover:#334155;--tab-active:#3b82f6;--tab-active-text:#fff;--tooltip-bg:#1e293b;--tooltip-shadow:rgba(0,0,0,.4);--shadow-color:rgba(0,0,0,.3);--input-text:#f1f5f9; }
body { font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;background-color:var(--bg-color);color:var(--text-primary);margin:0;padding:0;transition:background-color .3s,color .3s; }
* { box-sizing:border-box; }
</style>
