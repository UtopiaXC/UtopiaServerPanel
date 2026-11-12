<template>
  <div>
    <div v-if="!status && loadFailed" style="padding: 60px 0;">
      <n-empty :description="$t('common.noData')">
        <template #extra>
          <n-button size="small" type="primary" @click="retryLoad">
            {{ $t('common.refresh') }}
          </n-button>
        </template>
      </n-empty>
    </div>

    <div v-else-if="!status" style="padding: 60px 0; text-align: center;">
      <n-spin size="large" />
    </div>

    <template v-else>
      <!-- Top KPI Strip -->
      <n-grid :cols="kpiCols" :x-gap="14" :y-gap="14">
        <n-gi>
          <n-card size="small" style="border-radius: 12px; height: 100%;">
            <div class="kpi-label">{{ $t('dashboard.players') }}</div>
            <div class="kpi-value">
              {{ status.onlinePlayers ?? 0 }}
              <span class="kpi-sub">/ {{ status.maxPlayers ?? 0 }}</span>
            </div>
            <div style="margin-top: 8px; display: flex; align-items: center; gap: 6px;">
              <n-tag size="tiny" :bordered="false" type="info">{{ status.worldName || '-' }}</n-tag>
            </div>
          </n-card>
        </n-gi>

        <n-gi>
          <n-card size="small" style="border-radius: 12px; height: 100%;">
            <div class="kpi-label">{{ $t('dashboard.tps') }}</div>
            <div class="kpi-value" :style="{ color: tpsColor }">
              {{ Number(status.tps ?? 20).toFixed(1) }}
            </div>
            <div style="margin-top: 8px; font-size: 12px; color: var(--usp-text-muted);">
              {{ status.version || '-' }}
            </div>
          </n-card>
        </n-gi>

        <n-gi>
          <n-card size="small" style="border-radius: 12px; height: 100%;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span class="kpi-label">{{ $t('dashboard.cpu') }}</span>
              <n-popover v-if="status.coreLoads?.length" trigger="hover" placement="bottom">
                <template #trigger>
                  <n-tag size="tiny" :bordered="false" style="cursor: pointer;">
                    {{ status.coreLoads.length }} {{ $t('dashboard.coreCount') }}
                  </n-tag>
                </template>
                <div style="min-width: 190px;">
                  <div style="font-weight: 600; font-size: 12px; margin-bottom: 6px;">
                    {{ $t('dashboard.coreDetail') }}
                  </div>
                  <div class="cores-grid">
                    <div v-for="(load, idx) in status.coreLoads" :key="idx" class="core-cell">
                      <span>#{{ idx }}</span>
                      <span class="mono">{{ Number(load).toFixed(1) }}%</span>
                    </div>
                  </div>
                </div>
              </n-popover>
            </div>
            <div class="kpi-value">
              {{ status.cpuLoad >= 0 ? `${Number(status.cpuLoad).toFixed(1)}%` : 'N/A' }}
            </div>
            <n-progress
              type="line"
              style="margin-top: 8px;"
              :percentage="cpuPercent"
              :show-indicator="false"
              :status="cpuPercent >= 85 ? 'error' : cpuPercent >= 65 ? 'warning' : 'success'"
            />
          </n-card>
        </n-gi>

        <n-gi>
          <n-card size="small" style="border-radius: 12px; height: 100%;">
            <div class="kpi-label">{{ $t('dashboard.uptime') }}</div>
            <div class="kpi-value" style="font-size: 20px;">
              {{ formatUptime(status.uptime || 0) }}
            </div>
            <div style="margin-top: 8px; display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--usp-text-muted);">
              <span class="live-dot" />
              <span>{{ updatedTimeText }}</span>
            </div>
          </n-card>
        </n-gi>
      </n-grid>

      <!-- Memory, Storage & MOTD -->
      <n-grid :cols="isMobile ? 1 : 2" :x-gap="14" :y-gap="14" style="margin-top: 14px;">
        <n-gi>
          <n-card size="small" :title="$t('dashboard.jvmMemory')" style="border-radius: 12px; height: 100%;">
            <div class="metric-row">
              <span>{{ $t('dashboard.jvmMemory') }}</span>
              <span class="mono">{{ formatBytes(status.jvmUsedMemory) }} / {{ formatBytes(status.jvmMaxMemory) }}</span>
            </div>
            <n-progress
              type="line"
              :percentage="jvmMemPercent"
              :status="jvmMemPercent >= 85 ? 'error' : jvmMemPercent >= 70 ? 'warning' : 'success'"
              style="margin-bottom: 14px;"
            />

            <div class="metric-row">
              <span>{{ $t('dashboard.sysMemory') }}</span>
              <span class="mono">{{ formatBytes(status.systemUsedMemory) }} / {{ formatBytes(status.systemTotalMemory) }}</span>
            </div>
            <n-progress
              type="line"
              :percentage="sysMemPercent"
              :status="sysMemPercent >= 90 ? 'error' : sysMemPercent >= 75 ? 'warning' : 'info'"
            />
          </n-card>
        </n-gi>

        <n-gi>
          <n-card size="small" :title="$t('dashboard.storage')" style="border-radius: 12px; height: 100%;">
            <div class="metric-row">
              <span>{{ $t('dashboard.diskUsage') }}</span>
              <span class="mono">{{ formatBytes(diskUsed) }} / {{ formatBytes(status.diskTotalSpace) }}</span>
            </div>
            <n-progress
              type="line"
              :percentage="diskPercent"
              :status="diskPercent >= 90 ? 'error' : diskPercent >= 75 ? 'warning' : 'success'"
              style="margin-bottom: 12px;"
            />

            <div class="metric-row">
              <span>{{ $t('dashboard.gameFolder') }}</span>
              <span class="mono">{{ formatBytes(status.gameFolderSize) }}</span>
            </div>
            <div class="metric-row" style="margin-top: 6px;">
              <span>{{ $t('dashboard.motd') }}</span>
              <span class="motd-text" :title="status.motd">{{ status.motd || '-' }}</span>
            </div>
          </n-card>
        </n-gi>
      </n-grid>

      <!-- Server Properties -->
      <n-card
        v-if="status.properties && Object.keys(status.properties).length > 0"
        size="small"
        :title="$t('dashboard.properties')"
        style="border-radius: 12px; margin-top: 14px;"
      >
        <n-grid :cols="propCols" :x-gap="18" :y-gap="14">
          <!-- World -->
          <n-gi>
            <div class="prop-group-title">{{ $t('dashboard.catWorld') }}</div>
            <n-descriptions :column="1" size="small" label-placement="left" bordered>
              <n-descriptions-item
                v-if="status.properties.levelSeed !== undefined"
                :label="$t('dashboard.propSeed')"
              >
                <span class="mono">{{ status.properties.levelSeed || '-' }}</span>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propLevelType')">
                {{ formatLevelType(status.properties.levelType) }}
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propStructures')">
                <n-icon :color="status.properties.generateStructures ? '#18a058' : '#ef4444'">
                  <CheckmarkOutline v-if="status.properties.generateStructures" />
                  <CloseOutline v-else />
                </n-icon>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propViewDistance')">
                {{ status.properties.viewDistance }}
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propSimDistance')">
                {{ status.properties.simulationDistance }}
              </n-descriptions-item>
              <n-descriptions-item
                v-if="status.properties.maxBuildHeight !== undefined"
                :label="$t('dashboard.propBuildHeight')"
              >
                {{ status.properties.maxBuildHeight }}
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propWorldSize')">
                {{ Number(status.properties.maxWorldSize || 0).toLocaleString() }}
              </n-descriptions-item>
            </n-descriptions>
          </n-gi>

          <!-- Gameplay -->
          <n-gi>
            <div class="prop-group-title">{{ $t('dashboard.catGameplay') }}</div>
            <n-descriptions :column="1" size="small" label-placement="left" bordered>
              <n-descriptions-item :label="$t('dashboard.propGamemode')">
                <n-tag size="tiny" :bordered="false">{{ status.properties.gamemode }}</n-tag>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propDifficulty')">
                <n-tag size="tiny" :bordered="false">{{ status.properties.difficulty }}</n-tag>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propHardcore')">
                <n-icon :color="status.properties.hardcore ? '#18a058' : '#ef4444'">
                  <CheckmarkOutline v-if="status.properties.hardcore" />
                  <CloseOutline v-else />
                </n-icon>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propPvp')">
                <n-icon :color="status.properties.pvp ? '#18a058' : '#ef4444'">
                  <CheckmarkOutline v-if="status.properties.pvp" />
                  <CloseOutline v-else />
                </n-icon>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propFlight')">
                <n-icon :color="status.properties.allowFlight ? '#18a058' : '#ef4444'">
                  <CheckmarkOutline v-if="status.properties.allowFlight" />
                  <CloseOutline v-else />
                </n-icon>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propNether')">
                <n-icon :color="status.properties.allowNether ? '#18a058' : '#ef4444'">
                  <CheckmarkOutline v-if="status.properties.allowNether" />
                  <CloseOutline v-else />
                </n-icon>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propSpawnProtection')">
                {{ status.properties.spawnProtection }}
              </n-descriptions-item>
            </n-descriptions>
          </n-gi>

          <!-- Network & Security -->
          <n-gi>
            <div class="prop-group-title">{{ $t('dashboard.catNetwork') }}</div>
            <n-descriptions :column="1" size="small" label-placement="left" bordered>
              <n-descriptions-item :label="$t('dashboard.propOnlineMode')">
                <n-icon :color="status.properties.onlineMode ? '#18a058' : '#ef4444'">
                  <CheckmarkOutline v-if="status.properties.onlineMode" />
                  <CloseOutline v-else />
                </n-icon>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propServerIp')">
                <span class="mono">{{ status.properties.serverIp || '*' }}</span>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propPort')">
                <span class="mono">{{ status.properties.serverPort }}</span>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propMaxTick')">
                {{ formatTickTime(status.properties.maxTickTime) }}
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propWhitelist')">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <n-icon :color="status.properties.whiteList ? '#18a058' : '#ef4444'">
                    <CheckmarkOutline v-if="status.properties.whiteList" />
                    <CloseOutline v-else />
                  </n-icon>
                  <n-popover v-if="status.properties.whiteList" trigger="click" placement="top">
                    <template #trigger>
                      <n-button size="tiny" secondary>
                        {{ whitelistPlayers.length }} {{ $t('dashboard.whitelistCount') }}
                      </n-button>
                    </template>
                    <div style="max-height: 180px; overflow-y: auto; min-width: 140px;">
                      <div style="font-weight: 600; font-size: 12px; margin-bottom: 6px;">
                        {{ $t('dashboard.whitelist') }}
                      </div>
                      <div v-if="whitelistPlayers.length === 0" style="font-size: 12px; color: var(--usp-text-muted);">
                        {{ $t('dashboard.whitelistEmpty') }}
                      </div>
                      <div v-for="p in whitelistPlayers" :key="p" class="mono" style="font-size: 12px; padding: 2px 0;">
                        {{ p }}
                      </div>
                    </div>
                  </n-popover>
                </div>
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propIdleTimeout')">
                {{ formatIdleTimeout(status.properties.playerIdleTimeout) }}
              </n-descriptions-item>
              <n-descriptions-item :label="$t('dashboard.propMaxPlayers')">
                {{ status.properties.maxPlayers }}
              </n-descriptions-item>
            </n-descriptions>
          </n-gi>
        </n-grid>
      </n-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { CheckmarkOutline, CloseOutline } from '@vicons/ionicons5'
