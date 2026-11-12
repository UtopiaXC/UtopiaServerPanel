import { ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type PermissionDef, type SiteInfo } from '../api/auth'
import { setAppLocale } from '../i18n'

export const useSiteStore = defineStore('site', () => {
  const loaded = ref(false)
  const setupCompleted = ref(true)
  const siteName = ref('Utopia Server Panel')
  const siteDescription = ref('')
  const baseUrl = ref('')
  const defaultLocale = ref('zh-CN')
  const allowRegistration = ref(false)
  const requireEmailVerification = ref(false)
  const requireBindingOnRegister = ref(false)
  const forcePlayerBinding = ref(false)
  const smtpEnabled = ref(false)
  const captchaPolicy = ref<'auto' | 'always' | 'off'>('auto')
  const force2fa = ref(false)
  const guestPermissions = ref<Record<string, number>>({})
  const permissionsCatalog = ref<PermissionDef[]>([])
  const monitorShowPlayerNames = ref(false)

  let pendingPromise: Promise<void> | null = null

  function applyInfo(info: SiteInfo) {
    setupCompleted.value = Boolean(info.setupCompleted)
    siteName.value = info.siteName || 'Utopia Server Panel'
    siteDescription.value = info.siteDescription || ''
    baseUrl.value = info.baseUrl || ''
    defaultLocale.value = info.defaultLocale || 'zh-CN'
    allowRegistration.value = Boolean(info.allowRegistration)
    requireEmailVerification.value = Boolean(info.requireEmailVerification)
    requireBindingOnRegister.value = Boolean(info.requireBindingOnRegister)
    forcePlayerBinding.value = Boolean(info.forcePlayerBinding)
    smtpEnabled.value = Boolean(info.smtpEnabled)
    captchaPolicy.value = info.captchaPolicy || 'auto'
    force2fa.value = Boolean(info.force2fa)
    guestPermissions.value = info.guestPermissions || {}
    permissionsCatalog.value = info.permissionsCatalog || []
    monitorShowPlayerNames.value = Boolean(info.monitorShowPlayerNames)
    loaded.value = true

    if (typeof document !== 'undefined') {
      document.title = siteName.value
    }
    if (!localStorage.getItem('usp_locale') && defaultLocale.value) {
      setAppLocale(defaultLocale.value)
    }
  }

  async function refresh() {
    if (pendingPromise) return pendingPromise
    pendingPromise = authApi
      .getSiteInfo()
      .then((info) => {
        applyInfo(info)
      })
      .finally(() => {
        pendingPromise = null
      })
    return pendingPromise
  }

  async function ensureLoaded() {
    if (!loaded.value) {
      await refresh()
    }
  }

  return {
    loaded,
    setupCompleted,
    siteName,
    siteDescription,
    baseUrl,
    defaultLocale,
    allowRegistration,
    requireEmailVerification,
    requireBindingOnRegister,
    forcePlayerBinding,
    smtpEnabled,
    captchaPolicy,
    force2fa,
    guestPermissions,
    permissionsCatalog,
    monitorShowPlayerNames,
    refresh,
    ensureLoaded
  }
})

