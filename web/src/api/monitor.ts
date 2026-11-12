import { api, unwrap } from './client'

export const monitorApi = {
  getStatus: () => unwrap<any>(api.get('/status')),
  getDisplayConfig: () =>
    unwrap<{ showPlayerNames: boolean; enabled: boolean }>(api.get('/monitor/config')),
  queryPerfLogs: (start: number, end: number) =>
    unwrap<{ logs: any[]; start: number; end: number }>(
      api.get('/monitor/perf', { params: { start, end } })
    ),
  queryPlayerEvents: (start: number, end: number) =>
    unwrap<{
      initialCount: number
      initialPlayers: string[]
      events: any[]
      start: number
      end: number
    }>(api.get('/monitor/players', { params: { start, end } })),
  queryLifecyclePaged: (page = 1, size = 10) =>
    unwrap<{ events: any[]; total: number; page: number; size: number }>(
      api.get('/monitor/lifecycle', { params: { page, size } })
    ),
  queryLifecycleRange: (start: number, end: number) =>
    unwrap<{ events: any[]; start: number; end: number }>(
      api.get('/monitor/lifecycle/range', { params: { start, end } })
    )
}

