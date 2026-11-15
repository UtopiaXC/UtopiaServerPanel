<template>
  <div :class="embedded ? 'whitelist-apply-wrap' : 'whitelist-apply-tab'">
    <n-card
      :title="$t('whitelist.applyTitle')"
      :bordered="!embedded"
      :class="embedded ? '' : 'whitelist-card'"
    >
      <n-spin :show="loadingStatus">
        <n-alert
          v-if="!loadingStatus && !featureAvailable"
          type="info"
          :title="$t('whitelist.unavailableTitle')"
        >
          {{ $t('whitelist.unavailableDesc') }}
        </n-alert>

        <n-result
          v-else-if="!loadingStatus && guestLoginRequired"
          :title="$t('whitelist.loginRequiredTitle')"
          :description="$t('whitelist.loginRequiredDesc')"
        >
          <template #icon>
            <n-icon size="52" :component="LockClosedOutline" style="color: var(--usp-primary);" />
          </template>
          <template #footer>
            <n-space justify="center">
              <n-button
                type="primary"
                @click="router.push({ path: '/login', query: { redirect: '/whitelist/apply' } })"
              >
                <template #icon>
                  <n-icon :component="LogInOutline" />
                </template>
                {{ $t('common.login') }}
              </n-button>
              <n-button
                secondary
                :disabled="!siteStore.allowRegistration"
                @click="router.push({ path: '/login', query: { mode: 'register', redirect: '/whitelist/apply' } })"
              >
                <template #icon>
                  <n-icon :component="PersonAddOutline" />
                </template>
                {{ $t('common.register') }}
              </n-button>
            </n-space>
          </template>
        </n-result>

        <n-result
          v-else-if="!loadingStatus && (!hasApplyPermission || permissionDenied)"
          status="403"
          :title="$t('errors.FORBIDDEN')"
          :description="$t('whitelist.noPermissionDesc')"
        >
          <template v-if="!embedded" #footer>
            <n-space justify="center">
              <n-button @click="router.push('/')">
                {{ $t('common.back') }}
              </n-button>
            </n-space>
          </template>
        </n-result>

        <template v-else-if="status && featureAvailable">
          <n-alert
            v-if="submittedSuccess"
            type="success"
            :title="$t('whitelist.submittedTitle')"
            style="margin-bottom: 14px;"
          >
            {{ $t('whitelist.submittedDesc') }}
          </n-alert>

          <n-form label-placement="top">
            <n-form-item required>
              <template #label>
                <span>{{ $t('whitelist.playerName') }}</span>
              </template>
              <div style="display: flex; gap: 8px; width: 100%;">
                <n-input
                  v-model:value="form.username"
                  :placeholder="$t('whitelist.playerNamePlaceholder')"
                />
                <n-button secondary :loading="lookingUp" @click="handleLookup">
                  {{ $t('whitelist.lookupBtn') }}
                </n-button>
              </div>
            </n-form-item>

            <!-- Lookup Preview Card -->
            <n-card
              v-if="lookupResult"
              size="small"
              embedded
              style="margin-bottom: 14px;"
            >
              <div class="lookup-box">
                <img
                  :src="lookupResult.avatarUrl"
                  alt="Player Avatar"
                  class="player-avatar"
                />
                <div class="lookup-uuids">
                  <div class="uuid-row">
                    <span class="uuid-lbl">{{ $t('whitelist.onlineUuid') }}:</span>
                    <span class="uuid-val">
                      {{ lookupResult.onlineFound && lookupResult.onlineUuid ? lookupResult.onlineUuid : $t('whitelist.notPremium') }}
                    </span>
                  </div>
                  <div class="uuid-row">
                    <span class="uuid-lbl">{{ $t('whitelist.offlineUuid') }}:</span>
                    <span class="uuid-val">{{ lookupResult.offlineUuid }}</span>
                  </div>
                  <div style="margin-top: 6px;">
                    <n-radio-group v-model:value="form.preferredUuidMode" size="small">
                      <n-radio value="auto">{{ $t('whitelist.uuidAuto') }}</n-radio>
                      <n-radio value="online" :disabled="!lookupResult.onlineFound">
                        {{ $t('whitelist.uuidOnline') }}
                      </n-radio>
                      <n-radio value="offline">{{ $t('whitelist.uuidOffline') }}</n-radio>
                    </n-radio-group>
                  </div>
                </div>
              </div>
            </n-card>

            <n-form-item :label="$t('whitelist.email')" required>
              <n-input v-model:value="form.email" placeholder="player@example.com" />
            </n-form-item>

            <!-- Structured Social Accounts with + dropdown -->
            <n-form-item :required="isSocialRequired">
              <template #label>
                <div class="social-header-row">
                  <span>{{ $t('whitelist.socialAccount') }}</span>
                  <n-dropdown
                    trigger="click"
                    :options="socialDropdownOptions"
                    @select="handleAddSocial"
                  >
                    <n-button size="tiny" secondary type="primary">
                      <template #icon>
                        <n-icon :component="AddOutline" />
                      </template>
                      {{ $t('whitelist.addSocialBtn') }}
                    </n-button>
                  </n-dropdown>
                </div>
              </template>

              <div class="social-rows">
                <div v-if="socialRows.length === 0" class="social-empty-hint">
                  {{ isSocialRequired ? $t('whitelist.socialRequiredHint') : $t('whitelist.socialOptionalHint') }}
                </div>
                <div v-for="row in socialRows" :key="row.id" class="social-row">
                  <n-tag size="small" type="info" :bordered="false" class="social-platform-tag">
                    {{ getPlatformLabel(row.platform) }}
                  </n-tag>
                  <n-tag
                    v-if="isPlatformRequired(row.platform)"
                    size="tiny"
                    type="error"
                    :bordered="false"
                  >
                    {{ $t('common.required') }}
                  </n-tag>
                  <n-input
                    v-if="row.platform === 'custom'"
                    v-model:value="row.customPlatform"
                    :placeholder="$t('whitelist.customPlatformPlaceholder')"
                    style="max-width: 140px;"
                  />
                  <n-input
                    v-model:value="row.account"
                    :placeholder="$t('whitelist.socialAccountPlaceholder', { platform: getPlatformLabel(row.platform) })"
                    style="flex: 1; min-width: 150px;"
                  />
                  <n-button
                    size="small"
                    quaternary
                    type="error"
                    :disabled="isLastRequiredRow(row)"
                    @click="removeSocialRow(row.id)"
                  >
                    <template #icon>
                      <n-icon :component="CloseOutline" />
                    </template>
                  </n-button>
                </div>
              </div>
            </n-form-item>

            <n-form-item :label="$t('whitelist.reason')" required>
              <n-input
                v-model:value="form.reason"
                type="textarea"
                :autosize="{ minRows: 3, maxRows: 5 }"
                :placeholder="$t('whitelist.reasonPlaceholder')"
              />
            </n-form-item>

            <n-form-item
              v-if="authStore.isLoggedIn"
              :label="$t('whitelist.bindOnApprove')"
            >
              <n-switch v-model:value="form.bindOnApprove" />
            </n-form-item>

            <n-form-item v-if="!authStore.isLoggedIn" :label="$t('whitelist.captchaLabel')" required>
              <PowCaptcha ref="captchaRef" v-model="form.captcha" />
            </n-form-item>

            <n-button
              type="primary"
              block
              size="large"
              :loading="submitting"
              @click="handleSubmit"
            >
              {{ $t('whitelist.submitBtn') }}
            </n-button>
          </n-form>

          <div v-if="!embedded" style="margin-top: 14px; display: flex; justify-content: space-between;">
            <n-button v-if="!authStore.isLoggedIn" text size="small" @click="router.push('/login')">
              {{ $t('common.login') }}
            </n-button>
            <n-button v-if="authStore.isLoggedIn" text size="small" @click="router.push('/home')">
              {{ $t('nav.home') }}
            </n-button>
          </div>
        </template>
      </n-spin>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { AddOutline, CloseOutline, LockClosedOutline, LogInOutline, PersonAddOutline } from '@vicons/ionicons5'
