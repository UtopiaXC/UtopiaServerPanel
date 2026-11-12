<template>
  <n-card size="small" style="border-radius: 12px;">
    <template #header>
      <div class="monitor-header">
        <div class="monitor-title-wrap">
          <span class="monitor-title">{{ title }}</span>
          <n-tag v-if="timeOffsetWeeks > 0" size="tiny" type="warning" round>
            -{{ timeOffsetWeeks }}w
          </n-tag>
        </div>

        <div class="monitor-controls">
          <n-button
            size="tiny"
            quaternary
            :title="$t('logs.shiftBack')"
            :disabled="loading"
            @click="shiftTimeBack"
          >
            <template #icon>
              <n-icon><ChevronBackOutline /></n-icon>
            </template>
          </n-button>
          <n-button
            size="tiny"
            quaternary
            :title="$t('common.refresh')"
            :loading="loading"
            @click="resetOrRefresh"
          >
            <template #icon>
              <n-icon><RefreshOutline /></n-icon>
            </template>
          </n-button>
          <n-button-group size="tiny">
            <n-button
              :type="timeRange === '1day' ? 'primary' : 'default'"
              @click="setTimeRange('1day')"
            >
              {{ $t('logs.range1d') }}
            </n-button>
            <n-button
              :type="timeRange === '1week' ? 'primary' : 'default'"
              @click="setTimeRange('1week')"
            >
              {{ $t('logs.range1w') }}
            </n-button>
            <n-button
              :type="timeRange === 'custom' ? 'primary' : 'default'"
              @click="setTimeRange('custom')"
            >
              {{ $t('logs.rangeCustom') }}
            </n-button>
          </n-button-group>
        </div>
      </div>
    </template>

    <div v-if="timeRange === 'custom'" class="custom-range-bar">
      <n-date-picker
        v-model:value="customRange"
        type="datetimerange"
        size="small"
        style="flex: 1; min-width: 220px;"
      />
      <n-button size="small" type="primary" :loading="loading" @click="fetchData">
        {{ $t('common.confirm') }}
      </n-button>
    </div>

    <div class="chart-box">
      <n-spin :show="loading" style="height: 100%; width: 100%;">
        <v-chart class="echart" :option="chartOption" autoresize />
      </n-spin>
    </div>
  </n-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import {
  TooltipComponent,
  GridComponent,
  DataZoomComponent,
  LegendComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ChevronBackOutline, RefreshOutline } from '@vicons/ionicons5'
import { monitorApi } from '../api/monitor'
import { useWebSocket } from '../composables/useWebSocket'
import { useThemeStore } from '../stores/theme'
import { formatBytes } from '../utils/format'

use([
  CanvasRenderer,
  LineChart,
  TooltipComponent,
  GridComponent,
  DataZoomComponent,
  LegendComponent
])

const props = defineProps<{
  title: string
  type: 'cpu' | 'memory' | 'tps' | 'disk' | 'player'
}>()

const { t } = useI18n()
const themeStore = useThemeStore()
const { onMonitorLog, onPlayerEvent } = useWebSocket()

const loading = ref(false)
const timeRange = ref<'1day' | '1week' | 'custom'>('1day')
const timeOffsetWeeks = ref(0)
const nowMs = Date.now()
const customRange = ref<[number, number]>([nowMs - 86400 * 1000, nowMs])

const logsData = ref<any[]>([])
const playerEventsData = ref<any[]>([])
const playerInitialPlayers = ref<string[]>([])
const lifecycleData = ref<any[]>([])
const showPlayerNames = ref(false)

let unsubMonitor: (() => void) | null = null
let unsubPlayer: (() => void) | null = null

function getRangeTimestamps(): { start: number; end: number } {
  if (timeRange.value === 'custom' && customRange.value) {
    return {
      start: Math.floor(customRange.value[0] / 1000),
      end: Math.floor(customRange.value[1] / 1000)
    }
  }
  const end = Math.floor(Date.now() / 1000)
  const start = timeRange.value === '1day' ? end - 86400 : end - 7 * 86400
  return { start, end }
}

