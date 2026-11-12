<template>
  <div>
    <n-tabs v-model:value="activeTab" type="line" animated>
      <!-- Profile Tab -->
      <n-tab-pane name="profile" :tab="$t('settings.tabProfile')">
        <n-card size="small" style="max-width: 640px; border-radius: 12px;">
          <n-form label-placement="left" :label-width="isMobile ? 'auto' : 120">
            <n-form-item :label="$t('settings.username')">
              <div style="display: flex; align-items: center; gap: 10px; width: 100%;">
                <n-input :value="authStore.user?.username" disabled />
                <n-button size="small" secondary @click="showUsernameModal = true">
                  {{ $t('settings.changeUsername') }}
                </n-button>
              </div>
            </n-form-item>

            <n-form-item :label="$t('settings.nickname')">
              <n-input v-model:value="profileForm.nickname" />
            </n-form-item>

            <n-form-item :label="$t('settings.email')">
              <n-input v-model:value="profileForm.email" />
            </n-form-item>

            <n-form-item :label="$t('settings.locale')">
              <n-select v-model:value="profileForm.locale" :options="localeOptions" />
            </n-form-item>

            <div style="display: flex; justify-content: flex-end;">
              <n-button type="primary" :loading="savingProfile" @click="saveProfile">
                {{ $t('common.save') }}
              </n-button>
            </div>
          </n-form>
        </n-card>
      </n-tab-pane>

      <!-- Security Tab -->
      <n-tab-pane name="security" :tab="$t('settings.tabSecurity')">
        <div style="display: flex; flex-direction: column; gap: 16px; max-width: 820px;">
          <!-- Password -->
          <n-card size="small" :title="$t('settings.passwordSection')" style="border-radius: 12px;">
            <n-form label-placement="left" :label-width="isMobile ? 'auto' : 130">
              <n-form-item :label="$t('settings.currentPassword')">
                <n-input v-model:value="pwdForm.currentPassword" type="password" show-password-on="click" />
              </n-form-item>
              <n-form-item :label="$t('settings.newPassword')">
                <n-input v-model:value="pwdForm.newPassword" type="password" show-password-on="click" />
              </n-form-item>
              <n-form-item :label="$t('settings.confirmPassword')">
                <n-input v-model:value="pwdForm.confirmPassword" type="password" show-password-on="click" />
              </n-form-item>
              <div style="display: flex; justify-content: flex-end;">
                <n-button type="primary" :loading="savingPwd" @click="handleChangePassword">
                  {{ $t('common.save') }}
                </n-button>
              </div>
            </n-form>
          </n-card>

          <!-- 2FA TOTP -->
          <n-card size="small" style="border-radius: 12px;">
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span>{{ $t('settings.totpSection') }}</span>
                <FieldHelp :text="$t('settings.totpHelp')" />
                <n-tag
                  size="small"
                  :bordered="false"
                  :type="authStore.user?.totpEnabled ? 'success' : 'default'"
                >
                  {{ authStore.user?.totpEnabled ? $t('common.enabled') : $t('common.disabled') }}
                </n-tag>
              </div>
            </template>

            <div v-if="!authStore.user?.totpEnabled">
              <n-button v-if="!totpSecret" type="primary" :loading="totpLoading" @click="startTotp">
                {{ $t('settings.enableTotp') }}
              </n-button>

              <div v-else style="display: flex; flex-direction: column; gap: 14px; max-width: 360px;">
                <p style="margin: 0; font-size: 13px; color: var(--usp-text-muted);">
                  {{ $t('settings.scanQr') }}
                </p>
                <div style="background: #fff; padding: 8px; border-radius: 8px; width: fit-content;">
                  <img :src="totpQrUrl" alt="TOTP QR" style="width: 164px; height: 164px; display: block;" />
                </div>
                <div style="font-size: 12px; color: var(--usp-text-muted);">
                  {{ $t('auth.secretKey') }}:
                  <code class="mono" style="color: var(--usp-primary); user-select: all;">{{ totpSecret }}</code>
                </div>
                <div style="display: flex; gap: 8px;">
                  <n-input
                    v-model:value="totpCode"
                    maxlength="6"
                    :placeholder="$t('settings.totpCode')"
                  />
                  <n-button type="primary" :loading="totpLoading" @click="confirmTotp">
                    {{ $t('common.confirm') }}
                  </n-button>
                </div>
              </div>
            </div>

            <div v-else>
              <n-form
                v-if="!siteStore.force2fa && !authStore.user?.require2fa"
                label-placement="left"
                :label-width="isMobile ? 'auto' : 130"
                style="max-width: 440px;"
              >
                <n-form-item :label="$t('settings.currentPassword')">
                  <n-input v-model:value="disableTotpPwd" type="password" show-password-on="click" />
                </n-form-item>
                <n-form-item :label="$t('settings.totpCode')">
                  <n-input v-model:value="disableTotpCode" maxlength="6" />
                </n-form-item>
                <n-button type="error" secondary :loading="totpLoading" @click="handleDisableTotp">
                  {{ $t('settings.disableTotp') }}
                </n-button>
              </n-form>
              <n-alert v-else type="info" :bordered="false">
                {{ $t('errors.TWO_FA_REQUIRED_BY_POLICY') }}
              </n-alert>
            </div>
          </n-card>

          <!-- Active Sessions -->
          <n-card size="small" style="border-radius: 12px;">
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span>{{ $t('settings.sessionsSection') }}</span>
                <FieldHelp :text="$t('settings.sessionsHelp')" />
              </div>
            </template>

            <div class="table-responsive">
              <n-table :bordered="false" :single-line="false" size="small">
                <thead>
                  <tr>
                    <th>{{ $t('home.loginIp') }}</th>
                    <th>{{ $t('home.loginUa') }}</th>
                    <th>{{ $t('settings.lastSeen') }}</th>
                    <th>{{ $t('settings.expiresAt') }}</th>
                    <th style="text-align: right;">{{ $t('common.actions') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="s in sessions" :key="s.sessionId">
                    <td class="mono" style="font-size: 12px;">
                      {{ s.ipAddress || '-' }}
                      <n-tag v-if="s.current" size="tiny" type="success" :bordered="false" style="margin-left: 6px;">
                        {{ $t('settings.currentSession') }}
                      </n-tag>
                    </td>
                    <td style="max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--usp-text-muted);" :title="s.userAgent">
                      {{ s.userAgent || '-' }}
                    </td>
                    <td class="mono" style="font-size: 12px; white-space: nowrap;">
                      {{ formatTimestamp(s.lastSeenAt) }}
                    </td>
                    <td class="mono" style="font-size: 12px; white-space: nowrap;">
                      {{ formatTimestamp(s.expiresAt) }}
                    </td>
                    <td style="text-align: right;">
                      <n-button
                        size="tiny"
                        quaternary
                        type="error"
                        :disabled="s.current"
                        @click="revokeSession(s.sessionId)"
                      >
                        {{ $t('settings.revoke') }}
                      </n-button>
                    </td>
                  </tr>
                </tbody>
              </n-table>
            </div>
          </n-card>
        </div>
      </n-tab-pane>

      <!-- Player Binding Tab -->
      <n-tab-pane name="binding" :tab="$t('settings.tabBinding')">
        <n-card size="small" :title="$t('settings.bindingSection')" style="max-width: 600px; border-radius: 12px;">
          <template v-if="authStore.user?.bound">
            <n-descriptions :column="1" size="small" label-placement="left" bordered style="margin-bottom: 16px;">
              <n-descriptions-item :label="$t('home.boundPlayer')">
                <strong>{{ authStore.user.playerName }}</strong>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('settings.playerUuid')">
                <span class="mono">{{ authStore.user.playerUuid }}</span>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('settings.boundAt')">
                <span class="mono">{{ formatTimestamp(authStore.user.boundAt) }}</span>
              </n-descriptions-item>
            </n-descriptions>

            <n-popconfirm @positive-click="handleUnbind">
              <template #trigger>
                <n-button type="error" secondary :loading="bindingLoading">
                  {{ $t('settings.unbindBtn') }}
                </n-button>
              </template>
              {{ $t('settings.confirmUnbind') }}
            </n-popconfirm>
          </template>

          <n-form v-else @submit.prevent="handleBind">
            <n-form-item>
              <template #label>
                <span style="display: inline-flex; align-items: center; gap: 6px;">
                  {{ $t('settings.bindingCode') }}
                  <FieldHelp :text="$t('settings.bindingCodeHelp')" />
                </span>
              </template>
              <n-input v-model:value="bindingCode" maxlength="12" clearable />
            </n-form-item>
            <n-button type="primary" :loading="bindingLoading" @click="handleBind">
              {{ $t('settings.bindBtn') }}
            </n-button>
          </n-form>
        </n-card>
      </n-tab-pane>
    </n-tabs>

    <!-- Change Username Modal -->
    <n-modal
      v-model:show="showUsernameModal"
      preset="card"
      :title="$t('settings.changeUsername')"
      style="width: 420px; max-width: 94vw; border-radius: 12px;"
    >
      <n-form>
        <n-form-item :label="$t('settings.newUsername')">
          <n-input v-model:value="usernameForm.newUsername" />
        </n-form-item>
        <n-form-item :label="$t('settings.currentPassword')">
          <n-input v-model:value="usernameForm.currentPassword" type="password" show-password-on="click" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="showUsernameModal = false">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" :loading="savingUsername" @click="handleChangeUsername">
            {{ $t('common.save') }}
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import QRCode from 'qrcode'
import FieldHelp from '../components/FieldHelp.vue'
import { profileApi, type UserSessionItem } from '../api/profile'
import { bindingApi } from '../api/binding'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useSiteStore } from '../stores/site'
import { setAppLocale } from '../i18n'
import { formatTimestamp } from '../utils/format'

const route = useRoute()
const message = useMessage()
const { t } = useI18n()
const authStore = useAuthStore()
const siteStore = useSiteStore()

const isMobile = ref(false)
function updateMobile() {
  isMobile.value = window.innerWidth <= 768
}

const activeTab = ref('profile')
const localeOptions = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
]

