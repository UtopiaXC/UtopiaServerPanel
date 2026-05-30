<template>
  <div class="dashboard">
    <div class="stats-grid" v-if="status">

      <!-- Order: Online Players, CPU Usage, Memory Usage, World Size, World Name, Version, Uptime, TPS -->

      <div class="stat-card">
        <h3>{{ $t('summary.onlinePlayers') }}</h3>
        <p>{{ status.onlinePlayers }} / {{ status.maxPlayers }}</p>
      </div>

      <div class="stat-card">
        <h3>{{ $t('summary.cpuUsage') }}</h3>
        <div class="progress-bar-container" @mouseenter="handleTooltipEnter($event, 'cores')" @mouseleave="showCores = false">
          <div class="progress-bar-bg">
            <div class="progress-bar-fill" :style="{ width: status.cpuLoad + '%' }"></div>
          </div>
          <div class="progress-label">{{ status.cpuLoad >= 0 ? status.cpuLoad.toFixed(1) + '%' : 'N/A' }}</div>

          <div class="tooltip cores-tooltip" :class="{ 'show-below': showCoresBelow }" v-if="showCores && status.coreLoads">
            <div class="tooltip-title">{{ $t('summary.coreUsage') }}</div>
            <div class="cores-grid">
              <div v-for="(load, index) in status.coreLoads" :key="index" class="core-item">
                <span class="core-label">{{ $t('summary.corePrefix') }} {{ index }}</span>
                <span class="core-value">{{ load.toFixed(1) }}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="stat-card">
        <h3>{{ $t('summary.memoryUsage') }}</h3>
        <div class="progress-bar-container memory-bar-container" @mouseenter="handleTooltipEnter($event, 'mem')" @mouseleave="showMemTooltip = false">
          <div class="progress-bar-bg memory-total" style="position: relative;">
            <div class="progress-bar-limit memory-sys-used" :style="{ width: (status.systemUsedMemory / status.systemTotalMemory * 100) + '%' }"></div>
            <div class="progress-bar-limit memory-jvm-max" :style="{ width: (status.jvmMaxMemory / status.systemTotalMemory * 100) + '%' }"></div>
            <div class="progress-bar-fill memory-jvm-used" :style="{ width: (status.jvmUsedMemory / status.systemTotalMemory * 100) + '%' }"></div>
          </div>
          <div class="progress-label">{{ formatMemoryInt(status.jvmUsedMemory) }} / {{ formatMemoryInt(status.jvmMaxMemory) }} / {{ formatMemoryInt(status.systemTotalMemory) }}</div>

          <div class="tooltip" :class="{ 'show-below': showMemBelow }" v-if="showMemTooltip">
            <div class="tooltip-title">{{ $t('summary.memoryUsage') }}</div>
            <div class="inline-legend-tooltip">
              <div><span class="dot used-dot"></span> {{ $t('summary.jvmUsedMemory') }}: {{ formatMemory(status.jvmUsedMemory) }}</div>
              <div><span class="dot max-dot"></span> {{ $t('summary.jvmMaxMemory') }}: {{ formatMemory(status.jvmMaxMemory) }}</div>
              <div><span class="dot sys-used-dot"></span> {{ $t('summary.systemUsedMemory') }}: {{ formatMemory(status.systemUsedMemory) }}</div>
              <div><span class="dot total-dot"></span> {{ $t('summary.systemTotalMemory') }}: {{ formatMemory(status.systemTotalMemory) }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="stat-card">
        <h3>{{ $t('summary.diskUsage') }}</h3>
        <div class="progress-bar-container disk-bar-container" @mouseenter="handleTooltipEnter($event, 'disk')" @mouseleave="showDiskTooltip = false">
          <div class="progress-bar-bg disk-total">
             <div class="progress-bar-limit disk-used" :style="{ width: ((status.diskTotalSpace - status.diskFreeSpace) / status.diskTotalSpace * 100) + '%' }">
                <div class="progress-bar-fill disk-world" :style="{ width: (status.gameFolderSize / (status.diskTotalSpace - status.diskFreeSpace) * 100) + '%' }"></div>
             </div>
          </div>
          <div class="progress-label">{{ formatMemoryInt(status.gameFolderSize) }} / {{ formatMemoryInt(status.diskTotalSpace - status.diskFreeSpace) }} / {{ formatMemoryInt(status.diskTotalSpace) }}</div>

          <div class="tooltip" :class="{ 'show-below': showDiskBelow }" v-if="showDiskTooltip">
            <div class="tooltip-title">{{ $t('summary.diskUsage') }}</div>
            <div class="inline-legend-tooltip">
              <div><span class="dot world-dot"></span> {{ $t('summary.gameFolderSize') }}: {{ formatMemory(status.gameFolderSize) }}</div>
              <div><span class="dot max-dot"></span> {{ $t('summary.diskUsed') }}: {{ formatMemory(status.diskTotalSpace - status.diskFreeSpace) }}</div>
              <div><span class="dot total-dot"></span> {{ $t('summary.diskTotal') }}: {{ formatMemory(status.diskTotalSpace) }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="stat-card">
        <h3>{{ $t('summary.worldName') }}</h3>
        <p class="text-md">{{ status.worldName }}</p>
      </div>

      <div class="stat-card">
        <h3>{{ $t('summary.version') }}</h3>
        <p class="text-md">{{ status.version }}</p>
      </div>

      <div class="stat-card">
        <h3>{{ $t('summary.uptime') }}</h3>
        <p class="text-md">{{ formatUptime(status.uptime) }}</p>
      </div>

      <div class="stat-card">
        <h3>{{ $t('summary.tps') }}</h3>
        <p class="text-md">{{ status.tps ? status.tps.toFixed(1) : '20.0' }}</p>
      </div>

      <div class="stat-card full-width">
        <h3>{{ $t('summary.motd') }}</h3>
        <p class="text-md">{{ status.motd }}</p>
      </div>

      <div class="stat-card full-width" v-if="status.properties && Object.keys(status.properties).length > 0">
        <h3>{{ $t('summary.properties') }}</h3>
        <div class="props-groups">
          <!-- World -->
          <div class="props-group">
            <h4 class="props-group-title">{{ $t('summary.propGroups.world') }}</h4>
            <div class="props-table">
              <div class="prop-row" v-if="status.properties.levelSeed !== undefined">
                <span class="prop-label">{{ $t('summary.props.levelSeed') }}</span>
                <span class="prop-value" :title="status.properties.levelSeed">{{ status.properties.levelSeed || '-' }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.levelType') }}</span>
                <span class="prop-value">{{ formatLevelType(status.properties.levelType) }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.generateStructures') }}</span>
                <span class="prop-value">{{ formatBool(status.properties.generateStructures) }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.viewDistance') }}</span>
                <span class="prop-value">{{ status.properties.viewDistance }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.simulationDistance') }}</span>
                <span class="prop-value">{{ status.properties.simulationDistance }}</span>
              </div>
              <div class="prop-row" v-if="status.properties.maxBuildHeight !== undefined">
                <span class="prop-label">{{ $t('summary.props.maxBuildHeight') }}</span>
                <span class="prop-value">{{ status.properties.maxBuildHeight }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.maxWorldSize') }}</span>
                <span class="prop-value">{{ formatNumber(status.properties.maxWorldSize) }}</span>
              </div>
            </div>
          </div>

          <!-- Gameplay -->
          <div class="props-group">
            <h4 class="props-group-title">{{ $t('summary.propGroups.gameplay') }}</h4>
            <div class="props-table">
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.gamemode') }}</span>
                <span class="prop-value">{{ status.properties.gamemode }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.difficulty') }}</span>
                <span class="prop-value">{{ status.properties.difficulty }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.hardcore') }}</span>
                <span class="prop-value">{{ formatBool(status.properties.hardcore) }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.pvp') }}</span>
                <span class="prop-value">{{ formatBool(status.properties.pvp) }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.allowFlight') }}</span>
                <span class="prop-value">{{ formatBool(status.properties.allowFlight) }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.allowNether') }}</span>
                <span class="prop-value">{{ formatBool(status.properties.allowNether) }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.spawnProtection') }}</span>
                <span class="prop-value">{{ status.properties.spawnProtection }}</span>
              </div>
            </div>
          </div>

          <!-- Network & Security -->
          <div class="props-group">
            <h4 class="props-group-title">{{ $t('summary.propGroups.network') }}</h4>
            <div class="props-table">
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.onlineMode') }}</span>
                <span class="prop-value">{{ formatBool(status.properties.onlineMode) }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.serverIp') }}</span>
                <span class="prop-value">{{ status.properties.serverIp }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.serverPort') }}</span>
                <span class="prop-value">{{ status.properties.serverPort }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.maxTickTime') }}</span>
                <span class="prop-value">{{ formatTickTime(status.properties.maxTickTime) }}</span>
              </div>
              <div class="prop-row whitelist-row">
                <span class="prop-label whitelist-hint-container" @mouseenter="showWhitelist = true" @mouseleave="showWhitelist = false" @click="showWhitelist = !showWhitelist">
                  {{ $t('summary.props.whiteList') }}
                  <span class="whitelist-hint-line" v-if="status.properties.whiteList"></span>
                </span>
                <span class="prop-value whitelist-val-container">
                  <span>{{ formatBool(status.properties.whiteList) }}</span>

                  <div class="tooltip whitelist-tooltip" v-if="showWhitelist && status.properties.whiteList">
                    <div class="whitelist-tooltip-title">{{ $t('summary.props.whitelistPlayers') }}</div>
                    <ul v-if="whitelistPlayers.length > 0" class="whitelist-player-list">
                      <li v-for="player in whitelistPlayers" :key="player">{{ player }}</li>
                    </ul>
                    <div v-else class="whitelist-tooltip-empty">{{ $t('summary.props.whitelistEmpty') }}</div>
                  </div>
                </span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.playerIdleTimeout') }}</span>
                <span class="prop-value">{{ formatIdleTimeout(status.properties.playerIdleTimeout) }}</span>
              </div>
              <div class="prop-row">
                <span class="prop-label">{{ $t('summary.props.maxPlayers') }}</span>
                <span class="prop-value">{{ status.properties.maxPlayers }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>
    <div v-else class="loading">{{ $t('summary.loading') }}</div>

    <div v-if="lastUpdated" class="update-time-tag">
      <span class="update-dot"></span>
      {{ $t('summary.dataUpdated') }} {{ updateTimeDisplay }}
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { on, off, fetchStatus } from '../api.js';

const { t } = useI18n();
const status = ref(null);
const lastUpdated = ref(null);
const showWhitelist = ref(false);
const showCores = ref(false);
const showMemTooltip = ref(false);
const showDiskTooltip = ref(false);
const showCoresBelow = ref(false);
const showMemBelow = ref(false);
const showDiskBelow = ref(false);

const handleTooltipEnter = (event, type) => {
  // Show the right tooltip
  if (type === 'cores') showCores.value = true;
  else if (type === 'mem') showMemTooltip.value = true;
  else if (type === 'disk') showDiskTooltip.value = true;

  // Check if tooltip would overflow the top of the viewport
  const el = event.currentTarget;
  const rect = el.getBoundingClientRect();
  // Estimate tooltip height ~120px for cores, ~80px for mem/disk
  const tooltipHeight = type === 'cores' ? 130 : 90;
  const showBelow = rect.top < tooltipHeight + 20;
  if (type === 'cores') showCoresBelow.value = showBelow;
  else if (type === 'mem') showMemBelow.value = showBelow;
  else if (type === 'disk') showDiskBelow.value = showBelow;
};

const whitelistPlayers = computed(() => {
  if (!status.value?.properties?.whitelistPlayers) return [];
  return status.value.properties.whitelistPlayers;
});

const formatMemory = (bytes) => {
  if (!bytes && bytes !== 0) return '0 MB';
  if (bytes === 0) return '0 MB';
  const mb = bytes / 1024 / 1024;
  if (mb >= 1024 * 1024) return (mb / (1024 * 1024)).toFixed(2) + ' TB';
  if (mb >= 1024) return (mb / 1024).toFixed(2) + ' GB';
  return mb.toFixed(2) + ' MB';
};

const formatMemoryInt = (bytes) => {
  if (!bytes && bytes !== 0) return '0 MB';
  if (bytes === 0) return '0 MB';
  const mb = bytes / 1024 / 1024;
  if (mb >= 1024 * 1024) return Math.round(mb / (1024 * 1024)) + ' TB';
  if (mb >= 1024) return Math.round(mb / 1024) + ' GB';
  return Math.round(mb) + ' MB';
};

const formatUptime = (ms) => {
  const totalSeconds = Math.floor(ms / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  
  if (days > 0) {
    return t('summary.uptimeFormat.days', { d: days, h: hours, m: minutes });
  } else if (hours > 0) {
    return t('summary.uptimeFormat.hours', { h: hours, m: minutes });
  } else if (minutes > 0) {
    return t('summary.uptimeFormat.minutes', { m: minutes });
  } else {
    return t('summary.uptimeFormat.seconds', { s: seconds });
  }
};

const formatUpdateTime = (date) => {
  if (!date) return '';
  const h = String(date.getHours()).padStart(2, '0');
  const m = String(date.getMinutes()).padStart(2, '0');
  const s = String(date.getSeconds()).padStart(2, '0');
  return `${h}:${m}:${s}`;
};

const formatBool = (val) => {
  if (val === true) return '\u2714';
  if (val === false) return '\u2716';
  return String(val);
};

const formatNumber = (num) => {
  if (!num && num !== 0) return '-';
  return num.toLocaleString();
};

const formatLevelType = (type) => {
  if (!type) return '-';
  return type.replace(/^minecraft:/, '');
};

const formatTickTime = (ms) => {
  if (!ms && ms !== 0) return '-';
  if (ms >= 60000) return (ms / 60000).toFixed(0) + ' min';
  if (ms >= 1000) return (ms / 1000).toFixed(1) + ' s';
  return ms + ' ms';
};

const formatIdleTimeout = (minutes) => {
  if (!minutes && minutes !== 0) return '-';
  if (minutes === 0) return '\u221E (disabled)';
  if (minutes >= 60) return (minutes / 60).toFixed(1) + ' h';
  return minutes + ' min';
};

const updateTimeDisplay = computed(() => {
  return formatUpdateTime(lastUpdated.value);
});

const handleStatusMsg = (msg) => {
  status.value = msg.data;
  lastUpdated.value = new Date();
};

const handleStatusDeltaMsg = (msg) => {
  if (msg.timestamp) {
    lastUpdated.value = new Date(msg.timestamp);
  } else {
    lastUpdated.value = new Date();
  }

  if (!status.value) {
    status.value = msg.data || {};
  } else if (msg.data) {
    // Deep merge
    for (const key in msg.data) {
      if (typeof msg.data[key] === 'object' && msg.data[key] !== null && !Array.isArray(msg.data[key]) && status.value[key]) {
        Object.assign(status.value[key], msg.data[key]);
      } else {
        status.value[key] = msg.data[key];
      }
    }
  }
};

onMounted(() => {
  on('status', handleStatusMsg);
  on('status_delta', handleStatusDeltaMsg);
  fetchStatus();
});

onUnmounted(() => {
  off('status', handleStatusMsg);
  off('status_delta', handleStatusDeltaMsg);
});
</script>

<style scoped>
.dashboard { margin-bottom: 2rem; }
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}
.stat-card {
  background: var(--card-bg);
  padding: 1.5rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px var(--shadow-color);
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
}
.full-width { grid-column: 1 / -1; }
.stat-card h3 {
  margin: 0 0 0.5rem 0;
  color: var(--text-secondary);
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.stat-card p {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-strong);
}
.stat-card p.text-md { font-size: 1.25rem; }

/* Whitelist Styles */
.whitelist-row {
  position: relative;
}
.whitelist-val-container {
  position: relative;
  display: flex;
  align-items: center;
}
.whitelist-hint-container {
  cursor: pointer;
  position: relative;
  display: inline-flex;
  flex-direction: column;
  justify-content: center;
}
.whitelist-hint-line {
  display: block;
  border-bottom: 2px dashed var(--border-hover);
  width: 100%;
  margin-top: 2px;
  opacity: 0.5;
  transition: opacity 0.2s;
}
.whitelist-hint-container:hover .whitelist-hint-line {
  opacity: 1;
  border-color: var(--text-secondary);
}
.whitelist-player-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 150px;
  overflow-y: auto;
}
.whitelist-player-list li {
  padding: 2px 0;
  font-family: 'Consolas', monospace;
  color: var(--text-strong);
}
.whitelist-player-list li::before {
  content: '\2022 ';
  color: #22c55e;
}
.whitelist-tooltip-empty {
  color: var(--text-secondary);
  font-style: italic;
}

/* Progress Bar Styles */
.progress-bar-container {
  margin-top: 0.5rem;
  position: relative;
}
.progress-bar-bg {
  background-color: var(--progress-bg);
  border-radius: 4px;
  height: 12px;
  width: 100%;
  overflow: hidden;
  position: relative;
}
.progress-bar-fill {
  background-color: #3b82f6; /* Default blue */
  height: 100%;
  transition: width 0.3s ease;
}
.progress-label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-strong);
  margin-top: 0.25rem;
  text-align: right;
}

/* Tooltip Generic */
.tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 8px;
  background: var(--tooltip-bg);
  color: var(--text-strong);
  border-radius: 6px;
  padding: 0.75rem;
  font-size: 0.85rem;
  z-index: 100;
  box-shadow: 0 4px 12px var(--tooltip-shadow);
  min-width: 200px;
  border: 1px solid var(--border-color);
}
.tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border-width: 6px;
  border-style: solid;
  border-color: var(--tooltip-bg) transparent transparent transparent;
  filter: drop-shadow(0 2px 2px var(--shadow-color));
}
.tooltip.show-below {
  bottom: auto;
  top: 100%;
  margin-top: 8px;
  margin-bottom: 0;
}
.tooltip.show-below::after {
  top: auto;
  bottom: 100%;
  border-color: transparent transparent var(--tooltip-bg) transparent;
  filter: drop-shadow(0 -2px 2px var(--shadow-color));
}
.tooltip-title, .whitelist-tooltip-title {
  font-weight: 600;
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--text-secondary);
  margin-bottom: 0.5rem;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 0.25rem;
}

/* CPU Cores */
.cores-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem;
}
.core-item {
  display: flex;
  justify-content: space-between;
}
.core-label {
  color: var(--text-secondary);
}

/* Memory & Disk Bars */
.memory-total, .disk-total {
  background-color: var(--progress-bg);
}
.memory-sys-used {
  background-color: #f59e0b;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  z-index: 1;
}
.memory-jvm-max, .disk-used {
  background-color: var(--text-secondary);
  height: 100%;
  position: relative;
}
.memory-jvm-max {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  opacity: 0.6; /* Semi-transparent so overlapping layers are visible */
}
.memory-jvm-used {
  background-color: #10b981;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  z-index: 3;
}
.disk-world {
  background-color: #8b5cf6;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  margin-right: 4px;
  flex-shrink: 0;
  box-sizing: border-box;
}
.used-dot { background-color: #10b981; }
.max-dot { background-color: var(--text-secondary); }
.sys-used-dot { background-color: #f59e0b; }
.total-dot { background-color: var(--dot-total); border: 1px solid var(--dot-total-border); }
.world-dot { background-color: #8b5cf6; }

.inline-legend-tooltip {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.inline-legend-tooltip div {
  display: flex;
  align-items: center;
}

.props-groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1.25rem;
  margin-top: 0.75rem;
}
.props-group { min-width: 0; }
.props-group-title {
  margin: 0 0 0.6rem 0;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text-secondary);
  padding-bottom: 0.4rem;
  border-bottom: 1px solid var(--border-color);
}
.props-table {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.prop-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.25rem 0;
  font-size: 0.88rem;
}
.prop-label { color: var(--text-secondary); flex-shrink: 0; margin-right: 1rem; }
.prop-value {
  color: var(--text-strong);
  font-weight: 500;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loading {
  text-align: center;
  padding: 2rem;
  color: var(--text-secondary);
  font-size: 1.2rem;
}

.update-time-tag {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 0.75rem;
  font-size: 0.8rem;
  color: var(--text-secondary);
  font-weight: 400;
  padding-right: 4px;
}
.update-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.5);
  animation: dot-pulse 2s infinite;
}
@keyframes dot-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>