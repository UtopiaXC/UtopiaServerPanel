<template>
  <div class="pow-box">
    <div v-if="verified" class="pow-verified">
      <div class="pow-verified-left">
        <n-icon size="18" color="var(--usp-primary)">
          <CheckmarkCircleOutline />
        </n-icon>
        <span>{{ $t('auth.captchaVerified') }}</span>
      </div>
      <n-button size="tiny" quaternary @click="reset" :title="$t('common.refresh')">
        <template #icon>
          <n-icon><RefreshOutline /></n-icon>
        </template>
      </n-button>
    </div>

    <n-button
      v-else
      block
      secondary
      :type="solving ? 'info' : 'warning'"
      :loading="solving"
      @click="solve"
    >
      <template #icon>
        <n-icon><ShieldCheckmarkOutline /></n-icon>
      </template>
      {{ solving ? $t('auth.captchaSolving') : $t('auth.captchaSolve') }}
    </n-button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from 'naive-ui'
import { CheckmarkCircleOutline, RefreshOutline, ShieldCheckmarkOutline } from '@vicons/ionicons5'
import { authApi } from '../api/auth'
import { getApiErrorMessage } from '../api/client'
import { solvePowChallenge } from '../utils/pow'

const emit = defineEmits<{
  (e: 'update:modelValue', token: string): void
}>()

const message = useMessage()
const solving = ref(false)
const verified = ref(false)

async function solve(): Promise<string> {
  if (solving.value) return ''
  solving.value = true
  try {
    const challenge = await authApi.getCaptchaChallenge()
    const payload = await solvePowChallenge(challenge)
    verified.value = true
    emit('update:modelValue', payload)
    return payload
  } catch (err) {
    verified.value = false
    emit('update:modelValue', '')
    message.error(getApiErrorMessage(err))
    return ''
  } finally {
    solving.value = false
  }
}

function reset() {
  verified.value = false
  emit('update:modelValue', '')
}

defineExpose({ solve, reset })
</script>

<style scoped>
.pow-box {
  width: 100%;
}

.pow-verified {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 12px;
  border-radius: 8px;
  border: 1px solid var(--usp-primary);
  background: var(--usp-primary-soft);
  font-size: 13px;
  color: var(--usp-text);
}

.pow-verified-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}
</style>

