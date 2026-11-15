import { api, unwrap } from './client'

export interface ModEntry {
  modId: string
  name: string
  version: string
  description: string
  authors: string
  homepage: string
  configFiles: string[]
}

export interface ModsOverview {
  mods: ModEntry[]
  allConfigFiles: string[]
  unmappedConfigFiles: string[]
  pendingRestart: boolean
  restartHint: string
}

export interface ConfigEntry {
  section?: string
  key: string
  value: string
  type?: string
  rawValue?: string
}

export interface ConfigUpdateItem {
  section?: string
  key: string
  value: string
}

export interface ModConfigDocument {
  path: string
  fileName: string
  extension: string
  size: number
  lastModified: number
  content: string
  structuredEntries: ConfigEntry[]
  pendingRestart: boolean
}

export const modsApi = {
  getOverview: () => unwrap<ModsOverview>(api.get('/mods/overview')),
  getConfig: (path: string) =>
    unwrap<ModConfigDocument>(api.get('/mods/config', { params: { path } })),
  saveConfig: (payload: { path: string; content?: string; updates?: ConfigUpdateItem[] }) =>
    unwrap<{ saved: boolean; pendingRestart: boolean; restartHint?: string }>(
      api.put('/mods/config', payload)
    ),
  getServerProperties: () =>
    unwrap<{ exists: boolean; raw: string; entries: ConfigEntry[]; pendingRestart: boolean; restartHint: string }>(
      api.get('/mods/server-properties')
    ),
  saveServerProperties: (payload: { raw?: string; updates?: ConfigUpdateItem[] }) =>
    unwrap<{ saved: boolean; pendingRestart: boolean; restartHint?: string }>(
      api.put('/mods/server-properties', payload)
    ),
  restartServer: () =>
    unwrap<{ dispatched: boolean; method: string; restartHint: string }>(api.post('/mods/restart'))
}
