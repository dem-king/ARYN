import { uniappRequestAdapter } from '@alova/adapter-uniapp'
import { createAlova } from 'alova'

export const DELIVERY_AUTH_STORAGE_KEY = 'aryn-delivery-auth'

export interface DeliveryStoredSession {
  token?: string
  tenantId?: string
}

export class DeliveryApiError extends Error {
  code: number
  data?: unknown

  constructor(message: string, code: number, data?: unknown) {
    super(message)
    this.name = 'DeliveryApiError'
    this.code = code
    this.data = data
  }
}

interface ApiResponse<T = unknown> {
  code?: number
  data?: T
  msg?: string
}

export function buildDeliveryHeaders(token?: string, tenantId?: string, appId?: string) {
  return {
    ...(appId ? { 'app-id': appId } : {}),
    ...(token ? { satoken: token } : {}),
    ...(tenantId ? { 'tenant-id': tenantId } : {}),
  }
}

export function encodeDeliveryForm(data: Record<string, number | string | undefined>) {
  return Object.entries(data)
    .filter((entry): entry is [string, number | string] => entry[1] !== undefined)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
}

export function rewriteDeliveryBootUrl(url: string, openBoot = import.meta.env.VITE_OPEN_BOOT === 'true') {
  if (!openBoot || /^[a-z][a-z\d+.-]*:\/\//i.test(url))
    return url

  const suffixIndex = url.search(/[?#]/)
  const pathname = suffixIndex < 0 ? url : url.slice(0, suffixIndex)
  const suffix = suffixIndex < 0 ? '' : url.slice(suffixIndex)
  const segments = pathname.split('/').filter(Boolean)
  if (segments[0] === 'boot' || segments.length < 2)
    return url

  return `/boot/${segments.slice(1).join('/')}${suffix}`
}

export function normalizeDeliveryApiBaseUrl(baseUrl: string | undefined) {
  return String(baseUrl || '').trim().replace(/\/+$/, '')
}

export const deliveryApiBaseUrl = normalizeDeliveryApiBaseUrl(import.meta.env.VITE_API_BASE_URL)

export function buildDeliveryApiUrl(
  path: string,
  baseUrl = deliveryApiBaseUrl,
  openBoot = import.meta.env.VITE_OPEN_BOOT === 'true',
) {
  const base = normalizeDeliveryApiBaseUrl(baseUrl)
  const rewritten = rewriteDeliveryBootUrl(path, openBoot)
  return `${base}/${rewritten.replace(/^\/+/, '')}`
}

export function buildDeliveryWebSocketUrl(
  path: string,
  baseUrl = deliveryApiBaseUrl,
  openBoot = import.meta.env.VITE_OPEN_BOOT === 'true',
) {
  return buildDeliveryApiUrl(path, baseUrl, openBoot).replace(/^http:/i, 'ws:').replace(/^https:/i, 'wss:')
}

function parseResponse<T>(data: unknown): ApiResponse<T> {
  if (typeof data !== 'string')
    return (data ?? {}) as ApiResponse<T>
  try {
    return JSON.parse(data) as ApiResponse<T>
  }
  catch {
    return { data: data as T }
  }
}

let redirecting = false

function expireDeliverySession(message = '登录已过期，请重新登录') {
  uni.removeStorageSync(DELIVERY_AUTH_STORAGE_KEY)
  uni.$emit?.('delivery-auth-expired')
  uni.showToast({ title: message, icon: 'none' })
  if (redirecting)
    return
  redirecting = true
  uni.reLaunch({
    url: '/pages/login/index',
    complete: () => {
      redirecting = false
    },
  })
}

export const alovaInstance = createAlova({
  baseURL: deliveryApiBaseUrl,
  requestAdapter: uniappRequestAdapter,
  beforeRequest: (method) => {
    const stored = uni.getStorageSync(DELIVERY_AUTH_STORAGE_KEY) as DeliveryStoredSession | undefined
    const skipToken = method.config.headers.skipToken === true
    delete method.config.headers.skipToken
    Object.assign(
      method.config.headers,
      buildDeliveryHeaders(
        skipToken ? undefined : stored?.token,
        stored?.tenantId || import.meta.env.VITE_TENANT_ID,
        import.meta.env.VITE_DELIVERY_MINI_APP_ID,
      ),
    )
    method.url = rewriteDeliveryBootUrl(method.url)
  },
  responded: {
    onSuccess: async (response) => {
      const { statusCode, data } = response as UniNamespace.RequestSuccessCallbackResult
      const result = parseResponse(data)
      const code = typeof result.code === 'number' ? result.code : statusCode
      if (statusCode === 401 || statusCode === 403 || code === 401 || code === 403) {
        expireDeliverySession()
        throw new DeliveryApiError('登录已过期，请重新登录', code, data)
      }
      if (statusCode >= 400 || (typeof result.code === 'number' && result.code !== 0)) {
        const message = result.msg || '请求失败'
        uni.showToast({ title: message, icon: 'none' })
        throw new DeliveryApiError(message, code, data)
      }
      return result.data
    },
    onError: (error) => {
      if (error instanceof DeliveryApiError)
        throw error
      const message = error instanceof Error ? error.message : '网络请求失败，请稍后重试'
      uni.showToast({ title: message, icon: 'none' })
      throw error
    },
  },
  timeout: 60000,
  cacheFor: null,
})

export default alovaInstance
