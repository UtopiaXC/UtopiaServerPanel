<template>
  <div class="map-page">
    <n-spin :show="loading">
      <n-alert
        v-if="bootstrap && !bootstrap.enabled"
        type="warning"
        :title="$t('map.disabledTitle')"
        style="margin-bottom: 12px;"
      >
        {{ $t('map.disabledDesc') }}
      </n-alert>

      <n-alert
        v-else-if="bootstrap && !bootstrap.healthy"
        type="error"
        :title="$t('map.unhealthyTitle')"
        style="margin-bottom: 12px;"
      >
        <div>{{ bootstrap.lastError || $t('map.unhealthyDesc') }}</div>
        <div v-if="isAdmin" style="margin-top: 8px; display: flex; gap: 8px;">
          <n-button size="small" type="primary" @click="handleResetHealth">
            {{ $t('map.resetHealth') }}
          </n-button>
          <n-button size="small" @click="handleClearCache">
            {{ $t('map.clearCache') }}
          </n-button>
        </div>
      </n-alert>

      <div v-if="bootstrap && bootstrap.enabled" class="map-shell">
        <!-- Top Toolbar -->
        <n-card size="small" :bordered="false" class="map-toolbar">
          <div class="toolbar-row">
            <div class="toolbar-group">
              <n-select
                v-model:value="activeDim"
                :options="dimensionOptions"
                size="small"
                style="width: 170px;"
                @update:value="handleDimensionChange"
              />
              <n-input
                v-model:value="searchQuery"
                size="small"
                clearable
                :placeholder="$t('map.searchPlaceholder')"
                style="width: 220px;"
                @update:value="handleSearchUpdate"
              />
              <n-button size="small" secondary @click="showJumpModal = true">
                {{ $t('map.jumpCoords') }}
              </n-button>
              <n-button
                v-if="bootstrap.canManageWaypoints && bootstrap.visibleLayers.waypoints"
                size="small"
                type="primary"
                secondary
                @click="openAddWaypointModal(centerBlockX, centerBlockZ)"
              >
                {{ $t('map.addWaypoint') }}
              </n-button>
            </div>

            <div class="toolbar-group">
              <n-button-group size="small">
                <n-button @click="adjustZoom(1.4)">+</n-button>
                <n-button @click="resetView">1:1</n-button>
                <n-button @click="adjustZoom(1 / 1.4)">-</n-button>
              </n-button-group>
              <n-button size="small" secondary @click="refreshOverlays">
                {{ $t('common.refresh') }}
              </n-button>
              <n-button v-if="isAdmin" size="small" secondary @click="handleClearCache">
                {{ $t('map.clearCache') }}
              </n-button>
            </div>
          </div>

          <!-- Layer Filter Toggles -->
          <div class="layers-row">
            <span class="layers-label">{{ $t('map.layers') }}:</span>
            <n-tag
              v-if="bootstrap.visibleLayers.terrain"
              size="small"
              checkable
              v-model:checked="layerFlags.terrain"
              @update:checked="scheduleDraw"
            >
              {{ $t('map.layerTerrain') }}
            </n-tag>
            <n-tag
              v-if="bootstrap.visibleLayers.grid !== false"
              size="small"
              checkable
              v-model:checked="layerFlags.grid"
              @update:checked="scheduleDraw"
            >
              {{ $t('map.layerGrid') }}
            </n-tag>
            <n-tag
              v-if="bootstrap.visibleLayers.players"
              size="small"
              checkable
              v-model:checked="layerFlags.players"
              @update:checked="scheduleDraw"
            >
              {{ $t('map.layerPlayers') }} ({{ overlays.players.length }})
            </n-tag>
            <n-tag
              v-if="bootstrap.visibleLayers.entities"
              size="small"
              checkable
              v-model:checked="layerFlags.entities"
              @update:checked="scheduleDraw"
            >
              {{ $t('map.layerEntities') }} ({{ overlays.entities.length }})
            </n-tag>
            <n-tag
              v-if="bootstrap.visibleLayers.structures"
              size="small"
              checkable
              v-model:checked="layerFlags.structures"
              @update:checked="scheduleDraw"
            >
              {{ $t('map.layerStructures') }} ({{ overlays.structures.length }})
            </n-tag>
            <n-tag
              v-if="bootstrap.visibleLayers.waypoints"
              size="small"
              checkable
              v-model:checked="layerFlags.waypoints"
              @update:checked="scheduleDraw"
            >
              {{ $t('map.layerWaypoints') }} ({{ overlays.waypoints.length }})
            </n-tag>
          </div>
        </n-card>

        <!-- Main Canvas + Search Sidebar -->
        <div class="map-body">
          <div ref="canvasHostRef" class="canvas-stage" @contextmenu.prevent="handleContextMenu">
            <canvas
              ref="canvasRef"
              class="map-canvas"
              @mousedown="onMouseDown"
              @mousemove="onMouseMove"
              @mouseup="onMouseUp"
              @mouseleave="onMouseLeave"
              @wheel.prevent="onWheel"
              @click="onCanvasClick"
            />

            <!-- Coordinate HUD -->
            <div v-if="bootstrap.visibleLayers.coordinates !== false" class="hud-coords">
              <span>{{ activeDimLabel }}</span>
              <span>X: {{ hoverBlockX }}, Z: {{ hoverBlockZ }}</span>
              <span>{{ $t('map.chunk') }}: {{ Math.floor(hoverBlockX / 16) }}, {{ Math.floor(hoverBlockZ / 16) }}</span>
              <span>{{ $t('map.zoom') }}: {{ scaleLabel }}</span>
            </div>

            <!-- Selected Marker Popover Card -->
            <n-card
              v-if="selectedMarker"
              size="small"
              class="marker-card"
              :title="displayMarkerName(selectedMarker)"
              closable
              @close="selectedMarker = null"
            >
              <div class="marker-meta">
                <div>
                  {{ $t('map.markerType') }}: {{ displayMarkerCategory(selectedMarker) }}
                  <template v-if="selectedMarker.typeId"> · {{ selectedMarker.typeId }}</template>
                </div>
                <div>
                  {{ $t('map.coordinates') }}: {{ Math.round(selectedMarker.x) }},
                  {{ Math.round(selectedMarker.y) }}, {{ Math.round(selectedMarker.z) }}
                </div>
              </div>
              <div style="margin-top: 8px; display: flex; gap: 8px;">
                <n-button
                  v-if="bootstrap.canTeleport"
                  size="tiny"
                  type="primary"
                  @click="handleTeleport(selectedMarker.x, selectedMarker.y, selectedMarker.z)"
                >
                  {{ $t('map.teleportHere') }}
                </n-button>
                <n-button
                  v-if="(selectedMarker.category === 'waypoint' || selectedMarker.kind === 'waypoint') && bootstrap.canManageWaypoints"
                  size="tiny"
                  type="error"
                  secondary
                  @click="handleDeleteWaypoint(selectedMarker.id)"
                >
                  {{ $t('common.delete') }}
                </n-button>
              </div>
            </n-card>
          </div>

          <!-- Search / Marker List Side Card -->
          <n-card size="small" :bordered="false" class="marker-sidebar" :title="$t('map.markersTitle')">
            <div v-if="filteredMarkers.length === 0" class="empty-hint">
              {{ $t('common.noData') }}
            </div>
            <div v-else class="marker-list">
              <div
                v-for="m in filteredMarkers.slice(0, 120)"
                :key="m.id || `${m.name}-${m.x}-${m.z}`"
                class="marker-item"
                @click="focusMarker(m)"
              >
                <div class="marker-item-top">
                  <span class="marker-name">{{ displayMarkerName(m) }}</span>
                  <n-tag size="tiny" :bordered="false" :type="markerTagType(m.category || m.kind || 'other')">
                    {{ displayMarkerCategory(m) }}
                  </n-tag>
                </div>
                <div class="marker-item-coords">
                  {{ Math.round(m.x) }}, {{ Math.round(m.y) }}, {{ Math.round(m.z) }}
                </div>
              </div>
            </div>
          </n-card>
        </div>
      </div>
    </n-spin>

    <!-- Jump Coordinates Modal -->
    <n-modal v-model:show="showJumpModal" preset="card" :title="$t('map.jumpCoords')" style="max-width: 380px;">
      <n-form label-placement="left" :label-width="80">
        <n-form-item label="X">
          <n-input-number v-model:value="jumpX" style="width: 100%;" />
        </n-form-item>
        <n-form-item label="Z">
          <n-input-number v-model:value="jumpZ" style="width: 100%;" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="showJumpModal = false">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" @click="confirmJump">{{ $t('common.confirm') }}</n-button>
        </div>
      </template>
    </n-modal>

    <!-- Add Waypoint Modal -->
    <n-modal v-model:show="showWaypointModal" preset="card" :title="$t('map.addWaypoint')" style="max-width: 420px;">
      <n-form label-placement="left" :label-width="90">
        <n-form-item :label="$t('map.waypointName')">
          <n-input v-model:value="wpForm.name" />
        </n-form-item>
        <n-form-item label="X / Y / Z">
          <div style="display: flex; gap: 6px; width: 100%;">
            <n-input-number v-model:value="wpForm.x" style="flex: 1;" />
            <n-input-number v-model:value="wpForm.y" style="flex: 1;" />
            <n-input-number v-model:value="wpForm.z" style="flex: 1;" />
          </div>
        </n-form-item>
        <n-form-item :label="$t('map.waypointColor')">
          <n-color-picker v-model:value="wpForm.color" :show-alpha="false" />
        </n-form-item>
        <n-form-item :label="$t('map.waypointPublic')">
          <n-switch v-model:value="wpForm.isPublic" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="showWaypointModal = false">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" :loading="savingWaypoint" @click="submitWaypoint">
            {{ $t('common.save') }}
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { mapApi, type MapBootstrap, type MapMarker, type MapOverlays } from '../api/map'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import {
  localizeDimensionName,
  localizeMarkerCategory,
  localizeMarkerName
} from '../utils/mcI18n'

