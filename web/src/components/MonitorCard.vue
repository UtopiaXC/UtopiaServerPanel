<template>
  <div class="chart-card">
    <div class="card-header">
      <div class="card-title">{{ title }} <span v-if="timeOffsetWeeks > 0" class="offset-badge">(-{{ timeOffsetWeeks }}w)</span></div>
      <div class="card-controls">
        <button class="icon-btn" @click="shiftTimeBack" title="Load 1 week earlier">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M11.354 1.646a.5.5 0 0 1 0 .708L5.707 8l5.647 5.646a.5.5 0 0 1-.708.708l-6-6a.5.5 0 0 1 0-.708l6-6a.5.5 0 0 1 .708 0z"/></svg>
        </button>
        <button class="icon-btn" @click="resetOrRefresh" :disabled="loading" title="Refresh">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M8 3a5 5 0 1 0 4.546 2.914.5.5 0 0 1 .908-.417A6 6 0 1 1 8 2v1z"/><path d="M8 4.466V.534a.25.25 0 0 1 .41-.192l2.36 1.966c.12.1.12.284 0 .384L8.41 4.658A.25.25 0 0 1 8 4.466z"/></svg>
        </button>
        
        <div class="time-range-btns">
          <button :class="{ active: timeRange === '1day' }" @click="setTimeRange('1day')">{{ $t('logs.last1Day') || 'Last 1 Day' }}</button>
          <button :class="{ active: timeRange === '1week' }" @click="setTimeRange('1week')">{{ $t('logs.last1Week') || 'Last 1 Week' }}</button>
          <button :class="{ active: timeRange === 'custom' }" @click="setTimeRange('custom')">{{ $t('logs.custom') || 'Custom' }}</button>
        </div>
      </div>
    </div>
    
    <div class="custom-time-row" v-if="timeRange === 'custom'">
      <input type="datetime-local" v-model="customStart" />
      <span> - </span>
      <input type="datetime-local" v-model="customEnd" />
      <button class="apply-btn" @click="fetchData">{{ $t('common.confirm') || 'OK' }}</button>
    </div>
    
    <div class="chart-container">
      <div v-if="loading" class="loading-overlay">
        <div class="spinner"></div>
      </div>
      <v-chart class="echart" :option="chartOption" autoresize />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, onUnmounted } from 'vue';
