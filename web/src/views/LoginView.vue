<template>
  <div class="auth-viewport">
    <div class="auth-topbar">
      <ThemeSwitcher />
      <LanguageSelector compact />
    </div>

    <n-card class="auth-card" :title="cardTitle" :bordered="false">
      <!-- Mode: Password Login & MFA -->
      <template v-if="mode === 'login'">
        <n-form v-if="loginStage === 'password'" @submit.prevent="handleLogin">
          <n-form-item :label="$t('auth.account')">
            <n-input v-model:value="loginForm.username" size="large" clearable>
              <template #prefix>
                <n-icon :component="PersonOutline" />
              </template>
            </n-input>
          </n-form-item>

          <n-form-item :label="$t('auth.password')">
            <n-input
              v-model:value="loginForm.password"
              type="password"
              show-password-on="click"
              size="large"
              @keydown.enter.prevent="handleLogin"
            >
              <template #prefix>
                <n-icon :component="LockClosedOutline" />
              </template>
            </n-input>
          </n-form-item>

          <n-form-item v-if="showLoginCaptcha" :show-label="false">
            <PowCaptcha ref="loginCaptchaRef" v-model="loginForm.captcha" />
          </n-form-item>

          <div class="login-links-row">
            <n-checkbox v-model:checked="loginForm.rememberMe">
              {{ $t('auth.rememberMe') }}
            </n-checkbox>
            <n-button text size="small" type="primary" @click="switchMode('forgot-password')">
              {{ $t('auth.forgotPassword') }}
            </n-button>
          </div>

          <n-button
            type="primary"
            block
            size="large"
            :loading="loading"
            @click="handleLogin"
          >
            {{ $t('auth.submitLogin') }}
          </n-button>

          <div v-if="siteStore.allowRegistration" class="switch-row">
            <n-button text size="small" type="primary" @click="switchMode('register')">
              {{ $t('auth.noAccount') }}
            </n-button>
          </div>
        </n-form>

        <!-- MFA Code Challenge -->
        <div v-else-if="loginStage === 'mfa'">
          <p class="auth-subtext">{{ $t('auth.mfaPrompt') }}</p>
          <n-form @submit.prevent="handleVerifyMfa">
            <n-form-item :label="$t('auth.mfaCode')">
              <n-input
                v-model:value="mfaCode"
                maxlength="6"
                size="large"
                clearable
                @keydown.enter.prevent="handleVerifyMfa"
              />
            </n-form-item>

            <n-button
              type="primary"
              block
              size="large"
              :loading="loading"
              @click="handleVerifyMfa"
            >
              {{ $t('auth.verifyLogin') }}
            </n-button>
          </n-form>

          <div class="login-links-row" style="margin-top: 14px; margin-bottom: 0;">
            <n-button text size="small" type="warning" @click="switchMode('forgot-2fa')">
              {{ $t('auth.lost2fa') }}
            </n-button>
            <n-button text size="small" @click="resetToPassword">
              {{ $t('auth.backToLogin') }}
            </n-button>
          </div>
        </div>

        <!-- Forced 2FA Setup During Login -->
        <div v-else-if="loginStage === 'setup2fa'">
          <p class="auth-subtext">{{ $t('auth.setup2faHelp') }}</p>

          <div style="text-align: center; margin-bottom: 14px;">
            <div v-if="setupQrUrl" class="qr-frame">
              <img :src="setupQrUrl" alt="TOTP QR" class="qr-img" />
            </div>
            <n-spin v-else size="medium" />
            <div v-if="setupSecret" class="secret-line">
              <span>{{ $t('auth.secretKey') }}:</span>
              <code class="mono secret-code">{{ setupSecret }}</code>
            </div>
          </div>

          <n-form @submit.prevent="handleCompleteSetup2fa">
            <n-form-item :label="$t('auth.mfaCode')">
              <n-input
                v-model:value="setupTotpCode"
                maxlength="6"
                size="large"
                @keydown.enter.prevent="handleCompleteSetup2fa"
              />
            </n-form-item>

            <n-button
              type="primary"
              block
              size="large"
              :loading="loading"
              @click="handleCompleteSetup2fa"
            >
              {{ $t('auth.verifyLogin') }}
            </n-button>
          </n-form>

          <div class="switch-row">
            <n-button text size="small" @click="resetToPassword">
              {{ $t('auth.backToLogin') }}
            </n-button>
          </div>
        </div>
      </template>

      <!-- Mode: Register -->
      <n-form v-else-if="mode === 'register'" @submit.prevent="handleRegister">
        <n-form-item :label="$t('auth.username')">
          <n-input v-model:value="regForm.username" clearable />
        </n-form-item>

        <n-form-item :label="$t('auth.nickname')">
          <n-input v-model:value="regForm.nickname" clearable />
        </n-form-item>

        <n-form-item :label="$t('auth.email')">
          <n-input v-model:value="regForm.email" clearable />
        </n-form-item>

        <n-form-item v-if="siteStore.requireBindingOnRegister">
          <template #label>
            <span class="label-with-help">
              {{ $t('auth.bindingCode') }}
              <FieldHelp :text="$t('auth.bindingCodeHelp')" />
            </span>
          </template>
          <n-input v-model:value="regForm.bindingCode" maxlength="12" clearable />
        </n-form-item>

        <n-form-item :label="$t('auth.password')">
          <n-input v-model:value="regForm.password" type="password" show-password-on="click" />
        </n-form-item>

        <n-form-item :label="$t('auth.confirmPassword')">
          <n-input
            v-model:value="regForm.confirmPassword"
            type="password"
            show-password-on="click"
          />
        </n-form-item>

        <n-form-item v-if="siteStore.captchaPolicy !== 'off'" :show-label="false">
          <PowCaptcha ref="regCaptchaRef" v-model="regForm.captcha" />
        </n-form-item>

        <n-button type="primary" block size="large" :loading="loading" @click="handleRegister">
          {{ $t('auth.submitRegister') }}
        </n-button>

        <div class="switch-row">
          <n-button text size="small" type="primary" @click="switchMode('login')">
            {{ $t('auth.haveAccount') }}
          </n-button>
        </div>
      </n-form>

      <!-- Mode: Activate Account -->
      <div v-else-if="mode === 'activate'">
        <n-form @submit.prevent="handleActivate">
          <n-form-item :label="$t('auth.resetToken')">
            <n-input v-model:value="actionToken" clearable />
          </n-form-item>
          <n-button type="primary" block size="large" :loading="loading" @click="handleActivate">
            {{ $t('auth.activateBtn') }}
          </n-button>
        </n-form>
        <div class="switch-row">
          <n-button text size="small" type="primary" @click="switchMode('login')">
            {{ $t('auth.backToLogin') }}
          </n-button>
        </div>
      </div>

      <!-- Mode: Forgot Password -->
      <div v-else-if="mode === 'forgot-password'">
        <n-alert type="info" :bordered="false" style="margin-bottom: 14px; font-size: 12px;">
          {{ $t('auth.recoveryEmailHelp') }}
        </n-alert>
        <n-form @submit.prevent="handleForgotPassword">
          <n-form-item v-if="siteStore.smtpEnabled" :label="$t('auth.account')">
            <n-input v-model:value="recoveryAccount" clearable />
          </n-form-item>

          <n-form-item v-if="siteStore.smtpEnabled && siteStore.captchaPolicy !== 'off'" :show-label="false">
            <PowCaptcha ref="recoveryCaptchaRef" v-model="recoveryCaptcha" />
          </n-form-item>

          <n-button
            v-if="siteStore.smtpEnabled"
            type="primary"
            block
            size="large"
            :loading="loading"
            @click="handleForgotPassword"
          >
            {{ $t('auth.sendResetEmail') }}
          </n-button>
        </n-form>

        <div class="login-links-row" style="margin-top: 14px; margin-bottom: 0;">
          <n-button text size="small" @click="switchMode('reset-password')">
            {{ $t('auth.resetPasswordTitle') }}
          </n-button>
          <n-button text size="small" type="primary" @click="switchMode('login')">
            {{ $t('auth.backToLogin') }}
          </n-button>
        </div>
      </div>

      <!-- Mode: Reset Password -->
      <n-form v-else-if="mode === 'reset-password'" @submit.prevent="handleResetPassword">
        <n-form-item :label="$t('auth.resetToken')">
          <n-input v-model:value="actionToken" clearable />
        </n-form-item>
        <n-form-item :label="$t('auth.newPassword')">
          <n-input v-model:value="resetNewPassword" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item :label="$t('auth.confirmPassword')">
          <n-input
            v-model:value="resetConfirmPassword"
            type="password"
            show-password-on="click"
            @keydown.enter.prevent="handleResetPassword"
          />
        </n-form-item>
        <n-button type="primary" block size="large" :loading="loading" @click="handleResetPassword">
          {{ $t('auth.submitResetPassword') }}
        </n-button>
        <div class="switch-row">
          <n-button text size="small" type="primary" @click="switchMode('login')">
            {{ $t('auth.backToLogin') }}
          </n-button>
        </div>
      </n-form>

      <!-- Mode: Forgot 2FA -->
      <div v-else-if="mode === 'forgot-2fa'">
        <n-alert type="info" :bordered="false" style="margin-bottom: 14px; font-size: 12px;">
          {{ $t('auth.recoveryEmailHelp') }}
        </n-alert>
        <n-form @submit.prevent="handleForgot2fa">
          <n-form-item v-if="siteStore.smtpEnabled" :label="$t('auth.account')">
            <n-input v-model:value="recoveryAccount" clearable />
          </n-form-item>
          <n-form-item v-if="siteStore.smtpEnabled && siteStore.captchaPolicy !== 'off'" :show-label="false">
            <PowCaptcha ref="recoveryCaptchaRef" v-model="recoveryCaptcha" />
          </n-form-item>
          <n-button
            v-if="siteStore.smtpEnabled"
            type="primary"
            block
            size="large"
            :loading="loading"
            @click="handleForgot2fa"
          >
            {{ $t('auth.sendResetEmail') }}
          </n-button>
        </n-form>

        <div class="login-links-row" style="margin-top: 14px; margin-bottom: 0;">
          <n-button text size="small" @click="switchMode('reset-2fa')">
            {{ $t('auth.reset2faTitle') }}
          </n-button>
          <n-button text size="small" type="primary" @click="switchMode('login')">
            {{ $t('auth.backToLogin') }}
          </n-button>
        </div>
      </div>

      <!-- Mode: Reset 2FA -->
      <n-form v-else-if="mode === 'reset-2fa'" @submit.prevent="handleReset2fa">
        <n-form-item :label="$t('auth.resetToken')">
          <n-input
            v-model:value="actionToken"
            clearable
            @keydown.enter.prevent="handleReset2fa"
          />
        </n-form-item>
        <n-button type="primary" block size="large" :loading="loading" @click="handleReset2fa">
          {{ $t('auth.submitReset2fa') }}
        </n-button>
        <div class="switch-row">
          <n-button text size="small" type="primary" @click="switchMode('login')">
            {{ $t('auth.backToLogin') }}
          </n-button>
        </div>
      </n-form>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import QRCode from 'qrcode'
