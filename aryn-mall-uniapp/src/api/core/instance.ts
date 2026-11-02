import { uniappRequestAdapter } from '@alova/adapter-uniapp'
import { createAlova } from 'alova'
import vueHook from 'alova/vue'
import { getLocale, translate } from '@/locales'
import { useSessionStore } from '@/store/sessionStore'
import { Local } from '@/utils/storage'
import { apiBaseUrl } from './api-base-url'
import { parseOpenBoot, rewriteBootUrl } from './boot-url'
import { ApiError, handleAlovaError, handleAlovaResponse } from './handlers'
import { ensureSessionTenant, ensureTenantReady } from './tenant-identity'
import { TenantPreflightError } from './tenant-preflight'

const openBoot = parseOpenBoot(import.meta.env.VITE_OPEN_BOOT)

export const alovaInstance = createAlova({
  baseURL: apiBaseUrl,
  requestAdapter: uniappRequestAdapter,
  statesHook: vueHook,
  // beforeRequest 支持异步：租户守卫在发送前完成校验，失败零传输
  beforeRequest: async (method) => {
    // 已进入会话失效态：直接本地 reject，不发网络。
    // 页面里的轮询/倒计时/列表加载在弹窗期间仍会尝试请求，不短路会形成 401 风暴
    // （每个请求都会再走一遍租户校验 → 又一个 401），也会让 WS 拿死 token 反复重连。
    const sessionStore = useSessionStore()
    if (sessionStore.invalid) {
      throw new ApiError(translate('sessionInvalid.content.invalid'), 401)
    }

    // Add content type for POST/PUT/PATCH requests
    if (['POST', 'PUT', 'PATCH'].includes(method.type)) {
      method.config.headers['Content-Type'] = 'application/json'
    }

    // Add tenant ID to request headers
    method.config.headers['tenant-id'] = import.meta.env.VITE_TENANT_ID
    // 后端 I18nFilter 按该请求头设置 Locale；业务模块的英文文案包后续补齐
    method.config.headers['Accept-Language'] = getLocale()
    // Delivery uses a separate TOB token so it cannot accidentally reuse the customer session.
    const authState = uni.getStorageSync('auth') as { token?: string } | undefined
    const skipToken = method.config.headers.skipToken === true
    const deliveryRequest = method.url.includes('/app/delivery/') || method.config.headers.authScope === 'delivery'
    delete method.config.headers.skipToken
    delete method.config.headers.authScope
    // token 在写 meta 之前先算好：响应侧必须能区分「这次请求到底有没有携带凭证」。
    // 没带凭证的 401 是游客访问需登录接口（reason=NOT_TOKEN），不是会话失效，
    // 不能弹「登录状态已失效」（见 handlers.isGuestRequest）。
    const token = skipToken ? '' : ((deliveryRequest ? Local.get('deliveryToken') : authState?.token) || '')
    // 免登请求不带任何凭证，响应侧若收到 401/403 只能说明该接口未开通公开访问，
    // 与登录态是否过期无关。这里把标记透传给响应处理器，避免一次白名单漏配
    // 就把已登录用户清掉 token 并跳回登录页（金刚区→商品列表的品牌接口曾如此）。
    // deliveryRequest 同理透传：响应侧判 401 归属时 authScope 头已被删除，
    // 只能靠这个标记区分「清配送登录态」还是「清商城登录态」。
    // hadToken 同理透传：undefined 按「携带凭证」处理，保持既有 401 语义不变。
    method.config.meta = {
      ...(method.config.meta as object | undefined),
      deliveryRequest,
      publicRequest: skipToken,
      hadToken: Boolean(token),
    }
    if (token) {
      method.config.headers.satoken = token
    }
    method.url = rewriteBootUrl(method.url, openBoot) ?? method.url

    // 租户身份守卫（仅租户构建生效；默认开发直接放行）：
    // 1. binding preflight 通过前不放行任何业务请求；
    // 2. 携带 token 的请求在其首次使用前做会话租户校验（mall/delivery 独立）。
    try {
      await ensureTenantReady()
      const guardedToken = method.config.headers.satoken as string | undefined
      if (guardedToken) {
        await ensureSessionTenant(deliveryRequest ? 'delivery' : 'mall', guardedToken)
      }
    }
    catch (error) {
      // 会话校验 401：仅该 scope 的旧 token 失效。比对当前存储 token 仍等于本次校验
      // token 才进入失效流程，防止迟到的旧 token 401 把已重新登录的新会话再打回弹窗；
      // 403/网络故障保留 token、只阻断本请求（属身份配置/网络问题，不是会话失效）。
      if (error instanceof TenantPreflightError && error.type === 'unauthorized' && error.scope) {
        const currentToken = error.scope === 'delivery'
          ? String(Local.get('deliveryToken') || '')
          : (uni.getStorageSync('auth') as { token?: string } | undefined)?.token
        if (currentToken === error.checkedToken) {
          // 统一失效入口（与 alova 响应处理器同一状态机）：只置状态并弹窗，
          // 不再 $emit('auth-expired') 立刻清态——弹窗期间页面数据保持原样，
          // 用户点「重新登录/退出」时才清凭证跳转（见 sessionStore）。
          useSessionStore().invalidate({
            reason: error.invalidPayload.reason,
            subReason: error.invalidPayload.subReason,
            invalidAt: error.invalidPayload.invalidAt,
            device: error.invalidPayload.device,
            target: error.scope,
          })
        }
      }
      throw error
    }

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
