/*
 * @Author: weisheng
 * @Date: 2025-04-17 15:58:11
 * @LastEditTime: 2025-06-15 21:47:22
 * @LastEditors: weisheng
 * @Description: Alova response and error handlers
 * @FilePath: /aryn-uniapp-pro/src/api/core/handlers.ts
 */
import type { Method } from 'alova'

// 添加一个状态变量来防止重复跳转
let isRedirectingToLogin = false
let isRedirectingToDeliveryLogin = false

function redirectToLogin(deliveryRequest = false) {
  const timer = setTimeout(() => {
    clearTimeout(timer)
    uni.reLaunch({
      url: deliveryRequest ? '/pages/delivery/login' : '/pages/login/index',
      complete: () => {
        if (deliveryRequest)
          isRedirectingToDeliveryLogin = false
        else
          isRedirectingToLogin = false
      },
    })
  }, 1000)
}

function expireAuthentication() {
  uni.removeStorageSync('auth')
  uni.$emit('auth-expired')
}

function expireDeliveryAuthentication() {
  uni.removeStorageSync('deliveryToken')
  // 与登录/个人中心写入的配送员资料 key 保持一致
  uni.removeStorageSync('deliveryStaffInfo')
  uni.$emit('delivery-auth-expired')
}

export function isDeliveryRequest(method?: Method) {
  const url = method?.url || ''
  return url.includes('/app/delivery/')
    || url.includes('/staff/delivery-evidence/')
    || url.includes('/token/delivery-login')
}

function handleAuthenticationExpired(globalToast: ReturnType<typeof useGlobalToast>, deliveryRequest: boolean) {
  if (deliveryRequest) {
    if (isRedirectingToDeliveryLogin)
      return
    expireDeliveryAuthentication()
    isRedirectingToDeliveryLogin = true
    globalToast.error({ msg: '配送登录已过期，请重新登录！', duration: 500 })
    redirectToLogin(true)
    return
  }
  if (isRedirectingToLogin)
    return
  expireAuthentication()
  isRedirectingToLogin = true
  globalToast.error({ msg: '登录已过期，请重新登录！', duration: 500 })
  redirectToLogin()
}

// Custom error class for API errors
export class ApiError extends Error {
  code: number
  data?: unknown

  constructor(message: string, code: number, data?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.data = data
  }
}

// Define a type for the expected API response structure
export interface ApiResponse {
  code?: number
  msg?: string
  data?: unknown
}

export function parseApiResponse(data: unknown): ApiResponse {
  if (typeof data === 'string') {
    try {
      const parsed = JSON.parse(data)
      if (parsed && typeof parsed === 'object') {
        return parsed as ApiResponse
      }
    }
    catch {
      return { data }
    }
    return { data }
  }
  if (data && typeof data === 'object') {
    return data as ApiResponse
  }
  return { data }
}

export function isUnauthorizedResponse(statusCode: number, response: ApiResponse) {
  return statusCode === 401 || statusCode === 403 || response.code === 401 || response.code === 403
}

// Handle successful responses
export async function handleAlovaResponse(
  response: UniApp.RequestSuccessCallbackResult | UniApp.UploadFileSuccessCallbackResult | UniApp.DownloadSuccessData,
  method?: Method,
) {
  const globalToast = useGlobalToast()
  // Extract status code and data from UniApp response
  const { statusCode, data } = response as UniNamespace.RequestSuccessCallbackResult
  const resp = parseApiResponse(data)
  const msg = typeof resp.msg === 'string' ? resp.msg.trim() : ''

  // 处理401/403错误（如果不是在handleAlovaResponse中处理的）
  if (isUnauthorizedResponse(statusCode, resp)) {
    const deliveryRequest = isDeliveryRequest(method)
    handleAuthenticationExpired(globalToast, deliveryRequest)

    throw new ApiError(deliveryRequest ? '配送登录已过期，请重新登录！' : '登录已过期，请重新登录！', statusCode, data)
  }

  // Handle HTTP error status codes
  if (statusCode >= 400) {
    globalToast.error(msg || '请求失败')
    throw new ApiError(msg || `Request failed with status: ${statusCode}`, statusCode, data)
  }
  // 处理业务层错误（例如 code != 0）
  if (typeof resp.code === 'number' && resp.code !== 0) {
    globalToast.error(msg || '请求失败')
    throw new ApiError(msg || `Request failed with code: ${resp.code}`, resp.code, data)
  }
  // The data is already parsed by UniApp adapter
  // Log response in development
  if (import.meta.env.MODE === 'development') {
    console.log('[Alova Response]', resp)
  }

  // Return data for successful responses
  return resp.data
}

// Handle request errors
export function handleAlovaError(error: any, method: Method) {
  const globalToast = useGlobalToast()
  // Log error in development
  if (import.meta.env.MODE === 'development') {
    console.error('[Alova Error]', error, method)
  }

  // 处理401/403错误（如果不是在handleAlovaResponse中处理的）
  if (error instanceof ApiError && (error.code === 401 || error.code === 403)) {
    const deliveryRequest = isDeliveryRequest(method)
    handleAuthenticationExpired(globalToast, deliveryRequest)
    throw new ApiError(deliveryRequest ? '配送登录已过期，请重新登录！' : '登录已过期，请重新登录！', error.code, error.data)
  }

  // Handle different types of errors
  const errMsg = typeof error === 'string' ? error : (error?.message || error?.errMsg || '')
  if (error?.name === 'NetworkError' || errMsg.includes('request:fail')) {
    globalToast.error('网络请求失败，请检查网络或接口域名配置')
  }
  else if (error?.name === 'TimeoutError' || errMsg.toLowerCase().includes('timeout')) {
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
