import { uniappRequestAdapter } from '@alova/adapter-uniapp'
import { createAlova } from 'alova'
import vueHook from 'alova/vue'
import { handleAlovaError, handleAlovaResponse } from './handlers'
import { useAuthStore } from '@/store/authStore'

export const alovaInstance = createAlova({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  requestAdapter: uniappRequestAdapter,
  statesHook: vueHook,
  beforeRequest: (method) => {
    // Add content type for POST/PUT/PATCH requests
    if (['POST', 'PUT', 'PATCH'].includes(method.type)) {
      method.config.headers['Content-Type'] = 'application/json'
    }

    // Add tenant ID to request headers
    method.config.headers['tenant-id'] = import.meta.env.VITE_TENANT_ID
    // Add satoken to request headers
    const authStore = useAuthStore()
    if (authStore.getToken && !method.config.headers.skipToken) {
      method.config.headers.satoken = authStore.getToken
    }
    // 单体模式 URL 重写：将 /xxx/yyy 重写为 /boot/yyy
    // 使用 slice 而非 splice，避免修改原数组
    if (import.meta.env.VITE_OPEN_BOOT === 'true' && method.url) {
      method.url = `/boot/${method.url.split('/').slice(2).join('/')}`
    }
    // Add platform-specific headers
    // #ifdef MP
    method.config.headers['app-id'] = uni.getAccountInfoSync().miniProgram.appId
    // #endif

    // #ifdef MP-WEIXIN
    method.config.headers['platform-type'] = 'WX_MA' // 客户端微信小程序
    // #endif
    // #ifdef MP-ALIPAY
    method.config.headers['platform-type'] = 'ALI_MA' // 客户端支付宝小程序
    // #endif
    // #ifdef MP-TOUTIAO
    method.config.headers['platform-type'] = 'TT_MA' // 客户端抖音小程序
    // #endif
    // #ifdef MP-BAIDU
    method.config.headers['platform-type'] = 'BD_MA' // 客户端百度小程序
    // #endif
    // #ifdef MP-QQ
    method.config.headers['platform-type'] = 'QQ_MA' // 客户端QQ小程序
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
      console.log(`[API Base URL] ${import.meta.env.VITE_API_BASE_URL}`)
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
