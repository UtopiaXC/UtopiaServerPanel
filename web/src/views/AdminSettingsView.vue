<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">{{ $t('adminSettings.title') }}</h2>
    </div>

    <n-spin :show="loading">
      <n-tabs type="line" animated>
        <!-- Tab 1: Panel Settings -->
        <n-tab-pane name="panel" :tab="$t('adminSettings.tabPanel')">
          <div class="cards-stack">
            <!-- Site Info -->
            <n-card size="small" class="settings-card" :title="$t('adminSettings.tabSystem')">
              <n-form
                label-placement="left"
                :label-width="isMobile ? 'auto' : 180"
                :disabled="!canWrite"
                class="settings-form"
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
                <n-form-item>
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.siteFavicon') }}
                      <FieldHelp :text="$t('adminSettings.siteFaviconHelp')" />
                    </span>
                  </template>
                  <div class="favicon-row">
                    <div class="favicon-preview-box">
                      <img :src="faviconUrl" alt="favicon" class="favicon-preview-img" />
                    </div>
                    <n-tag size="small" :bordered="false" :type="hasCustomFavicon ? 'success' : 'default'">
                      {{ hasCustomFavicon ? $t('adminSettings.faviconCustom') : $t('adminSettings.faviconDefault') }}
                    </n-tag>
                    <input
                      ref="faviconInputRef"
                      type="file"
                      accept="image/png,image/x-icon,image/vnd.microsoft.icon,image/jpeg,image/webp,image/svg+xml"
                      style="display: none"
                      @change="handleFaviconFileChange"
                    />
                    <n-button
                      size="small"
                      secondary
                      :loading="uploadingFavicon"
                      :disabled="!canWrite"
                      @click="triggerFaviconPicker"
                    >
                      {{ $t('adminSettings.faviconUpload') }}
                    </n-button>
                    <n-button
                      size="small"
                      tertiary
                      :loading="resettingFavicon"
                      :disabled="!canWrite || !hasCustomFavicon"
                      @click="handleResetFavicon"
                    >
                      {{ $t('adminSettings.faviconReset') }}
                    </n-button>
                  </div>
                </n-form-item>
              </n-form>
              <div v-if="canWrite" class="card-actions">
                <n-button type="primary" :loading="savingSection === 'site'" @click="saveSiteSection">
                  {{ $t('adminSettings.saveBtn') }}
                </n-button>
              </div>
            </n-card>

            <!-- Registration & Binding -->
            <n-card size="small" class="settings-card" :title="$t('adminSettings.tabRegistration')">
              <n-form
                label-placement="left"
                :label-width="isMobile ? 'auto' : 190"
                :disabled="!canWrite"
                class="settings-form"
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
                  <div class="switch-with-hint">
                    <n-switch
                      v-model:value="form.requireEmailVerification"
                      :disabled="!canWrite || !isSmtpConfigured"
                    />
                    <span v-if="!isSmtpConfigured" class="field-hint">
                      {{ $t('adminSettings.smtpRequiredHint') }}
                    </span>
                  </div>
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
              <div v-if="canWrite" class="card-actions">
                <n-button
                  type="primary"
                  :loading="savingSection === 'registration'"
                  @click="saveRegistrationSection"
                >
                  {{ $t('adminSettings.saveBtn') }}
                </n-button>
              </div>
            </n-card>

            <!-- Security -->
            <n-card size="small" class="settings-card" :title="$t('adminSettings.tabSecurity')">
              <n-form
                label-placement="left"
                :label-width="isMobile ? 'auto' : 190"
                :disabled="!canWrite"
                class="settings-form"
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
              <div v-if="canWrite" class="card-actions">
                <n-button
                  type="primary"
                  :loading="savingSection === 'security'"
                  @click="saveSecuritySection"
                >
                  {{ $t('adminSettings.saveBtn') }}
                </n-button>
              </div>
            </n-card>

            <!-- SMTP Mail -->
            <n-card size="small" class="settings-card" :title="$t('adminSettings.tabSmtp')">
              <n-form
                label-placement="left"
                :label-width="isMobile ? 'auto' : 180"
                :disabled="!canWrite"
                class="settings-form"
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
              </n-form>
              <div v-if="canWrite" class="card-actions">
                <n-button secondary :loading="testingSmtp" @click="handleTestSmtp">
                  {{ $t('adminSettings.testSmtp') }}
                </n-button>
                <n-button type="primary" :loading="savingSection === 'smtp'" @click="saveSmtpSection">
                  {{ $t('adminSettings.saveBtn') }}
                </n-button>
              </div>
            </n-card>
          </div>
        </n-tab-pane>

        <!-- Tab 2: Panel Features -->
        <n-tab-pane name="features" :tab="$t('adminSettings.tabFeatures')">
          <div class="cards-stack">
            <!-- Monitoring -->
            <n-card size="small" class="settings-card" :title="$t('adminSettings.tabMonitor')">
              <n-form
                label-placement="left"
                :label-width="isMobile ? 'auto' : 190"
                :disabled="!canWrite"
                class="settings-form"
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
              <div v-if="canWrite" class="card-actions">
                <n-button
                  type="primary"
                  :loading="savingSection === 'monitor'"
                  @click="saveMonitorSection"
                >
                  {{ $t('adminSettings.saveBtn') }}
                </n-button>
              </div>
            </n-card>

            <!-- Web Map -->
            <n-card size="small" class="settings-card" :title="$t('adminSettings.tabMap')">
              <n-form
                label-placement="left"
                :label-width="isMobile ? 'auto' : 210"
                :disabled="!canWrite"
                class="settings-form"
              >
                <n-form-item :label="$t('adminSettings.mapEnabled')">
                  <n-switch v-model:value="form.mapEnabled" />
                </n-form-item>
                <n-form-item :label="$t('adminSettings.mapRenderMode')">
                  <n-select v-model:value="form.mapRenderMode" :options="renderModeOptions" />
                </n-form-item>
                <n-form-item :label="$t('adminSettings.mapMaxChunksPerTick')">
                  <n-input-number v-model:value="form.mapMaxChunksPerTick" :min="1" :max="16" style="width: 100%;" />
                </n-form-item>
                <n-form-item>
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.mapCacheMaxMb') }}
                      <FieldHelp :text="$t('adminSettings.mapCacheMaxMbHelp')" />
                    </span>
                  </template>
                  <n-input-number v-model:value="form.mapCacheMaxMb" :min="32" :max="16384" style="width: 100%;" />
                </n-form-item>
              </n-form>

              <n-divider title-placement="left" style="margin: 12px 0;">
                {{ $t('adminSettings.mapPermissionsTitle') }}
              </n-divider>

              <div class="table-scroll">
                <n-table size="small" :bordered="false" style="max-width: 780px;">
                  <thead>
                    <tr>
                      <th>{{ $t('adminSettings.mapCapabilityCol') }}</th>
                      <th style="text-align: right; width: 220px;">{{ $t('adminSettings.mapPermissionCol') }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in mapFeatureRows" :key="item.field">
                      <td>
                        <span class="lbl-help">
                          <span>{{ $t(item.labelKey) }}</span>
                          <FieldHelp :text="$t(item.helpKey)" />
                        </span>
                      </td>
                      <td style="text-align: right;">
                        <n-radio-group
                          v-model:value="form[item.field]"
                          size="small"
                          :disabled="!canWrite"
                        >
                          <n-radio-button value="readonly">
                            {{ $t('adminSettings.visReadonly') }}
                          </n-radio-button>
                          <n-radio-button value="full">
                            {{ $t('adminSettings.visFull') }}
                          </n-radio-button>
                        </n-radio-group>
                      </td>
                    </tr>
                  </tbody>
                </n-table>
              </div>

              <div v-if="canWrite" class="card-actions">
                <n-button type="primary" :loading="savingSection === 'map'" @click="saveMapSection">
                  {{ $t('adminSettings.saveBtn') }}
                </n-button>
              </div>
            </n-card>
          </div>
        </n-tab-pane>

        <!-- Tab 3: In-Game Settings -->
        <n-tab-pane name="ingame" :tab="$t('adminSettings.tabIngame')">
          <div class="cards-stack">
            <!-- Whitelist Card -->
            <n-card size="small" class="settings-card" :title="$t('adminSettings.cardWhitelist')">
              <n-form
                label-placement="left"
                :label-width="isMobile ? 'auto' : 210"
                :disabled="!canWrite"
                class="settings-form"
              >
                <n-form-item>
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.serverWhitelistStatus') }}
                      <FieldHelp :text="$t('adminSettings.serverWhitelistStatusHelp')" />
                    </span>
                  </template>
                  <n-switch
                    v-model:value="serverWhitelistEnabled"
                    :loading="togglingWhitelist"
                    @update:value="handleServerWhitelistToggle"
                  />
                </n-form-item>
                <n-form-item>
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.whitelistManagementEnabled') }}
                      <FieldHelp :text="$t('adminSettings.whitelistManagementEnabledHelp')" />
                    </span>
                  </template>
                  <n-switch
                    v-model:value="form.whitelistManagementEnabled"
                    @update:value="handleWhitelistMgmtToggle"
                  />
                </n-form-item>
                <n-form-item>
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.whitelistAllowGuest') }}
                      <FieldHelp :text="$t('adminSettings.whitelistAllowGuestHelp')" />
                    </span>
                  </template>
                  <n-switch
                    v-model:value="whitelistAllowGuest"
                    :loading="togglingGuestWhitelist"
                    @update:value="handleGuestWhitelistToggle"
                  />
                </n-form-item>
                <n-form-item>
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.whitelistApplyCooldownSec') }}
                      <FieldHelp :text="$t('adminSettings.whitelistApplyCooldownSecHelp')" />
                    </span>
                  </template>
                  <n-input-number v-model:value="form.whitelistApplyCooldownSec" :min="0" :max="86400" style="width: 100%;" />
                </n-form-item>
                <n-form-item :label="$t('adminSettings.whitelistMaxPendingPerIp')">
                  <n-input-number v-model:value="form.whitelistMaxPendingPerIp" :min="1" :max="50" style="width: 100%;" />
                </n-form-item>
                <n-form-item :label="$t('adminSettings.whitelistSendEmail')">
                  <div class="switch-with-hint">
                    <n-switch
                      v-model:value="form.whitelistSendEmail"
                      :disabled="!canWrite || !isSmtpConfigured"
                    />
                    <span v-if="!isSmtpConfigured" class="field-hint">
                      {{ $t('adminSettings.smtpRequiredHint') }}
                    </span>
                  </div>
                </n-form-item>
                <n-form-item>
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.whitelistSocialRequired') }}
                      <FieldHelp :text="$t('adminSettings.whitelistSocialRequiredHelp')" />
                    </span>
                  </template>
                  <n-switch
                    v-model:value="form.whitelistSocialRequired"
                    @update:value="handleSocialRequiredToggle"
                  />
                </n-form-item>
                <n-form-item v-if="form.whitelistSocialRequired">
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.whitelistRequiredSocialPlatforms') }}
                      <FieldHelp :text="$t('adminSettings.whitelistRequiredSocialPlatformsHelp')" />
                    </span>
                  </template>
                  <n-select
                    v-model:value="form.whitelistRequiredSocialPlatforms"
                    multiple
                    clearable
                    :options="socialPlatformOptions"
                    :placeholder="$t('adminSettings.whitelistRequiredSocialPlatformsPlaceholder')"
                    style="width: 100%;"
                  />
                </n-form-item>
              </n-form>
              <div v-if="canWrite" class="card-actions">
                <n-button
                  type="primary"
                  :loading="savingSection === 'whitelist'"
                  @click="saveWhitelistSection"
                >
                  {{ $t('adminSettings.saveBtn') }}
                </n-button>
              </div>
            </n-card>

            <!-- Restart Card -->
            <n-card size="small" class="settings-card" :title="$t('adminSettings.cardRestart')">
              <n-form
                label-placement="left"
                :label-width="isMobile ? 'auto' : 210"
                :disabled="!canWrite"
                class="settings-form"
              >
                <n-form-item>
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.serverRestartCommand') }}
                      <FieldHelp :text="$t('adminSettings.serverRestartCommandHelp')" />
                    </span>
                  </template>
                  <n-input v-model:value="form.serverRestartCommand" placeholder="stop" />
                </n-form-item>
                <n-form-item>
                  <template #label>
                    <span class="lbl-help">
                      {{ $t('adminSettings.serverRestartHint') }}
                      <FieldHelp :text="$t('adminSettings.serverRestartHintHelp')" />
                    </span>
                  </template>
                  <n-input v-model:value="form.serverRestartHint" />
                </n-form-item>
              </n-form>
              <div v-if="canWrite" class="card-actions">
                <n-button
                  type="primary"
                  :loading="savingSection === 'restart'"
                  @click="saveRestartSection"
                >
                  {{ $t('adminSettings.saveBtn') }}
                </n-button>
              </div>
            </n-card>

            <!-- Chat Sync Card -->
            <n-card size="small" class="settings-card" :title="$t('adminSettings.cardChat')">
              <n-form
                label-placement="left"
                :label-width="isMobile ? 'auto' : 210"
                :disabled="!canWrite"
                class="settings-form"
              >
                <n-form-item :label="$t('adminSettings.chatSyncEnabled')">
                  <n-switch v-model:value="form.chatSyncEnabled" />
                </n-form-item>
                <n-form-item :label="$t('adminSettings.webChatSendEnabled')">
                  <n-switch v-model:value="form.webChatSendEnabled" />
                </n-form-item>
                <n-form-item :label="$t('adminSettings.chatRetentionDays')">
                  <n-input-number v-model:value="form.chatRetentionDays" :min="1" :max="3650" style="width: 100%;" />
                </n-form-item>
              </n-form>
              <div v-if="canWrite" class="card-actions">
                <n-button type="primary" :loading="savingSection === 'chat'" @click="saveChatSection">
                  {{ $t('adminSettings.saveBtn') }}
                </n-button>
              </div>
            </n-card>
          </div>
        </n-tab-pane>

        <!-- Tab 4: Worlds & Databases -->
        <n-tab-pane name="worlds" :tab="$t('adminSettings.tabWorlds')">
          <n-card size="small" class="settings-card" :title="$t('adminSettings.tabWorlds')">
            <template #header-extra>
              <n-button size="small" secondary @click="loadWorldsOnly">
                {{ $t('common.refresh') }}
              </n-button>
            </template>
            <div class="table-scroll">
              <n-data-table
                size="small"
                :columns="worldColumns"
                :data="worlds"
                :pagination="false"
              />
            </div>
          </n-card>
        </n-tab-pane>
      </n-tabs>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import {
  adminApi,
  type AdminSettingsData,
  type RoleItem,
  type WorldOverviewItem
} from '../api/admin'
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
const savingSection = ref<string | null>(null)
const testingSmtp = ref(false)
const togglingWhitelist = ref(false)
const togglingGuestWhitelist = ref(false)
const uploadingFavicon = ref(false)
const resettingFavicon = ref(false)
const faviconInputRef = ref<HTMLInputElement | null>(null)

const roles = ref<RoleItem[]>([])
const worlds = ref<WorldOverviewItem[]>([])
const serverWhitelistEnabled = ref(false)
const whitelistAllowGuest = ref(true)
const hasSmtpPassword = ref(false)
const hasCustomFavicon = ref(false)
const faviconUrl = ref('/api/site/favicon?v=default')
const smtpPasswordInput = ref('')
const clearSmtpPassword = ref(false)

const form = reactive<Omit<AdminSettingsData, 'hasSmtpPassword' | 'hasCustomFavicon' | 'faviconVersion' | 'faviconUrl' | 'whitelistAllowGuest'>>({
  siteName: 'Utopia Server Panel',
  siteDescription: '',
  baseUrl: '',
  defaultLocale: 'zh-CN',
  allowRegistration: true,
  defaultRegisterRoleId: 3,
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
  monitorShowPlayerNames: true,
  mapEnabled: true,
  mapRenderMode: 'hybrid',
  mapMaxChunksPerTick: 2,
  mapCacheMaxMb: 512,
  mapVisTerrain: 'readonly',
  mapVisNetherCeiling: 'readonly',
  mapVisGrid: 'readonly',
  mapVisCoordinates: 'readonly',
  mapVisWaterDepth: 'readonly',
  mapVisHeightShading: 'readonly',
  mapVisBiomeTint: 'readonly',
  mapVisPlayers: 'readonly',
  mapVisWaypoints: 'readonly',
  mapVisMobsHostile: 'full',
  mapVisMobsPassive: 'full',
  mapVisEntities: 'full',
  mapVisStructures: 'full',
  mapVisTeleport: 'full',
  whitelistManagementEnabled: false,
  whitelistApplyCooldownSec: 60,
  whitelistMaxPendingPerIp: 5,
  whitelistSendEmail: false,
  whitelistSocialRequired: false,
  whitelistRequiredSocialPlatforms: [],
  serverRestartCommand: '',
  serverRestartHint: '',
  chatSyncEnabled: true,
  webChatSendEnabled: true,
  chatRetentionDays: 30
})

type MapVisField =
  | 'mapVisTerrain'
  | 'mapVisNetherCeiling'
  | 'mapVisGrid'
  | 'mapVisCoordinates'
  | 'mapVisWaterDepth'
  | 'mapVisHeightShading'
  | 'mapVisBiomeTint'
  | 'mapVisPlayers'
  | 'mapVisWaypoints'
  | 'mapVisMobsHostile'
  | 'mapVisMobsPassive'
  | 'mapVisEntities'
  | 'mapVisStructures'
  | 'mapVisTeleport'

const mapFeatureRows: { field: MapVisField; labelKey: string; helpKey: string }[] = [
  { field: 'mapVisTerrain', labelKey: 'adminSettings.mapVisTerrain', helpKey: 'adminSettings.mapVisTerrainHelp' },
  { field: 'mapVisNetherCeiling', labelKey: 'adminSettings.mapVisNetherCeiling', helpKey: 'adminSettings.mapVisNetherCeilingHelp' },
  { field: 'mapVisGrid', labelKey: 'adminSettings.mapVisGrid', helpKey: 'adminSettings.mapVisGridHelp' },
  { field: 'mapVisCoordinates', labelKey: 'adminSettings.mapVisCoordinates', helpKey: 'adminSettings.mapVisCoordinatesHelp' },
  { field: 'mapVisWaterDepth', labelKey: 'adminSettings.mapVisWaterDepth', helpKey: 'adminSettings.mapVisWaterDepthHelp' },
  { field: 'mapVisHeightShading', labelKey: 'adminSettings.mapVisHeightShading', helpKey: 'adminSettings.mapVisHeightShadingHelp' },
  { field: 'mapVisBiomeTint', labelKey: 'adminSettings.mapVisBiomeTint', helpKey: 'adminSettings.mapVisBiomeTintHelp' },
  { field: 'mapVisPlayers', labelKey: 'adminSettings.mapVisPlayers', helpKey: 'adminSettings.mapVisPlayersHelp' },
  { field: 'mapVisWaypoints', labelKey: 'adminSettings.mapVisWaypoints', helpKey: 'adminSettings.mapVisWaypointsHelp' },
  { field: 'mapVisMobsHostile', labelKey: 'adminSettings.mapVisMobsHostile', helpKey: 'adminSettings.mapVisMobsHostileHelp' },
  { field: 'mapVisMobsPassive', labelKey: 'adminSettings.mapVisMobsPassive', helpKey: 'adminSettings.mapVisMobsPassiveHelp' },
  { field: 'mapVisEntities', labelKey: 'adminSettings.mapVisEntities', helpKey: 'adminSettings.mapVisEntitiesHelp' },
  { field: 'mapVisStructures', labelKey: 'adminSettings.mapVisStructures', helpKey: 'adminSettings.mapVisStructuresHelp' },
  { field: 'mapVisTeleport', labelKey: 'adminSettings.mapVisTeleport', helpKey: 'adminSettings.mapVisTeleportHelp' }
]

const renderModeOptions = computed(() => [
  { label: t('adminSettings.renderHybrid'), value: 'hybrid' },
  { label: t('adminSettings.renderMcaOnly'), value: 'mca' }
])

function formatBytes(raw?: number | null): string {
  const bytes = Number(raw)
  if (!Number.isFinite(bytes) || bytes <= 0) return '0.0 KB'
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  return `${(bytes / 1024).toFixed(1)} KB`
}

const worldColumns = computed<DataTableColumns<WorldOverviewItem>>(() => [
  { title: t('adminSettings.worldName'), key: 'worldName', minWidth: 140 },
  {
    title: t('common.status'),
    key: 'active',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.active ? 'success' : 'default' },
        { default: () => (row.active ? t('adminSettings.worldActive') : t('adminSettings.worldInactive')) }
      )
  },
  { title: t('adminSettings.worldDbSize'), key: 'dbSizeBytes', render: (r) => formatBytes(r.dbSizeBytes) },
  { title: t('adminSettings.worldMapCache'), key: 'mapCacheBytes', render: (r) => formatBytes(r.mapCacheBytes) },
  { title: t('adminSettings.worldUsers'), key: 'userCount', width: 80 },
  { title: t('adminSettings.worldWaypoints'), key: 'waypointCount', width: 85 },
  { title: t('adminSettings.worldPendingWl'), key: 'whitelistPendingCount', width: 95 },
  { title: t('adminSettings.worldMessages'), key: 'messageCount', width: 85 }
])

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
  const {
    hasSmtpPassword: hasPwd,
    serverWhitelistEnabled: srvWl,
    whitelistAllowGuest: allowGuest,
    hasCustomFavicon: customFav,
    faviconVersion: _favVer,
    faviconUrl: favUrl,
    ...rest
  } = data
  Object.assign(form, rest)
  form.whitelistRequiredSocialPlatforms = Array.isArray(data.whitelistRequiredSocialPlatforms)
    ? [...data.whitelistRequiredSocialPlatforms]
    : []
  serverWhitelistEnabled.value = Boolean(srvWl)
  whitelistAllowGuest.value = allowGuest !== false
  hasSmtpPassword.value = Boolean(hasPwd)
  hasCustomFavicon.value = Boolean(customFav)
  faviconUrl.value = favUrl || '/api/site/favicon?v=default'
  smtpPasswordInput.value = ''
  clearSmtpPassword.value = false
}

