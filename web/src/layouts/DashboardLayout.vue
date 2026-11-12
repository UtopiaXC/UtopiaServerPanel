<template>
  <n-layout :has-sider="!isMobile" style="height: 100vh;">
    <!-- Desktop Sidebar -->
    <n-layout-sider
      v-if="!isMobile"
      bordered
      show-trigger="bar"
      collapse-mode="width"
      :collapsed="collapsed"
      :collapsed-width="64"
      :width="232"
      style="background: var(--usp-surface);"
      @collapse="collapsed = true"
      @expand="collapsed = false"
    >
      <div class="brand-box" :class="{ collapsed }">
        <svg class="brand-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2L3 7l9 5 9-5-9-5z" />
          <path d="M3 7v10l9 5 9-5V7" />
          <path d="M12 12v10" />
        </svg>
        <span v-if="!collapsed" class="brand-name">{{ siteStore.siteName }}</span>
      </div>

      <div style="flex: 1; overflow-y: auto;">
        <n-menu
          :collapsed="collapsed"
          :collapsed-width="64"
          :collapsed-icon-size="20"
          :value="activeKey"
          :options="menuOptions"
          @update:value="handleMenuSelect"
        />
      </div>
    </n-layout-sider>

    <!-- Mobile Navigation Drawer -->
    <n-drawer
      v-if="isMobile"
      v-model:show="showMobileDrawer"
      placement="left"
      :width="250"
    >
      <n-drawer-content
        :body-content-style="{
          padding: '0',
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          background: 'var(--usp-surface)'
        }"
      >
        <div class="brand-box" style="border-bottom: 1px solid var(--usp-border);">
          <svg class="brand-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2L3 7l9 5 9-5-9-5z" />
            <path d="M3 7v10l9 5 9-5V7" />
            <path d="M12 12v10" />
          </svg>
          <span class="brand-name">{{ siteStore.siteName }}</span>
        </div>

        <div style="flex: 1; overflow-y: auto;">
          <n-menu
            :value="activeKey"
            :options="menuOptions"
            @update:value="(key: string) => { handleMenuSelect(key); showMobileDrawer = false; }"
          />
        </div>
      </n-drawer-content>
    </n-drawer>

    <!-- Main Layout -->
    <n-layout>
      <n-layout-header
        bordered
        :style="{
          height: '60px',
          padding: isMobile ? '0 12px' : '0 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: 'var(--usp-surface)'
        }"
      >
        <div style="display: flex; align-items: center; gap: 10px; min-width: 0;">
          <n-button
            v-if="isMobile"
            quaternary
            circle
            size="medium"
            @click="showMobileDrawer = true"
          >
            <template #icon>
              <n-icon size="20"><MenuOutline /></n-icon>
            </template>
          </n-button>
          <div class="header-title">
            {{ currentTitle }}
          </div>
        </div>

        <div style="display: flex; align-items: center; gap: 8px;">
          <ThemeSwitcher />
          <LanguageSelector :compact="isMobile" />

          <template v-if="authStore.isLoggedIn && authStore.user">
            <n-tag type="success" size="small" :bordered="false" style="cursor: pointer;" @click="router.push('/home')">
              {{
                isMobile
                  ? authStore.user.nickname || authStore.user.username
                  : `${authStore.user.nickname || authStore.user.username} · ${authStore.user.roleDisplayName || authStore.user.roleName}`
              }}
            </n-tag>
            <n-button size="small" quaternary @click="handleLogout">
              <template v-if="isMobile" #icon>
                <n-icon><LogOutOutline /></n-icon>
              </template>
              <span v-if="!isMobile">{{ $t('common.logout') }}</span>
            </n-button>
          </template>

          <template v-else>
            <n-tag size="small" :bordered="false" type="info">{{ $t('common.guest') }}</n-tag>
            <n-button size="small" type="primary" @click="router.push('/login')">
              {{ $t('common.login') }}
            </n-button>
            <n-button
              v-if="siteStore.allowRegistration && !isMobile"
              size="small"
              secondary
              @click="router.push('/login?mode=register')"
            >
              {{ $t('common.register') }}
            </n-button>
          </template>
        </div>
      </n-layout-header>

      <n-layout-content
        :content-style="{
          minHeight: 'calc(100vh - 60px)',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          padding: isMobile ? '14px 12px' : '24px',
          background: 'var(--usp-bg)'
        }"
      >
        <div style="flex: 1 0 auto; width: 100%; max-width: 1440px; margin: 0 auto;">
          <router-view />
        </div>

        <footer class="app-footer">
          <span>
            Powered by
            <a
              href="https://github.com/UtopiaXC/UtopiaServerPanel"
              target="_blank"
              rel="noopener noreferrer"
              class="footer-link"
            >
              Utopia Server Panel
            </a>
          </span>
        </footer>
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<script setup lang="ts">
import { ref, computed, h, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NIcon, type MenuOption } from 'naive-ui'
import {
  SpeedometerOutline,
  TerminalOutline,
  AnalyticsOutline,
  HomeOutline,
  KeyOutline,
  SettingsOutline,
  PeopleOutline,
  ShieldCheckmarkOutline,
  OptionsOutline,
  MenuOutline,
  LogOutOutline
} from '@vicons/ionicons5'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import LanguageSelector from '../components/LanguageSelector.vue'
import { useSiteStore } from '../stores/site'
import { useAuthStore } from '../stores/auth'
import { retainWebSocket, releaseWebSocket } from '../composables/useWebSocket'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const siteStore = useSiteStore()
const authStore = useAuthStore()