import { useWebSocket } from '../composables/useWebSocket'
import { monitorApi } from '../api/monitor'
import { formatBytes, formatUptime } from '../utils/format'

const { connected, status, lastStatusAt, refreshStatus, ensureConnected } = useWebSocket()

const loadFailed = ref(false)
let fallbackTimer: ReturnType<typeof setTimeout> | null = null

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1280)
function onResize() {
  windowWidth.value = window.innerWidth
}
const isMobile = computed(() => windowWidth.value <= 768)
const kpiCols = computed(() => (windowWidth.value <= 600 ? 1 : windowWidth.value <= 1024 ? 2 : 4))
const propCols = computed(() => (windowWidth.value <= 820 ? 1 : windowWidth.value <= 1180 ? 2 : 3))

const tpsColor = computed(() => {
  const v = Number(status.value?.tps ?? 20)
  if (v >= 18) return 'var(--usp-primary)'
  if (v >= 15) return '#f59e0b'
  return '#ef4444'
})

const cpuPercent = computed(() => {
  const v = Number(status.value?.cpuLoad ?? 0)
  return v > 0 ? Math.min(100, Math.round(v)) : 0
})

const jvmMemPercent = computed(() => {
  const used = Number(status.value?.jvmUsedMemory ?? 0)
  const max = Number(status.value?.jvmMaxMemory ?? 0)
  return max > 0 ? Math.min(100, Math.round((used / max) * 100)) : 0
})

