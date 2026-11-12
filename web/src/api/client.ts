import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { i18n } from '../i18n'

const ACCESS_KEY = 'usp_access_token'
const REFRESH_KEY = 'usp_refresh_token'

export function getStoredAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY)
}

export function getStoredRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function setStoredTokens(accessToken: string, refreshToken?: string | null) {
  localStorage.setItem(ACCESS_KEY, accessToken)
  if (refreshToken !== undefined && refreshToken !== null) {
    localStorage.setItem(REFRESH_KEY, refreshToken)
  }
}

export function clearStoredTokens() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

export const api = axios.create({
  baseURL: '/api',
  timeout: 20000
})

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getStoredAccessToken()
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Locale'] = String((i18n.global.locale as any).value || 'zh-CN')
  return config
})

let refreshPromise: Promise<string | null> | null = null

const SKIP_REFRESH_PATHS = [
  '/auth/login',
  '/auth/refresh',
  '/auth/register',
  '/setup/verify-token',
  '/setup/complete',
  '/me/2fa/setup'
]

async function performTokenRefresh(): Promise<string | null> {
  const refreshToken = getStoredRefreshToken()
  if (!refreshToken) return null
  try {
    const res = await axios.post('/api/auth/refresh', { refreshToken }, {
      headers: {
        'X-Locale': String((i18n.global.locale as any).value || 'zh-CN')
      }
    })
    const payload = res.data?.data
    if (payload?.accessToken) {
      setStoredTokens(payload.accessToken, payload.refreshToken ?? refreshToken)
      window.dispatchEvent(new CustomEvent('usp:tokens-refreshed', { detail: payload }))
      return payload.accessToken
    }
    return null
  } catch {
    return null
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const status = error.response?.status
    const reqUrl = originalRequest?.url || ''

    if (
      status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !SKIP_REFRESH_PATHS.some((p) => reqUrl.includes(p))
    ) {
      originalRequest._retry = true
      if (!refreshPromise) {
        refreshPromise = performTokenRefresh().finally(() => {
          refreshPromise = null
        })
      }
      const nextToken = await refreshPromise
      if (nextToken) {
        originalRequest.headers.Authorization = `Bearer ${nextToken}`
        return api(originalRequest)
      }
      clearStoredTokens()
      window.dispatchEvent(new CustomEvent('usp:session-expired'))
    }
    return Promise.reject(error)
  }
)

export async function unwrap<T>(promise: Promise<{ data: { data: T } }>): Promise<T> {
  const res = await promise
  return res.data.data
}

export function getApiErrorCode(err: unknown): string | undefined {
  const axiosErr = err as AxiosError<{ errorCode?: string; message?: string }>
  return axiosErr?.response?.data?.errorCode
}

export function getApiErrorMessage(err: unknown): string {
  const axiosErr = err as AxiosError<{ errorCode?: string; message?: string }>
  const code = axiosErr?.response?.data?.errorCode
  if (code && (i18n.global as any).te(`errors.${code}`)) {
    return String(i18n.global.t(`errors.${code}`))
  }
  return (
    axiosErr?.response?.data?.message ||
    axiosErr?.message ||
    String(i18n.global.t('common.operationFailed'))
  )
}

