import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { darkTheme, type GlobalThemeOverrides } from 'naive-ui'

export type ThemeMode = 'auto' | 'light' | 'dark'

const THEME_KEY = 'usp_theme'

export const useThemeStore = defineStore('theme', () => {
  const saved = localStorage.getItem(THEME_KEY) as ThemeMode | null
  const mode = ref<ThemeMode>(saved === 'light' || saved === 'dark' || saved === 'auto' ? saved : 'auto')

  const mediaQuery =
    typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)') : null
  const systemDark = ref<boolean>(mediaQuery ? mediaQuery.matches : true)

  if (mediaQuery) {
    mediaQuery.addEventListener('change', (e) => {
      systemDark.value = e.matches
    })
  }

  const isDark = computed(() => {
    if (mode.value === 'dark') return true
    if (mode.value === 'light') return false
    return systemDark.value
  })

  const naiveTheme = computed(() => (isDark.value ? darkTheme : null))

  const themeOverrides = computed<GlobalThemeOverrides>(() => {
    if (isDark.value) {
      return {
        common: {
          primaryColor: '#63e2b7',
          primaryColorHover: '#7fe7c4',
          primaryColorPressed: '#5acea7',
          primaryColorSuppl: '#2a947d',
          bodyColor: '#101014',
          cardColor: '#18181c',
          modalColor: '#18181c',
          popoverColor: '#202026',
          borderRadius: '10px'
        },
        Card: {
          color: '#18181c',
          borderColor: '#2a2a32'
        },
        Layout: {
          siderColor: '#18181c',
          headerColor: '#18181c',
          color: '#101014'
        }
      }
    }
    return {
      common: {
        primaryColor: '#18a058',
        primaryColorHover: '#36ad6a',
        primaryColorPressed: '#0c7a43',
        primaryColorSuppl: '#36ad6a',
        bodyColor: '#f5f7f9',
        cardColor: '#ffffff',
        modalColor: '#ffffff',
        popoverColor: '#ffffff',
        borderRadius: '10px'
      },
      Card: {
        color: '#ffffff',
        borderColor: '#e5e7eb'
      },
      Layout: {
        siderColor: '#ffffff',
        headerColor: '#ffffff',
        color: '#f5f7f9'
      }
    }
  })

  function setMode(next: ThemeMode) {
    mode.value = next
    localStorage.setItem(THEME_KEY, next)
  }

  function cycleMode() {
    const order: ThemeMode[] = ['auto', 'light', 'dark']
    const idx = order.indexOf(mode.value)
    setMode(order[(idx + 1) % order.length])
  }

  watch(
    isDark,
    (dark) => {
      if (typeof document !== 'undefined') {
        document.documentElement.classList.toggle('dark', dark)
        document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light')
      }
    },
    { immediate: true }
  )

  return {
    mode,
    isDark,
    naiveTheme,
    themeOverrides,
    setMode,
    cycleMode
  }
})

