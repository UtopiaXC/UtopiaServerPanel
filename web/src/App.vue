<template>
  <n-config-provider
    :theme="themeStore.naiveTheme"
    :theme-overrides="themeStore.themeOverrides"
    :locale="naiveLocale"
    :date-locale="naiveDateLocale"
  >
    <n-message-provider>
      <n-dialog-provider>
        <n-global-style />
        <router-view />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { zhCN, dateZhCN, enUS, dateEnUS } from 'naive-ui'
import { useThemeStore } from './stores/theme'

const { locale } = useI18n()
const themeStore = useThemeStore()

const naiveLocale = computed(() => (String(locale.value) === 'zh-CN' ? zhCN : enUS))
const naiveDateLocale = computed(() => (String(locale.value) === 'zh-CN' ? dateZhCN : dateEnUS))
</script>