const route = useRoute()
const message = useMessage()
const { t, locale } = useI18n()
const authStore = useAuthStore()

const isAdmin = computed(() => authStore.hasPermission('admin', 2))

const loading = ref(true)
const bootstrap = ref<MapBootstrap | null>(null)
const activeDim = ref('minecraft:overworld')
const searchQuery = ref('')
const overlays = ref<MapOverlays>({ players: [], entities: [], structures: [], waypoints: [] })
const availableRegions = ref<Set<string>>(new Set())

const layerFlags = reactive({
  terrain: true,
  grid: true,
  players: true,
  entities: true,
  structures: true,
  waypoints: true
})

const canvasHostRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)

// Camera: world block coordinates at viewport center, and blocks-per-CSS-pixel (scale = pixels per block)
const camX = ref(0)
const camZ = ref(0)
const pxPerBlock = ref(1.0) // ranges 0.125 .. 6.0

const hoverBlockX = ref(0)
const hoverBlockZ = ref(0)
const selectedMarker = ref<MapMarker | null>(null)

const showJumpModal = ref(false)
const jumpX = ref(0)
const jumpZ = ref(0)

const showWaypointModal = ref(false)
const savingWaypoint = ref(false)
const wpForm = reactive({
  name: '',
  x: 0,
  y: 64,
  z: 0,
  color: '#3b82f6',
  isPublic: true
})

