import { defineStore } from 'pinia';
import { ref } from 'vue';
import request from '@/utils/request';

interface UserInfo {
  userId: number;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  permissions: string[];
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('accessToken') || '');
  const userInfo = ref<UserInfo | null>(null);
  const isLogin = ref(!!token.value);

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
    };
    isLogin.value = true;
    return userInfo.value;
  };

  const clearToken = () => {
    token.value = '';
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    isLogin.value = false;
    userInfo.value = null;
  };

  return {
    token,
    userInfo,
    isLogin,
    setToken,
    fetchUserInfo,
    clearToken,
  };
});