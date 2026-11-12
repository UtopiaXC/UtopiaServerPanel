<template>
  <div class="auth-viewport">
    <div class="auth-topbar">
      <ThemeSwitcher />
      <LanguageSelector compact />
    </div>

    <n-card class="setup-card" :bordered="false">
      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 18px;">
        <n-icon size="28" color="var(--usp-primary)">
          <ConstructOutline />
        </n-icon>
        <div>
          <div style="font-size: 20px; font-weight: 700;">{{ $t('setup.title') }}</div>
          <div style="font-size: 13px; color: var(--usp-text-muted);">{{ $t('setup.subtitle') }}</div>
        </div>
      </div>

      <!-- Phase 1: Verify Setup Token -->
      <div v-if="!tokenVerified">
        <n-alert type="info" :bordered="false" style="margin-bottom: 16px;">
          <template #header>{{ $t('setup.tokenHowTitle') }}</template>
          <ul style="margin: 6px 0 0; padding-left: 18px; line-height: 1.7; font-size: 13px;">
            <li><code class="mono">{{ $t('setup.tokenHowConfig') }}</code></li>
            <li><code class="mono">{{ $t('setup.tokenHowCommand') }}</code></li>
          </ul>
        </n-alert>

        <n-form @submit.prevent="handleVerifyToken">
          <n-form-item :label="$t('setup.tokenLabel')">
            <n-input
              v-model:value="setupToken"
              type="password"
              show-password-on="click"
              size="large"
              :placeholder="$t('setup.tokenPlaceholder')"
              @keydown.enter.prevent="handleVerifyToken"
            />
          </n-form-item>

          <n-button
            type="primary"
            block
            size="large"
            :loading="verifying"
            @click="handleVerifyToken"
          >
            {{ $t('setup.verifyToken') }}
          </n-button>
        </n-form>
      </div>

      <!-- Phase 2: 3-Step Configuration Wizard -->
      <div v-else>
        <n-steps :current="currentStep" size="small" style="margin-bottom: 24px;">
          <n-step :title="$t('setup.stepSite')" />
          <n-step :title="$t('setup.stepSmtp')" />
          <n-step :title="$t('setup.stepAdmin')" />
        </n-steps>

        <!-- Step 1: Site Info -->
        <n-form v-if="currentStep === 1" label-placement="top">
          <n-grid :cols="isMobile ? 1 : 2" :x-gap="16">
            <n-form-item-gi :label="$t('setup.siteName')">
              <n-input v-model:value="form.siteName" />
            </n-form-item-gi>
            <n-form-item-gi :label="$t('setup.defaultLocale')">
              <n-select v-model:value="form.defaultLocale" :options="localeOptions" />
            </n-form-item-gi>
          </n-grid>

          <n-form-item :label="$t('setup.siteDesc')">
            <n-input v-model:value="form.siteDescription" />
          </n-form-item>

          <n-form-item>
            <template #label>
              <span style="display: inline-flex; align-items: center; gap: 6px;">
                {{ $t('setup.baseUrl') }}
                <FieldHelp :text="$t('setup.baseUrlHelp')" />
              </span>
            </template>
            <n-input v-model:value="form.baseUrl" placeholder="https://panel.example.com" />
          </n-form-item>

          <div style="display: flex; justify-content: flex-end; margin-top: 8px;">
            <n-button type="primary" @click="currentStep = 2">
              {{ $t('common.next') }}
            </n-button>
          </div>
        </n-form>

        <!-- Step 2: Optional SMTP -->
        <n-form v-else-if="currentStep === 2" label-placement="top">
          <n-form-item>
            <template #label>
              <span style="display: inline-flex; align-items: center; gap: 6px;">
                {{ $t('setup.enableSmtp') }}
                <FieldHelp :text="$t('setup.smtpHelp')" />
              </span>
            </template>
            <n-switch v-model:value="form.smtpEnabled" />
          </n-form-item>

          <template v-if="form.smtpEnabled">
            <n-grid :cols="isMobile ? 1 : 3" :x-gap="14">
              <n-form-item-gi :span="isMobile ? 1 : 2" :label="$t('setup.smtpHost')">
                <n-input v-model:value="form.smtpHost" placeholder="smtp.example.com" />
              </n-form-item-gi>
              <n-form-item-gi :label="$t('setup.smtpPort')">
                <n-input-number v-model:value="form.smtpPort" :min="1" :max="65535" style="width: 100%;" />
              </n-form-item-gi>
            </n-grid>

            <n-grid :cols="isMobile ? 1 : 2" :x-gap="14">
              <n-form-item-gi :label="$t('setup.smtpEncryption')">
                <n-select v-model:value="form.smtpEncryption" :options="encryptionOptions" />
              </n-form-item-gi>
              <n-form-item-gi :label="$t('setup.smtpUser')">
                <n-input v-model:value="form.smtpUser" />
              </n-form-item-gi>
            </n-grid>

            <n-grid :cols="isMobile ? 1 : 2" :x-gap="14">
              <n-form-item-gi :label="$t('setup.smtpPassword')">
                <n-input
                  v-model:value="form.smtpPassword"
                  type="password"
                  show-password-on="click"
                />
              </n-form-item-gi>
              <n-form-item-gi :label="$t('setup.smtpFrom')">
                <n-input v-model:value="form.smtpFrom" placeholder="noreply@example.com" />
              </n-form-item-gi>
            </n-grid>

            <n-form-item :label="$t('setup.smtpFromName')">
              <n-input v-model:value="form.smtpFromName" />
            </n-form-item>

            <div style="margin-bottom: 16px;">
              <n-button secondary :loading="testingSmtp" @click="handleTestSmtp">
                {{ $t('setup.testSmtp') }}
              </n-button>
            </div>
          </template>

          <div style="display: flex; justify-content: space-between; margin-top: 8px;">
            <n-button @click="currentStep = 1">{{ $t('common.prev') }}</n-button>
            <n-button type="primary" @click="currentStep = 3">{{ $t('common.next') }}</n-button>
          </div>
        </n-form>

        <!-- Step 3: Admin Account -->
        <n-form v-else label-placement="top">
          <n-grid :cols="isMobile ? 1 : 2" :x-gap="14">
            <n-form-item-gi :label="$t('setup.adminUsername')">
              <n-input v-model:value="form.adminUsername" />
            </n-form-item-gi>
            <n-form-item-gi :label="$t('setup.adminNickname')">
              <n-input v-model:value="form.adminNickname" />
            </n-form-item-gi>
          </n-grid>

          <n-form-item :label="$t('setup.adminEmail')">
            <n-input v-model:value="form.adminEmail" placeholder="admin@example.com" />
          </n-form-item>

          <n-grid :cols="isMobile ? 1 : 2" :x-gap="14">
            <n-form-item-gi :label="$t('setup.adminPassword')">
              <n-input
                v-model:value="form.adminPassword"
                type="password"
                show-password-on="click"
              />
            </n-form-item-gi>
            <n-form-item-gi :label="$t('setup.confirmPassword')">
              <n-input
                v-model:value="form.confirmPassword"
                type="password"
                show-password-on="click"
                @keydown.enter.prevent="handleComplete"
              />
            </n-form-item-gi>
          </n-grid>

          <div style="display: flex; justify-content: space-between; margin-top: 8px;">
            <n-button @click="currentStep = 2">{{ $t('common.prev') }}</n-button>
            <n-button type="primary" :loading="submitting" @click="handleComplete">
              {{ $t('common.finish') }}
            </n-button>
          </div>
        </n-form>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { ConstructOutline } from '@vicons/ionicons5'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import LanguageSelector from '../components/LanguageSelector.vue'
