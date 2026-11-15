<template>
  <canvas
    ref="canvasRef"
    :width="canvasWidth"
    :height="canvasHeight"
    :style="{
      width: `${displayWidth}px`,
      height: `${displayHeight}px`,
      imageRendering: 'pixelated',
      display: 'block'
    }"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { publicProfileApi } from '../api/publicProfile'

const props = withDefaults(
  defineProps<{
    uuid: string
    name?: string
    mode?: 'head' | 'full'
    scale?: number
  }>(),
  {
    name: '',
    mode: 'full',
    scale: 6
  }
)

const canvasRef = ref<HTMLCanvasElement | null>(null)

const logicalWidth = computed(() => (props.mode === 'head' ? 8 : 16))
const logicalHeight = computed(() => (props.mode === 'head' ? 8 : 32))
const canvasWidth = computed(() => logicalWidth.value * props.scale)
const canvasHeight = computed(() => logicalHeight.value * props.scale)
const displayWidth = computed(() => canvasWidth.value)
const displayHeight = computed(() => canvasHeight.value)

function drawDefaultFallback(ctx: CanvasRenderingContext2D, s: number) {
  ctx.imageSmoothingEnabled = false
  if (props.mode === 'head') {
    // Crisp Steve-inspired fallback head
    ctx.fillStyle = '#c89b72'
    ctx.fillRect(0, 0, 8 * s, 8 * s)
    ctx.fillStyle = '#3b281a'
    ctx.fillRect(0, 0, 8 * s, 2 * s)
    ctx.fillRect(0, 2 * s, 1 * s, 1 * s)
    ctx.fillRect(7 * s, 2 * s, 1 * s, 1 * s)
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(1 * s, 4 * s, 1 * s, 1 * s)
    ctx.fillRect(6 * s, 4 * s, 1 * s, 1 * s)
    ctx.fillStyle = '#4746a8'
    ctx.fillRect(2 * s, 4 * s, 1 * s, 1 * s)
    ctx.fillRect(5 * s, 4 * s, 1 * s, 1 * s)
    ctx.fillStyle = '#a47555'
    ctx.fillRect(3 * s, 5 * s, 2 * s, 1 * s)
    ctx.fillStyle = '#6c422c'
    ctx.fillRect(2 * s, 6 * s, 4 * s, 2 * s)
    ctx.fillStyle = '#c89b72'
    ctx.fillRect(3 * s, 6 * s, 2 * s, 1 * s)
    return
  }

  // Full body fallback (16x32 logical)
  // Head (4..12, 0..8)
  ctx.fillStyle = '#c89b72'
  ctx.fillRect(4 * s, 0, 8 * s, 8 * s)
  ctx.fillStyle = '#3b281a'
  ctx.fillRect(4 * s, 0, 8 * s, 2 * s)
  ctx.fillRect(4 * s, 2 * s, 1 * s, 1 * s)
  ctx.fillRect(11 * s, 2 * s, 1 * s, 1 * s)
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(5 * s, 4 * s, 1 * s, 1 * s)
  ctx.fillRect(10 * s, 4 * s, 1 * s, 1 * s)
  ctx.fillStyle = '#4746a8'
  ctx.fillRect(6 * s, 4 * s, 1 * s, 1 * s)
  ctx.fillRect(9 * s, 4 * s, 1 * s, 1 * s)
  ctx.fillStyle = '#a47555'
  ctx.fillRect(7 * s, 5 * s, 2 * s, 1 * s)
  ctx.fillStyle = '#6c422c'
  ctx.fillRect(6 * s, 6 * s, 4 * s, 2 * s)
  ctx.fillStyle = '#c89b72'
  ctx.fillRect(7 * s, 6 * s, 2 * s, 1 * s)

  // Torso (4..12, 8..20)
  ctx.fillStyle = '#00a8a8'
  ctx.fillRect(4 * s, 8 * s, 8 * s, 12 * s)
  // Arms
  ctx.fillStyle = '#009090'
  ctx.fillRect(0, 8 * s, 4 * s, 4 * s)
  ctx.fillRect(12 * s, 8 * s, 4 * s, 4 * s)
  ctx.fillStyle = '#c89b72'
  ctx.fillRect(0, 12 * s, 4 * s, 8 * s)
  ctx.fillRect(12 * s, 12 * s, 4 * s, 8 * s)
  // Legs
  ctx.fillStyle = '#334696'
  ctx.fillRect(4 * s, 20 * s, 8 * s, 11 * s)
  ctx.fillStyle = '#555555'
  ctx.fillRect(4 * s, 31 * s, 4 * s, 1 * s)
  ctx.fillRect(8 * s, 31 * s, 4 * s, 1 * s)
}

