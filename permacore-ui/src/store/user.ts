import { defineStore } from 'pinia';
import { ref } from 'vue';

interface UserInfo {
  userId: number;
  username: string;
  nickname: string;
  permissions: string[];
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('accessToken') || '');
  const userInfo = ref<UserInfo | null>(null);
  const isLogin = ref(false);

  const setToken = (accessToken: string, refreshToken: string) => {
    token.value = accessToken;
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    isLogin.value = true;
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
    clearToken,
  };
});