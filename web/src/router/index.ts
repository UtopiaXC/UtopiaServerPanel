import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useSiteStore } from '../stores/site'
import { useAuthStore } from '../stores/auth'

const DashboardLayout = () => import('../layouts/DashboardLayout.vue')
const SetupView = () => import('../views/SetupView.vue')
const LoginView = () => import('../views/LoginView.vue')
const FirstPasswordView = () => import('../views/FirstPasswordView.vue')
const BindView = () => import('../views/BindView.vue')
const ForbiddenView = () => import('../views/ForbiddenView.vue')
const DashboardView = () => import('../views/DashboardView.vue')
const TerminalView = () => import('../views/TerminalView.vue')
const LogsView = () => import('../views/LogsView.vue')
const UserHomeView = () => import('../views/UserHomeView.vue')
const UserSettingsView = () => import('../views/UserSettingsView.vue')
const TokensView = () => import('../views/TokensView.vue')
const AdminUsersView = () => import('../views/AdminUsersView.vue')
const AdminRolesView = () => import('../views/AdminRolesView.vue')
const AdminSettingsView = () => import('../views/AdminSettingsView.vue')

const routes: RouteRecordRaw[] = [
  { path: '/stp', name: 'setup', component: SetupView },
  { path: '/login', name: 'login', component: LoginView },
  {
    path: '/first-password',
    name: 'firstPassword',
    component: FirstPasswordView,
    meta: { requiresAuth: true }
  },
  {
    path: '/bind',
    name: 'bind',
    component: BindView,
    meta: { requiresAuth: true }
  },
  { path: '/forbidden', name: 'forbidden', component: ForbiddenView },
  {
    path: '/',
    component: DashboardLayout,
    children: [
      { path: '', name: 'rootRedirect', component: { render: () => null } },
      {
        path: 'dsh/overview',
        name: 'dashboard',
        component: DashboardView,
        meta: { module: 'dashboard', minLevel: 1 }
      },
      {
        path: 'dsh/terminal',
        name: 'terminal',
        component: TerminalView,
        meta: { module: 'terminal', minLevel: 1 }
      },
      {
        path: 'dsh/logs',
        name: 'logs',
        component: LogsView,
        meta: { module: 'logs', minLevel: 1 }
      },
      {
        path: 'home',
        name: 'home',
        component: UserHomeView,
        meta: { requiresAuth: true }
      },
      {
        path: 'tokens',
        name: 'tokens',
        component: TokensView,
        meta: { requiresAuth: true }
      },
      {
        path: 'settings',
        name: 'settings',
        component: UserSettingsView,
        meta: { requiresAuth: true }
      },
      {
        path: 'admin/users',
        name: 'adminUsers',
        component: AdminUsersView,
        meta: { requiresAuth: true, module: 'admin', minLevel: 1 }
      },
      {
        path: 'admin/roles',
        name: 'adminRoles',
        component: AdminRolesView,
        meta: { requiresAuth: true, module: 'admin', minLevel: 1 }
      },
      {
        path: 'admin/settings',
        name: 'adminSettings',
        component: AdminSettingsView,
        meta: { requiresAuth:true, module: 'admin', minLevel: 1 }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

function resolveDefaultRoute(authStore: ReturnType<typeof useAuthStore>): string {
  if (authStore.hasPermission('dashboard', 1)) return '/dsh/overview'
  if (authStore.hasPermission('terminal', 1)) return '/dsh/terminal'
  if (authStore.hasPermission('logs', 1)) return '/dsh/logs'
  if (authStore.isLoggedIn) return '/home'
  return '/login'
}

router.beforeEach(async (to) => {
  const siteStore = useSiteStore()
  const authStore = useAuthStore()

  try {
    await siteStore.ensureLoaded()
  } catch {
    // Allow staying on setup or login if backend isn't ready
  }

  if (!siteStore.setupCompleted) {
    if (to.path !== '/stp') return '/stp'
    return true
  }

  await authStore.initialize()

  if (to.path === '/stp') {
    return resolveDefaultRoute(authStore)
  }

  if (authStore.isLoggedIn) {
    if (authStore.mustChangePassword && to.path !== '/first-password') {
      return '/first-password'
    }
    if (
      !authStore.mustChangePassword &&
      authStore.needsBinding &&
      to.path !== '/bind' &&
      to.path !== '/first-password'
    ) {
      return '/bind'
    }
    if (to.path === '/login') {
      return resolveDefaultRoute(authStore)
    }
  }

  if (to.path === '/') {
    return resolveDefaultRoute(authStore)
  }

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const mod = to.meta.module as string | undefined
  const minLv = (to.meta.minLevel as number | undefined) ?? 1
  if (mod && !authStore.hasPermission(mod, minLv)) {
    if (!authStore.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    return '/forbidden'
  }

  return true
})

export default router