async function loadAll() {
  loading.value = true
  try {
    const [settingsData, roleRes, worldsRes] = await Promise.all([
      adminApi.getSettings(),
      adminApi.listRoles(),
      adminApi.listWorlds().catch(() => ({ worlds: [] }))
    ])
    applyData(settingsData)
    roles.value = roleRes.roles || []
    worlds.value = worldsRes.worlds || []
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

async function loadWorldsOnly() {
  try {
    const res = await adminApi.listWorlds()
    worlds.value = res.worlds || []
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function savePayload(section: string, payload: Partial<AdminSettingsData> & { smtpPassword?: string; clearSmtpPassword?: boolean }) {
  savingSection.value = section
  try {
    const updated = await adminApi.updateSettings(payload)
    applyData(updated)
    await siteStore.refresh()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    savingSection.value = null
  }
}

function saveSiteSection() {
  return savePayload('site', {
    siteName: form.siteName,
    siteDescription: form.siteDescription,
    baseUrl: form.baseUrl,
    defaultLocale: form.defaultLocale
  })
}

function saveRegistrationSection() {
  return savePayload('registration', {
    allowRegistration: form.allowRegistration,
    defaultRegisterRoleId: form.defaultRegisterRoleId,
    requireEmailVerification: form.requireEmailVerification,
    requireBindingOnRegister: form.requireBindingOnRegister,
    forcePlayerBinding: form.forcePlayerBinding
  })
}

function saveSecuritySection() {
  return savePayload('security', {
    captchaPolicy: form.captchaPolicy,
    captchaDifficulty: form.captchaDifficulty,
    sessionTimeoutDays: form.sessionTimeoutDays,
    force2fa: form.force2fa,
    maxFailedLogins: form.maxFailedLogins,
    lockoutMinutes: form.lockoutMinutes
  })
}

function saveSmtpSection() {
  return savePayload('smtp', {
    smtpEnabled: form.smtpEnabled,
    smtpHost: form.smtpHost,
    smtpPort: form.smtpPort,
    smtpEncryption: form.smtpEncryption,
    smtpUser: form.smtpUser,
    smtpFrom: form.smtpFrom,
    smtpFromName: form.smtpFromName,
    smtpPassword: smtpPasswordInput.value || undefined,
    clearSmtpPassword: clearSmtpPassword.value
  })
}

function saveMonitorSection() {
  return savePayload('monitor', {
    monitorEnabled: form.monitorEnabled,
    monitorInterval: form.monitorInterval,
    monitorRetentionDays: form.monitorRetentionDays,
    monitorShowPlayerNames: form.monitorShowPlayerNames
  })
}

function saveMapSection() {
  return savePayload('map', {
    mapEnabled: form.mapEnabled,
    mapRenderMode: form.mapRenderMode,
    mapMaxChunksPerTick: form.mapMaxChunksPerTick,
    mapCacheMaxMb: form.mapCacheMaxMb,
    mapVisTerrain: form.mapVisTerrain,
    mapVisNetherCeiling: form.mapVisNetherCeiling,
    mapVisGrid: form.mapVisGrid,
    mapVisCoordinates: form.mapVisCoordinates,
    mapVisWaterDepth: form.mapVisWaterDepth,
    mapVisHeightShading: form.mapVisHeightShading,
    mapVisBiomeTint: form.mapVisBiomeTint,
    mapVisPlayers: form.mapVisPlayers,
    mapVisWaypoints: form.mapVisWaypoints,
    mapVisMobsHostile: form.mapVisMobsHostile,
    mapVisMobsPassive: form.mapVisMobsPassive,
    mapVisEntities: form.mapVisEntities,
    mapVisStructures: form.mapVisStructures,
    mapVisTeleport: form.mapVisTeleport
  })
}

async function handleServerWhitelistToggle(nextVal: boolean) {
  togglingWhitelist.value = true
  try {
    const res = await adminApi.toggleWhitelist(nextVal)
    serverWhitelistEnabled.value = Boolean(res.serverWhitelistEnabled)
    form.whitelistManagementEnabled = Boolean(res.whitelistManagementEnabled)
    await siteStore.refresh()
    message.success(t('common.saved'))
  } catch (err) {
    serverWhitelistEnabled.value = !nextVal
    message.error(getApiErrorMessage(err))
  } finally {
    togglingWhitelist.value = false
  }
}

async function handleWhitelistMgmtToggle(nextVal: boolean) {
  if (nextVal && !serverWhitelistEnabled.value) {
    await handleServerWhitelistToggle(true)
    form.whitelistManagementEnabled = true
  }
}

const isSmtpConfigured = computed(() => Boolean(form.smtpEnabled && String(form.smtpHost || '').trim()))

watch(isSmtpConfigured, (ok) => {
  if (!ok) {
    form.requireEmailVerification = false
    form.whitelistSendEmail = false
  }
})

const socialPlatformOptions = computed(() => [
  { label: t('socialPlatforms.discord'), value: 'discord' },
  { label: t('socialPlatforms.qq'), value: 'qq' },
  { label: t('socialPlatforms.phone'), value: 'phone' },
  { label: t('socialPlatforms.facebook'), value: 'facebook' },
  { label: t('socialPlatforms.x'), value: 'x' },
  { label: t('socialPlatforms.whatsapp'), value: 'whatsapp' },
  { label: t('socialPlatforms.wechat'), value: 'wechat' },
  { label: t('socialPlatforms.line'), value: 'line' },
  { label: t('socialPlatforms.tiktok'), value: 'tiktok' },
  { label: t('socialPlatforms.instagram'), value: 'instagram' }
])

async function handleGuestWhitelistToggle(nextVal: boolean) {
  togglingGuestWhitelist.value = true
  try {
    const res = await adminApi.toggleGuestWhitelist(nextVal)
    whitelistAllowGuest.value = Boolean(res.whitelistAllowGuest)
    await siteStore.refresh()
    message.success(t('common.saved'))
  } catch (err) {
    whitelistAllowGuest.value = !nextVal
    message.error(getApiErrorMessage(err))
  } finally {
    togglingGuestWhitelist.value = false
  }
}

function handleSocialRequiredToggle(nextVal: boolean) {
  if (!nextVal) {
    form.whitelistRequiredSocialPlatforms = []
  }
}

function saveWhitelistSection() {
  return savePayload('whitelist', {
    serverWhitelistEnabled: serverWhitelistEnabled.value,
    whitelistManagementEnabled: form.whitelistManagementEnabled,
    whitelistApplyCooldownSec: form.whitelistApplyCooldownSec,
    whitelistMaxPendingPerIp: form.whitelistMaxPendingPerIp,
    whitelistSendEmail: form.whitelistSendEmail,
    whitelistSocialRequired: form.whitelistSocialRequired,
    whitelistRequiredSocialPlatforms: form.whitelistSocialRequired
      ? form.whitelistRequiredSocialPlatforms
      : []
  })
}

function saveRestartSection() {
  return savePayload('restart', {
    serverRestartCommand: form.serverRestartCommand,
    serverRestartHint: form.serverRestartHint
  })
}

function saveChatSection() {
  return savePayload('chat', {
    chatSyncEnabled: form.chatSyncEnabled,
    webChatSendEnabled: form.webChatSendEnabled,
    chatRetentionDays: form.chatRetentionDays
  })
}

function triggerFaviconPicker() {
  faviconInputRef.value?.click()
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error(t('adminSettings.faviconInvalid')))
    reader.readAsDataURL(file)
  })
}

function normalizeFaviconImage(file: File, rawDataUrl: string): Promise<string> {
  const lowerType = (file.type || '').toLowerCase()
  if (lowerType === 'image/svg+xml' || lowerType === 'image/x-icon' || lowerType === 'image/vnd.microsoft.icon') {
    if (file.size > 240 * 1024) {
      return Promise.reject(new Error(t('adminSettings.faviconTooLarge')))
    }
    return Promise.resolve(rawDataUrl)
  }
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      if (file.size <= 128 * 1024 && img.width <= 128 && img.height <= 128 && lowerType === 'image/png') {
        resolve(rawDataUrl)
        return
      }
      const maxDim = 128
      const scale = Math.min(1, maxDim / Math.max(img.width || 1, img.height || 1))
      const w = Math.max(1, Math.round((img.width || 64) * scale))
      const h = Math.max(1, Math.round((img.height || 64) * scale))
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error(t('adminSettings.faviconInvalid')))
        return
      }
      ctx.imageSmoothingEnabled = w >= 64
      ctx.drawImage(img, 0, 0, w, h)
      resolve(canvas.toDataURL('image/png'))
    }
    img.onerror = () => reject(new Error(t('adminSettings.faviconInvalid')))
    img.src = rawDataUrl
  })
}