const tileCache = new Map<string, HTMLImageElement | 'loading' | 'empty'>()
const cacheBust = ref(0)
let drawScheduled = false
let overlayTimer: ReturnType<typeof setInterval> | null = null
let dragging = false
let dragMoved = false
let lastMouseX = 0
let lastMouseY = 0
let resizeObserver: ResizeObserver | null = null

const dimensionOptions = computed(() =>
  (bootstrap.value?.dimensions || []).map((d) => ({
    label: localizeDimensionName(d.id, d.name, locale.value),
    value: d.id
  }))
)

const activeDimLabel = computed(() => {
  const match = bootstrap.value?.dimensions.find((d) => d.id === activeDim.value)
  return localizeDimensionName(activeDim.value, match?.name, locale.value)
})

const centerBlockX = computed(() => Math.round(camX.value))
const centerBlockZ = computed(() => Math.round(camZ.value))
const scaleLabel = computed(() => `${pxPerBlock.value.toFixed(2)}x`)

function displayMarkerName(m: MapMarker): string {
  return localizeMarkerName(m, locale.value)
}

function displayMarkerCategory(m: MapMarker): string {
  const cat = m.category || m.kind || 'other'
  return localizeMarkerCategory(cat, locale.value)
}

function matchesQuery(m: MapMarker, q: string): boolean {
  if (!q) return true
  const display = displayMarkerName(m).toLowerCase()
  const catLabel = displayMarkerCategory(m).toLowerCase()
  const rawName = String(m.name || '').toLowerCase()
  const typeId = String(m.typeId || m.subType || '').toLowerCase()
  const cat = String(m.category || m.kind || '').toLowerCase()
  return (
    display.includes(q) ||
    catLabel.includes(q) ||
    rawName.includes(q) ||
    typeId.includes(q) ||
    cat.includes(q)
  )
}

