import { api, unwrap } from './client'
import type { PowChallenge } from '../utils/pow'

export interface PermissionDef {
  key: string
  category?: string
  sortOrder: number
  active?: boolean
  name?: string
  description?: string
}

export interface SiteInfo {
  setupCompleted: boolean
  siteName: string
  siteDescription: string
  baseUrl: string
  defaultLocale: string
  allowRegistration: boolean
  requireEmailVerification: boolean
  requireBindingOnRegister: boolean
  forcePlayerBinding: boolean
  smtpEnabled: boolean
  captchaPolicy: 'auto' | 'always' | 'off'
  force2fa: boolean
  guestPermissions: Record<string, number>
  permissionsCatalog: PermissionDef[]
  monitorShowPlayerNames: boolean
}

export interface LoginPayload {
  username?: string
  password?: string
  rememberMe?: boolean
  captcha?: string
  totpCode?: string
  mfaToken?: string
  setupTotpCode?: string
}

export interface RegisterPayload {
  username: string
  nickname: string
  email: string
  password: string
  bindingCode?: string
  captcha?: string
}

export const authApi = {
  getSiteInfo: () => unwrap<SiteInfo>(api.get('/site')),
  getCaptchaChallenge: () => unwrap<PowChallenge>(api.get('/captcha/challenge')),
  login: (payload: LoginPayload) => unwrap<any>(api.post('/auth/login', payload)),
  register: (payload: RegisterPayload) =>
    unwrap<{ message: string; activationRequired: boolean }>(api.post('/auth/register', payload)),
  activate: (token: string) => unwrap<{ message: string }>(api.post('/auth/activate', { token })),
  refresh: (refreshToken: string) => unwrap<any>(api.post('/auth/refresh', { refreshToken })),
  logout: (refreshToken?: string | null) =>
    unwrap<{ message: string }>(api.post('/auth/logout', { refreshToken: refreshToken || '' })),
  forgotPassword: (account: string, captcha?: string) =>
    unwrap<{ message: string }>(api.post('/auth/forgot-password', { account, captcha })),
  resetPassword: (token: string, newPassword: string) =>
    unwrap<{ message: string }>(api.post('/auth/reset-password', { token, newPassword })),
  forgot2fa: (account: string, captcha?: string) =>
    unwrap<{ message: string }>(api.post('/auth/forgot-2fa', { account, captcha })),
  reset2fa: (token: string) => unwrap<{ message: string }>(api.post('/auth/reset-2fa', { token }))
}

