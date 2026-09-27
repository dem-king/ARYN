import { uniappRequestAdapter } from '@alova/adapter-uniapp'
import { createAlova } from 'alova'
import vueHook from 'alova/vue'
import { apiBaseUrl } from './api-base-url'
import { parseOpenBoot, rewriteBootUrl } from './boot-url'
import { handleAlovaError, handleAlovaResponse } from './handlers'
import { Local } from '@/utils/storage'

const openBoot = parseOpenBoot(import.meta.env.VITE_OPEN_BOOT)

export const alovaInstance = createAlova({
  baseURL: apiBaseUrl,
  requestAdapter: uniappRequestAdapter,
  statesHook: vueHook,
  beforeRequest: (method) => {
    // Add content type for POST/PUT/PATCH requests
    if (['POST', 'PUT', 'PATCH'].includes(method.type)) {
      method.config.headers['Content-Type'] = 'application/json'
    }

    // Add tenant ID to request headers
    method.config.headers['tenant-id'] = import.meta.env.VITE_TENANT_ID
    // Delivery uses a separate TOB token so it cannot accidentally reuse the customer session.
    const authState = uni.getStorageSync('auth') as { token?: string } | undefined
    const skipToken = method.config.headers.skipToken === true
    const deliveryRequest = method.url.includes('/app/delivery/') || method.config.headers.authScope === 'delivery'
    delete method.config.headers.skipToken
    delete method.config.headers.authScope
    // 免登请求不带任何凭证，响应侧若收到 401/403 只能说明该接口未开通公开访问，
    // 与登录态是否过期无关。这里把标记透传给响应处理器，避免一次白名单漏配
    // 就把已登录用户清掉 token 并跳回登录页（金刚区→商品列表的品牌接口曾如此）。
    method.config.meta = { ...(method.config.meta as object | undefined), publicRequest: skipToken }
    if (!skipToken) {
      const token = deliveryRequest ? Local.get('deliveryToken') : authState?.token
      if (token) {
        method.config.headers.satoken = token
      }
    }
    method.url = rewriteBootUrl(method.url, openBoot) ?? method.url
    // Add platform-specific headers
    // #ifdef MP
    method.config.headers['app-id'] = uni.getAccountInfoSync().miniProgram.appId
    // #endif

    // #ifdef MP-WEIXIN
    method.config.headers['platform-type'] = 'WX_MA' // 客户端微信小程序
    // #endif

    // #ifdef APP-PLUS
    method.config.headers['platform-type'] = 'APP' // 客户端APP
    // #endif

    // #ifdef H5
    method.config.headers['platform-type'] = 'H5' // 客户端H5
    // #endif

    // Add timestamp to prevent caching for GET requests
    if (method.type === 'GET' && CommonUtil.isObj(method.config.params)) {
      method.config.params._t = Date.now()
    }

    // Log request in development
    if (import.meta.env.MODE === 'development') {
      console.log(`[Alova Request] ${method.type} ${method.url}`, method.data || method.config.params)
      console.log(`[API Base URL] ${apiBaseUrl}`)
      console.log(`[Environment] ${import.meta.env.VITE_ENV_NAME}`)
    }
  },

  // Response handlers
  responded: {
    // Success handler
    onSuccess: handleAlovaResponse,

    // Error handler
    onError: handleAlovaError,

    // Complete handler - runs after success or error
    onComplete: async () => {
      // Any cleanup or logging can be done here
    },
  },

  // We'll use the middleware in the hooks
  // middleware is not directly supported in createAlova options

  // Default request timeout (10 seconds)
  timeout: 60000,
  // 设置为null即可全局关闭全部请求缓存
  cacheFor: null,
})

export default alovaInstance
