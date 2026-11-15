import { api, unwrap } from './client'

export interface TickPhaseItem {
  key: string
  ms: number
  sharePct: number
  budgetPct: number
}

export interface DimensionHotspot {
  dimension: string
  loadedChunks: number
  entityCount: number
  blockEntityCount: number
  playerCount: number
}

export interface ChunkHotspot {
  dimension: string
  chunkX: number
  chunkZ: number
  centerBlockX: number
  centerBlockZ: number
  entityCount: number
  blockEntityCount: number
  topSummary: string
}

export interface TypeCountItem {
  typeId: string
  count: number
}

export interface GcInfo {
  name: string
  count: number
  timeMs: number
}

export interface ProfilerSnapshot {
  mapEnabled: boolean
  tickPhases: {
    samples: number
    avgMspt: number
    maxMspt: number
    tps: number
    budgetMs: number
    budgetUsagePct: number
    phases: TickPhaseItem[]
  }
  jvm: {
    heapUsedMb: number
    heapCommittedMb: number
    heapMaxMb: number
    nonHeapUsedMb: number
    gc: GcInfo[]
    threadCount: number
    daemonThreadCount: number
    deadlockedThreads: number
    threadStates: Record<string, number>
  }
  hotspots: {
    dimensions: DimensionHotspot[]
    topEntityChunks: ChunkHotspot[]
    topBlockEntityChunks: ChunkHotspot[]
    topEntityTypes: TypeCountItem[]
    topBlockEntityTypes: TypeCountItem[]
    capturedAt: number
  }
}

export const profilerApi = {
  getSnapshot: (refresh = false) =>
    unwrap<ProfilerSnapshot>(api.get('/profiler/snapshot', { params: { refresh } })),
  teleport: (payload: { dimension: string; x: number; y: number; z: number }) =>
    unwrap<{ teleported: boolean; message?: string }>(api.post('/profiler/teleport', payload))
}
