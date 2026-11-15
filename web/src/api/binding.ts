import { api, unwrap } from './client'
import type { BoundPlayerItem } from './admin'

export const bindingApi = {
  list: () => unwrap<{ boundPlayers: BoundPlayerItem[] }>(api.get('/binding/list')),
  bind: (code: string) =>
    unwrap<{ message: string; playerName: string; playerUuid: string; boundPlayers: BoundPlayerItem[] }>(
      api.post('/binding/bind', { code })
    ),
  setPrimary: (playerUuid: string) =>
    unwrap<{ message: string; boundPlayers: BoundPlayerItem[] }>(
      api.post('/binding/primary', { playerUuid })
    ),
  unbind: (payload?: number | { userId?: number; playerUuid?: string }) => {
    const body =
      typeof payload === 'number'
        ? { userId: payload }
        : payload || {}
    return unwrap<{ message: string; boundPlayers?: BoundPlayerItem[] }>(api.post('/binding/unbind', body))
  },
  getMyPlayer: (playerUuid?: string) =>
    unwrap<any>(api.get('/player/me', playerUuid ? { params: { playerUuid } } : undefined))
}

