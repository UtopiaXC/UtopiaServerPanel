import { api, unwrap } from './client'
import type { PermissionDef } from './auth'
import type { SmtpPayload } from './setup'

export interface AdminUserItem {
  id: number
  username: string
  nickname: string
  email: string | null
  roleId: number
  roleName: string
  roleDisplayName: string
  status: 'active' | 'frozen' | 'banned' | 'inactive'
  mustChangePassword: boolean
  require2fa: boolean
  totpEnabled: boolean
  failedLoginAttempts: number
  lockoutUntil: number | null
  bound: boolean
  playerUuid: string | null
  playerName: string | null
  boundAt: number | null
  locale: string
  createdAt: number
  updatedAt: number
}

export interface RoleItem {
  id: number
  name: string
  displayName: string
  description: string
  systemRole: boolean
  immutable: boolean
  userCount: number
  createdAt: number
  updatedAt: number
  permissionLevels: Record<string, number>
}

export interface AdminSettingsData {
  siteName: string
  siteDescription: string
  baseUrl: string
  defaultLocale: string
  allowRegistration: boolean
  defaultRegisterRoleId: number
  requireEmailVerification: boolean
  requireBindingOnRegister: boolean
  forcePlayerBinding: boolean
  captchaPolicy: 'auto' | 'always' | 'off'
  captchaDifficulty: number
  sessionTimeoutDays: number
  force2fa: boolean
  maxFailedLogins: number
  lockoutMinutes: number
  smtpEnabled: boolean
  smtpHost: string
  smtpPort: number
  smtpEncryption: string
  smtpUser: string
  hasSmtpPassword: boolean
  smtpFrom: string
  smtpFromName: string
  monitorEnabled: boolean
  monitorInterval: number
  monitorRetentionDays: number
  monitorShowPlayerNames: boolean
}

export const adminApi = {
  listUsers: (params: {
    search?: string
    roleId?: number | null
    status?: string | null
    sortBy?: string
    sortOrder?: string | null
    page?: number
    size?: number
  }) =>
    unwrap<{ users: AdminUserItem[]; total: number; page: number; size: number }>(
      api.get('/admin/users', { params })
    ),
  createUser: (data: {
    username: string
    nickname: string
    email: string
    password: string
    roleId: number
    status: string
    mustChangePassword: boolean
    require2fa: boolean
  }) => unwrap<AdminUserItem>(api.post('/admin/users', data)),
  updateUser: (
    id: number,
    data: Partial<{
      roleId: number
      status: string
      nickname: string
      email: string
      newPassword: string
      mustChangePassword: boolean
      require2fa: boolean
    }>
  ) => unwrap<AdminUserItem>(api.patch(`/admin/users/${id}`, data)),
  deleteUser: (id: number) => unwrap<{ message: string }>(api.delete(`/admin/users/${id}`)),
  unbindUser: (id: number) => unwrap<{ message: string }>(api.post(`/admin/users/${id}/unbind`)),
  resetUser2fa: (id: number) => unwrap<{ message: string }>(api.post(`/admin/users/${id}/reset-2fa`)),
  unlockUser: (id: number) => unwrap<{ message: string }>(api.post(`/admin/users/${id}/unlock`)),

  listRoles: () => unwrap<{ roles: RoleItem[] }>(api.get('/admin/roles')),
  createRole: (data: {
    name: string
    displayName: string
    description: string
    permissionLevels: Record<string, number>
  }) => unwrap<RoleItem>(api.post('/admin/roles', data)),
  updateRole: (
    id: number,
    data: {
      name?: string
      displayName: string
      description: string
      permissionLevels: Record<string, number>
    }
  ) => unwrap<RoleItem>(api.put(`/admin/roles/${id}`, data)),
  deleteRole: (id: number) => unwrap<{ message: string }>(api.delete(`/admin/roles/${id}`)),
  listPermissions: () =>
    unwrap<{ permissions: PermissionDef[]; levels: { value: number; label: string }[] }>(
      api.get('/admin/permissions')
    ),

  getSettings: () => unwrap<AdminSettingsData>(api.get('/admin/settings')),
  updateSettings: (data: Partial<AdminSettingsData> & { smtpPassword?: string; clearSmtpPassword?: boolean }) =>
    unwrap<AdminSettingsData>(api.patch('/admin/settings', data)),
  testSmtp: (cfg: Partial<SmtpPayload>) =>
    unwrap<{ connected: boolean }>(api.post('/admin/settings/test-smtp', cfg))
}

