<template>
  <div class="custom-dropdown" @click="langDropdownOpen = !langDropdownOpen" v-click-outside="() => langDropdownOpen = false">
    <div class="custom-dropdown-btn">{{ $i18n.locale === 'en-US' ? 'English' : '简体中文' }}</div>
    <ul class="custom-dropdown-menu" v-if="langDropdownOpen">
      <li @click="setLang('en-US')" :class="{active: $i18n.locale === 'en-US'}">English</li>
      <li @click="setLang('zh-CN')" :class="{active: $i18n.locale === 'zh-CN'}">简体中文</li>
    </ul>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';

const { locale } = useI18n();
const langDropdownOpen = ref(false);

const setLang = (lang) => {
  locale.value = lang;
  langDropdownOpen.value = false;
};

const vClickOutside = {
  mounted(el, binding) {
    el.clickOutsideEvent = (event) => {
      if (!(el === event.target || el.contains(event.target))) binding.value(event, el);
    };
    document.addEventListener('click', el.clickOutsideEvent);
  },
  unmounted(el) {
    document.removeEventListener('click', el.clickOutsideEvent);
  }
};
</script>

<style scoped>
.custom-dropdown { position: relative; display: inline-block; user-select: none; }
.custom-dropdown-btn { background-color: var(--tooltip-bg, var(--card-bg, #fff)); color: var(--input-text, var(--text-strong, #333)); border: 1px solid var(--border-color, #ccc); border-radius: 6px; padding: 0 30px 0 12px; height: 32px; display: flex; align-items: center; font-size: 0.9rem; cursor: pointer; box-shadow: 0 2px 4px var(--shadow-color, rgba(0,0,0,0.1)); background-image: url("data:image/svg+xml;charset=US-ASCII,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22292.4%22%20height%3D%22292.4%22%3E%3Cpath%20fill%3D%22%2364748b%22%20d%3D%22M287%2069.4a17.6%2017.6%200%200%200-13-5.4H18.4c-5%200-9.3%201.8-12.9%205.4A17.6%2017.6%200%200%200%200%2082.2c0%205%201.8%209.3%205.4%2012.9l128%20127.9c3.6%203.6%207.8%205.4%2012.8%205.4s9.2-1.8%2012.8-5.4L287%2095c3.5-3.5%205.4-7.8%205.4-12.8%200-5-1.9-9.2-5.5-12.8z%22%2F%3E%3C%2Fsvg%3E"); background-repeat: no-repeat; background-position: right 10px center; background-size: 10px auto; }
.custom-dropdown-btn:hover { border-color: var(--border-hover, #aaa); }
.custom-dropdown-menu { position: absolute; top: calc(100% + 4px); right: 0; background: var(--tooltip-bg, var(--card-bg, #fff)); border: 1px solid var(--border-color, #ccc); border-radius: 6px; box-shadow: 0 4px 12px var(--tooltip-shadow, rgba(0,0,0,0.15)); list-style: none; padding: 4px 0; margin: 0; z-index: 1000; min-width: 100%; }
.custom-dropdown-menu li { padding: 8px 16px; font-size: 0.9rem; color: var(--text-strong, #333); cursor: pointer; white-space: nowrap; }
.custom-dropdown-menu li:hover { background: var(--bg-color, #f0f0f0); }
.custom-dropdown-menu li.active { color: var(--primary-color, #007bff); font-weight: bold; }
</style>
