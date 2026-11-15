<template>
  <div class="profiler-page">
    <div class="header-bar">
      <div class="header-actions">
        <n-switch v-model:value="autoRefresh">
          <template #checked>{{ $t('profiler.autoRefreshOn') }}</template>
          <template #unchecked>{{ $t('profiler.autoRefreshOff') }}</template>
        </n-switch>
        <n-button type="primary" size="small" :loading="loading" @click="loadSnapshot(true)">
          {{ $t('profiler.sampleNow') }}
        </n-button>
      </div>
    </div>

    <n-spin :show="loading && !snapshot">
      <template v-if="snapshot">
        <!-- Summary Metrics -->
        <n-grid :cols="4" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-bottom: 14px;">
          <n-gi span="4 m:1">
            <n-card size="small" :bordered="false">
              <n-statistic :label="$t('profiler.avgMspt')" :value="snapshot.tickPhases.avgMspt.toFixed(2)">
                <template #suffix>ms</template>
              </n-statistic>
            </n-card>
          </n-gi>
          <n-gi span="4 m:1">
            <n-card size="small" :bordered="false">
              <n-statistic :label="$t('profiler.maxMspt')" :value="snapshot.tickPhases.maxMspt.toFixed(2)">
                <template #suffix>ms</template>
              </n-statistic>
            </n-card>
          </n-gi>
          <n-gi span="4 m:1">
            <n-card size="small" :bordered="false">
              <n-statistic :label="$t('profiler.tps')" :value="snapshot.tickPhases.tps.toFixed(2)" />
            </n-card>
          </n-gi>
          <n-gi span="4 m:1">
            <n-card size="small" :bordered="false">
              <n-statistic
                :label="$t('profiler.heapUsed')"
                :value="`${snapshot.jvm.heapUsedMb} / ${snapshot.jvm.heapMaxMb}`"
              >
                <template #suffix>MB</template>
              </n-statistic>
            </n-card>
          </n-gi>
        </n-grid>

        <!-- Tick Phases Chart -->
        <n-card size="small" :bordered="false" :title="$t('profiler.tickBreakdown')" style="margin-bottom: 14px;">
          <div ref="phaseChartRef" style="width: 100%; height: 150px;" />
        </n-card>

        <!-- Dimensions Overview -->
        <n-card size="small" :bordered="false" :title="$t('profiler.dimensionsTitle')" style="margin-bottom: 14px;">
          <n-data-table
            size="small"
            :columns="dimensionColumns"
            :data="snapshot.hotspots.dimensions"
            :pagination="false"
          />
        </n-card>

        <!-- Hotspots Tables -->
        <n-grid :cols="2" :x-gap="14" :y-gap="14" responsive="screen" item-responsive style="margin-bottom: 14px;">
          <n-gi span="2 l:1">
            <n-card size="small" :bordered="false" :title="$t('profiler.entityHotspots')">
              <n-data-table
                size="small"
                :columns="entityChunkColumns"
                :data="snapshot.hotspots.topEntityChunks"
                :pagination="false"
              />
            </n-card>
          </n-gi>
          <n-gi span="2 l:1">
            <n-card size="small" :bordered="false" :title="$t('profiler.blockEntityHotspots')">
              <n-data-table
                size="small"
                :columns="beChunkColumns"
                :data="snapshot.hotspots.topBlockEntityChunks"
                :pagination="false"
              />
            </n-card>
          </n-gi>
        </n-grid>

        <!-- Type Rankings & JVM Health -->
        <n-grid :cols="3" :x-gap="14" :y-gap="14" responsive="screen" item-responsive>
          <n-gi span="3 l:1">
            <n-card size="small" :bordered="false" :title="$t('profiler.topEntityTypes')">
              <div ref="entityTypeChartRef" style="width: 100%; height: 260px;" />
            </n-card>
          </n-gi>
          <n-gi span="3 l:1">
            <n-card size="small" :bordered="false" :title="$t('profiler.topBlockEntityTypes')">
              <div ref="beTypeChartRef" style="width: 100%; height: 260px;" />
            </n-card>
          </n-gi>
          <n-gi span="3 l:1">
            <n-card size="small" :bordered="false" :title="$t('profiler.jvmHealth')">
              <div class="jvm-list">
                <div class="jvm-row">
                  <span>{{ $t('profiler.heapCommitted') }}</span>
                  <span>{{ snapshot.jvm.heapCommittedMb }} MB</span>
                </div>
                <div class="jvm-row">
                  <span>{{ $t('profiler.nonHeap') }}</span>
                  <span>{{ snapshot.jvm.nonHeapUsedMb }} MB</span>
                </div>
                <div class="jvm-row">
                  <span>{{ $t('profiler.threads') }}</span>
                  <span>{{ snapshot.jvm.threadCount }} ({{ snapshot.jvm.daemonThreadCount }} daemon)</span>
                </div>
                <div class="jvm-row">
                  <span>{{ $t('profiler.deadlocked') }}</span>
                  <n-tag :type="snapshot.jvm.deadlockedThreads > 0 ? 'error' : 'success'" size="small">
                    {{ snapshot.jvm.deadlockedThreads }}
                  </n-tag>
                </div>
                <n-divider style="margin: 8px 0;" />
                <div v-for="gc in snapshot.jvm.gc" :key="gc.name" class="jvm-row">
                  <span>{{ gc.name }}</span>
                  <span>{{ gc.count }}x / {{ gc.timeMs }} ms</span>
                </div>
              </div>
            </n-card>
          </n-gi>
        </n-grid>
      </template>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NSpace, useMessage, type DataTableColumns } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])
