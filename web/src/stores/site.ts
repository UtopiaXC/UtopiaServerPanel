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
  const hasCustomFavicon = ref(false)
  const faviconVersion = ref('default')
  const faviconUrl = ref('/api/site/favicon?v=default')
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
  const whitelistFeatureVisible = ref(false)
  const serverWhitelistEnabled = ref(false)
  const whitelistManagementEnabled = ref(false)
  const whitelistAllowGuest = ref(false)
  const whitelistSocialRequired = ref(false)
  const whitelistRequiredSocialPlatforms = ref<string[]>([])
  const mapEnabled = ref(true)
  const webChatSendEnabled = ref(true)

  let pendingPromise: Promise<void> | null = null

  function applyDocumentFavicon(url: string) {
    if (typeof document === 'undefined') return
    let link = document.getElementById('usp-favicon') as HTMLLinkElement | null
    if (!link) {
      link = document.querySelector("link[rel~='icon']") as HTMLLinkElement | null
    }
    if (!link) {
      link = document.createElement('link')
      link.id = 'usp-favicon'
      link.rel = 'icon'
      document.head.appendChild(link)
    }
    link.href = url
  }

  function applyInfo(info: SiteInfo) {
    setupCompleted.value = Boolean(info.setupCompleted)
    siteName.value = info.siteName || 'Utopia Server Panel'
    siteDescription.value = info.siteDescription || ''
    baseUrl.value = info.baseUrl || ''
    defaultLocale.value = info.defaultLocale || 'zh-CN'
    hasCustomFavicon.value = Boolean(info.hasCustomFavicon)
    faviconVersion.value = info.faviconVersion || 'default'
    faviconUrl.value = info.faviconUrl || `/api/site/favicon?v=${encodeURIComponent(faviconVersion.value)}`
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
    whitelistFeatureVisible.value = Boolean(info.whitelistFeatureVisible)
    serverWhitelistEnabled.value = Boolean(info.serverWhitelistEnabled)
    whitelistManagementEnabled.value = Boolean(info.whitelistManagementEnabled)
    whitelistAllowGuest.value = Boolean(info.whitelistAllowGuest)
    whitelistSocialRequired.value = Boolean(info.whitelistSocialRequired)
    whitelistRequiredSocialPlatforms.value = Array.isArray(info.whitelistRequiredSocialPlatforms)
      ? info.whitelistRequiredSocialPlatforms
      : []
    mapEnabled.value = info.mapEnabled !== false
    webChatSendEnabled.value = info.webChatSendEnabled !== false
    loaded.value = true

    if (typeof document !== 'undefined') {
      document.title = siteName.value
      applyDocumentFavicon(faviconUrl.value)
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
    hasCustomFavicon,
    faviconVersion,
    faviconUrl,
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
    whitelistFeatureVisible,
    serverWhitelistEnabled,
    whitelistManagementEnabled,
    whitelistAllowGuest,
    whitelistSocialRequired,
    whitelistRequiredSocialPlatforms,
    mapEnabled,
    webChatSendEnabled,
    refresh,
    ensureLoaded
  }
})