import { LockClosedOutline, PersonOutline } from '@vicons/ionicons5'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import LanguageSelector from '../components/LanguageSelector.vue'
import FieldHelp from '../components/FieldHelp.vue'
import PowCaptcha from '../components/PowCaptcha.vue'
import { authApi } from '../api/auth'
import { profileApi } from '../api/profile'
import { getApiErrorMessage } from '../api/client'
import { useSiteStore } from '../stores/site'
import { useAuthStore } from '../stores/auth'

type AuthMode =
  | 'login'
  | 'register'
  | 'activate'
  | 'forgot-password'
  | 'reset-password'
  | 'forgot-2fa'
  | 'reset-2fa'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const { t } = useI18n()
const siteStore = useSiteStore()
const authStore = useAuthStore()

const mode = ref<AuthMode>('login')
const loginStage = ref<'password' | 'mfa' | 'setup2fa'>('password')
const loading = ref(false)
const captchaTriggered = ref(false)

const loginCaptchaRef = ref<InstanceType<typeof PowCaptcha> | null>(null)
const regCaptchaRef = ref<InstanceType<typeof PowCaptcha> | null>(null)
const recoveryCaptchaRef = ref<InstanceType<typeof PowCaptcha> | null>(null)

const loginForm = reactive({
  username: '',
  password: '',
  rememberMe: true,
  captcha: ''
})

