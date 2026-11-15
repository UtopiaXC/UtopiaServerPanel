import { api, unwrap } from './client'

export interface SocialAccountEntry {
  platform: string
  customPlatform?: string
  account: string
}

export interface WhitelistStatus {
  serverWhitelistEnabled: boolean
  whitelistManagementEnabled: boolean
  featureVisible: boolean
  visible?: boolean
  serverOnlineMode: boolean
  guestCanApply: boolean
  canGuestApply?: boolean
  canApply: boolean
  whitelistAllowGuest?: boolean
  socialRequired?: boolean
  requiredSocialPlatforms?: string[]
  boundPlayer?: any
  boundPlayers?: Array<{ playerUuid: string; playerName: string; primary?: boolean; boundAt?: number }>
}

export interface PlayerLookupResult {
  username: string
  onlineFound: boolean
  onlineUuid: string | null
  offlineUuid: string
  recommendedUuid: string
  serverOnlineMode: boolean
  avatarUrl: string
}

export interface WhitelistApplicationItem {
  id: number
  username: string
  onlineUuid: string | null
  offlineUuid: string | null
  preferredUuidMode: string
  reason: string
  email: string
  socialAccount: string
  bindUserId: number | null
  bindOnApprove: boolean
  status: 'pending' | 'approved' | 'rejected' | string
  reviewNote: string | null
  reviewerId: number | null
  reviewedAt: number | null
  clientIp: string | null
  createdAt: number
}

export interface WhitelistEntry {
  uuid: string
  name: string
}

export const whitelistApi = {
  getStatus: () => unwrap<WhitelistStatus>(api.get('/whitelist/status')),
  lookupPlayer: (username: string) =>
    unwrap<PlayerLookupResult>(api.get('/whitelist/lookup', { params: { username } })),
  apply: (payload: {
    username: string
    onlineUuid?: string | null
    offlineUuid?: string | null
    preferredUuidMode?: string
    email?: string
    reason: string
    socialAccount?: string
    socialAccounts?: SocialAccountEntry[]
    bindOnApprove?: boolean
    captcha?: string
  }) => unwrap<{ id: number; status: string; message: string }>(api.post('/whitelist/apply', payload)),

  listApplications: (params: { status?: string; search?: string; page?: number; pageSize?: number }) =>
    unwrap<{ items: WhitelistApplicationItem[]; total: number; page: number; pageSize: number }>(
      api.get('/whitelist/admin/applications', { params })
    ),
  reviewApplication: (id: number, payload: { approve: boolean; reviewNote?: string }) =>
    unwrap<{ reviewed: boolean; status: string }>(
      api.post(`/whitelist/admin/applications/${id}/review`, payload)
    ),
  listEntries: () => unwrap<{ items: WhitelistEntry[] }>(api.get('/whitelist/admin/entries')),
  addEntry: (payload: { username: string; uuid?: string }) =>
    unwrap<{ added: boolean }>(api.post('/whitelist/admin/entries', payload)),
  removeEntry: (name: string) =>
    unwrap<{ removed: boolean }>(api.delete(`/whitelist/admin/entries/${encodeURIComponent(name)}`))
}
