/*
 * Alova response and error handlers
 */
import type { Method } from 'alova'
import router from '@/router'

// 防止重复跳转登录页
let isRedirectingToLogin = false

/** 自定义 API 错误 */
export class ApiError extends Error {
  code: number
  data?: any

  constructor(message: string, code: number, data?: any) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.data = data
  }
}

/** API 响应结构 */
interface ApiResponse {
  code: number
  msg?: string
  data?: any | string
}

/**
 * 统一的 401/403 未授权处理（消除 handleAlovaResponse / handleAlovaError 中的重复逻辑）
 */
function handleUnauthorized(statusCode: number, data?: any): void {
  const globalToast = useGlobalToast()
  const authStore = useAuthStore()

  if (!isRedirectingToLogin) {
    if (authStore.isLoggedIn) {
      authStore.clearAuthData()
    }
    isRedirectingToLogin = true
    globalToast.error({ msg: '登录已过期，请重新登录！', duration: 500 })
    const timer = setTimeout(() => {
      clearTimeout(timer)
      router.replaceAll({ name: 'login' })
      isRedirectingToLogin = false
    }, 1000)
  }

  throw new ApiError('登录已过期，请重新登录！', statusCode, data)
}

/** 处理成功响应 */
export async function handleAlovaResponse(
  response: UniApp.RequestSuccessCallbackResult | UniApp.UploadFileSuccessCallbackResult | UniApp.DownloadSuccessData,
) {
  const globalToast = useGlobalToast()
  const { statusCode, data } = response as UniNamespace.RequestSuccessCallbackResult
  const resp = typeof data === 'string' ? JSON.parse(data) : data
  const msg = resp?.msg?.trim() || ''

  // 处理 401/403 未授权
  if (statusCode === 401 || statusCode === 403 || (data as ApiResponse).code === 401) {
    handleUnauthorized(statusCode, data)
  }

  // HTTP 错误
  if (statusCode >= 400) {
    globalToast.error(msg || '请求失败')
    throw new ApiError(msg || `Request failed with status: ${statusCode}`, statusCode, data)
  }

  // 业务层错误
  if (resp.code && resp.code !== 0) {
    globalToast.error(msg || '请求失败')
    throw new ApiError(msg || `Request failed with code: ${resp.code}`, resp.code, data)
  }

  if (import.meta.env.MODE === 'development') {
    console.log('[Alova Response]', resp)
  }

  return resp.data
}

/** 处理请求错误 */
export function handleAlovaError(error: any, method: Method) {
  const globalToast = useGlobalToast()

  if (import.meta.env.MODE === 'development') {
    console.error('[Alova Error]', error, method)
  }

  // 处理 401/403 未授权
  if (error instanceof ApiError && (error.code === 401 || error.code === 403)) {
    handleUnauthorized(error.code, error.data)
  }

  // 其他错误类型处理
  const errMsg = typeof error === 'string' ? error : (error?.message || error?.errMsg || '')
  if (error.name === 'NetworkError' || errMsg.includes('request:fail')) {
    globalToast.error('网络请求失败，请检查网络或接口域名配置')
  }
  else if (error.name === 'TimeoutError' || errMsg.toLowerCase().includes('timeout')) {
    globalToast.error('请求超时，请重试')
  }
  else if (error instanceof ApiError) {
    globalToast.error(error.message || '请求失败')
  }
  else {
    globalToast.error(errMsg || '请求失败')
  }

  throw error
}
