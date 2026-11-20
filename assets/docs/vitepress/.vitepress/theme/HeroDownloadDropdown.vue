<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useData } from 'vitepress'

const { lang } = useData()

const isMounted = ref(false)
const isOpen = ref(false)
const dropdownRef = ref<HTMLElement | null>(null)
let closeTimer: ReturnType<typeof setTimeout> | null = null

const isEn = computed(() => lang.value.startsWith('en'))
const label = computed(() => (isEn.value ? 'Download' : '下载'))

const links = computed(() => [
  {
    text: 'CurseForge',
    href: 'https://www.curseforge.com/minecraft/mc-mods/utopia-server-panel'
  },
  {
    text: 'Modrinth',
    href: 'https://modrinth.com/mod/utopia-server-panel'
  },
  {
    text: 'GitHub Releases',
    href: 'https://github.com/UtopiaXC/UtopiaServerPanel/releases'
  },
  {
    text: isEn.value ? 'MCMOD' : 'MC百科',
    href: 'https://www.mcmod.cn/class/32953.html'
  }
])

function clearCloseTimer() {
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = null
  }
}

function handleMouseEnter() {
  clearCloseTimer()
  isOpen.value = true
}

function handleMouseLeave() {
  clearCloseTimer()
  closeTimer = setTimeout(() => {
    isOpen.value = false
  }, 120)
}

function toggleOpen() {
  clearCloseTimer()
  isOpen.value = !isOpen.value
}

function handleDocumentClick(event: MouseEvent) {
  if (dropdownRef.value && !dropdownRef.value.contains(event.target as Node)) {
    isOpen.value = false
  }
}

function handleKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    isOpen.value = false
  }
}

onMounted(() => {
  isMounted.value = true
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleKeyDown)
})

onBeforeUnmount(() => {
  clearCloseTimer()
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <Teleport v-if="isMounted" to=".VPHero .actions">
    <div
      ref="dropdownRef"
      class="action hero-download-dropdown"
      @mouseenter="handleMouseEnter"
      @mouseleave="handleMouseLeave"
    >
      <button
        type="button"
        class="hero-download-btn"
        :aria-expanded="isOpen"
        aria-haspopup="menu"
        @click="toggleOpen"
      >
        <span>{{ label }}</span>
        <svg
          class="chevron"
          :class="{ open: isOpen }"
          xmlns="http://www.w3.org/2000/svg"
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>

      <div v-show="isOpen" class="hero-download-menu" role="menu">
        <a
          v-for="item in links"
          :key="item.href"
          :href="item.href"
          target="_blank"
          rel="noreferrer"
          class="hero-download-item"
          role="menuitem"
          @click="isOpen = false"
        >
          <span>{{ item.text }}</span>
          <svg
            class="external-icon"
            xmlns="http://www.w3.org/2000/svg"
            width="12"
            height="12"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
            <polyline points="15 3 21 3 21 9" />
            <line x1="10" y1="14" x2="21" y2="3" />
          </svg>
        </a>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.hero-download-dropdown {
  position: relative;
  display: inline-block;
  flex-shrink: 0;
  padding: 6px;
}

.hero-download-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 20px;
  padding: 0 20px;
  line-height: 38px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  border: 1px solid var(--vp-button-alt-border);
  color: var(--vp-button-alt-text);
  background-color: var(--vp-button-alt-bg);
  transition: color 0.25s, border-color 0.25s, background-color 0.25s;
}

.hero-download-btn:hover {
  border-color: var(--vp-button-alt-hover-border);
  color: var(--vp-button-alt-hover-text);
  background-color: var(--vp-button-alt-hover-bg);
}

.hero-download-btn:active {
  border-color: var(--vp-button-alt-active-border);
  color: var(--vp-button-alt-active-text);
  background-color: var(--vp-button-alt-active-bg);
}

.chevron {
  transition: transform 0.2s ease;
}

.chevron.open {
  transform: rotate(180deg);
}

.hero-download-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  min-width: 176px;
  padding: 8px;
  border-radius: 12px;
  border: 1px solid var(--vp-c-divider);
  background-color: var(--vp-c-bg-elv);
  box-shadow: var(--vp-shadow-3);
  z-index: 30;
}

.hero-download-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 12px;
  line-height: 34px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 6px;
  color: var(--vp-c-text-1);
  text-decoration: none;
  white-space: nowrap;
  transition: background-color 0.25s, color 0.25s;
}

.hero-download-item:hover {
  color: var(--vp-c-brand-1);
  background-color: var(--vp-c-default-soft);
}

.external-icon {
  opacity: 0.6;
  flex-shrink: 0;
}
</style>
