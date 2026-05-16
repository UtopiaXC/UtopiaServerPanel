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
const UsersPage = () => import('../pages/admin/Users.vue');
const RolesPage = () => import('../pages/admin/Roles.vue');
const ProfilePage = () => import('../pages/admin/Profile.vue');

const UserHome = () => import('../pages/user/Home.vue');
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
      { path: '', redirect: '/admin/users' },
      { path: 'users', name: 'AdminUsers', component: UsersPage, meta: { permission: 'admin.users.read' } },
      { path: 'roles', name: 'AdminRoles', component: RolesPage, meta: { permission: 'admin.roles.read' } },
      { path: 'profile', name: 'AdminProfile', component: ProfilePage }
    ]
  },
  { path: '/user', component: UserLayout, meta: { requiresAuth: true },
    children: [
      { path: 'home', name: 'UserHome', component: UserHome },
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

  if (auth.isLoggedIn && (to.name === 'Login' || to.name === 'Register')) {
    if (auth.mustChangePassword) return next('/user/first-modify-password');
    if (!auth.isAdmin && auth.bindingStatus !== 'bound') return next('/user/bind');
    return next('/dashboard');
  }

  if (to.matched.some(r => r.meta.requiresAuth)) {
    if (!auth.isLoggedIn) return next({ name: 'Login', query: { redirect: to.fullPath } });
    if (!auth.user) { try { await auth.fetchUser(); } catch { return next({ name: 'Login' }); } }

    // Admin: force password change
    if (auth.mustChangePassword && to.path.startsWith('/admin'))
      return next('/user/first-modify-password');
    if (auth.mustChangePassword && to.name !== 'FirstModifyPassword' && to.name !== 'UserHome')
      return next('/user/first-modify-password');

    // Non-admin: force binding
    if (!auth.isAdmin && auth.bindingStatus !== 'bound' && to.name !== 'UserBind' && to.name !== 'UserHome')
      return next('/user/bind');

    const perm = to.meta.permission;
    if (perm && !auth.hasPermission(perm)) return next('/forbidden');
  }
  next();
});

export default router;
