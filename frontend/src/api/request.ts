import axios, { AxiosError, type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types/api'

/** localStorage 键：与 stores/auth.ts 共用，避免两处硬编码 */
export const TOKEN_KEY = 'suite_token'
export const REFRESH_KEY = 'suite_refresh_token'
export const USER_KEY = 'suite_user'

/** 业务错误（HTTP 200 但 code !== 0，或非 2xx 且后端给了 Result 包裹） */
export class BizError extends Error {
  readonly code: number
  readonly traceId?: string

  constructor(code: number, message: string, traceId?: string) {
    super(message)
    this.name = 'BizError'
    this.code = code
    this.traceId = traceId
  }
}

interface RetryableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

/* --------------------------- 统一错误提示 --------------------------- */

let lastToast = ''
let lastToastAt = 0

function toastError(message: string, traceId?: string): void {
  // 同一毫秒内的重复报错（比如 401 并发）只提示一次，避免刷屏
  const now = Date.now()
  if (message === lastToast && now - lastToastAt < 800) return
  lastToast = message
  lastToastAt = now
  const text = traceId ? `${message}（traceId: ${traceId}）` : message
  ElMessage.error({ message: text, duration: 4000, showClose: true })
}

export function clearAuthStorage(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}

/* ---------------------------- Token 刷新 ---------------------------- */

let refreshPromise: Promise<string | null> | null = null

/** 单飞刷新：并发 401 只发一次 /auth/refresh，其余请求等待同一个 Promise */
async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) return null

  refreshPromise = (async () => {
    try {
      // 用裸 axios，避免走本实例的拦截器造成递归
      const resp = await axios.post<ApiResult<{ accessToken: string; refreshToken: string }>>(
        `${baseURL}/auth/refresh`,
        { refreshToken },
        { headers: { 'Content-Type': 'application/json' }, timeout: 10000 }
      )
      const data = resp.data
      if (data.code !== 0 || !data.data?.accessToken) return null
      localStorage.setItem(TOKEN_KEY, data.data.accessToken)
      localStorage.setItem(REFRESH_KEY, data.data.refreshToken)
      return data.data.accessToken
    } catch {
      return null
    } finally {
      refreshPromise = null
    }
  })()

  return refreshPromise
}

function isLoginRequest(config?: InternalAxiosRequestConfig): boolean {
  return config?.url ? config.url.includes('/auth/login') : false
}

/* ----------------------------- 拦截器 ----------------------------- */

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const body = response.data
    if (body && typeof body === 'object' && typeof body.code === 'number' && body.code !== 0) {
      toastError(body.message || '请求失败', body.traceId)
      return Promise.reject(new BizError(body.code, body.message || '请求失败', body.traceId))
    }
    return response
  },
  async (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    const body = error.response?.data
    const config = error.config as RetryableConfig | undefined

    // 401：先用 refreshToken 静默续期并重放一次，失败再清登录态回登录页
    if (status === 401 && config && !config._retried && !isLoginRequest(config)) {
      config._retried = true
      const token = await refreshAccessToken()
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
        return http.request(config)
      }
      clearAuthStorage()
      toastError('登录状态已失效，请重新登录')
      if (!window.location.hash.startsWith('#/login')) {
        window.location.hash = '#/login'
      }
      return Promise.reject(error)
    }

    const message = body?.message || error.message || '网络异常，请稍后重试'
    toastError(message, body?.traceId)
    return Promise.reject(error)
  }
)

/* --------------------------- 类型化请求方法 --------------------------- */

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await http.get<ApiResult<T>>(url, { params })
  return response.data.data
}

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const response = await http.post<ApiResult<T>>(url, data)
  return response.data.data
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  const response = await http.put<ApiResult<T>>(url, data)
  return response.data.data
}

export async function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await http.delete<ApiResult<T>>(url, { params })
  return response.data.data
}

export default http