const collapsed = ref(false)
const isMobile = ref(false)
const showMobileDrawer = ref(false)

function handleResize() {
  isMobile.value = window.innerWidth <= 768
}

function renderIcon(icon: any) {
  return () => h(NIcon, null, { default: () => h(icon) })
}

const activeKey = computed(() => route.path)

const currentTitle = computed(() => {
  const map: Record<string, string> = {
    '/dsh/overview': t('nav.dashboard'),
    '/dsh/terminal': t('nav.terminal'),
    '/dsh/logs': t('nav.logs'),
    '/home': t('nav.home'),
    '/tokens': t('nav.tokens'),
    '/settings': t('nav.settings'),
    '/admin/users': t('nav.adminUsers'),
    '/admin/roles': t('nav.adminRoles'),
    '/admin/settings': t('nav.adminSettings')
  }
  return map[route.path] || siteStore.siteName
})

const menuOptions = computed<MenuOption[]>(() => {
  const items: MenuOption[] = []

  if (authStore.hasPermission('dashboard', 1)) {
    items.push({
      label: () => t('nav.dashboard'),
      key: '/dsh/overview',
      icon: renderIcon(SpeedometerOutline)
    })
  }
  if (authStore.hasPermission('terminal', 1)) {
    items.push({
      label: () => t('nav.terminal'),
      key: '/dsh/terminal',
      icon: renderIcon(TerminalOutline)
    })
  }
  if (authStore.hasPermission('logs', 1)) {
    items.push({
      label: () => t('nav.logs'),
      key: '/dsh/logs',
      icon: renderIcon(AnalyticsOutline)
    })
  }

  if (authStore.isLoggedIn) {
    if (items.length > 0) {
      items.push({ type: 'divider', key: 'div-personal' })
    }
    items.push(
      { label: () => t('nav.home'), key: '/home', icon: renderIcon(HomeOutline) },
      { label: () => t('nav.tokens'), key: '/tokens', icon: renderIcon(KeyOutline) },
      { label: () => t('nav.settings'), key: '/settings', icon: renderIcon(SettingsOutline) }
    )
  }

  if (authStore.hasPermission('admin', 1)) {
    if (items.length > 0) {
      items.push({ type: 'divider', key: 'div-admin' })
    }
    items.push(
      { label: () => t('nav.adminUsers'), key: '/admin/users', icon: renderIcon(PeopleOutline) },
      {
        label: () => t('nav.adminRoles'),
        key: '/admin/roles',
        icon: renderIcon(ShieldCheckmarkOutline)
      },
      {
        label: () => t('nav.adminSettings'),
        key: '/admin/settings',
        icon: renderIcon(OptionsOutline)
      }
    )
  }

  return items
})

function handleMenuSelect(key: string) {
  router.push(key)
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
  retainWebSocket()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  releaseWebSocket()
})
</script>

<style scoped>
:deep(.n-layout-sider-scroll-container) {
  display: flex !important;
  flex-direction: column !important;
  height: 100% !important;
}

.brand-box {
  height: 60px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--usp-primary);
  font-weight: 700;
  font-size: 16px;
  letter-spacing: 0.3px;
  border-bottom: 1px solid var(--usp-border);
  white-space: nowrap;
  overflow: hidden;
}

.brand-box.collapsed {
  padding: 0;
}

.brand-icon {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.brand-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-title {
  font-weight: 600;
  font-size: 16px;
  color: var(--usp-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-footer {
  margin-top: 32px;
  padding: 16px 0 6px;
  text-align: center;
  font-size: 12px;
  color: var(--usp-text-muted);
  border-top: 1px solid var(--usp-border);
}

.footer-link {
  color: var(--usp-primary);
  text-decoration: none;
  font-weight: 500;
}

.footer-link:hover {
  text-decoration: underline;
}
</style>

