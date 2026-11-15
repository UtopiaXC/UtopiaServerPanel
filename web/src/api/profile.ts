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

export interface PasskeyItem {
  id: number
  name: string
  algorithm: string
  transports: string[]
  createdAt: number
  lastUsedAt: number | null
}

export interface SocialAccountItem {
  platform: string
  customPlatform?: string
  account: string
}

export interface ProfilePrivacyConfig {
  publicProfileEnabled: boolean
  showUsername: boolean
  showAvatar: boolean
  showNickname: boolean
  showBio: boolean
  showEmail: boolean
  showGender: boolean
  showBirthday: boolean
  showSocialAccounts: boolean
  showRole: boolean
  showRegisteredAt: boolean
  showMcCharacters: boolean
  showMcOnlineStatus: boolean
  showMcPlaytime: boolean
  showMcAchievements: boolean
  showMcStats: boolean
}

export interface UpdateProfilePayload {
  nickname: string
  email: string
  locale: string
  gender?: string
  birthday?: string
  bio?: string
  socialAccounts?: SocialAccountItem[]
  avatarData?: string
}

export const profileApi = {
  getMe: () => unwrap<any>(api.get('/me')),
  updateProfile: (data: UpdateProfilePayload) =>
    unwrap<any>(api.patch('/me/profile', data)),
  getPublicProfilePrivacy: () =>
    unwrap<ProfilePrivacyConfig>(api.get('/me/public-profile-settings')),
  updatePublicProfilePrivacy: (data: Partial<ProfilePrivacyConfig>) =>
    unwrap<any>(api.put('/me/public-profile-settings', data)),
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
  listPasskeys: () => unwrap<{ passkeys: PasskeyItem[] }>(api.get('/me/passkeys')),
  beginPasskeyRegister: (mfaToken?: string) =>
    unwrap<{ challengeId: string; publicKey: any }>(
      api.post('/me/passkeys/register/options', {}, mfaToken ? { headers: { 'X-MFA-Token': mfaToken } } : undefined)
    ),
  finishPasskeyRegister: (
    payload: { challengeId: string; name?: string; credential: any },
    mfaToken?: string
  ) =>
    unwrap<any>(
      api.post('/me/passkeys/register/verify', payload, mfaToken ? { headers: { 'X-MFA-Token': mfaToken } } : undefined)
    ),
  renamePasskey: (id: number, name: string) =>
    unwrap<{ name: string }>(api.put(`/me/passkeys/${id}`, { name })),
  deletePasskey: (id: number) => unwrap<{ message: string }>(api.delete(`/me/passkeys/${id}`)),
  listSessions: () => unwrap<{ sessions: UserSessionItem[] }>(api.get('/me/sessions')),
  revokeSession: (id: string) => unwrap<{ message: string }>(api.delete(`/me/sessions/${id}`)),
  listLoginHistory: () => unwrap<{ history: LoginHistoryItem[] }>(api.get('/me/login-history')),
  listTokens: () => unwrap<{ tokens: ApiTokenItem[] }>(api.get('/me/tokens')),
  createToken: (name: string, expiresDays: number | null) =>
    unwrap<ApiTokenItem & { token: string }>(api.post('/me/tokens', { name, expiresDays })),
  revokeToken: (id: number) => unwrap<{ message: string }>(api.delete(`/me/tokens/${id}`))
}