import { profilerApi, type ChunkHotspot, type DimensionHotspot, type ProfilerSnapshot } from '../api/profiler'
import { getApiErrorMessage } from '../api/client'
import { useThemeStore } from '../stores/theme'

const router = useRouter()
const message = useMessage()
const { t } = useI18n()
const themeStore = useThemeStore()

const loading = ref(false)
const autoRefresh = ref(false)
const snapshot = ref<ProfilerSnapshot | null>(null)

const phaseChartRef = ref<HTMLElement | null>(null)
const entityTypeChartRef = ref<HTMLElement | null>(null)
const beTypeChartRef = ref<HTMLElement | null>(null)
let phaseChart: echarts.ECharts | null = null
let entityTypeChart: echarts.ECharts | null = null
let beTypeChart: echarts.ECharts | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

const dimensionColumns = computed<DataTableColumns<DimensionHotspot>>(() => [
  { title: t('profiler.colDimension'), key: 'dimension' },
  { title: t('profiler.colChunks'), key: 'loadedChunks' },
  { title: t('profiler.colEntities'), key: 'entityCount' },
  { title: t('profiler.colBlockEntities'), key: 'blockEntityCount' },
  { title: t('profiler.colPlayers'), key: 'playerCount' }
])

function buildChunkColumns(countKey: 'entityCount' | 'blockEntityCount'): DataTableColumns<ChunkHotspot> {
  return [
    { title: t('profiler.colDimension'), key: 'dimension', ellipsis: true },
    {
      title: t('profiler.colChunkCoords'),
      key: 'chunkX',
      render: (row) => `${row.chunkX}, ${row.chunkZ} (${row.centerBlockX}, ${row.centerBlockZ})`
    },
    { title: t('profiler.colCount'), key: countKey, width: 75 },
    { title: t('profiler.colTopTypes'), key: 'topSummary', ellipsis: true },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 145,
      render: (row) => {
        const buttons: any[] = []
        if (snapshot.value?.mapEnabled) {
          buttons.push(
            h(
              NButton,
              {
                size: 'tiny',
                secondary: true,
                onClick: () =>
                  router.push({
                    path: '/map',
                    query: { dim: row.dimension, x: String(row.centerBlockX), z: String(row.centerBlockZ) }
                  })
              },
              { default: () => t('profiler.viewMap') }
            )
          )
        }
        buttons.push(
          h(
            NButton,
            {
              size: 'tiny',
              type: 'primary',
              onClick: () => handleTeleport(row)
            },
            { default: () => 'TP' }
          )
        )
        return h(NSpace, { size: 'small' }, { default: () => buttons })
      }
    }
  ]
}