function setTimeRange(range: '1day' | '1week' | 'custom') {
  timeRange.value = range
  timeOffsetWeeks.value = 0
  if (range === 'custom') {
    const end = Date.now()
    customRange.value = [end - 86400 * 1000, end]
  } else {
    fetchData()
  }
}

async function fetchData() {
  loading.value = true
  try {
    const { start, end } = getRangeTimestamps()
    const cfg = await monitorApi.getDisplayConfig()
    showPlayerNames.value = Boolean(cfg.showPlayerNames)

    if (props.type === 'player') {
      const [perfRes, evtRes, lcRes] = await Promise.all([
        monitorApi.queryPerfLogs(start, end),
        monitorApi.queryPlayerEvents(start, end),
        monitorApi.queryLifecycleRange(start, end)
      ])
      logsData.value = perfRes.logs || []
      playerInitialPlayers.value = evtRes.initialPlayers || []
      playerEventsData.value = evtRes.events || []
      lifecycleData.value = lcRes.events || []
    } else {
      const res = await monitorApi.queryPerfLogs(start, end)
      logsData.value = res.logs || []
    }
  } catch {
    // keep empty gracefully
  } finally {
    loading.value = false
  }
}

async function shiftTimeBack() {
  loading.value = true
  try {
    let currentOldest = Number.MAX_SAFE_INTEGER
    if (logsData.value.length > 0) {
      currentOldest = Math.min(currentOldest, logsData.value[0].ts)
    }
    if (playerEventsData.value.length > 0) {
      currentOldest = Math.min(currentOldest, playerEventsData.value[0].ts)
    }
    if (currentOldest === Number.MAX_SAFE_INTEGER) {
      currentOldest = getRangeTimestamps().start
    }

    const end = currentOldest
    const start = end - 7 * 86400

    if (props.type === 'player') {
      const [perfRes, evtRes, lcRes] = await Promise.all([
        monitorApi.queryPerfLogs(start, end),
        monitorApi.queryPlayerEvents(start, end),
        monitorApi.queryLifecycleRange(start, end)
      ])
      playerInitialPlayers.value = evtRes.initialPlayers || []
      logsData.value = [...(perfRes.logs || []), ...logsData.value]
      playerEventsData.value = [...(evtRes.events || []), ...playerEventsData.value]
      lifecycleData.value = [...(lcRes.events || []), ...lifecycleData.value]
    } else {
      const res = await monitorApi.queryPerfLogs(start, end)
      logsData.value = [...(res.logs || []), ...logsData.value]
    }
    timeOffsetWeeks.value++
  } finally {
    loading.value = false
  }
}

function resetOrRefresh() {
  timeOffsetWeeks.value = 0
  fetchData()
}