const sysMemPercent = computed(() => {
  const used = Number(status.value?.systemUsedMemory ?? 0)
  const total = Number(status.value?.systemTotalMemory ?? 0)
  return total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0
})

const diskUsed = computed(() => {
  const total = Number(status.value?.diskTotalSpace ?? 0)
  const free = Number(status.value?.diskFreeSpace ?? 0)
  return Math.max(0, total - free)
})

const diskPercent = computed(() => {
  const total = Number(status.value?.diskTotalSpace ?? 0)
  return total > 0 ? Math.min(100, Math.round((diskUsed.value / total) * 100)) : 0
})

const whitelistPlayers = computed<string[]>(
  () => status.value?.properties?.whitelistPlayers || []
)

const updatedTimeText = computed(() => {
  const d = lastStatusAt.value
  if (!d) return '--:--:--'
  return d.toLocaleTimeString()
})

function formatLevelType(type?: string) {
  return type ? type.replace(/^minecraft:/, '') : '-'
}

function formatTickTime(ms?: number) {
  if (ms === undefined || ms === null) return '-'
  if (ms >= 60000) return `${Math.round(ms / 60000)}m`
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`
  return `${ms}ms`
}

function formatIdleTimeout(minutes?: number) {
  if (minutes === undefined || minutes === null) return '-'
  if (minutes === 0) return '-'
  if (minutes >= 60) return `${(minutes / 60).toFixed(1)}h`
  return `${minutes}m`
}

async function loadInitialStatus() {
  loadFailed.value = false
  if (fallbackTimer) {
    clearTimeout(fallbackTimer)
  }
  fallbackTimer = setTimeout(() => {
    if (!status.value) {
      loadFailed.value = true
    }
  }, 8000)

  refreshStatus()
  if (!status.value) {
    try {
      const snap = await monitorApi.getStatus()
      if (snap && Object.keys(snap).length > 0 && !status.value) {
        status.value = snap
        lastStatusAt.value = new Date()
      } else if (!status.value && !connected.value) {
        loadFailed.value = true
      }
    } catch {
      if (!status.value && !connected.value) {
        loadFailed.value = true
      }
    }
  }
}

function retryLoad() {
  ensureConnected()
  loadInitialStatus()
}

watch(connected, (isOpen) => {
  if (isOpen) {
    refreshStatus()
  }
})

watch(status, (val) => {
  if (val) {
    loadFailed.value = false
    if (fallbackTimer) {
      clearTimeout(fallbackTimer)
      fallbackTimer = null
    }
  }
})

onMounted(() => {
  window.addEventListener('resize', onResize)
  loadInitialStatus()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (fallbackTimer) {
    clearTimeout(fallbackTimer)
    fallbackTimer = null
  }
})
</script>

<style scoped>
.kpi-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--usp-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.kpi-value {
  font-size: 26px;
  font-weight: 700;
  margin-top: 6px;
  line-height: 1.2;
  color: var(--usp-text);
}

.kpi-sub {
  font-size: 15px;
  font-weight: 500;
  color: var(--usp-text-muted);
}

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 13px;
}

.motd-text {
  max-width: 70%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--usp-text-muted);
}

.cores-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px 12px;
  font-size: 12px;
}

.core-cell {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.prop-group-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--usp-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--usp-primary);
  display: inline-block;
}
</style>

