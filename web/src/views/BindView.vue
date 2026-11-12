<template>
  <div class="auth-viewport">
    <div class="auth-topbar">
      <ThemeSwitcher />
      <LanguageSelector compact />
    </div>

    <n-card class="auth-card" :title="$t('settings.mandatoryBindTitle')" :bordered="false">
      <n-alert type="info" :bordered="false" style="margin-bottom: 16px; font-size: 13px;">
        {{ $t('settings.mandatoryBindPrompt') }}
      </n-alert>

      <n-form @submit.prevent="handleBind">
        <n-form-item>
          <template #label>
            <span style="display: inline-flex; align-items: center; gap: 6px;">
              {{ $t('settings.bindingCode') }}
              <FieldHelp :text="$t('settings.bindingCodeHelp')" />
            </span>
          </template>
          <n-input
            v-model:value="code"
            size="large"
            maxlength="12"
            clearable
            @keydown.enter.prevent="handleBind"
          />
        </n-form-item>

        <n-button type="primary" block size="large" :loading="loading" @click="handleBind">
          {{ $t('settings.bindBtn') }}
        </n-button>
      </n-form>

      <div style="margin-top: 14px; display: flex; justify-content: space-between;">
        <n-button v-if="!authStore.needsBinding" text size="small" @click="router.push('/home')">
          {{ $t('common.back') }}
        </n-button>
        <n-button text size="small" @click="handleLogout">{{ $t('common.logout') }}</n-button>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import LanguageSelector from '../components/LanguageSelector.vue'
import FieldHelp from '../components/FieldHelp.vue'
import { bindingApi } from '../api/binding'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const message = useMessage()
const { t } = useI18n()
const authStore = useAuthStore()

const code = ref('')
const loading = ref(false)

async function handleBind() {
  if (!code.value.trim()) return
  loading.value = true
  try {
    await bindingApi.bind(code.value.trim())
    await authStore.refreshMe()
    message.success(t('common.saved'))
    router.replace('/home')
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleLogout() {
  await authStore.logout()
  router.replace('/login')
}
</script>

