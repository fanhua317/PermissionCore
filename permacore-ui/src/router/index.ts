import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/store/user';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '控制台', requiresAuth: true },
      },
      {
        path: 'user',
        name: 'UserManage',
        component: () => import('@/views/UserManage.vue'),
        meta: { title: '用户管理', requiresAuth: true, permission: 'system:user' },
      },
      {
        path: 'role',
        name: 'RoleManage',
        component: () => import('@/views/RoleManage.vue'),
        meta: { title: '角色管理', requiresAuth: true, permission: 'system:role' },
      },
      {
        path: 'permission',
        name: 'PermissionManage',
        component: () => import('@/views/PermissionManage.vue'),
        meta: { title: '权限管理', requiresAuth: true, permission: 'system:permission' },
      },
      {
        path: 'sod',
        name: 'SodManage',
        component: () => import('@/views/SodManage.vue'),
        meta: { title: '职责分离', requiresAuth: true, permission: 'system:sod' },
      },
      {
        path: 'dept',
        name: 'DeptManage',
        component: () => import('@/views/DeptManage.vue'),
        meta: { title: '部门管理', requiresAuth: true, permission: 'system:dept' },
      },
      {
        path: 'login-log',
        name: 'LoginLog',
        component: () => import('@/views/LoginLog.vue'),
        meta: { title: '登录日志', requiresAuth: true, permission: 'system:log' },
      },
      {
        path: 'oper-log',
        name: 'OperLog',
        component: () => import('@/views/OperLog.vue'),
        meta: { title: '操作日志', requiresAuth: true, permission: 'system:log' },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to, _from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - PermaCore IAM` : 'PermaCore IAM';

  const userStore = useUserStore();
  if (!to.meta.requiresAuth) {
    next();
    return;
  }

  if (!userStore.token) {
    next('/login');
    return;
  }

  try {
    if (!userStore.userInfo) {
      await userStore.fetchUserInfo();
    }
    const permission = to.meta.permission as string | undefined;
    if (permission && !userStore.hasPermission(permission)) {
      next('/dashboard');
      return;
    }
    next();
  } catch {
    userStore.clearToken();
    next('/login');
  }
});

export default router;
