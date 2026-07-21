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

function redirectToLogin() {
  const timer = setTimeout(() => {
    clearTimeout(timer)
    uni.reLaunch({
      url: '/pages/login/index',
      complete: () => {
        isRedirectingToLogin = false
      },
    })
  }, 1000)
}

function expireAuthentication() {
  uni.removeStorageSync('auth')
  uni.$emit('auth-expired')
}

// Custom error class for API errors
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

// Define a type for the expected API response structure
interface ApiResponse {
  code: number
  msg?: string
  data?: any | string
}

// Handle successful responses
export async function handleAlovaResponse(
  response: UniApp.RequestSuccessCallbackResult | UniApp.UploadFileSuccessCallbackResult | UniApp.DownloadSuccessData,
) {
  const globalToast = useGlobalToast()
  // Extract status code and data from UniApp response
  const { statusCode, data } = response as UniNamespace.RequestSuccessCallbackResult
  const resp = typeof data === 'string' ? JSON.parse(data) : data
  const msg = resp?.msg?.trim() || ''

  // 处理401/403错误（如果不是在handleAlovaResponse中处理的）
  if ((statusCode === 401 || statusCode === 403 || (data as ApiResponse).code === 401)) {
    // 检查是否已经在跳转中，避免重复跳转
    if (!isRedirectingToLogin) {
      expireAuthentication()
      isRedirectingToLogin = true
      globalToast.error({ msg: '登录已过期，请重新登录！', duration: 500 })
      redirectToLogin()
    }

    throw new ApiError('登录已过期，请重新登录！', statusCode, data)
  }

  // Handle HTTP error status codes
  if (statusCode >= 400) {
    globalToast.error(msg || '请求失败')
    throw new ApiError(msg || `Request failed with status: ${statusCode}`, statusCode, data)
  }
  // 处理业务层错误（例如 code != 0）
  if (resp.code && resp.code !== 0) {
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
    // 如果是未授权错误，清除用户信息并跳转到登录页
    // 检查是否已经在跳转中，避免重复跳转
    if (!isRedirectingToLogin) {
      expireAuthentication()
      isRedirectingToLogin = true
      globalToast.error({ msg: '登录已过期，请重新登录！', duration: 500 })
      redirectToLogin()
    }
    throw new ApiError('登录已过期，请重新登录！', error.code, error.data)
  }

  // Handle different types of errors
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
