import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import request from '@/utils/request';

export interface SessionRole {
  id: number;
  roleKey: string;
  roleName: string;
  sortOrder?: number;
  active: boolean;
  effective: boolean;
}

interface UserInfo {
  userId: number;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  permissions: string[];
  roles: SessionRole[];
  activeRoleIds: number[];
  effectiveRoleIds: number[];
}

interface SessionPayload {
  permissions?: string[];
  roles?: SessionRole[];
  activeRoleIds?: number[];
  effectiveRoleIds?: number[];
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('accessToken') || '');
  const userInfo = ref<UserInfo | null>(null);
  const isLogin = ref(!!token.value);

  const permissions = computed(() => userInfo.value?.permissions ?? []);

  const applySessionPayload = (payload: SessionPayload) => {
    if (!userInfo.value) return;
    userInfo.value.permissions = payload.permissions ?? [];
    userInfo.value.roles = payload.roles ?? [];
    userInfo.value.activeRoleIds = payload.activeRoleIds ?? [];
    userInfo.value.effectiveRoleIds = payload.effectiveRoleIds ?? [];
  };

  const setToken = (accessToken: string, refreshToken: string) => {
    token.value = accessToken;
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    isLogin.value = true;
  };

  const fetchUserInfo = async () => {
    if (!token.value) {
      userInfo.value = null;
      isLogin.value = false;
      return null;
    }

    const data: any = await request.get('/api/auth/info');
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
      email: data.email ?? '',
      phone: data.phone ?? '',
      permissions: data.permissions ?? [],
      roles: data.roles ?? [],
      activeRoleIds: data.activeRoleIds ?? [],
      effectiveRoleIds: data.effectiveRoleIds ?? [],
    };
    isLogin.value = true;
    return userInfo.value;
  };

  const loadSessionRoles = async () => {
    const data: any = await request.get('/api/auth/session-roles');
    applySessionPayload(data);
    return data;
  };

  const switchActiveRoles = async (activeRoleIds: number[]) => {
    const data: any = await request.put('/api/auth/session-roles', { activeRoleIds });
    setToken(data.accessToken, data.refreshToken);
    applySessionPayload(data);
    return data;
  };

  const hasPermission = (permission?: string) => {
    if (!permission) return true;
    const list = permissions.value;
    return list.includes('admin:*') || list.includes(permission);
  };

  const hasAnyPermission = (permissionList: string[]) => {
    if (!permissionList.length) return true;
    const list = permissions.value;
    return list.includes('admin:*') || permissionList.some((permission) => list.includes(permission));
  };

  const clearToken = () => {
    token.value = '';
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    isLogin.value = false;
    userInfo.value = null;
  };

  const logout = async () => {
    try {
      if (token.value) {
        await request.post('/api/auth/logout');
      }
    } finally {
      clearToken();
    }
  };

  return {
    token,
    userInfo,
    isLogin,
    permissions,
    setToken,
    fetchUserInfo,
    loadSessionRoles,
    switchActiveRoles,
    hasPermission,
    hasAnyPermission,
    clearToken,
    logout,
  };
});