const mfaToken = ref('')
const mfaCode = ref('')
const setupSecret = ref('')
const setupQrUrl = ref('')
const setupTotpCode = ref('')

const regForm = reactive({
  username: '',
  nickname: '',
  email: '',
  bindingCode: '',
  password: '',
  confirmPassword: '',
  captcha: ''
})

const recoveryAccount = ref('')
const recoveryCaptcha = ref('')
const actionToken = ref('')
const resetNewPassword = ref('')
const resetConfirmPassword = ref('')

const showLoginCaptcha = computed(
  () => siteStore.captchaPolicy === 'always' || captchaTriggered.value
)

const cardTitle = computed(() => {
  switch (mode.value) {
    case 'register':
      return t('auth.registerTitle')
    case 'activate':
      return t('auth.activateTitle')
    case 'forgot-password':
      return t('auth.forgotPasswordTitle')
    case 'reset-password':
      return t('auth.resetPasswordTitle')
    case 'forgot-2fa':
      return t('auth.forgot2faTitle')
    case 'reset-2fa':
      return t('auth.reset2faTitle')
    default:
      if (loginStage.value === 'mfa') return t('auth.mfaTitle')
      if (loginStage.value === 'setup2fa') return t('auth.setup2faTitle')
      return siteStore.siteName
  }
})

