<template>
  <n-dropdown :options="options" trigger="click" @select="handleSelect">
    <n-button size="small" quaternary :title="$t('theme.tooltip')">
      <template #icon>
        <n-icon>
          <SunnyOutline v-if="themeStore.mode === 'light'" />
          <MoonOutline v-else-if="themeStore.mode === 'dark'" />
          <ContrastOutline v-else />
        </n-icon>
      </template>
      <span v-if="showLabel">{{ modeLabel }}</span>
    </n-button>
  </n-dropdown>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { useI18n } from 'vue-i18n'
import { NIcon } from 'naive-ui'
import { SunnyOutline, MoonOutline, ContrastOutline } from '@vicons/ionicons5'
import { useThemeStore, type ThemeMode } from '../stores/theme'

withDefaults(
  defineProps<{
    showLabel?: boolean
  }>(),
  {
    showLabel: false
  }
)

const { t } = useI18n()
const themeStore = useThemeStore()

const modeLabel = computed(() => t(`theme.${themeStore.mode}`))

const renderIcon = (icon: any) => () => h(NIcon, null, { default: () => h(icon) })

const options = computed(() => [
  { key: 'auto', label: t('theme.auto'), icon: renderIcon(ContrastOutline) },
  { key: 'light', label: t('theme.light'), icon: renderIcon(SunnyOutline) },
  { key: 'dark', label: t('theme.dark'), icon: renderIcon(MoonOutline) }
])

function handleSelect(key: string) {
  themeStore.setMode(key as ThemeMode)
}
</script>

