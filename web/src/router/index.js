import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const MainLayout = () => import('../layouts/MainLayout.vue');
const AdminLayout = () => import('../layouts/AdminLayout.vue');
const AuthLayout = () => import('../layouts/AuthLayout.vue');
const UserLayout = () => import('../layouts/UserLayout.vue');

const Dashboard = () => import('../components/Dashboard.vue');
const Terminal = () => import('../components/Terminal.vue');
const Logs = () => import('../components/Logs.vue');

const Login = () => import('../pages/Login.vue');
const Register = () => import('../pages/Register.vue');
const Forbidden = () => import('../pages/Forbidden.vue');
const RolesPage = () => import('../pages/admin/Roles.vue');
const UsersPage = () => import('../pages/admin/Users.vue');
const SystemSettings = () => import('../pages/admin/SystemSettings.vue');
const MonitorSettings = () => import('../pages/admin/MonitorSettings.vue');

const UserHome = () => import('../pages/user/Home.vue');
const UserProfile = () => import('../pages/user/Profile.vue');
const FirstModifyPassword = () => import('../pages/user/FirstModifyPassword.vue');
const UserBind = () => import('../pages/user/Bind.vue');

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: AuthLayout, children: [{ path: '', name: 'Login', component: Login }] },
  { path: '/register', component: AuthLayout, children: [{ path: '', name: 'Register', component: Register }] },
  { path: '/', component: MainLayout,
    children: [
      { path: 'dashboard', name: 'Dashboard', component: Dashboard },
      { path: 'console', name: 'Console', component: Terminal },
      { path: 'logs', name: 'Logs', component: Logs }
    ]
  },
  { path: '/admin', component: AdminLayout, meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/admin/system' },
      { path: 'roles', name: 'AdminRoles', component: RolesPage, meta: { permKey: 'admin', minLevel: 1 } },
      { path: 'users', name: 'AdminUsers', component: UsersPage, meta: { permKey: 'admin', minLevel: 1 } },
      { path: 'system', name: 'AdminSystem', component: SystemSettings, meta: { permKey: 'admin', minLevel: 1 } },
      { path: 'logs-config', name: 'AdminLogsConfig', component: MonitorSettings, meta: { permKey: 'admin', minLevel: 1 } },
    ]
  },
  { path: '/user', component: UserLayout, meta: { requiresAuth: true },
    children: [
      { path: 'home', name: 'UserHome', component: UserHome },
      { path: 'profile', name: 'UserProfile', component: UserProfile },
      { path: 'first-modify-password', name: 'FirstModifyPassword', component: FirstModifyPassword },
      { path: 'bind', name: 'UserBind', component: UserBind }
    ]
  },
  { path: '/forbidden', name: 'Forbidden', component: Forbidden },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
];

const router = createRouter({ history: createWebHistory(), routes });

router.beforeEach(async (to, from, next) => {
  const auth = useAuthStore();

  // Wait for auth initialization (already started in App.vue)
  if (!auth.isInitialized) {
    // The init is async and started in App.vue - App.vue won't render router-view
    // until isInitialized is true, so this shouldn't normally be reached.
    return next();
  }

  // If logged in and trying to reach login/register, redirect away
  if (auth.isLoggedIn && (to.name === 'Login' || to.name === 'Register')) {
    if (auth.mustChangePassword) return next('/user/first-modify-password');
    if (!auth.isAdmin && auth.bindingStatus !== 'bound') return next('/user/bind');
    return next('/dashboard');
  }

  if (to.matched.some(r => r.meta.requiresAuth)) {
    if (!auth.isLoggedIn) return next({ name: 'Login', query: { redirect: to.fullPath } });
    if (!auth.user) { try { await auth.fetchUser(); } catch { return next({ name: 'Login' }); } }

    // Force password change for any user with mustChangePassword
    if (auth.mustChangePassword && to.name !== 'FirstModifyPassword' && to.name !== 'UserHome')
      return next('/user/first-modify-password');

    // Non-admin: force binding
    if (!auth.isAdmin && auth.bindingStatus !== 'bound' && to.name !== 'UserBind' && to.name !== 'UserHome')
      return next('/user/bind');

    // Check level-based permissions
    const permKey = to.meta.permKey;
    const minLevel = to.meta.minLevel || 1;
    if (permKey && auth.getPermLevel(permKey) < minLevel) return next('/forbidden');
  }
  next();
});

export default router;
