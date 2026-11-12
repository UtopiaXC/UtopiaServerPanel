<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">
        {{ $t('home.welcome') }}, {{ authStore.user?.nickname || authStore.user?.username }}
      </h2>
      <n-button secondary @click="router.push('/settings')">
        <template #icon>
          <n-icon><SettingsOutline /></n-icon>
        </template>
        {{ $t('nav.settings') }}
      </n-button>
    </div>

    <n-grid :cols="isMobile ? 1 : 2" :x-gap="16" :y-gap="16">
      <!-- Bound Minecraft Player -->
      <n-gi>
        <n-card size="small" :title="$t('home.boundPlayer')" style="border-radius: 12px; height: 100%;">
          <template #header-extra>
            <n-tag
              v-if="playerData"
              size="small"
              :bordered="false"
              :type="playerData.online ? 'success' : 'default'"
            >
              {{ playerData.online ? $t('home.playerOnline') : $t('home.playerOffline') }}
            </n-tag>
          </template>

          <n-spin :show="loadingPlayer">
            <div v-if="!authStore.user?.bound" style="padding: 16px 0; text-align: center;">
              <n-empty :description="$t('home.unbound')">
                <template #extra>
                  <n-button type="primary" size="small" @click="router.push('/settings?tab=binding')">
                    {{ $t('home.goBind') }}
                  </n-button>
                </template>
              </n-empty>
            </div>

            <n-descriptions v-else-if="playerData" :column="1" size="small" label-placement="left" bordered>
              <n-descriptions-item :label="$t('home.boundPlayer')">
                <strong>{{ playerData.playerName || authStore.user?.playerName }}</strong>
              </n-descriptions-item>
              <n-descriptions-item
                v-if="playerData.dimension || playerData.lastDimension"
                :label="$t('home.dimension')"
              >
                <span class="mono">{{ playerData.dimension || playerData.lastDimension }}</span>
              </n-descriptions-item>
              <n-descriptions-item
                v-if="playerData.position || playerData.lastPosition"
                :label="$t('home.position')"
              >
                <span class="mono">{{ formatPos(playerData.position || playerData.lastPosition) }}</span>
              </n-descriptions-item>
              <n-descriptions-item
                v-if="playerData.gamemode || playerData.lastGamemode"
                :label="$t('home.gamemode')"
              >
                <n-tag size="tiny" :bordered="false">
                  {{ playerData.gamemode || playerData.lastGamemode }}
                </n-tag>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('home.health')">
                <div v-if="playerData.online" style="display: flex; align-items: center; gap: 10px; min-width: 140px;">
                  <n-progress
                    type="line"
                    :percentage="healthPercent"
                    status="success"
                    :show-indicator="false"
                    style="flex: 1;"
                  />
                  <span class="mono" style="font-size: 12px;">
                    {{ Math.round(playerData.health || 0) }} / {{ playerData.maxHealth || 20 }}
                  </span>
                </div>
                <span v-else>{{ playerData.lastHealth != null ? Math.round(playerData.lastHealth) : '-' }}</span>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('home.deaths')">
                {{ playerData.deathCount ?? '-' }}
              </n-descriptions-item>
            </n-descriptions>
          </n-spin>
        </n-card>
      </n-gi>

      <!-- Effective Permissions -->
      <n-gi>
        <n-card size="small" :title="$t('home.myPermissions')" style="border-radius: 12px; height: 100%;">
          <template #header-extra>
            <n-tag size="small" type="success" :bordered="false">
              {{ authStore.user?.roleDisplayName || authStore.user?.roleName }}
            </n-tag>
          </template>

          <n-descriptions :column="1" size="small" label-placement="left" bordered>
            <n-descriptions-item v-for="perm in catalog" :key="permKeyOf(perm)" :label="permLabel(perm)">
              <n-tag
                size="small"
                :bordered="false"
                :type="levelType(authStore.permissions[permKeyOf(perm)] ?? 0)"
              >
                {{ levelText(authStore.permissions[permKeyOf(perm)] ?? 0) }}
              </n-tag>
            </n-descriptions-item>
          </n-descriptions>
        </n-card>
      </n-gi>
    </n-grid>

    <!-- Login History -->
    <n-card
      size="small"
      :title="$t('home.loginHistory')"
      style="border-radius: 12px; margin-top: 16px;"
    >
      <div class="table-responsive">
        <n-table :bordered="false" :single-line="false" size="small">
          <thead>
            <tr>
              <th>{{ $t('home.loginTime') }}</th>
              <th>{{ $t('home.loginIp') }}</th>
              <th>{{ $t('home.loginStatus') }}</th>
              <th>{{ $t('home.loginUa') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in history" :key="item.id">
              <td class="mono" style="white-space: nowrap; font-size: 12px;">
                {{ formatTimestamp(item.loginTime) }}
              </td>
              <td class="mono" style="font-size: 12px;">{{ item.ipAddress || '-' }}</td>
              <td>
                <n-tag size="tiny" :bordered="false" :type="item.success ? 'success' : 'error'">
                  {{ item.success ? $t('common.yes') : item.failReason || $t('common.no') }}
                </n-tag>
              </td>
              <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--usp-text-muted);" :title="item.userAgent">
                {{ item.userAgent || '-' }}
              </td>
            </tr>
            <tr v-if="history.length === 0">
              <td colspan="4" style="text-align: center; color: var(--usp-text-muted); padding: 20px;">
                {{ $t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </n-table>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SettingsOutline } from '@vicons/ionicons5'
import { useAuthStore } from '../stores/auth'
import { useSiteStore } from '../stores/site'
import { bindingApi } from '../api/binding'
import { profileApi, type LoginHistoryItem } from '../api/profile'
import type { PermissionDef } from '../api/auth'
import { formatTimestamp } from '../utils/format'

const router = useRouter()
const { t, te } = useI18n()
const authStore = useAuthStore()
const siteStore = useSiteStore()

const isMobile = ref(false)
function updateMobile() {
  isMobile.value = window.innerWidth <= 768
}

const loadingPlayer = ref(false)
const playerData = ref<any>(null)
const history = ref<LoginHistoryItem[]>([])

const catalog = computed<PermissionDef[]>(() => {
  if (siteStore.permissionsCatalog.length > 0) return siteStore.permissionsCatalog
  return [
    { key: 'admin', name: 'Administration', description: '', sortOrder: 1 },
    { key: 'dashboard', name: 'Dashboard', description: '', sortOrder: 2 },
    { key: 'terminal', name: 'Console', description: '', sortOrder: 3 },
    { key: 'logs', name: 'Monitor Logs', description: '', sortOrder: 4 }
  ]
})

const healthPercent = computed(() => {
  if (!playerData.value) return 0
  const max = Number(playerData.value.maxHealth || 20)
  return max > 0 ? Math.min(100, Math.round((Number(playerData.value.health || 0) / max) * 100)) : 0
})

function permKeyOf(perm: PermissionDef): string {
  return perm.key || (perm as any).permKey || ''
}

function permLabel(perm: PermissionDef) {
  const k = permKeyOf(perm)
  const key = `adminRoles.perm_${k}`
  return te(key) ? t(key) : perm.name || k
}

function levelType(level: number): 'success' | 'warning' | 'default' {
  if (level >= 2) return 'success'
  if (level >= 1) return 'warning'
  return 'default'
}

function levelText(level: number): string {
  if (level >= 2) return t('home.levelFull')
  if (level >= 1) return t('home.levelReadonly')
  return t('home.levelDeny')
}

function formatPos(pos?: { x: number; y: number; z: number }) {
  if (!pos) return '-'
  return `${Math.round(pos.x)}, ${Math.round(pos.y)}, ${Math.round(pos.z)}`
}

onMounted(async () => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
  await siteStore.ensureLoaded()

  if (authStore.user?.bound) {
    loadingPlayer.value = true
    try {
      const p = await bindingApi.getMyPlayer()
      if (p && p.bound !== false) {
        playerData.value = p
      }
    } catch {
      // ignore
    } finally {
      loadingPlayer.value = false
    }
  }

  try {
    const res = await profileApi.listLoginHistory()
    history.value = res.history || []
  } catch {
    // ignore
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobile)
})
</script>

