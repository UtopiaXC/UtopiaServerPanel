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

      <template v-if="siteStore.whitelistFeatureVisible && !authStore.user?.bound">
        <n-divider style="margin: 16px 0;" />
        <n-button block secondary type="info" @click="showWhitelistModal = true">
          {{ $t('whitelist.applyAndBindBtn') }}
        </n-button>
      </template>
    </n-card>

    <n-modal v-model:show="showWhitelistModal" style="max-width: 620px; width: 95vw;">
      <WhitelistApplyView embedded />
    </n-modal>
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
import WhitelistApplyView from './WhitelistApplyView.vue'
import { bindingApi } from '../api/binding'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useSiteStore } from '../stores/site'

const router = useRouter()
const message = useMessage()
const { t } = useI18n()
const authStore = useAuthStore()
const siteStore = useSiteStore()

const code = ref('')
const loading = ref(false)
const showWhitelistModal = ref(false)

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

