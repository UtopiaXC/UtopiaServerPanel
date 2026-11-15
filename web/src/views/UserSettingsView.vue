<template>
  <div>
    <n-tabs v-model:value="activeTab" type="line" animated>
      <!-- Profile Tab -->
      <n-tab-pane name="profile" :tab="$t('settings.tabProfile')">
        <n-card size="small" style="max-width: 720px; border-radius: 12px;">
          <n-form label-placement="left" :label-width="isMobile ? 'auto' : 120">
            <n-form-item :label="$t('settings.avatar')">
              <div style="display: flex; align-items: center; gap: 16px; flex-wrap: wrap;">
                <div
                  style="width: 68px; height: 68px; border-radius: 14px; overflow: hidden; border: 1px solid var(--usp-border); background: var(--usp-surface-soft); display: flex; align-items: center; justify-content: center; flex-shrink: 0;"
                >
                  <img
                    v-if="profileForm.avatarData"
                    :src="profileForm.avatarData"
                    alt="Avatar"
                    style="width: 100%; height: 100%; object-fit: cover; display: block;"
                  />
                  <McSkinCanvas
                    v-else-if="primaryBoundPlayer"
                    :uuid="primaryBoundPlayer.playerUuid"
                    :name="primaryBoundPlayer.playerName"
                    mode="head"
                    :scale="8"
                  />
                  <span
                    v-else
                    style="font-size: 24px; font-weight: 700; color: var(--usp-primary);"
                  >
                    {{ (authStore.user?.username || 'U').slice(0, 1).toUpperCase() }}
                  </span>
                </div>
                <div style="display: flex; flex-direction: column; gap: 8px;">
                  <n-space size="small">
                    <n-button size="small" secondary @click="triggerAvatarUpload">
                      {{ $t('settings.uploadAvatar') }}
                    </n-button>
                    <n-button
                      v-if="profileForm.avatarData"
                      size="small"
                      quaternary
                      @click="profileForm.avatarData = ''"
                    >
                      {{ $t('settings.resetAvatar') }}
                    </n-button>
                  </n-space>
                  <span style="font-size: 12px; color: var(--usp-text-muted);">
                    {{ $t('settings.avatarHint') }}
                  </span>
                  <input
                    ref="avatarInputRef"
                    type="file"
                    accept="image/png,image/jpeg,image/webp,image/gif"
                    style="display: none;"
                    @change="handleAvatarFileChange"
                  />
                </div>
              </div>
            </n-form-item>

            <n-form-item :label="$t('settings.username')">
              <div style="display: flex; align-items: center; gap: 10px; width: 100%;">
                <n-input :value="authStore.user?.username" disabled />
                <n-button size="small" secondary @click="showUsernameModal = true">
                  {{ $t('settings.changeUsername') }}
                </n-button>
              </div>
            </n-form-item>

            <n-form-item :label="$t('settings.nickname')">
              <n-input v-model:value="profileForm.nickname" clearable />
            </n-form-item>

            <n-form-item :label="$t('settings.email')">
              <n-input v-model:value="profileForm.email" clearable />
            </n-form-item>

            <n-form-item :label="$t('settings.gender')">
              <n-select v-model:value="profileForm.gender" :options="genderOptions" />
            </n-form-item>

            <n-form-item :label="$t('settings.birthday')">
              <n-date-picker
                v-model:formatted-value="profileForm.birthday"
                type="date"
                value-format="yyyy-MM-dd"
                clearable
                style="width: 100%;"
              />
            </n-form-item>

            <n-form-item :label="$t('settings.locale')">
              <n-select v-model:value="profileForm.locale" :options="localeOptions" />
            </n-form-item>

            <n-form-item :label="$t('settings.bio')">
              <n-input
                v-model:value="profileForm.bio"
                type="textarea"
                :rows="3"
                :maxlength="500"
                show-count
                :placeholder="$t('settings.bioPlaceholder')"
              />
            </n-form-item>

            <n-form-item :label="$t('settings.socialAccounts')">
              <div style="display: flex; flex-direction: column; gap: 8px; width: 100%;">
                <div
                  v-for="(entry, idx) in profileForm.socialAccounts"
                  :key="idx"
                  style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;"
                >
                  <n-tag size="small" :bordered="false" style="min-width: 84px; justify-content: center;">
                    {{ $t(`socialPlatforms.${entry.platform}`) }}
                  </n-tag>
                  <n-input
                    v-if="entry.platform === 'custom'"
                    v-model:value="entry.customPlatform"
                    :placeholder="$t('whitelist.customPlatformPlaceholder')"
                    style="width: 120px;"
                  />
                  <n-input
                    v-model:value="entry.account"
                    :placeholder="$t('settings.socialAccountPlaceholder')"
                    style="flex: 1; min-width: 160px;"
                  />
                  <n-button size="small" quaternary type="error" @click="removeSocialEntry(idx)">
                    {{ $t('common.delete') }}
                  </n-button>
                </div>

                <div>
                  <n-dropdown
                    trigger="click"
                    :options="socialPlatformOptions"
                    @select="addSocialEntry"
                  >
                    <n-button size="small" dashed>
                      + {{ $t('settings.addSocialAccount') }}
                    </n-button>
                  </n-dropdown>
                </div>
              </div>
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
                v-if="(!siteStore.force2fa && !authStore.user?.require2fa) || passkeys.length > 0"
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

          <!-- Passkeys (WebAuthn) -->
          <n-card size="small" style="border-radius: 12px;">
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span>{{ $t('settings.passkeysSection') }}</span>
                <FieldHelp :text="$t('settings.passkeysHelp')" />
                <n-tag
                  size="small"
                  :bordered="false"
                  :type="passkeys.length > 0 ? 'success' : 'default'"
                >
                  {{ passkeys.length > 0 ? passkeys.length : $t('common.disabled') }}
                </n-tag>
              </div>
            </template>

            <n-alert v-if="!webAuthnSupported" type="warning" :bordered="false">
              {{ $t('settings.passkeyUnsupported') }}
            </n-alert>

            <div v-else style="display: flex; flex-direction: column; gap: 12px;">
              <div style="display: flex; gap: 8px; flex-wrap: wrap; max-width: 480px;">
                <n-input
                  v-model:value="newPasskeyName"
                  :placeholder="$t('settings.passkeyNamePlaceholder')"
                  style="flex: 1; min-width: 180px;"
                />
                <n-button type="primary" :loading="passkeyLoading" @click="handleAddPasskey">
                  {{ $t('settings.addPasskeyBtn') }}
                </n-button>
              </div>

              <div v-if="passkeys.length === 0" style="font-size: 13px; color: var(--usp-text-muted);">
                {{ $t('common.noData') }}
              </div>

              <div v-else class="table-responsive">
                <n-table :bordered="false" :single-line="false" size="small">
                  <thead>
                    <tr>
                      <th>{{ $t('settings.passkeyName') }}</th>
                      <th>{{ $t('settings.createdAt') }}</th>
                      <th>{{ $t('settings.lastUsedAt') }}</th>
                      <th style="text-align: right;">{{ $t('common.actions') }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="pk in passkeys" :key="pk.id">
                      <td>
                        <div v-if="editingPasskeyId === pk.id" style="display: flex; gap: 6px; align-items: center;">
                          <n-input v-model:value="editingPasskeyName" size="tiny" style="max-width: 180px;" />
                          <n-button size="tiny" type="primary" :loading="passkeyLoading" @click="confirmRenamePasskey(pk.id)">
                            {{ $t('common.save') }}
                          </n-button>
                          <n-button size="tiny" quaternary @click="editingPasskeyId = null">
                            {{ $t('common.cancel') }}
                          </n-button>
                        </div>
                        <span v-else style="font-weight: 500;">{{ pk.name }}</span>
                      </td>
                      <td class="mono" style="font-size: 12px; white-space: nowrap;">
                        {{ formatTimestamp(pk.createdAt) }}
                      </td>
                      <td class="mono" style="font-size: 12px; white-space: nowrap;">
                        {{ pk.lastUsedAt ? formatTimestamp(pk.lastUsedAt) : '-' }}
                      </td>
                      <td style="text-align: right; white-space: nowrap;">
                        <n-space size="small" justify="end">
                          <n-button size="tiny" quaternary @click="startRenamePasskey(pk)">
                            {{ $t('settings.renameBtn') }}
                          </n-button>
                          <n-popconfirm @positive-click="handleDeletePasskey(pk.id)">
                            <template #trigger>
                              <n-button size="tiny" quaternary type="error">
                                {{ $t('common.delete') }}
                              </n-button>
                            </template>
                            {{ $t('settings.confirmDeletePasskey') }}
                          </n-popconfirm>
                        </n-space>
                      </td>
                    </tr>
                  </tbody>
                </n-table>
              </div>
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
        <div style="display: flex; flex-direction: column; gap: 16px; max-width: 760px;">
          <n-card size="small" :title="$t('settings.bindingSection')" style="border-radius: 12px;">
            <div v-if="boundPlayers.length === 0" style="font-size: 13px; color: var(--usp-text-muted); margin-bottom: 12px;">
              {{ $t('home.unbound') }}
            </div>

            <div v-else class="table-responsive" style="margin-bottom: 14px;">
              <n-table :bordered="false" :single-line="false" size="small">
                <thead>
                  <tr>
                    <th>{{ $t('home.boundPlayer') }}</th>
                    <th>{{ $t('settings.playerUuid') }}</th>
                    <th>{{ $t('settings.boundAt') }}</th>
                    <th style="text-align: right;">{{ $t('common.actions') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="bp in boundPlayers" :key="bp.playerUuid">
                    <td>
                      <div style="display: flex; align-items: center; gap: 6px;">
                        <strong>{{ bp.playerName }}</strong>
                        <n-tag v-if="bp.primary" size="tiny" type="success" :bordered="false">
                          {{ $t('settings.primaryPlayer') }}
                        </n-tag>
                      </div>
                    </td>
                    <td class="mono" style="font-size: 12px;">{{ bp.playerUuid }}</td>
                    <td class="mono" style="font-size: 12px; white-space: nowrap;">
                      {{ formatTimestamp(bp.boundAt) }}
                    </td>
                    <td style="text-align: right; white-space: nowrap;">
                      <n-space size="small" justify="end">
                        <n-button
                          v-if="!bp.primary"
                          size="tiny"
                          secondary
                          :loading="bindingLoading"
                          @click="handleSetPrimary(bp.playerUuid)"
                        >
                          {{ $t('settings.setPrimaryBtn') }}
                        </n-button>
                        <n-popconfirm @positive-click="handleUnbind(bp.playerUuid)">
                          <template #trigger>
                            <n-button size="tiny" type="error" secondary :loading="bindingLoading">
                              {{ $t('settings.unbindBtn') }}
                            </n-button>
                          </template>
                          {{ $t('settings.confirmUnbind') }}
                        </n-popconfirm>
                      </n-space>
                    </td>
                  </tr>
                </tbody>
              </n-table>
            </div>

            <n-divider v-if="boundPlayers.length > 0" style="margin: 12px 0;" />

            <n-form @submit.prevent="handleBind" style="max-width: 440px;">
              <n-form-item>
                <template #label>
                  <span style="display: inline-flex; align-items: center; gap: 6px;">
                    {{ boundPlayers.length > 0 ? $t('settings.bindAnotherCode') : $t('settings.bindingCode') }}
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
        </div>
      </n-tab-pane>

      <!-- Public Player Homepage Tab -->
      <n-tab-pane name="publicProfile" :tab="$t('settings.tabPublicProfile')">
        <div style="display: flex; flex-direction: column; gap: 16px; max-width: 820px;">
          <n-card size="small" style="border-radius: 12px;">
            <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;">
              <div style="display: flex; flex-direction: column; gap: 4px; min-width: 0;">
                <div style="font-weight: 600; font-size: 15px;">
                  {{ $t('settings.publicProfileUrlTitle') }}
                </div>
                <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                  <code class="mono" style="font-size: 13px; color: var(--usp-primary); word-break: break-all;">
                    {{ publicProfileFullUrl }}
                  </code>
                </div>
              </div>
              <n-space size="small">
                <n-button size="small" secondary @click="copyPublicProfileUrl">
                  {{ $t('settings.copyPublicUrl') }}
                </n-button>
                <n-button size="small" type="primary" @click="openPublicProfilePage">
                  {{ $t('settings.visitPublicProfile') }}
                </n-button>
              </n-space>
            </div>

            <n-divider style="margin: 14px 0;" />

            <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px;">
              <div>
                <div style="font-weight: 500;">{{ $t('settings.enablePublicProfile') }}</div>
                <div style="font-size: 12px; color: var(--usp-text-muted);">
                  {{ $t('settings.enablePublicProfileDesc', { username: authStore.user?.username || 'username' }) }}
                </div>
              </div>
              <n-switch v-model:value="privacyForm.publicProfileEnabled" />
            </div>
          </n-card>

          <n-card size="small" :title="$t('settings.privacySectionPanel')" style="border-radius: 12px;">
            <n-table :bordered="false" :single-line="false" size="small">
              <thead>
                <tr>
                  <th>{{ $t('settings.privacyFieldCol') }}</th>
                  <th style="width: 110px; text-align: right;">{{ $t('settings.privacyVisibilityCol') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in panelPrivacyItems" :key="item.key">
                  <td>
                    <div style="font-weight: 500;">{{ item.label }}</div>
                    <div style="font-size: 12px; color: var(--usp-text-muted);">{{ item.desc }}</div>
                  </td>
                  <td style="text-align: right;">
                    <n-switch v-model:value="privacyForm[item.key]" />
                  </td>
                </tr>
              </tbody>
            </n-table>
          </n-card>

          <n-card size="small" :title="$t('settings.privacySectionMc')" style="border-radius: 12px;">
            <n-table :bordered="false" :single-line="false" size="small">
              <thead>
                <tr>
                  <th>{{ $t('settings.privacyFieldCol') }}</th>
                  <th style="width: 110px; text-align: right;">{{ $t('settings.privacyVisibilityCol') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in mcPrivacyItems" :key="item.key">
                  <td :style="{ opacity: item.key !== 'showMcCharacters' && !privacyForm.showMcCharacters ? 0.55 : 1 }">
                    <div style="font-weight: 500;">{{ item.label }}</div>
                    <div style="font-size: 12px; color: var(--usp-text-muted);">{{ item.desc }}</div>
                  </td>
                  <td style="text-align: right;">
                    <n-switch
                      v-model:value="privacyForm[item.key]"
                      :disabled="item.key !== 'showMcCharacters' && !privacyForm.showMcCharacters"
                    />
                  </td>
                </tr>
              </tbody>
            </n-table>

            <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 14px;">
              <n-button size="small" secondary @click="resetPrivacyDefaults">
                {{ $t('settings.resetPrivacyDefaults') }}
              </n-button>
              <n-button type="primary" :loading="savingPrivacy" @click="savePrivacy">
                {{ $t('common.save') }}
              </n-button>
            </div>
          </n-card>
        </div>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import QRCode from 'qrcode'
import FieldHelp from '../components/FieldHelp.vue'
import McSkinCanvas from '../components/McSkinCanvas.vue'
import {
  profileApi,
  type PasskeyItem,
  type ProfilePrivacyConfig,
  type SocialAccountItem,
  type UserSessionItem
} from '../api/profile'
import { bindingApi } from '../api/binding'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useSiteStore } from '../stores/site'
import { setAppLocale } from '../i18n'
import { formatTimestamp } from '../utils/format'
import { validateUsernameErrorKey } from '../utils/username'
import { createPasskeyCredential, isWebAuthnSupported } from '../utils/webauthn'

const route = useRoute()
const router = useRouter()
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
const genderOptions = computed(() => [
  { label: t('settings.genderUnspecified'), value: 'unspecified' },
  { label: t('settings.genderMale'), value: 'male' },
  { label: t('settings.genderFemale'), value: 'female' },
  { label: t('settings.genderOther'), value: 'other' },
  { label: t('settings.genderPreferNotToSay'), value: 'prefer_not_to_say' }
])
const SOCIAL_PLATFORMS = [
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
]
const socialPlatformOptions = computed(() =>
  SOCIAL_PLATFORMS.map((key) => ({
    label: t(`socialPlatforms.${key}`),
    key
  }))
)

const avatarInputRef = ref<HTMLInputElement | null>(null)
const savingProfile = ref(false)
const profileForm = reactive<{
  nickname: string
  email: string
  locale: string
  gender: string
  birthday: string | null
  bio: string
  avatarData: string
  socialAccounts: SocialAccountItem[]
}>({
  nickname: authStore.user?.nickname || '',
  email: authStore.user?.email || '',
  locale: authStore.user?.locale || 'zh-CN',
  gender: authStore.user?.gender || 'unspecified',
  birthday: authStore.user?.birthday || null,
  bio: authStore.user?.bio || '',
  avatarData: authStore.user?.avatarData || '',
  socialAccounts: Array.isArray(authStore.user?.socialAccounts)
    ? authStore.user.socialAccounts.map((s) => ({ ...s }))
    : []
})

const savingPrivacy = ref(false)
const privacyForm = reactive<ProfilePrivacyConfig>({
  publicProfileEnabled: true,
  showUsername: true,
  showAvatar: true,
  showNickname: false,
  showBio: false,
  showEmail: false,
  showGender: false,
  showBirthday: false,
  showSocialAccounts: false,
  showRole: false,
  showRegisteredAt: false,
  showMcCharacters: false,
  showMcOnlineStatus: false,
  showMcPlaytime: false,
  showMcAchievements: false,
  showMcStats: false
})

type PrivacyToggleKey = Exclude<keyof ProfilePrivacyConfig, 'publicProfileEnabled'>

const panelPrivacyItems = computed<Array<{ key: PrivacyToggleKey; label: string; desc: string }>>(() => [
  { key: 'showUsername', label: t('settings.username'), desc: t('settings.privacyDescUsername') },
  { key: 'showAvatar', label: t('settings.avatar'), desc: t('settings.privacyDescAvatar') },
  { key: 'showNickname', label: t('settings.nickname'), desc: t('settings.privacyDescNickname') },
  { key: 'showBio', label: t('settings.bio'), desc: t('settings.privacyDescBio') },
  { key: 'showEmail', label: t('settings.email'), desc: t('settings.privacyDescEmail') },
  { key: 'showGender', label: t('settings.gender'), desc: t('settings.privacyDescGender') },
  { key: 'showBirthday', label: t('settings.birthday'), desc: t('settings.privacyDescBirthday') },
  { key: 'showSocialAccounts', label: t('settings.socialAccounts'), desc: t('settings.privacyDescSocial') },
  { key: 'showRole', label: t('settings.privacyFieldRole'), desc: t('settings.privacyDescRole') },
  { key: 'showRegisteredAt', label: t('settings.privacyFieldRegisteredAt'), desc: t('settings.privacyDescRegisteredAt') }
])

const mcPrivacyItems = computed<Array<{ key: PrivacyToggleKey; label: string; desc: string }>>(() => [
  { key: 'showMcCharacters', label: t('settings.privacyFieldMcChars'), desc: t('settings.privacyDescMcChars') },
  { key: 'showMcOnlineStatus', label: t('settings.privacyFieldMcOnline'), desc: t('settings.privacyDescMcOnline') },
  { key: 'showMcPlaytime', label: t('settings.privacyFieldMcPlaytime'), desc: t('settings.privacyDescMcPlaytime') },
  { key: 'showMcAchievements', label: t('settings.privacyFieldMcAchievements'), desc: t('settings.privacyDescMcAchievements') },
  { key: 'showMcStats', label: t('settings.privacyFieldMcStats'), desc: t('settings.privacyDescMcStats') }
])

const publicProfilePath = computed(() => `/${authStore.user?.username || ''}`)
const publicProfileFullUrl = computed(() =>
  typeof window !== 'undefined'
    ? `${window.location.origin}${publicProfilePath.value}`
    : publicProfilePath.value
)

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

const webAuthnSupported = isWebAuthnSupported()
const passkeys = ref<PasskeyItem[]>([])
const passkeyLoading = ref(false)
const newPasskeyName = ref('')
const editingPasskeyId = ref<number | null>(null)
const editingPasskeyName = ref('')

const boundPlayers = computed(() => {
  const list = authStore.user?.boundPlayers
  if (Array.isArray(list) && list.length > 0) return list
  if (authStore.user?.bound && authStore.user?.playerUuid && authStore.user?.playerName) {
    return [
      {
        playerUuid: authStore.user.playerUuid,
        playerName: authStore.user.playerName,
        primary: true,
        boundAt: authStore.user.boundAt || 0
      }
    ]
  }
  return []
})

const primaryBoundPlayer = computed(() => {
  const list = boundPlayers.value
  return list.find((p) => p.primary) || list[0] || null
})

function triggerAvatarUpload() {
  avatarInputRef.value?.click()
}

function handleAvatarFileChange(e: Event) {
  const input = e.target as HTMLInputElement | null
  const file = input?.files?.[0]
  if (input) input.value = ''
  if (!file) return
  if (file.size > 4 * 1024 * 1024) {
    message.warning(t('settings.avatarTooLarge'))
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    const img = new Image()
    img.onload = () => {
      try {
        const size = 256
        const canvas = document.createElement('canvas')
        canvas.width = size
        canvas.height = size
        const ctx = canvas.getContext('2d')
        if (!ctx) return
        const minSide = Math.min(img.width, img.height)
        const sx = Math.floor((img.width - minSide) / 2)
        const sy = Math.floor((img.height - minSide) / 2)
        ctx.drawImage(img, sx, sy, minSide, minSide, 0, 0, size, size)
        let dataUrl = canvas.toDataURL('image/webp', 0.88)
        if (!dataUrl.startsWith('data:image/webp')) {
          dataUrl = canvas.toDataURL('image/png')
        }
        profileForm.avatarData = dataUrl
      } catch {
        message.error(t('settings.avatarProcessFailed'))
      }
    }
    img.onerror = () => message.error(t('settings.avatarProcessFailed'))
    if (typeof reader.result === 'string') {
      img.src = reader.result
    }
  }
  reader.readAsDataURL(file)
}

function addSocialEntry(platform: string) {
  if (profileForm.socialAccounts.length >= 15) return
  profileForm.socialAccounts.push({
    platform,
    customPlatform: platform === 'custom' ? '' : undefined,
    account: ''
  })
}

function removeSocialEntry(index: number) {
  profileForm.socialAccounts.splice(index, 1)
}

function applyUserToForms() {
  const u = authStore.user
  if (!u) return
  profileForm.nickname = u.nickname || ''
  profileForm.email = u.email || ''
  profileForm.locale = u.locale || 'zh-CN'
  profileForm.gender = u.gender || 'unspecified'
  profileForm.birthday = u.birthday || null
  profileForm.bio = u.bio || ''
  profileForm.avatarData = u.avatarData || ''
  profileForm.socialAccounts = Array.isArray(u.socialAccounts)
    ? u.socialAccounts.map((s) => ({ ...s }))
    : []

  if (u.privacy) {
    Object.assign(privacyForm, u.privacy)
  }
}

async function saveProfile() {
  savingProfile.value = true
  try {
    await profileApi.updateProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      locale: profileForm.locale,
      gender: profileForm.gender,
      birthday: profileForm.birthday || '',
      bio: profileForm.bio,
      avatarData: profileForm.avatarData,
      socialAccounts: profileForm.socialAccounts.filter((s) => s.account.trim())
    })
    setAppLocale(profileForm.locale)
    await authStore.refreshMe()
    applyUserToForms()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    savingProfile.value = false
  }
}

async function savePrivacy() {
  savingPrivacy.value = true
  try {
    await profileApi.updatePublicProfilePrivacy({ ...privacyForm })
    await authStore.refreshMe()
    applyUserToForms()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    savingPrivacy.value = false
  }
}

function resetPrivacyDefaults() {
  privacyForm.publicProfileEnabled = true
  privacyForm.showUsername = true
  privacyForm.showAvatar = true
  privacyForm.showNickname = false
  privacyForm.showBio = false
  privacyForm.showEmail = false
  privacyForm.showGender = false
  privacyForm.showBirthday = false
  privacyForm.showSocialAccounts = false
  privacyForm.showRole = false
  privacyForm.showRegisteredAt = false
  privacyForm.showMcCharacters = false
  privacyForm.showMcOnlineStatus = false
  privacyForm.showMcPlaytime = false
  privacyForm.showMcAchievements = false
  privacyForm.showMcStats = false
}

function openPublicProfilePage() {
  if (!authStore.user?.username) return
  router.push(publicProfilePath.value)
}

async function copyPublicProfileUrl() {
  try {
    await navigator.clipboard.writeText(publicProfileFullUrl.value)
    message.success(t('common.copied'))
  } catch {
    message.info(publicProfileFullUrl.value)
  }
}

async function handleChangeUsername() {
  const errKey = validateUsernameErrorKey(usernameForm.newUsername)
  if (errKey) {
    message.warning(t(`errors.${errKey}`))
    return
  }
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

async function loadPasskeys() {
  try {
    const res = await profileApi.listPasskeys()
    passkeys.value = res.passkeys || []
  } catch {
    // ignore
  }
}

async function handleAddPasskey() {
  if (!webAuthnSupported) {
    message.warning(t('settings.passkeyUnsupported'))
    return
  }
  passkeyLoading.value = true
  try {
    const opts = await profileApi.beginPasskeyRegister()
    const credential = await createPasskeyCredential(opts.publicKey)
    await profileApi.finishPasskeyRegister({
      challengeId: opts.challengeId,
      name: newPasskeyName.value.trim() || undefined,
      credential
    })
    newPasskeyName.value = ''
    await Promise.all([loadPasskeys(), authStore.refreshMe()])
    message.success(t('common.saved'))
  } catch (err: any) {
    if (err?.name === 'NotAllowedError' || err?.name === 'AbortError') {
      message.warning(t('settings.passkeyCancelled'))
    } else {
      message.error(err instanceof Error && !(err as any)?.response ? err.message : getApiErrorMessage(err))
    }
  } finally {
    passkeyLoading.value = false
  }
}

function startRenamePasskey(pk: PasskeyItem) {
  editingPasskeyId.value = pk.id
  editingPasskeyName.value = pk.name
}

async function confirmRenamePasskey(id: number) {
  const name = editingPasskeyName.value.trim()
  if (!name) return
  passkeyLoading.value = true
  try {
    await profileApi.renamePasskey(id, name)
    editingPasskeyId.value = null
    await loadPasskeys()
    message.success(t('common.updated'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    passkeyLoading.value = false
  }
}

async function handleDeletePasskey(id: number) {
  passkeyLoading.value = true
  try {
    await profileApi.deletePasskey(id)
    await Promise.all([loadPasskeys(), authStore.refreshMe()])
    message.success(t('common.deleted'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    passkeyLoading.value = false
  }
}

async function handleSetPrimary(playerUuid: string) {
  bindingLoading.value = true
  try {
    await bindingApi.setPrimary(playerUuid)
    await authStore.refreshMe()
    message.success(t('common.updated'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    bindingLoading.value = false
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

async function handleUnbind(playerUuid?: string) {
  bindingLoading.value = true
  try {
    await bindingApi.unbind(playerUuid ? { playerUuid } : undefined)
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
    if (tab === 'profile' || tab === 'security' || tab === 'binding' || tab === 'publicProfile') {
      activeTab.value = tab
    }
  },
  { immediate: true }
)

onMounted(async () => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
  await authStore.refreshMe()
  applyUserToForms()
  await Promise.all([loadSessions(), loadPasskeys()])
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobile)
})
</script>

