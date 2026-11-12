import { api, unwrap } from './client'

export interface SetupStatus {
  setupRequired: boolean
  setupCompleted: boolean
  configFile: string
}

export interface SmtpPayload {
  smtpEnabled?: boolean
  smtpHost: string
  smtpPort: number
  smtpEncryption: string
  smtpUser: string
  smtpPassword: string
  smtpFrom: string
  smtpFromName: string
}

export interface SetupCompletePayload extends SmtpPayload {
  token: string
  siteName: string
  siteDescription: string
  baseUrl: string
  defaultLocale: string
  adminUsername: string
  adminNickname: string
  adminEmail: string
  adminPassword: string
}

export const setupApi = {
  getStatus: () => unwrap<SetupStatus>(api.get('/setup/status')),
  verifyToken: (token: string) => unwrap<{ valid: boolean }>(api.post('/setup/verify-token', { token })),
  testSmtp: (token: string, cfg: SmtpPayload) =>
    unwrap<{ connected: boolean }>(
      api.post('/setup/test-smtp', cfg, { headers: { 'X-Setup-Token': token } })
    ),
  complete: (payload: SetupCompletePayload) =>
    unwrap<{ message: string }>(
      api.post('/setup/complete', payload, { headers: { 'X-Setup-Token': payload.token } })
    )
}

