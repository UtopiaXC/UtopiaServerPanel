import { api, unwrap } from './client'

export interface UserSessionItem {
  sessionId: string
  ipAddress: string
  userAgent: string
  rememberMe: boolean
  createdAt: number
  lastSeenAt: number
  expiresAt: number
  current: boolean
}

export interface LoginHistoryItem {
  id: number
  ipAddress: string
  userAgent: string
  success: boolean
  failReason: string
  loginTime: number
}

export interface ApiTokenItem {
  id: number
  name: string
  prefix: string
  scopes: string
  lastUsedAt: number | null
  expiresAt: number | null
  createdAt: number
}

export const profileApi = {
  getMe: () => unwrap<any>(api.get('/me')),
  updateProfile: (data: { nickname: string; email: string; locale: string }) =>
    unwrap<any>(api.patch('/me/profile', data)),
  changeUsername: (newUsername: string, currentPassword: string) =>
    unwrap<any>(api.put('/me/username', { newUsername, currentPassword })),
  changePassword: (currentPassword: string, newPassword: string) =>
    unwrap<{ message: string }>(api.post('/me/password', { currentPassword, newPassword })),
  setup2fa: (mfaToken?: string) =>
    unwrap<{ secret: string; otpauthUri: string }>(
      api.post('/me/2fa/setup', {}, mfaToken ? { headers: { 'X-MFA-Token': mfaToken } } : undefined)
    ),
  confirm2fa: (code: string) => unwrap<{ message: string }>(api.post('/me/2fa/confirm', { code })),
  disable2fa: (password: string, code: string) =>
    unwrap<{ message: string }>(api.post('/me/2fa/disable', { password, code })),
  listSessions: () => unwrap<{ sessions: UserSessionItem[] }>(api.get('/me/sessions')),
  revokeSession: (id: string) => unwrap<{ message: string }>(api.delete(`/me/sessions/${id}`)),
  listLoginHistory: () => unwrap<{ history: LoginHistoryItem[] }>(api.get('/me/login-history')),
  listTokens: () => unwrap<{ tokens: ApiTokenItem[] }>(api.get('/me/tokens')),
  createToken: (name: string, expiresDays: number | null) =>
    unwrap<ApiTokenItem & { token: string }>(api.post('/me/tokens', { name, expiresDays })),
  revokeToken: (id: number) => unwrap<{ message: string }>(api.delete(`/me/tokens/${id}`))
}

