import { h } from 'vue'
import type { Theme } from 'vitepress'
import DefaultTheme from 'vitepress/theme'
import HeroDownloadDropdown from './HeroDownloadDropdown.vue'

export default {
  extends: DefaultTheme,
  Layout: () => {
    return h(DefaultTheme.Layout, null, {
      'home-hero-actions-after': () => h(HeroDownloadDropdown)
    })
  }
} satisfies Theme
