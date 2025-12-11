import axios, { AxiosRequestConfig, AxiosResponse } from 'axios';
import { ElMessage } from 'element-plus';
import router from '../router';

// 创建axios实例
const service = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000,
});

// 请求拦截器：自动添加Token
service.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers = {
        ...config.headers,
        Authorization: `Bearer ${token}`,
      };
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器：处理Token过期
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, msg, data } = response.data;
    if (code === 200) {
      return data;
    } else {
      ElMessage.error(msg || '请求失败');
      return Promise.reject(new Error(msg || '请求失败'));
    }
  },
  async (error) => {
    const originalRequest = error.config;

    // Token过期自动刷新
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = localStorage.getItem('refreshToken');
        const res = await axios.post('http://localhost:8080/api/auth/refresh', {
          refreshToken,
        });

        const { accessToken, refreshToken: newRefreshToken } = res.data;
        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', newRefreshToken);

        // 重试原请求
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return service(originalRequest);
      } catch (refreshError) {
        // 刷新失败，跳转到登录页
        localStorage.clear();
        router.push('/login');
        return Promise.reject(refreshError);
      }
    }

    // 403权限不足
    if (error.response?.status === 403) {
      ElMessage.error('权限不足，请联系管理员');
    }

    return Promise.reject(error);
  }
);

export default service;