const filteredMarkers = computed(() => {
  const all: MapMarker[] = []
  if (layerFlags.players) all.push(...overlays.value.players)
  if (layerFlags.waypoints) all.push(...overlays.value.waypoints)
  if (layerFlags.structures) all.push(...overlays.value.structures)
  if (layerFlags.entities) all.push(...overlays.value.entities)
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return all
  return all.filter((m) => matchesQuery(m, q))
})

function markerTagType(cat: string): 'success' | 'warning' | 'info' | 'error' | 'default' {
  if (cat === 'player') return 'success'
  if (cat === 'structure') return 'warning'
  if (cat === 'waypoint') return 'info'
  return 'default'
}

function chooseTileZoom(): number {
  if (pxPerBlock.value >= 0.45) return 0
  if (pxPerBlock.value >= 0.2) return -1
  return -2
}

function scheduleDraw() {
  if (drawScheduled) return
  drawScheduled = true
  requestAnimationFrame(() => {
    drawScheduled = false
    drawCanvas()
  })
}

function getOrLoadTile(dim: string, z: number, tx: number, tz: number): HTMLImageElement | null {
  const key = `${dim}|${z}|${tx}|${tz}|${cacheBust.value}`
  const existing = tileCache.get(key)
  if (existing === 'loading' || existing === 'empty') return null
  if (existing) {
    // refresh LRU position
    tileCache.delete(key)
    tileCache.set(key, existing)
    return existing
  }
  // Keep cache bounded
  if (tileCache.size > 180) {
    const oldest = tileCache.keys().next().value
    if (oldest) tileCache.delete(oldest)
  }
  tileCache.set(key, 'loading')
  const img = new Image()
  img.crossOrigin = 'anonymous'
  img.onload = () => {
    tileCache.set(key, img)
    scheduleDraw()
  }
  img.onerror = () => {
    tileCache.set(key, 'empty')
  }
  img.src = mapApi.getTileUrl(dim, z, tx, tz, cacheBust.value || undefined)
  return null
}