import PowCaptcha from '../components/PowCaptcha.vue'
import {
  whitelistApi,
  type PlayerLookupResult,
  type SocialAccountEntry,
  type WhitelistStatus
} from '../api/whitelist'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useSiteStore } from '../stores/site'

defineProps<{ embedded?: boolean }>()

const PRESET_PLATFORMS = [
  'discord',
  'qq',
  'phone',
  'facebook',
  'x',
  'whatsapp',
  'wechat',
  'line',
  'tiktok',
  'instagram',
  'custom'
] as const

interface SocialRow extends SocialAccountEntry {
  id: number
}

const router = useRouter()
const message = useMessage()
const { t, te } = useI18n()
const authStore = useAuthStore()
const siteStore = useSiteStore()

const hasApplyPermission = computed(() => authStore.hasPermission('whitelist_apply', 2))
const permissionDenied = ref(false)
const loadingStatus = ref(true)
const status = ref<WhitelistStatus | null>(null)
const featureAvailable = computed(() =>
  status.value
    ? Boolean(status.value.featureVisible ?? status.value.visible)
    : siteStore.whitelistFeatureVisible
)
const guestLoginRequired = computed(
  () =>
    featureAvailable.value &&
    !authStore.isLoggedIn &&
    (!hasApplyPermission.value || permissionDenied.value || Boolean(status.value && !status.value.canApply))
)
const lookingUp = ref(false)
const submitting = ref(false)
const submittedSuccess = ref(false)
const lookupResult = ref<PlayerLookupResult | null>(null)
const captchaRef = ref<InstanceType<typeof PowCaptcha> | null>(null)

let nextRowId = 1
const socialRows = ref<SocialRow[]>([])

const form = reactive({
  username: '',
  preferredUuidMode: 'auto',
  email: authStore.user?.email || '',
  reason: '',
  bindOnApprove: true,
  captcha: ''
})