function switchMode(next: AuthMode) {
  mode.value = next
  if (next === 'login') {
    loginStage.value = 'password'
  }
}

function resetToPassword() {
  loginStage.value = 'password'
  mfaToken.value = ''
  mfaCode.value = ''
  setupSecret.value = ''
  setupQrUrl.value = ''
  setupTotpCode.value = ''
}

function navigateAfterAuth() {
  if (authStore.mustChangePassword) {
    router.replace('/first-password')
    return
  }
  if (authStore.needsBinding) {
    router.replace('/bind')
    return
  }
  const target = (route.query.redirect as string) || '/'
  router.replace(target)
}

async function beginForcedSetup2fa(token: string) {
  mfaToken.value = token
  loginStage.value = 'setup2fa'
  setupSecret.value = ''
  setupQrUrl.value = ''
  try {
    const res = await profileApi.setup2fa(token)
    setupSecret.value = res.secret
    setupQrUrl.value = await QRCode.toDataURL(res.otpauthUri, { width: 170, margin: 1 })
  } catch (err) {
    message.error(getApiErrorMessage(err))
    resetToPassword()
  }
}

async function handleLogin() {
  if (!loginForm.username || !loginForm.password) return
  if (showLoginCaptcha.value && !loginForm.captcha) {
    const solved = await loginCaptchaRef.value?.solve()
    if (!solved) return
  }

  loading.value = true
  try {
    const result = await authStore.login({
      username: loginForm.username,
      password: loginForm.password,
      rememberMe: loginForm.rememberMe,
      captcha: loginForm.captcha
    })
    if (result.status === 'mfa') {
      mfaToken.value = result.mfaToken
      loginStage.value = 'mfa'
    } else if (result.status === 'setup2fa') {
      await beginForcedSetup2fa(result.mfaToken)
    } else {
      message.success(t('auth.loginSuccess'))
      navigateAfterAuth()
    }
  } catch (err: any) {
    const data = err?.response?.data
    if (data?.captchaRequired) {
      captchaTriggered.value = true
      loginCaptchaRef.value?.reset()
    } else if (showLoginCaptcha.value) {
      loginCaptchaRef.value?.reset()
    }
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleVerifyMfa() {
  if (!mfaCode.value.trim()) return
  loading.value = true
  try {
    await authStore.login({ mfaToken: mfaToken.value, totpCode: mfaCode.value.trim() })
    message.success(t('auth.loginSuccess'))
    navigateAfterAuth()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleCompleteSetup2fa() {
  if (!setupTotpCode.value.trim()) return
  loading.value = true
  try {
    await authStore.login({
      mfaToken: mfaToken.value,
      setupTotpCode: setupTotpCode.value.trim()
    })
    message.success(t('auth.loginSuccess'))
    navigateAfterAuth()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (regForm.password !== regForm.confirmPassword) {
    message.warning(t('setup.passwordMismatch'))
    return
  }
  if (siteStore.captchaPolicy !== 'off' && !regForm.captcha) {
    const solved = await regCaptchaRef.value?.solve()
    if (!solved) return
  }

  loading.value = true
  try {
    const res = await authApi.register({
      username: regForm.username,
      nickname: regForm.nickname,
      email: regForm.email,
      password: regForm.password,
      bindingCode: regForm.bindingCode || undefined,
      captcha: regForm.captcha
    })
    message.success(
      res.activationRequired ? t('auth.registerActivationSent') : t('auth.registerSuccess')
    )
    loginForm.username = regForm.username
    switchMode('login')
  } catch (err) {
    regCaptchaRef.value?.reset()
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleActivate() {
  if (!actionToken.value.trim()) return
  loading.value = true
  try {
    await authApi.activate(actionToken.value.trim())
    message.success(t('auth.activatedSuccess'))
    switchMode('login')
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleForgotPassword() {
  if (!recoveryAccount.value.trim()) return
  if (siteStore.captchaPolicy !== 'off' && !recoveryCaptcha.value) {
    const solved = await recoveryCaptchaRef.value?.solve()
    if (!solved) return
  }
  loading.value = true
  try {
    await authApi.forgotPassword(recoveryAccount.value.trim(), recoveryCaptcha.value)
    message.success(t('auth.resetEmailSent'))
    switchMode('reset-password')
  } catch (err) {
    recoveryCaptchaRef.value?.reset()
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleResetPassword() {
  if (resetNewPassword.value !== resetConfirmPassword.value) {
    message.warning(t('setup.passwordMismatch'))
    return
  }
  loading.value = true
  try {
    await authApi.resetPassword(actionToken.value.trim(), resetNewPassword.value)
    message.success(t('auth.passwordResetSuccess'))
    actionToken.value = ''
    resetNewPassword.value = ''
    resetConfirmPassword.value = ''
    switchMode('login')
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleForgot2fa() {
  if (!recoveryAccount.value.trim()) return
  if (siteStore.captchaPolicy !== 'off' && !recoveryCaptcha.value) {
    const solved = await recoveryCaptchaRef.value?.solve()
    if (!solved) return
  }
  loading.value = true
  try {
    await authApi.forgot2fa(recoveryAccount.value.trim(), recoveryCaptcha.value)
    message.success(t('auth.resetEmailSent'))
    switchMode('reset-2fa')
  } catch (err) {
    recoveryCaptchaRef.value?.reset()
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function handleReset2fa() {
  if (!actionToken.value.trim()) return
  loading.value = true
  try {
    await authApi.reset2fa(actionToken.value.trim())
    message.success(t('auth.twoFaResetSuccess'))
    actionToken.value = ''
    switchMode('login')
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function syncFromRoute() {
  const qMode = String(route.query.mode || 'login') as AuthMode
  const validModes: AuthMode[] = [
    'login',
    'register',
    'activate',
    'forgot-password',
    'reset-password',
    'forgot-2fa',
    'reset-2fa'
  ]
  if (validModes.includes(qMode)) {
    mode.value = qMode
  }
  if (route.query.token) {
    actionToken.value = String(route.query.token)
  }
}

watch(
  () => route.query,
  () => {
    syncFromRoute()
  }
)

onMounted(async () => {
  await siteStore.ensureLoaded()
  syncFromRoute()
  if (mode.value === 'activate' && actionToken.value) {
    handleActivate()
  }
})
</script>

<style scoped>
.login-links-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.switch-row {
  margin-top: 16px;
  text-align: center;
}

.auth-subtext {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--usp-text-muted);
  line-height: 1.5;
}

.label-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.qr-frame {
  display: inline-block;
  padding: 8px;
  background: #ffffff;
  border-radius: 8px;
}

.qr-img {
  width: 168px;
  height: 168px;
  display: block;
}

.secret-line {
  margin-top: 8px;
  font-size: 12px;
  color: var(--usp-text-muted);
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

.secret-code {
  color: var(--usp-primary);
  font-weight: 600;
  user-select: all;
}
</style>