function drawCanvas() {
  const canvas = canvasRef.value
  if (!canvas || !bootstrap.value?.enabled) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const dpr = window.devicePixelRatio || 1
  const w = canvas.width / dpr
  const h = canvas.height / dpr
  ctx.save()
  ctx.scale(dpr, dpr)

  // Background
  ctx.fillStyle = '#0f172a'
  ctx.fillRect(0, 0, w, h)

  const scale = pxPerBlock.value
  const halfWBlocks = w / 2 / scale
  const halfHBlocks = h / 2 / scale
  const minWorldX = camX.value - halfWBlocks
  const maxWorldX = camX.value + halfWBlocks
  const minWorldZ = camZ.value - halfHBlocks
  const maxWorldZ = camZ.value + halfHBlocks

  // Terrain Tiles
  if (layerFlags.terrain && bootstrap.value.visibleLayers.terrain) {
    const zLevel = chooseTileZoom()
    const tileWorldSpan = 512 << -zLevel
    const minTx = Math.floor(minWorldX / tileWorldSpan)
    const maxTx = Math.floor(maxWorldX / tileWorldSpan)
    const minTz = Math.floor(minWorldZ / tileWorldSpan)
    const maxTz = Math.floor(maxWorldZ / tileWorldSpan)
    ctx.imageSmoothingEnabled = scale < 1.2

    for (let tx = minTx; tx <= maxTx; tx++) {
      for (let tz = minTz; tz <= maxTz; tz++) {
        // For z=0 check if region list knows it's non-existent? Still allow on-demand render if near
        const img = getOrLoadTile(activeDim.value, zLevel, tx, tz)
        const sx = (tx * tileWorldSpan - camX.value) * scale + w / 2
        const sy = (tz * tileWorldSpan - camZ.value) * scale + h / 2
        const sizePx = tileWorldSpan * scale
        if (img) {
          ctx.drawImage(img, sx, sy, sizePx, sizePx)
        } else {
          ctx.fillStyle = 'rgba(30, 41, 59, 0.45)'
          ctx.fillRect(sx, sy, sizePx, sizePx)
        }
      }
    }
  }

  // Region / Chunk Grid
  if (layerFlags.grid && bootstrap.value.visibleLayers.grid !== false) {
    const gridStep = scale >= 1.5 ? 16 : scale >= 0.4 ? 64 : 512
    ctx.strokeStyle = gridStep === 512 ? 'rgba(148,163,184,0.22)' : 'rgba(148,163,184,0.12)'
    ctx.lineWidth = 1
    const gxStart = Math.floor(minWorldX / gridStep) * gridStep
    const gzStart = Math.floor(minWorldZ / gridStep) * gridStep
    ctx.beginPath()
    for (let gx = gxStart; gx <= maxWorldX; gx += gridStep) {
      const sx = Math.round((gx - camX.value) * scale + w / 2) + 0.5
      ctx.moveTo(sx, 0)
      ctx.lineTo(sx, h)
    }
    for (let gz = gzStart; gz <= maxWorldZ; gz += gridStep) {
      const sy = Math.round((gz - camZ.value) * scale + h / 2) + 0.5
      ctx.moveTo(0, sy)
      ctx.lineTo(w, sy)
    }
    ctx.stroke()
  }

  // Draw markers in layering order
  const markersToDraw: MapMarker[] = []
  if (layerFlags.structures && bootstrap.value.visibleLayers.structures) {
    markersToDraw.push(...overlays.value.structures)
  }
  if (layerFlags.entities && bootstrap.value.visibleLayers.entities) {
    markersToDraw.push(...overlays.value.entities)
  }
  if (layerFlags.waypoints && bootstrap.value.visibleLayers.waypoints) {
    markersToDraw.push(...overlays.value.waypoints)
  }
  if (layerFlags.players && bootstrap.value.visibleLayers.players) {
    markersToDraw.push(...overlays.value.players)
  }

  const q = searchQuery.value.trim().toLowerCase()
  for (const m of markersToDraw) {
    if (!matchesQuery(m, q)) {
      continue
    }
    const sx = (m.x - camX.value) * scale + w / 2
    const sy = (m.z - camZ.value) * scale + h / 2
    if (sx < -40 || sx > w + 40 || sy < -40 || sy > h + 40) continue

    drawMarker(ctx, m, sx, sy)
  }

  ctx.restore()
}