import { useI18n } from 'vue-i18n';
import VChart from 'vue-echarts';
import { use } from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { TooltipComponent, GridComponent, DataZoomComponent, LegendComponent, TitleComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { monitorAPI } from '../api/admin';
import { on, off } from '../api';

use([CanvasRenderer, LineChart, TooltipComponent, GridComponent, DataZoomComponent, LegendComponent, TitleComponent]);

const props = defineProps({
  title: String,
  type: String, // 'cpu', 'memory', 'tps', 'disk', 'player'
});

const { t } = useI18n();

const loading = ref(false);
const timeRange = ref('1day'); // 1day, 1week, custom
const timeOffsetWeeks = ref(0);
const customStart = ref('');
const customEnd = ref('');
const logsData = ref([]);
const playerEventsData = ref([]);
const playerInitialCount = ref(0);
const playerInitialPlayers = ref([]);
const lifecycleData = ref([]);

const showPlayerNames = ref(false);

const setTimeRange = (range) => {
  timeRange.value = range;
  timeOffsetWeeks.value = 0; // reset offset when switching range
  
  if (range === 'custom') {
    const now = new Date();
    const oneDayAgo = new Date(now.getTime() - 86400 * 1000);
    // Format to YYYY-MM-DDThh:mm
    const formatDt = (d) => {
      const pad = (n) => String(n).padStart(2, '0');
      return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    };
    customEnd.value = formatDt(now);
    customStart.value = formatDt(oneDayAgo);
  } else {
    fetchData();
  }
};

const shiftTimeBack = async () => {
  loading.value = true;
  try {
    // Current oldest timestamp in our data, or fallback to computed start
    let currentOldest = Number.MAX_SAFE_INTEGER;
    if (logsData.value.length > 0) currentOldest = Math.min(currentOldest, logsData.value[0].ts);
    if (playerEventsData.value && playerEventsData.value.length > 0) currentOldest = Math.min(currentOldest, playerEventsData.value[0].ts);
    
    if (currentOldest === Number.MAX_SAFE_INTEGER) {
      const { start } = getRangeTimestamps();
      currentOldest = start;
    }

    const end = currentOldest;
    const start = end - 7 * 86400;

    if (props.type === 'player') {
      const [perfRes, evtRes, lcRes] = await Promise.all([
        monitorAPI.queryPerfLogs(start, end),
        monitorAPI.queryPlayerEvents(start, end),
        monitorAPI.queryLifecycleRange(start, end)
      ]);
      const newLogs = perfRes.data.data.logs || [];
      const newEvts = evtRes.data.data.events || [];
      const newLc = lcRes.data.data.events || [];
      // Replace initial state with the older range's initial state
      playerInitialCount.value = evtRes.data.data.initialCount || 0;
      playerInitialPlayers.value = evtRes.data.data.initialPlayers || [];
      // Prepend data
      logsData.value = [...newLogs, ...logsData.value];
      playerEventsData.value = [...newEvts, ...playerEventsData.value];
      lifecycleData.value = [...newLc, ...lifecycleData.value];
    } else {
      const res = await monitorAPI.queryPerfLogs(start, end);
      const newLogs = res.data.data.logs || [];
      logsData.value = [...newLogs, ...logsData.value];
    }
    
    // We increase timeOffsetWeeks so real-time updates are paused if we are looking at history
    timeOffsetWeeks.value++;
  } catch (e) {
    console.error("Failed to load older data for", props.type, e);
  } finally {
    loading.value = false;
  }
};

const resetOrRefresh = () => {
  timeOffsetWeeks.value = 0;
  fetchData();
};

const getRangeTimestamps = () => {
  if (timeRange.value === 'custom') {
    const start = Math.floor(new Date(customStart.value).getTime() / 1000);
    const end = Math.floor(new Date(customEnd.value).getTime() / 1000);
    return { start, end };
  }

  const now = Math.floor(new Date().getTime() / 1000);
  
  let start, end;
  if (timeRange.value === '1day') {
    end = now;
    start = end - 86400;
  } else {
    // 1week
    end = now;
    start = end - 7 * 86400;
  }
  return { start, end };
};

const fetchData = async () => {
  loading.value = true;
  try {
    const { start, end } = getRangeTimestamps();
    const configRes = await monitorAPI.getDisplayConfig();
    showPlayerNames.value = configRes.data.data.showPlayerNames;

    if (props.type === 'player') {
      const [perfRes, evtRes, lcRes] = await Promise.all([
        monitorAPI.queryPerfLogs(start, end),
        monitorAPI.queryPlayerEvents(start, end),
        monitorAPI.queryLifecycleRange(start, end)
      ]);
      logsData.value = perfRes.data.data.logs || [];
      playerInitialCount.value = evtRes.data.data.initialCount || 0;
      playerInitialPlayers.value = evtRes.data.data.initialPlayers || [];
      playerEventsData.value = evtRes.data.data.events || [];
      lifecycleData.value = lcRes.data.data.events || [];
    } else {
      const res = await monitorAPI.queryPerfLogs(start, end);
      logsData.value = res.data.data.logs || [];
    }
  } catch (e) {
    console.error("Failed to fetch monitor data for", props.type, e);
  } finally {
    loading.value = false;
  }
};

const formatBytes = (bytes) => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const chartOption = computed(() => {
  const isBytes = props.type === 'memory' || props.type === 'disk';
  
  let series = [];
  let xAxisData = [];
  
  const baseTooltip = {
    trigger: 'axis',
    valueFormatter: (value) => isBytes ? formatBytes(value) : value
  };

  if (props.type === 'cpu') {
    xAxisData = logsData.value.map(l => {
      const d = new Date(l.ts * 1000);
      return `${d.getMonth()+1}-${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    });
    series = [{
      name: t('logs.cpuUsage'),
      type: 'line',
      data: logsData.value.map(l => l.cpu),
      smooth: true,
      symbol: 'none',
      areaStyle: { opacity: 0.2 },
      itemStyle: { color: '#3b82f6' }
    }];
  } 
  else if (props.type === 'memory') {
    xAxisData = logsData.value.map(l => {
      const d = new Date(l.ts * 1000);
      return `${d.getMonth()+1}-${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    });
    series = [
      {
        name: t('logs.jvmUsed'),
        type: 'line',
        data: logsData.value.map(l => l.memJvm),
        smooth: true,
        symbol: 'none',
        areaStyle: { opacity: 0.1 },
        itemStyle: { color: '#10b981' }
      },
      {
        name: t('logs.sysUsed'),
        type: 'line',
        data: logsData.value.map(l => l.memSysUsed),
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#f59e0b' }
      },
      {
        name: t('logs.sysTotal'),
        type: 'line',
        data: logsData.value.map(l => l.memSysTotal),
        smooth: true,
        symbol: 'none',
        lineStyle: { type: 'dashed' },
        itemStyle: { color: '#9ca3af' }
      }
    ];
  }
  else if (props.type === 'tps') {
    xAxisData = logsData.value.map(l => {
      const d = new Date(l.ts * 1000);
      return `${d.getMonth()+1}-${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    });
    series = [{
      name: 'TPS',
      type: 'line',
      data: logsData.value.map(l => l.tps),
      smooth: true,
      symbol: 'none',
      areaStyle: { opacity: 0.2 },
      itemStyle: { color: '#22c55e' }
    }];
  }
  else if (props.type === 'disk') {
    xAxisData = logsData.value.map(l => {
      const d = new Date(l.ts * 1000);
      return `${d.getMonth()+1}-${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    });
    series = [
      {
        name: t('logs.gameFolderSize'),
        type: 'line',
        data: logsData.value.map(l => l.gameFolder),
        smooth: true,
        symbol: 'none',
        areaStyle: { opacity: 0.1 },
        itemStyle: { color: '#8b5cf6' }
      },
      {
        name: t('logs.diskUsed'),
        type: 'line',
        data: logsData.value.map(l => l.diskUsed),
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#ef4444' }
      },
      {
        name: t('logs.diskTotal'),
        type: 'line',
        data: logsData.value.map(l => l.diskTotal),
        smooth: true,
        symbol: 'none',
        lineStyle: { type: 'dashed' },
        itemStyle: { color: '#9ca3af' }
      }
    ];
  }
  else if (props.type === 'player') {
    // === Step 1: Replay player events to build state snapshots ===
    const events = playerEventsData.value
      .map(e => ({
        ts: e.ts,
        isJoin: e.eventType === 1 || e.eventType === '1' || e.eventType === true,
        name: e.playerName
      }))
      .sort((a, b) => a.ts - b.ts);

    // State snapshots: sorted list of { ts, count, players }
    const snapshots = [];
    const onlineSet = new Set(playerInitialPlayers.value);

    // Build snapshots from events
    events.forEach(e => {
      if (e.isJoin) {
        onlineSet.add(e.name);
      } else {
        onlineSet.delete(e.name);
      }
      snapshots.push({ ts: e.ts, count: onlineSet.size, players: Array.from(onlineSet) });
    });

    // === Step 2: Build offline intervals from lifecycle data ===
    // eventType: 0=start, 1=normal stop, 2=abnormal stop, 3=crash
    const lcEvents = (lifecycleData.value || [])
      .map(e => ({ ts: e.ts, type: Number(e.eventType) }))
      .sort((a, b) => a.ts - b.ts);

    const offlineIntervals = []; // [{start, end}]
    for (let i = 0; i < lcEvents.length; i++) {
      const lc = lcEvents[i];
      // A stop event (1, 2, 3) marks start of offline period
      if (lc.type === 1 || lc.type === 2 || lc.type === 3) {
        const offStart = lc.ts;
        // Find next start event
        let offEnd = null;
        for (let j = i + 1; j < lcEvents.length; j++) {
          if (lcEvents[j].type === 0) {
            offEnd = lcEvents[j].ts;
            break;
          }
        }
        // If no matching start found, server is still offline till end of range
        offlineIntervals.push({ start: offStart, end: offEnd });
      }
    }

    const isOfflineAt = (ts) => {
      for (const iv of offlineIntervals) {
        if (ts >= iv.start && (iv.end === null || ts <= iv.end)) return true;
      }
      return false;
    };

    // === Step 3: Find state at any timestamp via binary search ===
    const getStateAt = (ts) => {
      // Binary search: find last snapshot with snapshot.ts <= ts
      let lo = 0, hi = snapshots.length - 1, result = -1;
      while (lo <= hi) {
        const mid = (lo + hi) >> 1;
        if (snapshots[mid].ts <= ts) {
          result = mid;
          lo = mid + 1;
        } else {
          hi = mid - 1;
        }
      }
      if (result >= 0) {
        return { count: snapshots[result].count, players: snapshots[result].players };
      }
      // Before any event: use initial state
      return { count: playerInitialPlayers.value.length, players: Array.from(playerInitialPlayers.value) };
    };

    // === Step 4: Build unified timeline from perf_logs timestamps + event timestamps ===
    const toMinuteKey = (ts) => {
      const d = new Date(ts * 1000);
      return `${d.getMonth()+1}-${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    };

    // Collect all unique timestamps (perf_logs + events)
    const minuteBuckets = new Map(); // minuteKey -> { ts, count, players, offline }

    // Add perf_log time points as skeleton
    logsData.value.forEach(l => {
      const key = toMinuteKey(l.ts);
      if (!minuteBuckets.has(key)) {
        const offline = isOfflineAt(l.ts);
        if (offline) {
          minuteBuckets.set(key, { ts: l.ts, count: 0, players: [], offline: true });
        } else {
          const state = getStateAt(l.ts);
          minuteBuckets.set(key, { ts: l.ts, count: state.count, players: state.players, offline: false });
        }
      }
    });

    // Add/overwrite with event time points (these are exact)
    snapshots.forEach(s => {
      const key = toMinuteKey(s.ts);
      const offline = isOfflineAt(s.ts);
      minuteBuckets.set(key, {
        ts: s.ts,
        count: offline ? 0 : s.count,
        players: offline ? [] : s.players,
        offline
      });
    });

    // Sort by timestamp
    const sortedEntries = Array.from(minuteBuckets.entries())
      .sort((a, b) => a[1].ts - b[1].ts);

    xAxisData = sortedEntries.map(e => e[0]);
    const dataPoints = sortedEntries.map(e => ({
      val: e[1].count,
      onlineList: e[1].players,
      offline: e[1].offline
    }));

    series = [{
      name: t('logs.onlineCount'),
      type: 'line',
      step: 'end',
      data: dataPoints.map(p => ({
        value: p.val,
        onlineList: p.onlineList,
        offline: p.offline
      })),
      itemStyle: { color: '#6366f1' },
      areaStyle: { opacity: 0.1 }
    }];

    baseTooltip.formatter = (params) => {
      let res = params[0].axisValue + '<br/>';
      params.forEach(item => {
        if (item.data.offline) {
          res += `${item.marker} <span style="color:#ef4444;font-weight:bold;">${t('logs.serverOffline')}</span><br/>`;
        } else {
          res += `${item.marker} ${item.seriesName}: <b>${item.data.value}</b><br/>`;
          if (showPlayerNames.value && item.data.onlineList && item.data.onlineList.length > 0) {
            res += `<span style="font-size:12px;color:#8b5cf6;word-break:break-all;white-space:normal;display:inline-block;max-width:200px;">[ ${item.data.onlineList.join(', ')} ]</span><br/>`;
          }
        }
      });
      return res;
    };
  }

  return {
    tooltip: baseTooltip,
    legend: {
      top: 0,
      right: 10,
      icon: 'circle',
      textStyle: { color: '#6b7280' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '12%', 
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xAxisData,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#6b7280' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f3f4f6' } },
      axisLabel: {
        color: '#6b7280',
        formatter: (value) => isBytes ? formatBytes(value) : value
      }
    },
    dataZoom: [
      {
        type: 'slider',
        show: true,
        bottom: 5,
        height: 20,
        start: 0,
        end: 100,
        borderColor: 'transparent',
        fillerColor: 'rgba(59, 130, 246, 0.1)',
        handleSize: '100%',
        textStyle: { color: '#9ca3af' }
      },
      {
        type: 'inside',
        start: 0,
        end: 100
      }
    ],
    series
  };
});

const handleNewMonitorLog = (msg) => {
  if (!msg.data) return;
  if (timeRange.value === '1day' && timeOffsetWeeks.value === 0) {
    logsData.value.push(msg.data);
  }
};

const handleNewPlayerEvent = (msg) => {
  if (!msg.data || props.type !== 'player') return;
  if (timeRange.value === '1day' && timeOffsetWeeks.value === 0) {
    playerEventsData.value.push(msg.data);
  }
};

onMounted(() => {
  fetchData();
  on('new_monitor_log', handleNewMonitorLog);
  on('new_player_event', handleNewPlayerEvent);
});

onUnmounted(() => {
  off('new_monitor_log', handleNewMonitorLog);
  off('new_player_event', handleNewPlayerEvent);
});

</script>

<style scoped>
.chart-card {
  background: var(--card-bg, #fff);
  border-radius: 8px;
  padding: 1rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  border: 1px solid var(--border-color, #e5e7eb);
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 380px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 600;
  font-size: 1.1rem;
  color: var(--text-primary, #111827);
}

.card-controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.icon-btn {
  background: transparent;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 4px;
  padding: 4px 8px;
  cursor: pointer;
  color: var(--text-secondary, #6b7280);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.icon-btn:hover:not(:disabled) {
  background: var(--bg-color, #f3f4f6);
  color: var(--primary-color, #3b82f6);
}

.icon-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.offset-badge {
  font-size: 0.8rem;
  color: #ef4444;
  margin-left: 0.25rem;
  font-weight: 500;
}

.custom-time-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.custom-time-row input[type="datetime-local"] {
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 0.85rem;
  color: var(--text-primary);
  background: var(--bg-color, #fff);
}

.apply-btn {
  background: var(--primary-color, #3b82f6);
  color: white;
  border: none;
  border-radius: 4px;
  padding: 4px 12px;
  font-size: 0.85rem;
  cursor: pointer;
}
.apply-btn:hover {
  background: #2563eb;
}

.time-range-btns {
  display: flex;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 4px;
  overflow: hidden;
}

.time-range-btns button {
  background: transparent;
  border: none;
  border-right: 1px solid var(--border-color, #e5e7eb);
  padding: 4px 12px;
  font-size: 0.85rem;
  cursor: pointer;
  color: var(--text-secondary, #6b7280);
  transition: all 0.2s;
}

.time-range-btns button:last-child {
  border-right: none;
}

.time-range-btns button:hover {
  background: var(--bg-color, #f3f4f6);
}

.time-range-btns button.active {
  background: var(--primary-color, #3b82f6);
  color: white;
}

.chart-container {
  flex: 1;
  min-height: 0;
  position: relative;
  width: 100%;
}

.loading-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.spinner {
  width: 30px;
  height: 30px;
  border: 3px solid var(--border-color, #e5e7eb);
  border-top-color: var(--primary-color, #3b82f6);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.echart {
  width: 100%;
  height: 100%;
}
</style>
