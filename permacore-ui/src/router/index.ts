import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/store/user';
import { ElMessage } from 'element-plus';

const getErrorStatus = (error: unknown) => {
  if (typeof error !== 'object' || error === null || !('status' in error)) return undefined;
  const status = (error as { status?: unknown }).status;
  return typeof status === 'number' ? status : undefined;
};

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
        meta: { title: '用户管理', requiresAuth: true, permission: 'system:user:query' },
      },
      {
        path: 'role',
        name: 'RoleManage',
        component: () => import('@/views/RoleManage.vue'),
        meta: { title: '角色管理', requiresAuth: true, permission: 'system:role:query' },
      },
      {
        path: 'permission',
        name: 'PermissionManage',
        component: () => import('@/views/PermissionManage.vue'),
        meta: { title: '权限管理', requiresAuth: true, permission: 'system:permission:query' },
      },
      {
        path: 'sod',
        name: 'SodManage',
        component: () => import('@/views/SodManage.vue'),
        meta: { title: '职责分离', requiresAuth: true, permission: 'system:sod:query' },
      },
      {
        path: 'dept',
        name: 'DeptManage',
        component: () => import('@/views/DeptManage.vue'),
        meta: { title: '部门管理', requiresAuth: true, permission: 'system:dept:query' },
      },
      {
        path: 'login-log',
        name: 'LoginLog',
        component: () => import('@/views/LoginLog.vue'),
        meta: { title: '登录日志', requiresAuth: true, permission: 'system:log:query' },
      },
      {
        path: 'oper-log',
        name: 'OperLog',
        component: () => import('@/views/OperLog.vue'),
        meta: { title: '操作日志', requiresAuth: true, permission: 'system:log:query' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
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
  } catch (error) {
    if (getErrorStatus(error) === 401) {
      userStore.clearToken();
      next('/login');
      return;
    }
    ElMessage.error('认证服务暂不可用，请稍后重试');
    next(false);
  }
});

export default router;