const savingProfile = ref(false)
const profileForm = reactive({
  nickname: authStore.user?.nickname || '',
  email: authStore.user?.email || '',
  locale: authStore.user?.locale || 'zh-CN'
})

const showUsernameModal = ref(false)
const savingUsername = ref(false)
const usernameForm = reactive({
  newUsername: '',
  currentPassword: ''
})

const savingPwd = ref(false)
const pwdForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const totpLoading = ref(false)
const totpSecret = ref('')
const totpQrUrl = ref('')
const totpCode = ref('')
const disableTotpPwd = ref('')
const disableTotpCode = ref('')

const sessions = ref<UserSessionItem[]>([])
const bindingCode = ref('')
const bindingLoading = ref(false)

async function saveProfile() {
  savingProfile.value = true
  try {
    await profileApi.updateProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      locale: profileForm.locale
    })
    setAppLocale(profileForm.locale)
    await authStore.refreshMe()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    savingProfile.value = false
  }
}

async function handleChangeUsername() {
  savingUsername.value = true
  try {
    await profileApi.changeUsername(usernameForm.newUsername, usernameForm.currentPassword)
    await authStore.refreshMe()
    showUsernameModal.value = false
    usernameForm.newUsername = ''
    usernameForm.currentPassword = ''
    message.success(t('common.updated'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    savingUsername.value = false
  }
}

async function handleChangePassword() {
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    message.warning(t('setup.passwordMismatch'))
    return
  }
  savingPwd.value = true
  try {
    await profileApi.changePassword(pwdForm.currentPassword, pwdForm.newPassword)
    pwdForm.currentPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    message.success(t('common.updated'))
    await loadSessions()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    savingPwd.value = false
  }
}

async function startTotp() {
  totpLoading.value = true
  try {
    const res = await profileApi.setup2fa()
    totpSecret.value = res.secret
    totpQrUrl.value = await QRCode.toDataURL(res.otpauthUri, { width: 170, margin: 1 })
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    totpLoading.value = false
  }
}

async function confirmTotp() {
  if (!totpCode.value.trim()) return
  totpLoading.value = true
  try {
    await profileApi.confirm2fa(totpCode.value.trim())
    totpSecret.value = ''
    totpQrUrl.value = ''
    totpCode.value = ''
    await authStore.refreshMe()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    totpLoading.value = false
  }
}

async function handleDisableTotp() {
  totpLoading.value = true
  try {
    await profileApi.disable2fa(disableTotpPwd.value, disableTotpCode.value)
    disableTotpPwd.value = ''
    disableTotpCode.value = ''
    await authStore.refreshMe()
    message.success(t('common.updated'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    totpLoading.value = false
  }
}

async function loadSessions() {
  try {
    const res = await profileApi.listSessions()
    sessions.value = res.sessions || []
  } catch {
    // ignore
  }
}

async function revokeSession(id: string) {
  try {
    await profileApi.revokeSession(id)
    message.success(t('common.deleted'))
    await loadSessions()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function handleBind() {
  if (!bindingCode.value.trim()) return
  bindingLoading.value = true
  try {
    await bindingApi.bind(bindingCode.value.trim())
    bindingCode.value = ''
    await authStore.refreshMe()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    bindingLoading.value = false
  }
}

async function handleUnbind() {
  bindingLoading.value = true
  try {
    await bindingApi.unbind()
    await authStore.refreshMe()
    message.success(t('common.updated'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    bindingLoading.value = false
  }
}

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'profile' || tab === 'security' || tab === 'binding') {
      activeTab.value = tab
    }
  },
  { immediate: true }
)

onMounted(async () => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
  await authStore.refreshMe()
  profileForm.nickname = authStore.user?.nickname || ''
  profileForm.email = authStore.user?.email || ''
  profileForm.locale = authStore.user?.locale || 'zh-CN'
  await loadSessions()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobile)
})
</script>

