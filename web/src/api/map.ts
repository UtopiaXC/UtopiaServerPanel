import { api, unwrap } from './client'

export type LayerVisibility = 'readonly' | 'full'

export interface MapDimensionInfo {
  id: string
  name: string
  hasCeiling: boolean
  minY: number
  maxY: number
  coordinateScale: number
}

export interface MapBootstrap {
  enabled: boolean
  healthy: boolean
  lastError: string
  userLevel: number
  canManageWaypoints: boolean
  canTeleport: boolean
  visibleLayers: Record<string, boolean>
  configuredLayers: Record<string, LayerVisibility>
  dimensions: MapDimensionInfo[]
  defaultDimension: string
  tileSize: number
}

export interface MapMarker {
  id: string
  kind?: 'player' | 'entity' | 'structure' | 'waypoint' | string
  category?: 'player' | 'entity' | 'structure' | 'waypoint' | 'hostile' | 'passive' | 'other' | string
  typeId?: string
  subType?: string
  name: string
  dimension: string
  x: number
  y: number
  z: number
  color?: string
  icon?: string
  avatarUrl?: string
  ownerId?: number
  isPublic?: boolean
}

export interface MapOverlays {
  players: MapMarker[]
  entities: MapMarker[]
  structures: MapMarker[]
  waypoints: MapMarker[]
}

export const mapApi = {
  getBootstrap: () => unwrap<MapBootstrap>(api.get('/map/bootstrap')),
  getRegions: (dim: string) =>
    unwrap<{ regions: [number, number][] }>(api.get('/map/regions', { params: { dim } })),
  getOverlays: (dim: string, q?: string) =>
    unwrap<MapOverlays>(api.get('/map/overlays', { params: { dim, ...(q ? { q } : {}) } })),
  getTileUrl: (dim: string, z: number, x: number, y: number, bust?: number) => {
    const params = new URLSearchParams({ dim, z: String(z), x: String(x), y: String(y) })
    if (bust) params.set('_t', String(bust))
    return `/api/map/tile?${params.toString()}`
  },
  createWaypoint: (payload: {
    name: string
    dimension: string
    x: number
    y: number
    z: number
    color?: string
    icon?: string
    isPublic?: boolean
  }) => unwrap<MapMarker>(api.post('/map/waypoints', payload)),
  deleteWaypoint: (id: number | string) =>
    unwrap<{ deleted: boolean }>(api.delete(`/map/waypoints/${id}`)),
  teleport: (payload: { dimension: string; x: number; y: number; z: number }) =>
    unwrap<{ teleported: boolean; message?: string }>(api.post('/map/teleport', payload)),
  resetHealth: () => unwrap<{ healthy: boolean }>(api.post('/map/reset-health')),
  clearCache: () => unwrap<{ cleared: boolean }>(api.post('/map/clear-cache'))
}