async function handleFaviconFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploadingFavicon.value = true
  try {
    const raw = await readFileAsDataUrl(file)
    const dataUrl = await normalizeFaviconImage(file, raw)
    const res = await adminApi.uploadFavicon(dataUrl)
    hasCustomFavicon.value = Boolean(res.hasCustomFavicon)
    faviconUrl.value = res.faviconUrl
    await siteStore.refresh()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(err instanceof Error ? err.message : getApiErrorMessage(err))
  } finally {
    uploadingFavicon.value = false
  }
}

async function handleResetFavicon() {
  resettingFavicon.value = true
  try {
    const res = await adminApi.resetFavicon()
    hasCustomFavicon.value = Boolean(res.hasCustomFavicon)
    faviconUrl.value = res.faviconUrl
    await siteStore.refresh()
    message.success(t('adminSettings.faviconResetSuccess'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    resettingFavicon.value = false
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
.cards-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.settings-card {
  border-radius: 12px;
}

.settings-form {
  max-width: 680px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--usp-border);
}

.lbl-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.switch-with-hint {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.field-hint {
  font-size: 12px;
  color: var(--usp-text-muted);
}

.table-scroll {
  overflow-x: auto;
}

.favicon-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.favicon-preview-box {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid var(--usp-border);
  background: var(--usp-surface-2, rgba(128, 128, 128, 0.08));
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.favicon-preview-img {
  width: 28px;
  height: 28px;
  object-fit: contain;
  image-rendering: pixelated;
}
</style>