import FieldHelp from '../components/FieldHelp.vue'
import { setupApi } from '../api/setup'
import { getApiErrorMessage } from '../api/client'
import { useSiteStore } from '../stores/site'

const router = useRouter()
const message = useMessage()
const { t } = useI18n()
const siteStore = useSiteStore()

const isMobile = ref(false)
function updateMobile() {
  isMobile.value = window.innerWidth <= 768
}

const setupToken = ref('')
const tokenVerified = ref(false)
const verifying = ref(false)
const testingSmtp = ref(false)
const submitting = ref(false)
const currentStep = ref(1)

const form = reactive({
  siteName: 'Utopia Server Panel',
  siteDescription: 'Minecraft Server Management Panel',
  baseUrl: typeof window !== 'undefined' ? window.location.origin : '',
  defaultLocale: 'zh-CN',
  smtpEnabled: false,
  smtpHost: '',
  smtpPort: 465,
  smtpEncryption: 'ssl',
  smtpUser: '',
  smtpPassword: '',
  smtpFrom: '',
  smtpFromName: 'Utopia Server Panel',
  adminUsername: 'admin',
  adminNickname: 'Administrator',
  adminEmail: '',
  adminPassword: '',
  confirmPassword: ''
})

const localeOptions = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
]

const encryptionOptions = [
  { label: 'SSL / TLS', value: 'ssl' },
  { label: 'STARTTLS', value: 'starttls' },
  { label: 'None', value: 'none' }
]

async function handleVerifyToken() {
  if (!setupToken.value.trim()) return
  verifying.value = true
  try {
    await setupApi.verifyToken(setupToken.value.trim())
    tokenVerified.value = true
    message.success(t('setup.tokenVerified'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    verifying.value = false
  }
}

async function handleTestSmtp() {
  testingSmtp.value = true
  try {
    await setupApi.testSmtp(setupToken.value.trim(), {
      smtpHost: form.smtpHost,
      smtpPort: form.smtpPort,
      smtpEncryption: form.smtpEncryption,
      smtpUser: form.smtpUser,
      smtpPassword: form.smtpPassword,
      smtpFrom: form.smtpFrom,
      smtpFromName: form.smtpFromName
    })
    message.success(t('setup.testSmtpSuccess'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    testingSmtp.value = false
  }
}

async function handleComplete() {
  if (form.adminPassword !== form.confirmPassword) {
    message.warning(t('setup.passwordMismatch'))
    return
  }
  submitting.value = true
  try {
    await setupApi.complete({
      token: setupToken.value.trim(),
      siteName: form.siteName,
      siteDescription: form.siteDescription,
      baseUrl: form.baseUrl,
      defaultLocale: form.defaultLocale,
      smtpEnabled: form.smtpEnabled,
      smtpHost: form.smtpHost,
      smtpPort: form.smtpPort,
      smtpEncryption: form.smtpEncryption,
      smtpUser: form.smtpUser,
      smtpPassword: form.smtpPassword,
      smtpFrom: form.smtpFrom,
      smtpFromName: form.smtpFromName || form.siteName,
      adminUsername: form.adminUsername,
      adminNickname: form.adminNickname,
      adminEmail: form.adminEmail,
      adminPassword: form.adminPassword
    })
    await siteStore.refresh()
    message.success(t('setup.completeSuccess'))
    router.replace('/login')
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
  try {
    const st = await setupApi.getStatus()
    if (!st.setupRequired) {
      await siteStore.refresh()
      router.replace('/')
    }
  } catch {
    // ignore
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobile)
})
</script>

