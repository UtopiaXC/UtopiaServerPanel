<template>
  <n-select
    :value="currentLocale"
    :options="localeOptions"
    size="small"
    :style="{ width: compact ? '90px' : '112px' }"
    @update:value="onChange"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setAppLocale } from '../i18n'

withDefaults(
  defineProps<{
    compact?: boolean
  }>(),
  {
    compact: false
  }
)

const { locale } = useI18n()
const currentLocale = computed(() => String(locale.value))

const localeOptions = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
]

function onChange(val: string) {
  setAppLocale(val)
}
</script>