const requiredPlatforms = computed<string[]>(() => status.value?.requiredSocialPlatforms || [])
const isSocialRequired = computed(
  () => Boolean(status.value?.socialRequired) || requiredPlatforms.value.length > 0
)

const socialDropdownOptions = computed(() =>
  PRESET_PLATFORMS.map((key) => ({
    label: getPlatformLabel(key),
    key
  }))
)

function getPlatformLabel(platform: string): string {
  const key = `socialPlatforms.${platform.toLowerCase()}`
  return te(key) ? t(key) : platform
}

function isPlatformRequired(platform: string): boolean {
  const lower = platform.toLowerCase()
  return requiredPlatforms.value.some((p) => p.toLowerCase() === lower)
}

function isLastRequiredRow(row: SocialRow): boolean {
  if (!isPlatformRequired(row.platform)) return false
  const lower = row.platform.toLowerCase()
  return socialRows.value.filter((r) => r.platform.toLowerCase() === lower).length <= 1
}

function handleAddSocial(key: string) {
  socialRows.value.push({
    id: nextRowId++,
    platform: key,
    customPlatform: '',
    account: ''
  })
}

function removeSocialRow(id: number) {
  socialRows.value = socialRows.value.filter((r) => r.id !== id)
}

function seedRequiredSocialRows() {
  for (const req of requiredPlatforms.value) {
    const lower = req.toLowerCase()
    const exists = socialRows.value.some((r) => r.platform.toLowerCase() === lower)
    if (!exists) {
      socialRows.value.push({
        id: nextRowId++,
        platform: lower,
        customPlatform: '',
        account: ''
      })
    }
  }
}

async function handleLookup() {
  if (!form.username.trim()) return
  lookingUp.value = true
  try {
    lookupResult.value = await whitelistApi.lookupPlayer(form.username.trim())
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    lookingUp.value = false
  }
}

async function handleSubmit() {
  if (!form.username.trim() || !form.email.trim() || !form.reason.trim()) {
    message.warning(t('whitelist.requiredFields'))
    return
  }

  const cleanedEntries: SocialAccountEntry[] = []
  for (const row of socialRows.value) {
    const acc = row.account.trim()
    if (!acc) continue
    if (row.platform === 'custom' && !row.customPlatform?.trim()) {
      message.warning(t('whitelist.customPlatformRequired'))
      return
    }
    cleanedEntries.push({
      platform: row.platform,
      customPlatform: row.platform === 'custom' ? row.customPlatform?.trim() : undefined,
      account: acc
    })
  }

  if (isSocialRequired.value && cleanedEntries.length === 0) {
    message.warning(t('whitelist.socialRequiredError'))
    return
  }

  for (const req of requiredPlatforms.value) {
    const lower = req.toLowerCase()
    if (!cleanedEntries.some((e) => e.platform.toLowerCase() === lower)) {
      message.warning(t('whitelist.missingRequiredSocial', { platform: getPlatformLabel(req) }))
      return
    }
  }

  submitting.value = true
  try {
    await whitelistApi.apply({
      username: form.username.trim(),
      onlineUuid: lookupResult.value?.onlineUuid || undefined,
      offlineUuid: lookupResult.value?.offlineUuid || undefined,
      preferredUuidMode: form.preferredUuidMode,
      email: form.email.trim(),
      socialAccounts: cleanedEntries,
      reason: form.reason.trim(),
      bindOnApprove: authStore.isLoggedIn ? form.bindOnApprove : false,
      captcha: form.captcha || undefined
    })
    submittedSuccess.value = true
    form.reason = ''
    captchaRef.value?.reset()
    message.success(t('whitelist.submittedTitle'))
  } catch (err) {
    captchaRef.value?.reset()
    message.error(getApiErrorMessage(err))
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loadingStatus.value = true
  try {
    status.value = await whitelistApi.getStatus()
    if (hasApplyPermission.value) {
      seedRequiredSocialRows()
    }
  } catch (err: any) {
    if (err?.response?.status === 403) {
      permissionDenied.value = true
    } else {
      message.error(getApiErrorMessage(err))
    }
  } finally {
    loadingStatus.value = false
  }
})
</script>

<style scoped>
.whitelist-apply-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.whitelist-apply-tab {
  width: 100%;
  max-width: 720px;
}

.whitelist-card {
  border-radius: 12px;
}

.social-header-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.social-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.social-empty-hint {
  font-size: 12px;
  color: var(--usp-text-muted);
}

.social-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.social-platform-tag {
  min-width: 74px;
  justify-content: center;
}

.lookup-box {
  display: flex;
  gap: 14px;
  align-items: center;
}

.player-avatar {
  width: 56px;
  height: 56px;
  border-radius: 6px;
  image-rendering: pixelated;
  background: rgba(128, 128, 128, 0.15);
}

.lookup-uuids {
  flex: 1;
  min-width: 0;
  font-size: 12px;
}

.uuid-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.uuid-lbl {
  opacity: 0.7;
}

.uuid-val {
  font-family: monospace;
  word-break: break-all;
}
</style>
