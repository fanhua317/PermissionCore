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
        meta: { title: '用户管理', requiresAuth: true },
      },
      {
        path: 'role',
        name: 'RoleManage',
        component: () => import('@/views/RoleManage.vue'),
        meta: { title: '角色管理', requiresAuth: true },
      },
      {
        path: 'permission',
        name: 'PermissionManage',
        component: () => import('@/views/PermissionManage.vue'),
        meta: { title: '权限管理', requiresAuth: true },
      },
      {
        path: 'sod',
        name: 'SodManage',
        component: () => import('@/views/SodManage.vue'),
        meta: { title: '职责分离', requiresAuth: true },
      },
      {
        path: 'dept',
        name: 'DeptManage',
        component: () => import('@/views/DeptManage.vue'),
        meta: { title: '部门管理', requiresAuth: true },
      },
      {
        path: 'login-log',
        name: 'LoginLog',
        component: () => import('@/views/LoginLog.vue'),
        meta: { title: '登录日志', requiresAuth: true },
      },
      {
        path: 'oper-log',
        name: 'OperLog',
        component: () => import('@/views/OperLog.vue'),
        meta: { title: '操作日志', requiresAuth: true },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, _from, next) => {
  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - PermaCore IAM` : 'PermaCore IAM';
  
  const userStore = useUserStore();
  if (to.meta.requiresAuth) {
    // 兼容刷新：本地有 token 但 store 尚未加载 userInfo
    if (!userStore.token) {
      next('/login');
      return;
    }
    if (!userStore.userInfo) {
      userStore.fetchUserInfo()
        .then(() => next())
        .catch(() => next('/login'));
      return;
    }
  }
  next();
});

export default router;
