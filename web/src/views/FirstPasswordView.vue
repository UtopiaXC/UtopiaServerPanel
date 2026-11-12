<template>
  <div class="auth-viewport">
    <div class="auth-topbar">
      <ThemeSwitcher />
      <LanguageSelector compact />
    </div>

    <n-card class="auth-card" :title="$t('settings.firstPasswordTitle')" :bordered="false">
      <p style="margin: 0 0 16px; font-size: 13px; color: var(--usp-text-muted);">
        {{ $t('settings.firstPasswordPrompt') }}
      </p>

      <n-form @submit.prevent="handleSubmit">
        <n-form-item :label="$t('settings.currentPassword')">
          <n-input v-model:value="form.currentPassword" type="password" show-password-on="click" />
        </n-form-item>

        <n-form-item :label="$t('settings.newPassword')">
          <n-input v-model:value="form.newPassword" type="password" show-password-on="click" />
        </n-form-item>

        <n-form-item :label="$t('settings.confirmPassword')">
          <n-input
            v-model:value="form.confirmPassword"
            type="password"
            show-password-on="click"
            @keydown.enter.prevent="handleSubmit"
          />
        </n-form-item>

        <n-button type="primary" block size="large" :loading="loading" @click="handleSubmit">
          {{ $t('common.confirm') }}
        </n-button>
      </n-form>

      <div style="margin-top: 14px; text-align: center;">
        <n-button text size="small" @click="handleLogout">{{ $t('common.logout') }}</n-button>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import LanguageSelector from '../components/LanguageSelector.vue'
import { profileApi } from '../api/profile'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const message = useMessage()
const { t } = useI18n()
const authStore = useAuthStore()

const loading = ref(false)
const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

async function handleSubmit() {
  if (form.newPassword !== form.confirmPassword) {
    message.warning(t('setup.passwordMismatch'))
    return
  }
  loading.value = true
  try {
    await profileApi.changePassword(form.currentPassword, form.newPassword)
    await authStore.refreshMe()
    message.success(t('auth.passwordResetSuccess'))
    router.replace(authStore.needsBinding ? '/bind' : '/')
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

