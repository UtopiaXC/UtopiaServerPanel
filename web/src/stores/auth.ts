import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type LoginPayload } from '../api/auth'
import { profileApi } from '../api/profile'
import {
  clearStoredTokens,
  getStoredAccessToken,
  getStoredRefreshToken,
  setStoredTokens
} from '../api/client'
import { reauthenticateWs } from '../composables/useWebSocket'
import { useSiteStore } from './site'
import { setAppLocale } from '../i18n'

export interface UserProfile {
  id: number
  username: string
  nickname: string
  email: string | null
  roleId: number
  roleName: string
  roleDisplayName: string
  status: string
  mustChangePassword: boolean
  require2fa: boolean
  totpEnabled: boolean
  bound: boolean
  playerUuid: string | null
  playerName: string | null
  boundAt: number | null
  locale: string
  createdAt: number
  permissions: Record<string, number>
  needsBinding: boolean
  needs2fa: boolean
}

export const useAuthStore = defineStore('auth', () => {
  const initialized = ref(false)
  const accessToken = ref<string | null>(getStoredAccessToken())
  const refreshToken = ref<string | null>(getStoredRefreshToken())
  const user = ref<UserProfile | null>(null)
  const permissions = ref<Record<string, number>>({})
  const mustChangePassword = ref(false)
  const needsBinding = ref(false)

  let initPromise: Promise<void> | null = null
  let listenersAttached = false

  const isLoggedIn = computed(() => Boolean(accessToken.value && user.value))

  function applySessionPayload(payload: any) {
    if (!payload) return
    if (payload.accessToken) {
      accessToken.value = payload.accessToken
      setStoredTokens(payload.accessToken, payload.refreshToken)
    }
    if (payload.refreshToken) {
      refreshToken.value = payload.refreshToken
    }
    if (payload.user) {
      user.value = payload.user
      mustChangePassword.value = Boolean(payload.mustChangePassword ?? payload.user.mustChangePassword)
      needsBinding.value = Boolean(payload.needsBinding ?? payload.user.needsBinding)
    }
    if (payload.permissions) {
      permissions.value = payload.permissions
    } else if (payload.user?.permissions) {
      permissions.value = payload.user.permissions
    }
    if (payload.user?.locale && !localStorage.getItem('usp_locale')) {
      setAppLocale(payload.user.locale)
    }
    reauthenticateWs(accessToken.value)
  }

  function clearSession() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    permissions.value = {}
    mustChangePassword.value = false
    needsBinding.value = false
    clearStoredTokens()
    reauthenticateWs(null)
  }

  function attachBrowserListeners() {
    if (listenersAttached || typeof window === 'undefined') return
    listenersAttached = true

    window.addEventListener('usp:tokens-refreshed', ((e: CustomEvent) => {
      if (e.detail) {
        applySessionPayload(e.detail)
      }
    }) as EventListener)

    window.addEventListener('usp:session-expired', () => {
      clearSession()
    })
  }

  async function initialize() {
    attachBrowserListeners()
    if (initialized.value) return
    if (initPromise) return initPromise

    initPromise = (async () => {
      try {
        if (accessToken.value) {
          const me = await profileApi.getMe()
          user.value = me
          permissions.value = me.permissions || {}
          mustChangePassword.value = Boolean(me.mustChangePassword)
          needsBinding.value = Boolean(me.needsBinding)
        } else if (refreshToken.value) {
          const res = await authApi.refresh(refreshToken.value)
          applySessionPayload(res)
        }
      } catch {
        if (refreshToken.value) {
          try {
            const res = await authApi.refresh(refreshToken.value)
            applySessionPayload(res)
          } catch {
            clearSession()
          }
        } else {
          clearSession()
        }
      } finally {
        initialized.value = true
        initPromise = null
      }
    })()

    return initPromise
  }

  async function refreshMe() {
    if (!accessToken.value) return
    const me = await profileApi.getMe()
    user.value = me
    permissions.value = me.permissions || {}
    mustChangePassword.value = Boolean(me.mustChangePassword)
    needsBinding.value = Boolean(me.needsBinding)
  }

  async function login(payload: LoginPayload): Promise<
    | { status: 'ok' }
    | { status: 'mfa'; mfaToken: string }
    | { status: 'setup2fa'; mfaToken: string }
  > {
    const res = await authApi.login(payload)
    if (res?.mfaRequired && res?.mfaToken) {
      return { status: 'mfa', mfaToken: res.mfaToken }
    }
    if (res?.setup2faRequired && res?.mfaToken) {
      return { status: 'setup2fa', mfaToken: res.mfaToken }
    }
    applySessionPayload(res)
    initialized.value = true
    return { status: 'ok' }
  }

  async function logout() {
    try {
      await authApi.logout(refreshToken.value)
    } catch {
      // ignore remote error
    } finally {
      clearSession()
    }
  }

  function effectivePermissionLevel(moduleKey: string): number {
    const siteStore = useSiteStore()
    const source = isLoggedIn.value ? permissions.value : siteStore.guestPermissions
    return source?.[moduleKey] ?? 0
  }

  function hasPermission(moduleKey: string, minLevel = 1): boolean {
    return effectivePermissionLevel(moduleKey) >= minLevel
  }

  return {
    initialized,
    accessToken,
    refreshToken,
    user,
    permissions,
    mustChangePassword,
    needsBinding,
    isLoggedIn,
    initialize,
    refreshMe,
    applySessionPayload,
    clearSession,
    login,
    logout,
    effectivePermissionLevel,
    hasPermission
  }
})

