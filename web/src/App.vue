<template>
  <div class="app-container">
    <header>
      <div class="header-top">
        <h1>{{ $t('app.title') }}</h1>
        <div class="header-controls">
          <button class="theme-toggle" @click="cycleTheme" :title="$t('app.theme')">
            <svg v-if="theme === 'auto'" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16"><path d="M8 15A7 7 0 1 0 8 1v14zm0 1A8 8 0 1 1 8 0a8 8 0 0 1 0 16z"/></svg>
            <svg v-else-if="theme === 'light'" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16"><path d="M8 11a3 3 0 1 1 0-6 3 3 0 0 1 0 6zm0 1a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM8 0a.5.5 0 0 1 .5.5v2a.5.5 0 0 1-1 0v-2A.5.5 0 0 1 8 0zm0 13a.5.5 0 0 1 .5.5v2a.5.5 0 0 1-1 0v-2A.5.5 0 0 1 8 13zm8-5a.5.5 0 0 1-.5.5h-2a.5.5 0 0 1 0-1h2a.5.5 0 0 1 .5.5zM3 8a.5.5 0 0 1-.5.5h-2a.5.5 0 0 1 0-1h2A.5.5 0 0 1 3 8zm10.657-5.657a.5.5 0 0 1 0 .707l-1.414 1.415a.5.5 0 1 1-.707-.708l1.414-1.414a.5.5 0 0 1 .707 0zm-9.193 9.193a.5.5 0 0 1 0 .707L3.05 13.657a.5.5 0 0 1-.707-.707l1.414-1.414a.5.5 0 0 1 .707 0zm9.193 2.121a.5.5 0 0 1-.707 0l-1.414-1.414a.5.5 0 0 1 .707-.707l1.414 1.414a.5.5 0 0 1 0 .707zM4.464 4.465a.5.5 0 0 1-.707 0L2.343 3.05a.5.5 0 1 1 .707-.707l1.414 1.414a.5.5 0 0 1 0 .708z"/></svg>
            <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16"><path d="M6 .278a.768.768 0 0 1 .08.858 7.208 7.208 0 0 0-.878 3.46c0 4.021 3.278 7.277 7.318 7.277.527 0 1.04-.055 1.533-.16a.787.787 0 0 1 .81.316.733.733 0 0 1-.031.893A8.349 8.349 0 0 1 8.344 16C3.734 16 0 12.286 0 7.71 0 4.266 2.114 1.312 5.124.06A.752.752 0 0 1 6 .278z"/></svg>
          </button>

          <div class="custom-dropdown" @click="langDropdownOpen = !langDropdownOpen" v-click-outside="() => langDropdownOpen = false">
            <div class="custom-dropdown-btn">
              {{ $i18n.locale === 'en-US' ? 'English' : '简体中文' }}
            </div>
            <ul class="custom-dropdown-menu" v-if="langDropdownOpen">
              <li @click="setLang('en-US')" :class="{active: $i18n.locale === 'en-US'}">English</li>
              <li @click="setLang('zh-CN')" :class="{active: $i18n.locale === 'zh-CN'}">简体中文</li>
            </ul>
          </div>
        </div>
      </div>
      <nav class="tabs">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="{ active: currentTab === tab.id }"
          @click="currentTab = tab.id"
        >
          {{ $t(`app.tabs.${tab.id}`) }}
        </button>
      </nav>
    </header>
    <main>
      <KeepAlive>
        <component :is="currentComponent"></component>
      </KeepAlive>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import Dashboard from './components/Dashboard.vue';
import Terminal from './components/Terminal.vue';
import Logs from './components/Logs.vue';
import { useI18n } from 'vue-i18n';

const { locale } = useI18n();
const currentTab = ref('summary');

const theme = ref(localStorage.getItem('theme') || 'auto');
const langDropdownOpen = ref(false);

const setLang = (lang) => {
  locale.value = lang;
  langDropdownOpen.value = false;
};

// custom directive to close dropdown when clicking outside
const vClickOutside = {
  mounted(el, binding) {
    el.clickOutsideEvent = function(event) {
      if (!(el === event.target || el.contains(event.target))) {
        binding.value(event, el);
      }
    };
    document.addEventListener('click', el.clickOutsideEvent);
  },
  unmounted(el) {
    document.removeEventListener('click', el.clickOutsideEvent);
  }
};

const applyTheme = (t) => {
  if (t === 'dark' || (t === 'auto' && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    document.documentElement.classList.add('dark');
  } else {
    document.documentElement.classList.remove('dark');
  }
};

watch(theme, (newTheme) => {
  localStorage.setItem('theme', newTheme);
  applyTheme(newTheme);
});

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

const tabs = [
  { id: 'summary', component: Dashboard },
  { id: 'terminal', component: Terminal },
  { id: 'logs', component: Logs }
];

const currentComponent = computed(() => {
  return tabs.find(t => t.id === currentTab.value)?.component;
});
</script>

<style>
.app-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

header {
  margin-bottom: 20px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 10px;
  margin-bottom: 15px;
}

.header-top h1 {
  margin: 0;
  color: var(--text-strong);
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tabs {
  display: flex;
  gap: 10px;
}

.tabs button {
  padding: 10px 20px;
  border: none;
  background: var(--tab-bg);
  color: var(--tab-text);
  cursor: pointer;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 500;
  transition: all 0.2s;
}

.tabs button:hover {
  background: var(--tab-hover);
}

.tabs button.active {
  background: var(--tab-active);
  color: var(--tab-active-text);
}

/* Dropdown override */
.custom-dropdown {
  position: relative;
  display: inline-block;
  user-select: none;
}
.custom-dropdown-btn {
  background-color: var(--tooltip-bg);
  color: var(--input-text);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 0 30px 0 12px;
  height: 32px;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  font-size: 0.9rem;
  cursor: pointer;
  box-shadow: 0 2px 4px var(--shadow-color);
  transition: all 0.2s;
  background-image: url("data:image/svg+xml;charset=US-ASCII,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22292.4%22%20height%3D%22292.4%22%3E%3Cpath%20fill%3D%22%2364748b%22%20d%3D%22M287%2069.4a17.6%2017.6%200%200%200-13-5.4H18.4c-5%200-9.3%201.8-12.9%205.4A17.6%2017.6%200%200%200%200%2082.2c0%205%201.8%209.3%205.4%2012.9l128%20127.9c3.6%203.6%207.8%205.4%2012.8%205.4s9.2-1.8%2012.8-5.4L287%2095c3.5-3.5%205.4-7.8%205.4-12.8%200-5-1.9-9.2-5.5-12.8z%22%2F%3E%3C%2Fsvg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  background-size: 10px auto;
}
.custom-dropdown-btn:hover {
  border-color: var(--border-hover);
}
.custom-dropdown-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  background: var(--tooltip-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  box-shadow: 0 4px 12px var(--tooltip-shadow);
  list-style: none;
  padding: 4px 0;
  margin: 0;
  z-index: 1000;
  min-width: 100%;
}
.custom-dropdown-menu li {
  padding: 8px 16px;
  font-size: 0.9rem;
  color: var(--text-strong);
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s;
}
.custom-dropdown-menu li:hover {
  background: var(--bg-color);
}
.custom-dropdown-menu li.active {
  color: var(--primary-color);
  font-weight: bold;
}
</style>