function formatAxisTs(ts: number): string {
  const d = new Date(ts * 1000)
  return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(
    d.getMinutes()
  ).padStart(2, '0')}`
}

const chartOption = computed(() => {
  const dark = themeStore.isDark
  const axisColor = dark ? '#94a3b8' : '#64748b'
  const gridColor = dark ? 'rgba(148, 163, 184, 0.12)' : 'rgba(100, 116, 139, 0.16)'
  const tooltipBg = dark ? 'rgba(24, 24, 28, 0.96)' : 'rgba(255, 255, 255, 0.96)'
  const tooltipBorder = dark ? '#33333d' : '#e2e8f0'
  const tooltipText = dark ? '#e5e7eb' : '#1f2937'

  const isBytes = props.type === 'memory' || props.type === 'disk'
  let series: any[] = []
  let xAxisData: string[] = []

  const tooltip: any = {
    trigger: 'axis',
    backgroundColor: tooltipBg,
    borderColor: tooltipBorder,
    textStyle: { color: tooltipText, fontSize: 12 },
    valueFormatter: (value: number) => (isBytes ? formatBytes(value) : value)
  }

  if (props.type === 'cpu') {
    xAxisData = logsData.value.map((l) => formatAxisTs(l.ts))
    series = [
      {
        name: t('logs.metricCpu'),
        type: 'line',
        data: logsData.value.map((l) => Number((l.cpu ?? 0).toFixed(2))),
        smooth: true,
        symbol: 'none',
        areaStyle: { opacity: 0.16 },
        itemStyle: { color: '#3b82f6' }
      }
    ]
  } else if (props.type === 'memory') {
    xAxisData = logsData.value.map((l) => formatAxisTs(l.ts))
    series = [
      {
        name: t('logs.jvmUsed'),
        type: 'line',
        data: logsData.value.map((l) => l.memJvm),
        smooth: true,
        symbol: 'none',
        areaStyle: { opacity: 0.14 },
        itemStyle: { color: dark ? '#63e2b7' : '#18a058' }
      },
      {
        name: t('logs.sysUsed'),
        type: 'line',
        data: logsData.value.map((l) => l.memSysUsed),
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#f59e0b' }
      },
      {
        name: t('logs.sysTotal'),
        type: 'line',
        data: logsData.value.map((l) => l.memSysTotal),
        smooth: true,
        symbol: 'none',
        lineStyle: { type: 'dashed' },
        itemStyle: { color: '#9ca3af' }
      }
    ]
  } else if (props.type === 'tps') {
    xAxisData = logsData.value.map((l) => formatAxisTs(l.ts))
    series = [
      {
        name: t('logs.metricTps'),
        type: 'line',
        data: logsData.value.map((l) => Number((l.tps ?? 20).toFixed(2))),
        smooth: true,
        symbol: 'none',
        areaStyle: { opacity: 0.18 },
        itemStyle: { color: dark ? '#63e2b7' : '#18a058' }
      }
    ]
  } else if (props.type === 'disk') {
    xAxisData = logsData.value.map((l) => formatAxisTs(l.ts))
    series = [
      {
        name: t('logs.gameFolder'),
        type: 'line',
        data: logsData.value.map((l) => l.gameFolder),
        smooth: true,
        symbol: 'none',
        areaStyle: { opacity: 0.14 },
        itemStyle: { color: '#8b5cf6' }
      },
      {
        name: t('logs.diskUsed'),
        type: 'line',
        data: logsData.value.map((l) => l.diskUsed),
        smooth: true,
        symbol: 'none',
        itemStyle: { color: '#ef4444' }
      },
      {
        name: t('logs.diskTotal'),
        type: 'line',
        data: logsData.value.map((l) => l.diskTotal),
        smooth: true,
        symbol: 'none',
        lineStyle: { type: 'dashed' },
        itemStyle: { color: '#9ca3af' }
      }
    ]
  } else if (props.type === 'player') {
    const events = playerEventsData.value
      .map((e) => ({
        ts: e.ts,
        isJoin: e.eventType === 1 || e.eventType === '1' || e.eventType === true,
        name: e.playerName || t('logs.anonPlayer')
      }))
      .sort((a, b) => a.ts - b.ts)

    const snapshots: { ts: number; count: number; players: string[] }[] = []
    const onlineSet = new Set<string>(playerInitialPlayers.value)

    events.forEach((e) => {
      if (e.isJoin) {
        onlineSet.add(e.name)
      } else {
        onlineSet.delete(e.name)
      }
      snapshots.push({ ts: e.ts, count: onlineSet.size, players: Array.from(onlineSet) })
    })

    const lcEvents = (lifecycleData.value || [])
      .map((e) => ({ ts: e.ts, type: Number(e.eventType) }))
      .sort((a, b) => a.ts - b.ts)

    const offlineIntervals: { start: number; end: number | null }[] = []
    for (let i = 0; i < lcEvents.length; i++) {
      const lc = lcEvents[i]
      if (lc.type === 1 || lc.type === 2 || lc.type === 3) {
        let offEnd: number | null = null
        for (let j = i + 1; j < lcEvents.length; j++) {
          if (lcEvents[j].type === 0) {
            offEnd = lcEvents[j].ts
            break
          }
        }
        offlineIntervals.push({ start: lc.ts, end: offEnd })
      }
    }

    const isOfflineAt = (ts: number) =>
      offlineIntervals.some((iv) => ts >= iv.start && (iv.end === null || ts <= iv.end))

    const getStateAt = (ts: number) => {
      let lo = 0
      let hi = snapshots.length - 1
      let result = -1
      while (lo <= hi) {
        const mid = (lo + hi) >> 1
        if (snapshots[mid].ts <= ts) {
          result = mid
          lo = mid + 1
        } else {
          hi = mid - 1
        }
      }
      if (result >= 0) {
        return { count: snapshots[result].count, players: snapshots[result].players }
      }
      return {
        count: playerInitialPlayers.value.length,
        players: [...playerInitialPlayers.value]
      }
    }

    const buckets = new Map<
      string,
      { ts: number; count: number; players: string[]; offline: boolean }
    >()

    logsData.value.forEach((l) => {
      const key = formatAxisTs(l.ts)
      if (!buckets.has(key)) {
        const offline = isOfflineAt(l.ts)
        if (offline) {
          buckets.set(key, { ts: l.ts, count: 0, players: [], offline: true })
        } else {
          const st = getStateAt(l.ts)
          buckets.set(key, { ts: l.ts, count: st.count, players: st.players, offline: false })
        }
      }
    })

    snapshots.forEach((s) => {
      const key = formatAxisTs(s.ts)
      const offline = isOfflineAt(s.ts)
      buckets.set(key, {
        ts: s.ts,
        count: offline ? 0 : s.count,
        players: offline ? [] : s.players,
        offline
      })
    })

    const sorted = Array.from(buckets.entries()).sort((a, b) => a[1].ts - b[1].ts)
    xAxisData = sorted.map((e) => e[0])

    series = [
      {
        name: t('logs.onlineCount'),
        type: 'line',
        step: 'end',
        data: sorted.map((e) => ({
          value: e[1].count,
          onlineList: e[1].players,
          offline: e[1].offline
        })),
        itemStyle: { color: '#6366f1' },
        areaStyle: { opacity: 0.14 }
      }
    ]

    tooltip.formatter = (params: any[]) => {
      if (!params || !params.length) return ''
      let html = `${params[0].axisValue}<br/>`
      for (const item of params) {
        if (item.data?.offline) {
          html += `${item.marker} <span style="color:#ef4444;font-weight:600;">${t('logs.serverOffline')}</span><br/>`
        } else {
          html += `${item.marker} ${item.seriesName}: <b>${item.data?.value ?? 0}</b><br/>`
          if (showPlayerNames.value && item.data?.onlineList?.length > 0) {
            html += `<span style="font-size:11px;color:${axisColor};word-break:break-all;white-space:normal;display:inline-block;max-width:220px;">[ ${item.data.onlineList.join(', ')} ]</span><br/>`
          }
        }
      }
      return html
    }
  }

  return {
    tooltip,
    legend: {
      top: 0,
      right: 6,
      icon: 'circle',
      textStyle: { color: axisColor, fontSize: 12 }
    },
    grid: {
      left: '2%',
      right: '3%',
      bottom: 42,
      top: 34,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xAxisData,
      axisLine: { lineStyle: { color: gridColor } },
      axisLabel: { color: axisColor, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: gridColor } },
      axisLabel: {
        color: axisColor,
        fontSize: 11,
        formatter: (value: number) => (isBytes ? formatBytes(value) : String(value))
      }
    },
    dataZoom: [
      {
        type: 'slider',
        show: true,
        bottom: 4,
        height: 18,
        start: 0,
        end: 100,
        borderColor: 'transparent',
        fillerColor: dark ? 'rgba(99, 226, 183, 0.14)' : 'rgba(24, 160, 88, 0.12)',
        textStyle: { color: axisColor }
      },
      { type: 'inside', start: 0, end: 100 }
    ],
    series
  }
})

onMounted(() => {
  fetchData()
  unsubMonitor = onMonitorLog((log) => {
    if (timeRange.value === '1day' && timeOffsetWeeks.value === 0) {
      logsData.value.push(log)
    }
  })
  unsubPlayer = onPlayerEvent((evt) => {
    if (props.type === 'player' && timeRange.value === '1day' && timeOffsetWeeks.value === 0) {
      playerEventsData.value.push(evt)
    }
  })
})

onUnmounted(() => {
  unsubMonitor?.()
  unsubPlayer?.()
})
</script>

<style scoped>
.monitor-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.monitor-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.monitor-title {
  font-size: 15px;
  font-weight: 600;
}

.monitor-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.custom-range-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.chart-box {
  height: 300px;
  width: 100%;
}

.echart {
  width: 100%;
  height: 300px;
}
</style>

