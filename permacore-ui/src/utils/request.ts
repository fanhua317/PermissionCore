import axios from 'axios';
import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';
import router from '../router';

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

interface RefreshResponse {
  code: number;
  msg?: string;
  data?: {
    accessToken?: string;
    refreshToken?: string;
  };
}

interface ErrorResponse {
  msg?: unknown;
}

interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export class RequestError extends Error {
  readonly status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = 'RequestError';
    this.status = status;
  }
}

export const getRequestErrorStatus = (error: unknown) => {
  if (error instanceof RequestError) return error.status;
  if (axios.isAxiosError(error)) return error.response?.status;
  return undefined;
};

interface AuthStoreAdapter {
  setToken: (accessToken: string, refreshToken: string) => void;
  clearToken: () => void;
}

let refreshPromise: Promise<TokenPair> | null = null;
let clearSessionPromise: Promise<void> | null = null;
let authStoreAdapter: AuthStoreAdapter | null = null;

export const bindAuthStore = (adapter: AuthStoreAdapter) => {
  authStoreAdapter = adapter;
};

const service = axios.create({
  baseURL: '', // Use relative path for proxy
  timeout: 10000,
});

const isNonEmptyString = (value: unknown): value is string =>
  typeof value === 'string' && value.trim().length > 0;

const getErrorMessage = (error: unknown, fallback: string) => {
  if (axios.isAxiosError<ErrorResponse>(error)) {
    const responseMessage = error.response?.data?.msg;
    if (isNonEmptyString(responseMessage)) return responseMessage;
    if (isNonEmptyString(error.message)) return error.message;
  }
  if (error instanceof Error && isNonEmptyString(error.message)) return error.message;
  return fallback;
};

const toRequestError = (error: unknown, fallback: string, defaultStatus?: number) =>
  error instanceof RequestError
    ? error
    : new RequestError(getErrorMessage(error, fallback), getRequestErrorStatus(error) ?? defaultStatus);

const syncTokensToStore = ({ accessToken, refreshToken }: TokenPair) => {
  localStorage.setItem('accessToken', accessToken);
  localStorage.setItem('refreshToken', refreshToken);
  authStoreAdapter?.setToken(accessToken, refreshToken);
};

const clearAuthSession = async () => {
  if (clearSessionPromise) return clearSessionPromise;

  clearSessionPromise = (async () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');

    authStoreAdapter?.clearToken();

    if (router.currentRoute.value.path !== '/login') {
      const redirect = router.currentRoute.value.fullPath;
      await router.replace({ path: '/login', query: { redirect } });
    }
  })().finally(() => {
    clearSessionPromise = null;
  });

  return clearSessionPromise;
};

const requestTokenRefresh = () => {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!isNonEmptyString(refreshToken)) {
      throw new RequestError('登录状态已失效，请重新登录', 401);
    }

    let response: AxiosResponse<RefreshResponse>;
    try {
      response = await axios.post<RefreshResponse>('/api/auth/refresh', { refreshToken });
    } catch (error) {
      throw toRequestError(error, '刷新登录状态失败');
    }
    const accessToken = response.data?.data?.accessToken;
    const newRefreshToken = response.data?.data?.refreshToken;

    if (response.data?.code !== 200 || !isNonEmptyString(accessToken) || !isNonEmptyString(newRefreshToken)) {
      throw new RequestError(response.data?.msg || '刷新登录状态失败', 401);
    }

    const tokens = { accessToken, refreshToken: newRefreshToken };
    syncTokensToStore(tokens);
    return tokens;
  })().finally(() => {
    refreshPromise = null;
  });

  return refreshPromise;
};

service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

service.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, msg, data } = response.data;
    if (code === 200) {
      return data;
    } else {
      ElMessage.error(msg || '请求失败');
      return Promise.reject(new RequestError(msg || '请求失败', response.status));
    }
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined;
    const requestUrl = originalRequest?.url ?? '';
    const isLoginOrRefreshRequest = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/refresh');

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isLoginOrRefreshRequest) {
      originalRequest._retry = true;
      try {
        const { accessToken } = await requestTokenRefresh();
        originalRequest.headers.set('Authorization', `Bearer ${accessToken}`);
        return service(originalRequest);
      } catch (refreshError) {
        if (getRequestErrorStatus(refreshError) === 401) {
          await clearAuthSession();
        }
        return Promise.reject(refreshError);
      }
    }
    if (error.response?.status === 401 && originalRequest?._retry) {
      await clearAuthSession();
    }
    if (error.response?.status === 403) {
      ElMessage.error('权限不足，请联系管理员');
    }
    return Promise.reject(toRequestError(error, '请求失败'));
  }
);

export default service;
