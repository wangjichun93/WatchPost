import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResult } from '@/types'

/**
 * Axios 客户端: 统一注入 JWT、解包 ApiResult、401 重定向登录页。
 * 后端所有响应为 { code, message, data, timestamp }; code=0 成功。
 */
const TOKEN_KEY = 'wp_access_token'
const REFRESH_KEY = 'wp_refresh_token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  setRefresh: (t: string) => localStorage.setItem(REFRESH_KEY, t),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

export const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截: 注入 Bearer 令牌
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.get()
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 业务错误 (code != 0) */
export class BizError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

// 响应拦截: 解包 ApiResult, 统一错误处理
http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    // 非标准包络 (如 actuator) 直接返回
    if (body == null || typeof body.code !== 'number') {
      return response.data
    }
    if (body.code === 0) {
      // 把 data 提升到顶层, 调用方直接拿 data
      return body.data
    }
    return Promise.reject(new BizError(body.code, body.message))
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    if (status === 401) {
      // 令牌失效: 清理并重定向登录页 (保留当前路径用于回跳)
      tokenStore.clear()
      const current = window.location.pathname + window.location.search
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = `/login?redirect=${encodeURIComponent(current)}`
      }
      return Promise.reject(new BizError(401, '未认证或令牌失效'))
    }
    const msg =
      (error.response?.data as ApiResult<unknown>)?.message ||
      error.message ||
      '网络错误'
    return Promise.reject(new BizError(status ?? -1, msg))
  },
)

/** 类型化 GET (返回已解包的 data) */
export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return http.get(url, { params }) as unknown as Promise<T>
}
export async function post<T>(url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
  return http.post(url, body, { params }) as unknown as Promise<T>
}
export async function put<T>(url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
  return http.put(url, body, { params }) as unknown as Promise<T>
}
export async function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return http.delete(url, { params }) as unknown as Promise<T>
}
