<template>
  <div class="profile-page-wrap">
    <div v-if="loading" style="display: flex; justify-content: center; padding: 64px 0;">
      <n-spin size="large" />
    </div>

    <n-card v-else-if="notFound" style="max-width: 560px; margin: 40px auto; border-radius: 14px;">
      <n-result
        status="404"
        :title="$t('playerProfile.notFoundTitle')"
        :description="$t('playerProfile.notFoundDesc')"
      >
        <template #footer>
          <n-button type="primary" @click="router.push('/')">
            {{ $t('common.backHome') }}
          </n-button>
        </template>
      </n-result>
    </n-card>

    <n-card
      v-else-if="profile && !profile.publicProfileEnabled && !profile.isOwner"
      style="max-width: 560px; margin: 40px auto; border-radius: 14px;"
    >
      <n-result
        status="403"
        :title="$t('playerProfile.privateTitle')"
        :description="$t('playerProfile.privateDesc')"
      >
        <template #footer>
          <n-button type="primary" @click="router.push('/')">
            {{ $t('common.backHome') }}
          </n-button>
        </template>
      </n-result>
    </n-card>

    <div v-else-if="profile" style="display: flex; flex-direction: column; gap: 18px;">
      <!-- Owner quick actions & preview banner -->
      <n-alert
        v-if="profile.isOwner"
        :type="profile.publicProfileEnabled ? 'info' : 'warning'"
        style="border-radius: 12px;"
      >
        <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;">
          <span>
            {{
              profile.publicProfileEnabled
                ? $t('playerProfile.ownerBannerPublic')
                : $t('playerProfile.ownerBannerHidden')
            }}
          </span>
          <n-space size="small">
            <n-button size="tiny" secondary @click="router.push('/settings?tab=profile')">
              {{ $t('playerProfile.editProfileBtn') }}
            </n-button>
            <n-button size="tiny" type="primary" @click="router.push('/settings?tab=publicProfile')">
              {{ $t('playerProfile.privacySettingsBtn') }}
            </n-button>
          </n-space>
        </div>
      </n-alert>

      <!-- Hero Card: Panel User Info -->
      <n-card style="border-radius: 14px;">
        <div class="hero-flex">
          <div
            v-if="showAvatarBlock"
            class="avatar-box"
          >
            <img
              v-if="profile.avatarData"
              :src="profile.avatarData"
              alt="Avatar"
              style="width: 100%; height: 100%; object-fit: cover; display: block;"
            />
            <McSkinCanvas
              v-else-if="activeChar"
              :uuid="activeChar.playerUuid"
              :name="activeChar.playerName"
              mode="head"
              :scale="10"
            />
            <span v-else class="avatar-fallback-letter">
              {{ avatarInitial }}
            </span>
          </div>

          <div style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px;">
            <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap;">
              <h2 style="margin: 0; font-size: 22px; font-weight: 700;">
                {{ heroDisplayName }}
              </h2>
              <span
                v-if="profile.username && profile.nickname"
                class="mono"
                style="font-size: 13px; color: var(--usp-text-muted);"
              >
                @{{ profile.username }}
              </span>
              <n-tag
                v-if="profile.roleDisplayName || profile.roleName"
                size="small"
                :bordered="false"
                type="info"
              >
                {{ profile.roleDisplayName || profile.roleName }}
              </n-tag>
              <n-tag
                v-if="activeChar?.onlineStatus"
                size="small"
                :bordered="false"
                :type="activeChar.onlineStatus.online ? 'success' : 'default'"
              >
                {{ activeChar.onlineStatus.online ? $t('home.playerOnline') : $t('home.playerOffline') }}
              </n-tag>
            </div>

            <p
              v-if="profile.bio !== undefined"
              style="margin: 0; font-size: 14px; color: var(--usp-text); line-height: 1.55; white-space: pre-wrap;"
            >
              {{ profile.bio || $t('playerProfile.noBio') }}
            </p>

            <!-- Structured Metadata Chips -->
            <div v-if="hasMetadataItems" class="meta-chips">
              <div v-if="profile.gender !== undefined" class="meta-chip">
                <span class="meta-label">{{ $t('settings.gender') }}:</span>
                <span>{{ formatGender(profile.gender) }}</span>
              </div>
              <div v-if="profile.birthday !== undefined && profile.birthday" class="meta-chip">
                <span class="meta-label">{{ $t('settings.birthday') }}:</span>
                <span class="mono">{{ profile.birthday }}</span>
              </div>
              <div v-if="profile.email" class="meta-chip">
                <span class="meta-label">{{ $t('settings.email') }}:</span>
                <span>{{ profile.email }}</span>
              </div>
              <div v-if="profile.createdAt" class="meta-chip">
                <span class="meta-label">{{ $t('playerProfile.joinedAt') }}:</span>
                <span class="mono">{{ formatTimestamp(profile.createdAt) }}</span>
              </div>
            </div>

            <!-- Social Accounts -->
            <div
              v-if="profile.socialAccounts && profile.socialAccounts.length > 0"
              style="display: flex; flex-wrap: wrap; gap: 8px; margin-top: 2px;"
            >
              <n-tag
                v-for="(item, idx) in profile.socialAccounts"
                :key="idx"
                size="small"
                round
                :bordered="true"
                style="cursor: pointer;"
                @click="copyText(item.account)"
              >
                <strong>{{ formatSocialPlatform(item) }}:</strong>&nbsp;{{ item.account }}
              </n-tag>
            </div>
          </div>
        </div>
      </n-card>

      <!-- Bound Minecraft Characters -->
      <template v-if="profile.mcCharacters !== undefined">
        <n-card
          v-if="profile.mcCharacters.length === 0"
          style="border-radius: 14px;"
          :title="$t('playerProfile.mcSectionTitle')"
        >
          <n-empty :description="$t('playerProfile.noBoundCharacters')" />
        </n-card>

        <template v-else-if="activeChar">
          <!-- Multi-character switcher -->
          <div
            v-if="profile.mcCharacters.length > 1"
            style="display: flex; flex-wrap: wrap; gap: 8px; align-items: center;"
          >
            <span style="font-size: 13px; color: var(--usp-text-muted); margin-right: 4px;">
              {{ $t('playerProfile.boundCharactersCount', { count: profile.mcCharacters.length }) }}:
            </span>
            <n-button
              v-for="ch in profile.mcCharacters"
              :key="ch.playerUuid"
              size="small"
              :type="ch.playerUuid === activeChar.playerUuid ? 'primary' : 'default'"
              :secondary="ch.playerUuid !== activeChar.playerUuid"
              @click="selectedCharUuid = ch.playerUuid"
            >
              <div style="display: flex; align-items: center; gap: 6px;">
                <McSkinCanvas :uuid="ch.playerUuid" :name="ch.playerName" mode="head" :scale="2" />
                <span>{{ ch.playerName }}</span>
                <n-tag v-if="ch.primary" size="tiny" :bordered="false" type="success">
                  {{ $t('settings.primaryPlayer') }}
                </n-tag>
              </div>
            </n-button>
          </div>

          <div class="mc-main-grid">
            <!-- Character Skin & Live Status Card -->
            <n-card style="border-radius: 14px;" :title="$t('playerProfile.characterCardTitle')">
              <div style="display: flex; flex-direction: column; align-items: center; gap: 12px;">
                <div class="skin-stage">
                  <McSkinCanvas
                    :uuid="activeChar.playerUuid"
                    :name="activeChar.playerName"
                    mode="full"
                    :scale="7"
                  />
                </div>

                <div style="text-align: center; width: 100%;">
                  <div style="font-size: 18px; font-weight: 700; display: flex; justify-content: center; align-items: center; gap: 6px;">
                    <span>{{ activeChar.playerName }}</span>
                    <n-tag v-if="activeChar.primary" size="tiny" type="success" :bordered="false">
                      {{ $t('settings.primaryPlayer') }}
                    </n-tag>
                  </div>
                  <div
                    class="mono"
                    style="font-size: 11px; color: var(--usp-text-muted); margin-top: 4px; cursor: pointer; word-break: break-all;"
                    @click="copyText(activeChar.playerUuid)"
                  >
                    {{ activeChar.playerUuid }}
                  </div>
                </div>

                <n-descriptions :column="1" size="small" bordered style="width: 100%;">
                  <n-descriptions-item :label="$t('settings.boundAt')">
                    <span class="mono">{{ formatTimestamp(activeChar.boundAt) }}</span>
                  </n-descriptions-item>

                  <n-descriptions-item
                    v-if="activeChar.playTimeSeconds !== undefined"
                    :label="$t('playerProfile.playtime')"
                  >
                    <strong>{{ formatDurationSeconds(activeChar.playTimeSeconds) }}</strong>
                  </n-descriptions-item>

                  <template v-if="activeChar.onlineStatus">
                    <n-descriptions-item :label="$t('common.status')">
                      <n-tag
                        size="small"
                        :bordered="false"
                        :type="activeChar.onlineStatus.online ? 'success' : 'default'"
                      >
                        {{ activeChar.onlineStatus.online ? $t('home.playerOnline') : $t('home.playerOffline') }}
                      </n-tag>
                    </n-descriptions-item>
                    <n-descriptions-item
                      v-if="activeChar.onlineStatus.dimension"
                      :label="$t('home.dimension')"
                    >
                      <span class="mono">{{ formatDimension(activeChar.onlineStatus.dimension) }}</span>
                    </n-descriptions-item>
                    <n-descriptions-item
                      v-if="activeChar.onlineStatus.gamemode"
                      :label="$t('home.gamemode')"
                    >
                      <n-tag size="small" :bordered="false">{{ activeChar.onlineStatus.gamemode }}</n-tag>
                    </n-descriptions-item>
                    <n-descriptions-item
                      v-if="activeChar.onlineStatus.health !== null && activeChar.onlineStatus.health !== undefined"
                      :label="$t('home.health')"
                    >
                      <span class="mono">
                        {{ activeChar.onlineStatus.health.toFixed(1) }}
                        <template v-if="activeChar.onlineStatus.maxHealth">
                          / {{ activeChar.onlineStatus.maxHealth.toFixed(1) }}
                        </template>
                      </span>
                    </n-descriptions-item>
                  </template>
                </n-descriptions>
              </div>
            </n-card>

            <!-- Statistics & Achievements Right Panel -->
            <div style="display: flex; flex-direction: column; gap: 16px; min-width: 0;">
              <!-- Statistics Section -->
              <n-card
                v-if="activeChar.stats"
                style="border-radius: 14px;"
                :title="$t('playerProfile.statsTitle')"
              >
                <n-empty
                  v-if="!activeChar.stats.available"
                  :description="$t('playerProfile.statsUnavailable')"
                />

                <template v-else>
                  <!-- Highlight KPIs -->
                  <div class="stat-kpi-grid">
                    <div class="stat-kpi-card">
                      <div class="stat-kpi-label">{{ $t('playerProfile.playtime') }}</div>
                      <div class="stat-kpi-val">
                        {{ formatDurationSeconds(statSummary('playTimeSeconds')) }}
                      </div>
                    </div>
                    <div class="stat-kpi-card">
                      <div class="stat-kpi-label">{{ $t('playerProfile.statDeaths') }}</div>
                      <div class="stat-kpi-val">{{ formatNumber(effectiveDeaths) }}</div>
                    </div>
                    <div class="stat-kpi-card">
                      <div class="stat-kpi-label">{{ $t('playerProfile.statJumps') }}</div>
                      <div class="stat-kpi-val">{{ formatNumber(statSummary('jumps')) }}</div>
                    </div>
                    <div class="stat-kpi-card">
                      <div class="stat-kpi-label">{{ $t('playerProfile.statWalkDist') }}</div>
                      <div class="stat-kpi-val">{{ formatDistanceCm(statSummary('walkDistanceCm')) }}</div>
                    </div>
                    <div class="stat-kpi-card">
                      <div class="stat-kpi-label">{{ $t('playerProfile.statSprintDist') }}</div>
                      <div class="stat-kpi-val">{{ formatDistanceCm(statSummary('sprintDistanceCm')) }}</div>
                    </div>
                    <div class="stat-kpi-card">
                      <div class="stat-kpi-label">{{ $t('playerProfile.statMobKills') }}</div>
                      <div class="stat-kpi-val">{{ formatNumber(statSummary('mobKills')) }}</div>
                    </div>
                    <div class="stat-kpi-card">
                      <div class="stat-kpi-label">{{ $t('playerProfile.statBlocksMined') }}</div>
                      <div class="stat-kpi-val">{{ formatNumber(statSummary('blocksMinedTotal')) }}</div>
                    </div>
                    <div class="stat-kpi-card">
                      <div class="stat-kpi-label">{{ $t('playerProfile.statItemsCrafted') }}</div>
                      <div class="stat-kpi-val">{{ formatNumber(statSummary('itemsCraftedTotal')) }}</div>
                    </div>
                  </div>

                  <n-tabs type="line" size="small" style="margin-top: 16px;">
                    <n-tab-pane name="overview" :tab="$t('playerProfile.statsTabOverview')">
                      <div class="detail-grid">
                        <n-descriptions :column="1" size="small" bordered>
                          <n-descriptions-item :label="$t('playerProfile.statWalkDist')">
                            {{ formatDistanceCm(statSummary('walkDistanceCm')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statSprintDist')">
                            {{ formatDistanceCm(statSummary('sprintDistanceCm')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statCrouchDist')">
                            {{ formatDistanceCm(statSummary('crouchDistanceCm')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statSwimDist')">
                            {{ formatDistanceCm(statSummary('swimDistanceCm')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statFlyDist')">
                            {{ formatDistanceCm(statSummary('flyDistanceCm')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statAviateDist')">
                            {{ formatDistanceCm(statSummary('aviateDistanceCm')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statBoatDist')">
                            {{ formatDistanceCm(statSummary('boatDistanceCm')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statHorseDist')">
                            {{ formatDistanceCm(statSummary('horseDistanceCm')) }}
                          </n-descriptions-item>
                        </n-descriptions>

                        <n-descriptions :column="1" size="small" bordered>
                          <n-descriptions-item :label="$t('playerProfile.statTimeSinceDeath')">
                            {{ formatDurationSeconds(statSummary('timeSinceDeathSeconds')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statPlayerKills')">
                            {{ formatNumber(statSummary('playerKills')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statDamageDealt')">
                            {{ formatDamage(statSummary('damageDealt')) }} HP
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statDamageTaken')">
                            {{ formatDamage(statSummary('damageTaken')) }} HP
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statAnimalsBred')">
                            {{ formatNumber(statSummary('animalsBred')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statFishCaught')">
                            {{ formatNumber(statSummary('fishCaught')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statEnchanted')">
                            {{ formatNumber(statSummary('itemsEnchanted')) }}
                          </n-descriptions-item>
                          <n-descriptions-item :label="$t('playerProfile.statSleepInBed')">
                            {{ formatNumber(statSummary('sleepInBed')) }}
                          </n-descriptions-item>
                        </n-descriptions>
                      </div>
                    </n-tab-pane>

                    <n-tab-pane name="top" :tab="$t('playerProfile.statsTabTopItems')">
                      <div class="top-lists-grid">
                        <div v-for="group in topStatGroups" :key="group.key" class="top-list-card">
                          <div class="top-list-title">{{ group.label }}</div>
                          <div v-if="group.items.length === 0" style="font-size: 12px; color: var(--usp-text-muted);">
                            {{ $t('common.noData') }}
                          </div>
                          <div v-else style="display: flex; flex-direction: column; gap: 6px;">
                            <div
                              v-for="row in group.items"
                              :key="row.id"
                              style="display: flex; justify-content: space-between; gap: 8px; font-size: 12px;"
                            >
                              <span class="mono" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                                {{ formatResourceId(row.id) }}
                              </span>
                              <strong class="mono">{{ formatNumber(row.value) }}</strong>
                            </div>
                          </div>
                        </div>
                      </div>
                    </n-tab-pane>

                    <n-tab-pane name="all" :tab="$t('playerProfile.statsTabAllCustom')">
                      <n-input
                        v-model:value="statSearch"
                        size="small"
                        clearable
                        :placeholder="$t('playerProfile.searchStatsPlaceholder')"
                        style="max-width: 300px; margin-bottom: 10px;"
                      />
                      <div class="table-responsive" style="max-height: 320px; overflow-y: auto;">
                        <n-table :bordered="false" :single-line="false" size="small">
                          <thead>
                            <tr>
                              <th>{{ $t('playerProfile.statNameCol') }}</th>
                              <th style="width: 140px; text-align: right;">{{ $t('playerProfile.statValueCol') }}</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr v-for="entry in filteredCustomStats" :key="entry.id">
                              <td class="mono" style="font-size: 12px;">{{ formatResourceId(entry.id) }}</td>
                              <td class="mono" style="text-align: right; font-weight: 600;">
                                {{ formatNumber(entry.value) }}
                              </td>
                            </tr>
                          </tbody>
                        </n-table>
                      </div>
                    </n-tab-pane>
                  </n-tabs>
                </template>
              </n-card>

              <!-- Achievements / Advancements Section -->
              <n-card
                v-if="activeChar.achievements"
                style="border-radius: 14px;"
                :title="$t('playerProfile.achievementsTitle')"
              >
                <n-empty
                  v-if="!activeChar.achievements.available && activeChar.achievements.items.length === 0"
                  :description="$t('playerProfile.achievementsUnavailable')"
                />

                <template v-else>
                  <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 12px;">
                    <n-space size="small">
                      <n-tag type="success" :bordered="false" size="small">
                        {{ $t('playerProfile.completedCount', { count: activeChar.achievements.completedCount }) }}
                      </n-tag>
                      <n-tag type="warning" :bordered="false" size="small">
                        {{ $t('playerProfile.inProgressCount', { count: activeChar.achievements.inProgressCount }) }}
                      </n-tag>
                      <n-tag v-if="activeChar.achievements.recipesUnlocked > 0" :bordered="false" size="small">
                        {{ $t('playerProfile.recipesUnlocked', { count: activeChar.achievements.recipesUnlocked }) }}
                      </n-tag>
                    </n-space>

                    <n-space size="small">
                      <n-select
                        v-model:value="advFilterStatus"
                        size="small"
                        :options="advStatusOptions"
                        style="width: 128px;"
                      />
                      <n-input
                        v-model:value="advSearch"
                        size="small"
                        clearable
                        :placeholder="$t('playerProfile.searchAchievementsPlaceholder')"
                        style="width: 200px;"
                      />
                    </n-space>
                  </div>

                  <div class="adv-grid">
                    <div
                      v-for="adv in filteredAchievements"
                      :key="adv.id"
                      class="adv-card"
                      :class="{ done: adv.done }"
                    >
                      <div style="display: flex; align-items: center; justify-content: space-between; gap: 6px;">
                        <n-tag size="tiny" :bordered="false" :type="adv.done ? 'success' : 'default'">
                          {{ adv.done ? $t('playerProfile.advDone') : $t('playerProfile.advPending') }}
                        </n-tag>
                        <n-tag size="tiny" :bordered="false">
                          {{ adv.category }}
                        </n-tag>
                      </div>
                      <div style="font-weight: 600; font-size: 13px; margin-top: 6px; word-break: break-word;">
                        {{ formatResourceId(adv.id) }}
                      </div>
                      <div
                        class="mono"
                        style="font-size: 11px; color: var(--usp-text-muted); margin-top: 2px; word-break: break-all;"
                      >
                        {{ adv.id }}
                      </div>
                      <div
                        v-if="adv.achievedAt"
                        class="mono"
                        style="font-size: 11px; color: var(--usp-text-muted); margin-top: 6px;"
                      >
                        {{ adv.achievedAt }}
                      </div>
                    </div>
                  </div>
                </template>
              </n-card>
            </div>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import McSkinCanvas from '../components/McSkinCanvas.vue'
import { publicProfileApi, type PublicPlayerProfile } from '../api/publicProfile'
import type { SocialAccountItem } from '../api/profile'
import { formatTimestamp } from '../utils/format'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const notFound = ref(false)
const profile = ref<PublicPlayerProfile | null>(null)
const selectedCharUuid = ref('')
const statSearch = ref('')
const advSearch = ref('')
const advFilterStatus = ref<'all' | 'done' | 'pending'>('done')

const advStatusOptions = computed(() => [
  { label: t('playerProfile.advFilterDone'), value: 'done' },
  { label: t('playerProfile.advFilterPending'), value: 'pending' },
  { label: t('common.all'), value: 'all' }
])

const showAvatarBlock = computed(() => Boolean(profile.value && profile.value.avatarData !== undefined))

const heroDisplayName = computed(() => {
  if (!profile.value) return ''
  if (profile.value.nickname) return profile.value.nickname
  if (profile.value.username) return profile.value.username
  return t('playerProfile.anonymousPlayer')
})

const avatarInitial = computed(() => {
  const src = profile.value?.nickname || profile.value?.username || 'P'
  return src.slice(0, 1).toUpperCase()
})

const hasMetadataItems = computed(() => {
  const p = profile.value
  if (!p) return false
  return Boolean(
    p.gender !== undefined ||
      (p.birthday !== undefined && p.birthday) ||
      p.email ||
      p.createdAt
  )
})

const activeChar = computed(() => {
  const list = profile.value?.mcCharacters
  if (!list || list.length === 0) return null
  return (
    list.find((c) => c.playerUuid === selectedCharUuid.value) ||
    list.find((c) => c.primary) ||
    list[0]
  )
})

const effectiveDeaths = computed(() => {
  const live = activeChar.value?.onlineStatus?.deathCount
  const saved = statSummary('deaths')
  if (typeof live === 'number' && live > saved) return live
  return saved
})

const topStatGroups = computed(() => {
  const st = activeChar.value?.stats
  if (!st) return []
  return [
    { key: 'mined', label: t('playerProfile.topMined'), items: st.topMined || [] },
    { key: 'crafted', label: t('playerProfile.topCrafted'), items: st.topCrafted || [] },
    { key: 'used', label: t('playerProfile.topUsed'), items: st.topUsed || [] },
    { key: 'killed', label: t('playerProfile.topKilled'), items: st.topKilled || [] },
    { key: 'killedBy', label: t('playerProfile.topKilledBy'), items: st.topKilledBy || [] }
  ]
})

const filteredCustomStats = computed(() => {
  const entries = activeChar.value?.stats?.customEntries || []
  const q = statSearch.value.trim().toLowerCase()
  if (!q) return entries
  return entries.filter((e) => e.id.toLowerCase().includes(q) || formatResourceId(e.id).toLowerCase().includes(q))
})

const filteredAchievements = computed(() => {
  const items = activeChar.value?.achievements?.items || []
  const q = advSearch.value.trim().toLowerCase()
  return items.filter((item) => {
    if (advFilterStatus.value === 'done' && !item.done) return false
    if (advFilterStatus.value === 'pending' && item.done) return false
    if (!q) return true
    return (
      item.id.toLowerCase().includes(q) ||
      item.category.toLowerCase().includes(q) ||
      formatResourceId(item.id).toLowerCase().includes(q)
    )
  })
})

function statSummary(key: string): number {
  return Number(activeChar.value?.stats?.summary?.[key] || 0)
}

function formatNumber(n: number): string {
  return new Intl.NumberFormat().format(Math.round(n))
}

function formatDurationSeconds(sec: number): string {
  if (!sec || sec <= 0) return '0m'
  const hours = Math.floor(sec / 3600)
  const mins = Math.floor((sec % 3600) / 60)
  if (hours > 0) {
    return `${hours}h ${mins}m`
  }
  const remSec = sec % 60
  return mins > 0 ? `${mins}m ${remSec}s` : `${remSec}s`
}

function formatDistanceCm(cm: number): string {
  if (!cm || cm <= 0) return '0 m'
  const meters = cm / 100
  if (meters >= 1000) {
    return `${(meters / 1000).toFixed(2)} km`
  }
  return `${meters.toFixed(1)} m`
}

function formatDamage(raw: number): string {
  if (!raw || raw <= 0) return '0'
  return (raw / 10).toFixed(1)
}

function formatGender(g: string): string {
  switch (g) {
    case 'male':
      return t('settings.genderMale')
    case 'female':
      return t('settings.genderFemale')
    case 'other':
      return t('settings.genderOther')
    case 'prefer_not_to_say':
      return t('settings.genderPreferNotToSay')
    default:
      return t('settings.genderUnspecified')
  }
}

function formatSocialPlatform(item: SocialAccountItem): string {
  if (item.platform === 'custom' && item.customPlatform) {
    return item.customPlatform
  }
  return t(`socialPlatforms.${item.platform}`)
}

function formatDimension(dim: string): string {
  if (!dim) return '-'
  if (dim === 'minecraft:overworld') return t('playerProfile.dimOverworld')
  if (dim === 'minecraft:the_nether') return t('playerProfile.dimNether')
  if (dim === 'minecraft:the_end') return t('playerProfile.dimEnd')
  return dim
}

function formatResourceId(id: string): string {
  let clean = id
  const colonIdx = clean.indexOf(':')
  if (colonIdx >= 0) clean = clean.substring(colonIdx + 1)
  if (clean.startsWith('stat.')) clean = clean.substring(5)
  if (clean.startsWith('achievement.')) clean = clean.substring(12)
  const slashIdx = clean.lastIndexOf('/')
  if (slashIdx >= 0) clean = clean.substring(slashIdx + 1)
  return clean
    .replace(/[._-]+/g, ' ')
    .replace(/\b\w/g, (ch) => ch.toUpperCase())
}

async function copyText(val: string) {
  try {
    await navigator.clipboard.writeText(val)
    message.success(t('common.copied'))
  } catch {
    message.info(val)
  }
}

async function loadProfile() {
  const username = String(route.params.username || '').trim()
  if (!username) {
    notFound.value = true
    loading.value = false
    return
  }
  loading.value = true
  notFound.value = false
  profile.value = null
  selectedCharUuid.value = ''
  try {
    const data = await publicProfileApi.getByUsername(username)
    profile.value = data
    if (data.mcCharacters && data.mcCharacters.length > 0) {
      const def = data.mcCharacters.find((c) => c.primary) || data.mcCharacters[0]
      selectedCharUuid.value = def.playerUuid
    }
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

watch(() => route.params.username, loadProfile, { immediate: true })
</script>

<style scoped>
.profile-page-wrap {
  width: 100%;
}
.hero-flex {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  flex-wrap: wrap;
}
.avatar-box {
  width: 88px;
  height: 88px;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--usp-border);
  background: var(--usp-surface-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar-fallback-letter {
  font-size: 32px;
  font-weight: 700;
  color: var(--usp-primary);
}
.meta-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  font-size: 13px;
}
.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.meta-label {
  color: var(--usp-text-muted);
}
.mc-main-grid {
  display: grid;
  grid-template-columns: 310px 1fr;
  gap: 16px;
  align-items: start;
}
.skin-stage {
  padding: 18px 28px;
  border-radius: 12px;
  background: linear-gradient(180deg, var(--usp-surface-soft), transparent);
  border: 1px solid var(--usp-border);
}
.stat-kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}
.stat-kpi-card {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--usp-border);
  background: var(--usp-surface-soft);
}
.stat-kpi-label {
  font-size: 12px;
  color: var(--usp-text-muted);
}
.stat-kpi-val {
  font-size: 17px;
  font-weight: 700;
  margin-top: 3px;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}
.top-lists-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}
.top-list-card {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--usp-border);
  background: var(--usp-surface-soft);
}
.top-list-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
}
.adv-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
  max-height: 440px;
  overflow-y: auto;
}
.adv-card {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--usp-border);
  background: var(--usp-surface-soft);
}
.adv-card.done {
  border-color: rgba(34, 197, 94, 0.35);
}
@media (max-width: 900px) {
  .mc-main-grid {
    grid-template-columns: 1fr;
  }
}
</style>
