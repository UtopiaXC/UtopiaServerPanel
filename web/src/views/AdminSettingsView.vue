<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">{{ $t('adminSettings.title') }}</h2>
      <n-button v-if="canWrite" type="primary" :loading="saving" @click="handleSave">
        {{ $t('adminSettings.saveBtn') }}
      </n-button>
    </div>

    <n-spin :show="loading">
      <n-card size="small" style="border-radius: 12px;">
        <n-tabs type="line" animated>
          <!-- Tab 1: Site -->
          <n-tab-pane name="site" :tab="$t('adminSettings.tabSystem')">
            <n-form
              label-placement="left"
              :label-width="isMobile ? 'auto' : 170"
              :disabled="!canWrite"
              style="max-width: 680px; margin-top: 8px;"
            >
              <n-form-item :label="$t('adminSettings.siteName')">
                <n-input v-model:value="form.siteName" />
              </n-form-item>
              <n-form-item :label="$t('adminSettings.siteDesc')">
                <n-input v-model:value="form.siteDescription" />
              </n-form-item>
              <n-form-item>
                <template #label>
                  <span class="lbl-help">
                    {{ $t('adminSettings.baseUrl') }}
                    <FieldHelp :text="$t('adminSettings.baseUrlHelp')" />
                  </span>
                </template>
                <n-input v-model:value="form.baseUrl" placeholder="https://panel.example.com" />
              </n-form-item>
              <n-form-item :label="$t('adminSettings.defaultLocale')">
                <n-select v-model:value="form.defaultLocale" :options="localeOptions" />
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <!-- Tab 2: Registration & Binding -->
          <n-tab-pane name="registration" :tab="$t('adminSettings.tabRegistration')">
            <n-form
              label-placement="left"
              :label-width="isMobile ? 'auto' : 190"
              :disabled="!canWrite"
              style="max-width: 680px; margin-top: 8px;"
            >
              <n-form-item :label="$t('adminSettings.allowRegistration')">
                <n-switch v-model:value="form.allowRegistration" />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.defaultRegisterRole')">
                <n-select v-model:value="form.defaultRegisterRoleId" :options="registerRoleOptions" />
              </n-form-item>

              <n-form-item>
                <template #label>
                  <span class="lbl-help">
                    {{ $t('adminSettings.requireEmailVerification') }}
                    <FieldHelp :text="$t('adminSettings.requireEmailVerificationHelp')" />
                  </span>
                </template>
                <n-switch v-model:value="form.requireEmailVerification" />
              </n-form-item>

              <n-form-item>
                <template #label>
                  <span class="lbl-help">
                    {{ $t('adminSettings.requireBindingOnRegister') }}
                    <FieldHelp :text="$t('adminSettings.requireBindingOnRegisterHelp')" />
                  </span>
                </template>
                <n-switch v-model:value="form.requireBindingOnRegister" />
              </n-form-item>

              <n-form-item>
                <template #label>
                  <span class="lbl-help">
                    {{ $t('adminSettings.forcePlayerBinding') }}
                    <FieldHelp :text="$t('adminSettings.forcePlayerBindingHelp')" />
                  </span>
                </template>
                <n-switch v-model:value="form.forcePlayerBinding" />
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <!-- Tab 3: Security -->
          <n-tab-pane name="security" :tab="$t('adminSettings.tabSecurity')">
            <n-form
              label-placement="left"
              :label-width="isMobile ? 'auto' : 190"
              :disabled="!canWrite"
              style="max-width: 680px; margin-top: 8px;"
            >
              <n-form-item :label="$t('adminSettings.captchaPolicy')">
                <n-select v-model:value="form.captchaPolicy" :options="captchaOptions" />
              </n-form-item>

              <n-form-item>
                <template #label>
                  <span class="lbl-help">
                    {{ $t('adminSettings.captchaDifficulty') }}
                    <FieldHelp :text="$t('adminSettings.captchaDifficultyHelp')" />
                  </span>
                </template>
                <n-input-number
                  v-model:value="form.captchaDifficulty"
                  :min="10000"
                  :max="1000000"
                  :step="10000"
                  style="width: 100%;"
                />
              </n-form-item>

              <n-form-item>
                <template #label>
                  <span class="lbl-help">
                    {{ $t('adminSettings.sessionTimeoutDays') }}
                    <FieldHelp :text="$t('adminSettings.sessionTimeoutDaysHelp')" />
                  </span>
                </template>
                <n-input-number
                  v-model:value="form.sessionTimeoutDays"
                  :min="1"
                  :max="365"
                  style="width: 100%;"
                />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.force2fa')">
                <n-switch v-model:value="form.force2fa" />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.maxFailedLogins')">
                <n-input-number
                  v-model:value="form.maxFailedLogins"
                  :min="3"
                  :max="50"
                  style="width: 100%;"
                />
              </n-form-item>

              <n-form-item>
                <template #label>
                  <span class="lbl-help">
                    {{ $t('adminSettings.lockoutMinutes') }}
                    <FieldHelp :text="$t('adminSettings.lockoutMinutesHelp')" />
                  </span>
                </template>
                <n-input-number
                  v-model:value="form.lockoutMinutes"
                  :min="1"
                  :max="1440"
                  style="width: 100%;"
                />
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <!-- Tab 4: SMTP Mail -->
          <n-tab-pane name="smtp" :tab="$t('adminSettings.tabSmtp')">
            <n-form
              label-placement="left"
              :label-width="isMobile ? 'auto' : 170"
              :disabled="!canWrite"
              style="max-width: 680px; margin-top: 8px;"
            >
              <n-form-item :label="$t('adminSettings.smtpEnabled')">
                <n-switch v-model:value="form.smtpEnabled" />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.smtpHost')">
                <n-input v-model:value="form.smtpHost" placeholder="smtp.example.com" />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.smtpPort')">
                <n-input-number v-model:value="form.smtpPort" :min="1" :max="65535" style="width: 100%;" />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.smtpEncryption')">
                <n-select v-model:value="form.smtpEncryption" :options="encryptionOptions" />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.smtpUser')">
                <n-input v-model:value="form.smtpUser" />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.smtpPassword')">
                <div style="width: 100%; display: flex; flex-direction: column; gap: 6px;">
                  <n-input
                    v-model:value="smtpPasswordInput"
                    type="password"
                    show-password-on="click"
                    :placeholder="
                      hasSmtpPassword ? $t('adminSettings.smtpPasswordPlaceholder') : ''
                    "
                  />
                  <n-checkbox v-if="hasSmtpPassword" v-model:checked="clearSmtpPassword">
                    {{ $t('adminSettings.clearSmtpPassword') }}
                  </n-checkbox>
                </div>
              </n-form-item>

              <n-form-item :label="$t('adminSettings.smtpFrom')">
                <n-input v-model:value="form.smtpFrom" />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.smtpFromName')">
                <n-input v-model:value="form.smtpFromName" />
              </n-form-item>

              <div v-if="canWrite" style="display: flex; justify-content: flex-end;">
                <n-button secondary :loading="testingSmtp" @click="handleTestSmtp">
                  {{ $t('adminSettings.testSmtp') }}
                </n-button>
              </div>
            </n-form>
          </n-tab-pane>

          <!-- Tab 5: Monitoring -->
          <n-tab-pane name="monitor" :tab="$t('adminSettings.tabMonitor')">
            <n-form
              label-placement="left"
              :label-width="isMobile ? 'auto' : 190"
              :disabled="!canWrite"
              style="max-width: 680px; margin-top: 8px;"
            >
              <n-form-item :label="$t('adminSettings.monitorEnabled')">
                <n-switch v-model:value="form.monitorEnabled" />
              </n-form-item>

              <n-form-item>
                <template #label>
                  <span class="lbl-help">
                    {{ $t('adminSettings.monitorInterval') }}
                    <FieldHelp :text="$t('adminSettings.monitorIntervalHelp')" />
                  </span>
                </template>
                <n-input-number v-model:value="form.monitorInterval" :min="10" :max="3600" style="width: 100%;" />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.monitorRetentionDays')">
                <n-input-number
                  v-model:value="form.monitorRetentionDays"
                  :min="1"
                  :max="3650"
                  style="width: 100%;"
                />
              </n-form-item>

              <n-form-item :label="$t('adminSettings.monitorShowPlayerNames')">
                <n-switch v-model:value="form.monitorShowPlayerNames" />
              </n-form-item>
            </n-form>
          </n-tab-pane>
        </n-tabs>
      </n-card>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import { adminApi, type AdminSettingsData, type RoleItem } from '../api/admin'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useSiteStore } from '../stores/site'

