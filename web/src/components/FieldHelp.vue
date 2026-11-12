<template>
  <n-tooltip trigger="hover" placement="top" :style="{ maxWidth: '280px' }">
    <template #trigger>
      <n-icon
        :size="15"
        style="color: var(--usp-text-muted); cursor: help; vertical-align: middle; flex-shrink: 0;"
      >
        <HelpCircleOutline />
      </n-icon>
    </template>
    {{ text }}
  </n-tooltip>
</template>

<script setup lang="ts">
import { HelpCircleOutline } from '@vicons/ionicons5'

defineProps<{
  text: string
}>()
</script>