function drawSkinImage(ctx: CanvasRenderingContext2D, img: HTMLImageElement, isSlim: boolean, s: number) {
  ctx.imageSmoothingEnabled = false
  ctx.clearRect(0, 0, canvasWidth.value, canvasHeight.value)

  const modern = img.height >= 64
  if (props.mode === 'head') {
    // Base face (8,8,8,8) + Hat overlay (40,8,8,8)
    ctx.drawImage(img, 8, 8, 8, 8, 0, 0, 8 * s, 8 * s)
    ctx.drawImage(img, 40, 8, 8, 8, 0, 0, 8 * s, 8 * s)
    return
  }

  const armW = isSlim ? 3 : 4
  const armOffset = isSlim ? 1 : 0

  // Head + Hat
  ctx.drawImage(img, 8, 8, 8, 8, 4 * s, 0, 8 * s, 8 * s)
  ctx.drawImage(img, 40, 8, 8, 8, 4 * s, 0, 8 * s, 8 * s)

  // Torso
  ctx.drawImage(img, 20, 20, 8, 12, 4 * s, 8 * s, 8 * s, 12 * s)
  if (modern) {
    ctx.drawImage(img, 20, 36, 8, 12, 4 * s, 8 * s, 8 * s, 12 * s)
  }

  // Right leg (viewer's left)
  ctx.drawImage(img, 4, 20, 4, 12, 4 * s, 20 * s, 4 * s, 12 * s)
  // Left leg (viewer's right)
  if (modern) {
    ctx.drawImage(img, 20, 52, 4, 12, 8 * s, 20 * s, 4 * s, 12 * s)
    ctx.drawImage(img, 4, 36, 4, 12, 4 * s, 20 * s, 4 * s, 12 * s)
    ctx.drawImage(img, 4, 52, 4, 12, 8 * s, 20 * s, 4 * s, 12 * s)
  } else {
    ctx.save()
    ctx.translate(12 * s, 20 * s)
    ctx.scale(-1, 1)
    ctx.drawImage(img, 4, 20, 4, 12, 0, 0, 4 * s, 12 * s)
    ctx.restore()
  }

  // Right arm (viewer's left)
  ctx.drawImage(img, 44, 20, armW, 12, armOffset * s, 8 * s, armW * s, 12 * s)
  // Left arm (viewer's right)
  if (modern) {
    ctx.drawImage(img, 36, 52, armW, 12, 12 * s, 8 * s, armW * s, 12 * s)
    ctx.drawImage(img, 44, 36, armW, 12, armOffset * s, 8 * s, armW * s, 12 * s)
    ctx.drawImage(img, 52, 52, armW, 12, 12 * s, 8 * s, armW * s, 12 * s)
  } else {
    ctx.save()
    ctx.translate(16 * s, 8 * s)
    ctx.scale(-1, 1)
    ctx.drawImage(img, 44, 20, 4, 12, 0, 0, 4 * s, 12 * s)
    ctx.restore()
  }
}

async function renderSkin() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const s = props.scale
  drawDefaultFallback(ctx, s)

  if (!props.uuid && !props.name) return
  const url = publicProfileApi.getMcSkinUrl(props.uuid, props.name)
  try {
    const resp = await fetch(url)
    if (resp.status !== 200) {
      return
    }
    const isSlim = resp.headers.get('X-Skin-Model') === 'slim'
    const blob = await resp.blob()
    const objUrl = URL.createObjectURL(blob)
    const img = new Image()
    img.onload = () => {
      try {
        drawSkinImage(ctx, img, isSlim, s)
      } finally {
        URL.revokeObjectURL(objUrl)
      }
    }
    img.onerror = () => {
      URL.revokeObjectURL(objUrl)
    }
    img.src = objUrl
  } catch {
    // keep default fallback skin
  }
}

onMounted(renderSkin)
watch(() => [props.uuid, props.name, props.mode, props.scale], renderSkin)
</script>