const message = useMessage()
const { t } = useI18n()
const authStore = useAuthStore()
const siteStore = useSiteStore()

const canWrite = computed(() => authStore.hasPermission('admin', 2))

const isMobile = ref(false)
function updateMobile() {
  isMobile.value = window.innerWidth <= 768
}

const loading = ref(false)
const saving = ref(false)
const testingSmtp = ref(false)

const roles = ref<RoleItem[]>([])
const hasSmtpPassword = ref(false)
const smtpPasswordInput = ref('')
const clearSmtpPassword = ref(false)

const form = reactive<Omit<AdminSettingsData, 'hasSmtpPassword'>>({
  siteName: 'Utopia Server Panel',
  siteDescription: '',
  baseUrl: '',
  defaultLocale: 'zh-CN',
  allowRegistration: true,
  defaultRegisterRoleId: 2,
  requireEmailVerification: false,
  requireBindingOnRegister: false,
  forcePlayerBinding: false,
  captchaPolicy: 'auto',
  captchaDifficulty: 80000,
  sessionTimeoutDays: 7,
  force2fa: false,
  maxFailedLogins: 5,
  lockoutMinutes: 15,
  smtpEnabled: false,
  smtpHost: '',
  smtpPort: 465,
  smtpEncryption: 'ssl',
  smtpUser: '',
  smtpFrom: '',
  smtpFromName: '',
  monitorEnabled: true,
  monitorInterval: 60,
  monitorRetentionDays: 30,
  monitorShowPlayerNames: true
})