const entityChunkColumns = computed(() => buildChunkColumns('entityCount'))
const beChunkColumns = computed(() => buildChunkColumns('blockEntityCount'))

async function handleTeleport(row: ChunkHotspot) {
  try {
    await profilerApi.teleport({
      dimension: row.dimension,
      x: row.centerBlockX,
      y: 100,
      z: row.centerBlockZ
    })
    message.success(t('map.teleportSent'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

function renderCharts() {
  if (!snapshot.value) return
  const textColor = themeStore.isDark ? '#cbd5e1' : '#334155'

  if (phaseChartRef.value) {
    if (!phaseChart) phaseChart = echarts.init(phaseChartRef.value)
    const phases = snapshot.value.tickPhases.phases || []
    phaseChart.setOption({
      tooltip: { trigger: 'item' },
      grid: { left: 20, right: 20, top: 20, bottom: 26, containLabel: true },
      xAxis: { type: 'value', name: 'ms', axisLabel: { color: textColor } },
      yAxis: {
        type: 'category',
        data: phases.map((p) => t(`profiler.phase_${p.key}`) || p.key),
        axisLabel: { color: textColor }
      },
      series: [
        {
          type: 'bar',
          data: phases.map((p) => Number(p.ms.toFixed(3))),
          itemStyle: { color: '#3b82f6', borderRadius: [0, 4, 4, 0] }
        }
      ]
    })
  }

  renderTypeBar(
    entityTypeChartRef.value,
    (c) => {
      entityTypeChart = c
    },
    () => entityTypeChart,
    snapshot.value.hotspots.topEntityTypes || [],
    '#10b981',
    textColor
  )
  renderTypeBar(
    beTypeChartRef.value,
    (c) => {
      beTypeChart = c
    },
    () => beTypeChart,
    snapshot.value.hotspots.topBlockEntityTypes || [],
    '#f59e0b',
    textColor
  )
}

function renderTypeBar(
  el: HTMLElement | null,
  assign: (c: echarts.ECharts) => void,
  getter: () => echarts.ECharts | null,
  items: { typeId: string; count: number }[],
  color: string,
  textColor: string
) {
  if (!el) return
  let chart = getter()
  if (!chart) {
    chart = echarts.init(el)
    assign(chart)
  }
  const entries = items.slice(0, 8).reverse()
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 10, right: 20, top: 10, bottom: 16, containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: textColor } },
    yAxis: {
      type: 'category',
      data: entries.map((e) => (e.typeId || '').replace('minecraft:', '')),
      axisLabel: { color: textColor, fontSize: 11 }
    },
    series: [{ type: 'bar', data: entries.map((e) => e.count), itemStyle: { color } }]
  })
}

async function loadSnapshot(refresh = false) {
  loading.value = true
  try {
    snapshot.value = await profilerApi.getSnapshot(refresh)
    await nextTick()
    renderCharts()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

watch(autoRefresh, (val) => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (val) {
    pollTimer = setInterval(() => loadSnapshot(true), 10000)
  }
})

function handleResize() {
  phaseChart?.resize()
  entityTypeChart?.resize()
  beTypeChart?.resize()
}

onMounted(() => {
  loadSnapshot(true)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  window.removeEventListener('resize', handleResize)
  phaseChart?.dispose()
  entityTypeChart?.dispose()
  beTypeChart?.dispose()
})
</script>

<style scoped>
.header-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.jvm-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}

.jvm-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
