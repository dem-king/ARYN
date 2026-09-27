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

/**
 * 是否是不携带任何凭证的公开请求（api 层用 `skipToken: true` 声明）。
 *
 * 判定依据是请求侧打的 `meta.publicRequest` 标记，而不是响应内容 —— 服务端
 * 返回的 401 无法区分「token 过期」与「这个接口本该免登却没配白名单」。
 */
export function isPublicRequest(method?: Method) {
  return (method?.config?.meta as { publicRequest?: boolean } | undefined)?.publicRequest === true
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

/**
 * 未登录 / 登录态失效（HTTP 401 或业务 code 401）。
 *
 * 只有这一种才该清 token 并跳登录页。
 */
export function isUnauthorizedResponse(statusCode: number, response: ApiResponse) {
  return statusCode === 401 || response.code === 401
}

/**
 * 已登录但权限不足（HTTP 403 或业务 code 403）。
 *
 * 与 401 必须分开：403 说明 token 是有效的、服务端认得出调用者，只是没给这个权限。
 * 早先两者共用一个判定，导致「C 端页面误调管理端接口」这种权限问题被报成
 * 「登录已过期」，用户被清掉 token 踢回登录页 —— 重新登录也无济于事，
 * 因为重新登录并不会凭空获得管理端权限。
 */
export function isForbiddenResponse(statusCode: number, response: ApiResponse) {
  return statusCode === 403 || response.code === 403
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

  // 免登请求不带任何凭证，401/403 都只说明服务端未放行该接口，与登录态无关。
  // 据此清 token 会把已登录用户踢回登录页（金刚区品牌接口曾因白名单漏配踩过）。
  if (isPublicRequest(method) && (isUnauthorizedResponse(statusCode, resp) || isForbiddenResponse(statusCode, resp))) {
    const publicMsg = msg || '该功能暂不可用，请稍后重试'
    globalToast.error(publicMsg)
    throw new ApiError(publicMsg, statusCode, data)
  }

  // 权限不足：token 有效，只是缺少该接口所需权限。不清登录态、不跳登录页，
  // 否则用户会陷入「重新登录 → 仍被踢出」的死循环。
  if (isForbiddenResponse(statusCode, resp)) {
    const forbiddenMsg = msg || '暂无权限访问该功能'
    globalToast.error(forbiddenMsg)
    throw new ApiError(forbiddenMsg, 403, data)
  }

  // 登录态失效：只有 401 才清 token 并跳登录页
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
    // 公开请求已在 handleAlovaResponse 里转为业务错误并抛过 toast，这里不再清登录态
    if (isPublicRequest(method)) {
      throw error
    }
    // 403 是权限不足而非登录失效：token 仍然有效，清掉它只会让用户白登一次
    if (error.code === 403) {
      throw error
    }
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