const localeOptions = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
]

const captchaOptions = computed(() => [
  { label: t('adminSettings.captchaAuto'), value: 'auto' },
  { label: t('adminSettings.captchaAlways'), value: 'always' },
  { label: t('adminSettings.captchaOff'), value: 'off' }
])

const encryptionOptions = [
  { label: 'SSL / TLS', value: 'ssl' },
  { label: 'STARTTLS', value: 'starttls' },
  { label: 'None', value: 'none' }
]

const registerRoleOptions = computed(() =>
  roles.value
    .filter((r) => r.name !== 'guest' && r.name !== 'admin')
    .map((r) => ({ label: r.displayName || r.name, value: r.id }))
)

function applyData(data: AdminSettingsData) {
  const { hasSmtpPassword: hasPwd, ...rest } = data
  Object.assign(form, rest)
  hasSmtpPassword.value = Boolean(hasPwd)
  smtpPasswordInput.value = ''
  clearSmtpPassword.value = false
}

async function loadAll() {
  loading.value = true
  try {
    const [settingsData, roleRes] = await Promise.all([
      adminApi.getSettings(),
      adminApi.listRoles()
    ])
    applyData(settingsData)
    roles.value = roleRes.roles || []
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const updated = await adminApi.updateSettings({
      ...form,
      smtpPassword: smtpPasswordInput.value || undefined,
      clearSmtpPassword: clearSmtpPassword.value
    })
    applyData(updated)
    await siteStore.refresh()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    saving.value = false
  }
}

async function handleTestSmtp() {
  testingSmtp.value = true
  try {
    await adminApi.testSmtp({
      smtpHost: form.smtpHost,
      smtpPort: form.smtpPort,
      smtpEncryption: form.smtpEncryption,
      smtpUser: form.smtpUser,
      smtpPassword: smtpPasswordInput.value,
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

onMounted(() => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
  loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobile)
})
</script>

<style scoped>
.lbl-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>

