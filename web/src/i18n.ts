import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN.json'
import enUS from './locales/en-US.json'

const savedLocale = localStorage.getItem('usp_locale')
const browserLang = navigator.language || 'zh-CN'
const initialLocale = savedLocale || (browserLang.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US')

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale,
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  }
})

export function setAppLocale(locale: string) {
  const normalized = locale.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
  ;(i18n.global.locale as any).value = normalized
  localStorage.setItem('usp_locale', normalized)
}

