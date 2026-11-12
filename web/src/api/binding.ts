import { api, unwrap } from './client'

export const bindingApi = {
  bind: (code: string) =>
    unwrap<{ message: string; playerName: string; playerUuid: string }>(
      api.post('/binding/bind', { code })
    ),
  unbind: (userId?: number) =>
    unwrap<{ message: string }>(api.post('/binding/unbind', userId ? { userId } : {})),
  getMyPlayer: () => unwrap<any>(api.get('/player/me'))
}