function drawMarker(ctx: CanvasRenderingContext2D, m: MapMarker, sx: number, sy: number) {
  ctx.save()
  ctx.font = '12px sans-serif'
  const cat = m.category || m.kind || 'other'
  const label = displayMarkerName(m)
  if (cat === 'player') {
    ctx.fillStyle = '#22c55e'
    ctx.strokeStyle = '#ffffff'
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.arc(sx, sy, 6, 0, Math.PI * 2)
    ctx.fill()
    ctx.stroke()
    drawLabel(ctx, label, sx, sy - 10, '#f8fafc')
  } else if (cat === 'waypoint') {
    ctx.fillStyle = m.color || '#3b82f6'
    ctx.strokeStyle = '#ffffff'
    ctx.lineWidth = 1.5
    ctx.beginPath()
    ctx.moveTo(sx, sy + 7)
    ctx.lineTo(sx - 6, sy - 4)
    ctx.lineTo(sx + 6, sy - 4)
    ctx.closePath()
    ctx.fill()
    ctx.stroke()
    drawLabel(ctx, label, sx, sy - 10, '#e0f2fe')
  } else if (cat === 'structure' || m.kind === 'structure') {
    ctx.fillStyle = '#f59e0b'
    ctx.strokeStyle = '#1e293b'
    ctx.lineWidth = 1.5
    ctx.beginPath()
    ctx.moveTo(sx, sy - 6)
    ctx.lineTo(sx + 6, sy)
    ctx.lineTo(sx, sy + 6)
    ctx.lineTo(sx - 6, sy)
    ctx.closePath()
    ctx.fill()
    ctx.stroke()
    drawLabel(ctx, label, sx, sy - 9, '#fde68a')
  } else {
    const sub = m.category || m.subType || ''
    ctx.fillStyle = sub === 'hostile' ? '#ef4444' : sub === 'passive' ? '#38bdf8' : '#cbd5e1'
    ctx.beginPath()
    ctx.arc(sx, sy, 3.5, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.restore()
}

function drawLabel(
  ctx: CanvasRenderingContext2D,
  text: string,
  sx: number,
  sy: number,
  color: string
) {
  const w = ctx.measureText(text).width + 8
  ctx.fillStyle = 'rgba(15, 23, 42, 0.78)'
  ctx.fillRect(sx - w / 2, sy - 11, w, 15)
  ctx.fillStyle = color
  ctx.textAlign = 'center'
  ctx.fillText(text, sx, sy)
}

function resizeCanvas() {
  const host = canvasHostRef.value
  const canvas = canvasRef.value
  if (!host || !canvas) return
  const rect = host.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1
  canvas.width = Math.max(100, Math.floor(rect.width * dpr))
  canvas.height = Math.max(300, Math.floor(rect.height * dpr))
  scheduleDraw()
}

function clientToWorld(clientX: number, clientY: number): { wx: number; wz: number } {
  const canvas = canvasRef.value
  if (!canvas) return { wx: 0, wz: 0 }
  const rect = canvas.getBoundingClientRect()
  const dx = clientX - rect.left - rect.width / 2
  const dy = clientY - rect.top - rect.height / 2
  return {
    wx: camX.value + dx / pxPerBlock.value,
    wz: camZ.value + dy / pxPerBlock.value
  }
}

function onMouseDown(ev: MouseEvent) {
  if (ev.button !== 0) return
  dragging = true
  dragMoved = false
  lastMouseX = ev.clientX
  lastMouseY = ev.clientY
}

function onMouseMove(ev: MouseEvent) {
  const pos = clientToWorld(ev.clientX, ev.clientY)
  hoverBlockX.value = Math.round(pos.wx)
  hoverBlockZ.value = Math.round(pos.wz)

  if (!dragging) return
  const dx = ev.clientX - lastMouseX
  const dy = ev.clientY - lastMouseY
  if (Math.abs(dx) > 2 || Math.abs(dy) > 2) dragMoved = true
  lastMouseX = ev.clientX
  lastMouseY = ev.clientY
  camX.value -= dx / pxPerBlock.value
  camZ.value -= dy / pxPerBlock.value
  scheduleDraw()
}

function onMouseUp() {
  dragging = false
}

function onMouseLeave() {
  dragging = false
}

function onWheel(ev: WheelEvent) {
  const factor = ev.deltaY < 0 ? 1.18 : 1 / 1.18
  adjustZoom(factor)
}

function adjustZoom(factor: number) {
  pxPerBlock.value = Math.min(6.0, Math.max(0.125, pxPerBlock.value * factor))
  scheduleDraw()
}

function resetView() {
  camX.value = 0
  camZ.value = 0
  pxPerBlock.value = 1.0
  scheduleDraw()
}

function onCanvasClick(ev: MouseEvent) {
  if (dragMoved || !canvasRef.value || !bootstrap.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const cx = ev.clientX - rect.left
  const cy = ev.clientY - rect.top

  const candidates = filteredMarkers.value
  let hit: MapMarker | null = null
  let bestDist = 14
  for (const m of candidates) {
    const sx = (m.x - camX.value) * pxPerBlock.value + rect.width / 2
    const sy = (m.z - camZ.value) * pxPerBlock.value + rect.height / 2
    const d = Math.hypot(sx - cx, sy - cy)
    if (d <= bestDist) {
      bestDist = d
      hit = m
    }
  }
  selectedMarker.value = hit
}

function handleContextMenu(ev: MouseEvent) {
  if (!bootstrap.value?.canManageWaypoints || !bootstrap.value.visibleLayers.waypoints) return
  const pos = clientToWorld(ev.clientX, ev.clientY)
  openAddWaypointModal(Math.round(pos.wx), Math.round(pos.wz))
}

function openAddWaypointModal(x: number, z: number) {
  wpForm.name = ''
  wpForm.x = x
  wpForm.y = 64
  wpForm.z = z
  wpForm.color = '#3b82f6'
  wpForm.isPublic = true
  showWaypointModal.value = true
}

function focusMarker(m: MapMarker) {
  camX.value = m.x
  camZ.value = m.z
  selectedMarker.value = m
  scheduleDraw()
}

function confirmJump() {
  camX.value = jumpX.value
  camZ.value = jumpZ.value
  showJumpModal.value = false
  scheduleDraw()
}

async function submitWaypoint() {
  if (!wpForm.name.trim()) return
  savingWaypoint.value = true
  try {
    await mapApi.createWaypoint({
      name: wpForm.name.trim(),
      dimension: activeDim.value,
      x: wpForm.x,
      y: wpForm.y,
      z: wpForm.z,
      color: wpForm.color,
      isPublic: wpForm.isPublic
    })
    showWaypointModal.value = false
    await refreshOverlays()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    savingWaypoint.value = false
  }
}

async function handleDeleteWaypoint(rawId: string) {
  const numId = rawId.startsWith('wp:') ? rawId.slice(3) : rawId
  try {
    await mapApi.deleteWaypoint(numId)
    selectedMarker.value = null
    await refreshOverlays()
    message.success(t('common.deleted'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function handleTeleport(x: number, y: number, z: number) {
  try {
    await mapApi.teleport({ dimension: activeDim.value, x, y, z })
    message.success(t('map.teleportSent'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function handleResetHealth() {
  try {
    await mapApi.resetHealth()
    await initMap()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function handleClearCache() {
  try {
    await mapApi.clearCache()
    tileCache.clear()
    cacheBust.value = Date.now()
    scheduleDraw()
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

function handleSearchUpdate() {
  scheduleDraw()
}

async function handleDimensionChange() {
  selectedMarker.value = null
  await Promise.all([loadRegions(), refreshOverlays()])
  scheduleDraw()
}

async function loadRegions() {
  try {
    const res = await mapApi.getRegions(activeDim.value)
    const set = new Set<string>()
    for (const [rx, rz] of res.regions || []) {
      set.add(`${rx},${rz}`)
    }
    availableRegions.value = set
  } catch {
    availableRegions.value = new Set()
  }
}

async function refreshOverlays() {
  if (!bootstrap.value?.enabled) return
  try {
    overlays.value = await mapApi.getOverlays(activeDim.value)
    scheduleDraw()
  } catch {
    // ignore transient poll errors
  }
}

async function initMap() {
  loading.value = true
  try {
    const data = await mapApi.getBootstrap()
    bootstrap.value = data
    if (route.query.dim && typeof route.query.dim === 'string') {
      activeDim.value = route.query.dim
    } else if (data.defaultDimension) {
      activeDim.value = data.defaultDimension
    }
    if (route.query.x !== undefined && route.query.z !== undefined) {
      const qx = Number(route.query.x)
      const qz = Number(route.query.z)
      if (!Number.isNaN(qx) && !Number.isNaN(qz)) {
        camX.value = qx
        camZ.value = qz
        pxPerBlock.value = 1.5
      }
    }
    await Promise.all([loadRegions(), refreshOverlays()])
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
    requestAnimationFrame(() => resizeCanvas())
  }
}

onMounted(() => {
  initMap()
  if (canvasHostRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => resizeCanvas())
    resizeObserver.observe(canvasHostRef.value)
  }
  overlayTimer = setInterval(refreshOverlays, 4000)
})

onBeforeUnmount(() => {
  if (overlayTimer) clearInterval(overlayTimer)
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<style scoped>
.map-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.toolbar-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.layers-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.layers-label {
  font-size: 12px;
  opacity: 0.75;
}

.map-body {
  display: grid;
  grid-template-columns: 1fr 270px;
  gap: 12px;
  min-height: 600px;
}

@media (max-width: 960px) {
  .map-body {
    grid-template-columns: 1fr;
  }
}

.canvas-stage {
  position: relative;
  min-height: 580px;
  border-radius: 8px;
  overflow: hidden;
  background: #0f172a;
}

.map-canvas {
  width: 100%;
  height: 100%;
  display: block;
  cursor: grab;
}

.map-canvas:active {
  cursor: grabbing;
}

.hud-coords {
  position: absolute;
  left: 12px;
  bottom: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.84);
  color: #f8fafc;
  font-size: 12px;
  pointer-events: none;
}

.marker-card {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 260px;
  z-index: 4;
}

.marker-meta {
  font-size: 12px;
  opacity: 0.85;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.marker-sidebar {
  max-height: 620px;
  overflow-y: auto;
}

.marker-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.marker-item {
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  background: rgba(128, 128, 128, 0.08);
}

.marker-item:hover {
  background: rgba(128, 128, 128, 0.16);
}

.marker-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
}

.marker-name {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.marker-item-coords {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 2px;
}

.empty-hint {
  text-align: center;
  opacity: 0.6;
  padding: 20px 0;
  font-size: 13px;
}
</style>
