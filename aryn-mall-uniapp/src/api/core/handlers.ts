/*
 * @Author: weisheng
 * @Date: 2025-04-17 15:58:11
 * @LastEditTime: 2025-06-15 21:47:22
 * @LastEditors: weisheng
 * @Description: Alova response and error handlers
 * @FilePath: /aryn-uniapp-pro/src/api/core/handlers.ts
 */
import type { Method } from 'alova'
import { translate } from '@/locales'
import { useSessionStore } from '@/store/sessionStore'
import type { SessionInvalidPayload, SessionInvalidTarget } from '@/utils/session-invalid'
import { extractSessionInvalidPayload, resolveSessionInvalidTitle } from '@/utils/session-invalid'

/**
 * 登录态失效的统一入口（被顶下线 / 自然过期 / 被踢下线 / 旧后端无 reason 的业务 401）。
 *
 * 一期契约（README §4.3 / §7.2）+ 二期契约（subReason 细分）：
 *  - 只置 sessionStore.invalid（冻结请求与 WS），**不 toast、不清 storage、不跳转**；
 *    用户点「重新登录」或「退出」时才真正清凭证并 reLaunch（见 sessionStore）。
 *  - 幂等去重：`invalidate()` 在已失效时返回 false，不覆盖首次原因 —— 这就是
 *    原先 isRedirectingToLogin 标志要表达的「已提示过」语义，只是状态提升到了 store，
 *    并发 401（首页多个楼层层层发请求）不会叠弹/叠提示。
 *  - 免登请求 401 与 403 不走这里（见 handleAlovaResponse）。
 */
function enterSessionInvalid(
  payload: SessionInvalidPayload,
  target: SessionInvalidTarget,
): {
  reason: ReturnType<typeof useSessionStore>['reason']
  subReason: ReturnType<typeof useSessionStore>['subReason']
  target: SessionInvalidTarget
} {
  const sessionStore = useSessionStore()
  sessionStore.invalidate({
    reason: payload.reason,
    subReason: payload.subReason,
    invalidAt: payload.invalidAt,
    device: payload.device,
    target,
  })
  return { reason: sessionStore.reason, subReason: sessionStore.subReason, target }
}

function isUnauthorizedPreflight(error: unknown): boolean {
  return (error as { type?: string })?.type === 'unauthorized'
}

/**
 * 旁路请求（uni.uploadFile）的租户会话预检 401：与 alova 响应处理器共用同一失效入口。
 *
 * 先比对「本次校验的 token」与调用方持有的 token：迟到 401 不能把刚重新登录的新会话
 * 再打回弹窗（旧实现也是先比对再清配送 token）。403/网络故障不进入失效流程。
 */
export function convergePreflightUnauthorized(
  error: unknown,
  target: 'mall' | 'delivery',
  token: string,
) {
  if (!isUnauthorizedPreflight(error))
    return
  const checkedToken = (error as { checkedToken?: string }).checkedToken
  if (checkedToken && checkedToken !== token)
    return
  const payload = (error as { invalidPayload?: unknown }).invalidPayload
  useSessionStore().invalidate({ ...extractSessionInvalidPayload(payload), target })
}

/** 旁路（uni.uploadFile）请求的失效态短路：与 alova beforeRequest 同一判断。 */
export function assertSessionUsable() {
  if (useSessionStore().invalid) {
    throw new Error(translate('sessionInvalid.content.invalid'))
  }
}

export function isDeliveryRequest(method?: Method) {
  // beforeRequest 已按 URL/authScope 判定过一次并写进 meta：响应侧再判时 authScope
  // 头已被删除，必须读 meta。否则 /message/staff/** 这类 URL 不带配送特征的配送请求
  // 会被当成商城请求，401 时错清商城登录态并把司机踢到商城登录页。
  if ((method?.config?.meta as { deliveryRequest?: boolean } | undefined)?.deliveryRequest === true) {
    return true
  }
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

/**
 * 是否是「游客请求」：请求侧确认没有携带任何凭证（beforeRequest 写 `meta.hadToken = false`）。
 *
 * 游客打开需登录页面时，服务端会返回 401（reason=NOT_TOKEN）——这不是「登录态失效」：
 * 游客本来就没有可失效的会话，不该弹「登录状态已失效」、也不该冻结请求
 * （设计文档《被顶下线弹窗提示》§1 第 5 行 / §7.4：未登录 = 直接去登录页，不弹「被顶」）。
 *
 * 只有显式 `hadToken === false` 才算游客：`undefined`（旧调用方/旧测试未打标记）
 * 仍按「携带凭证」处理，保持既有 401 语义不变。
 */
export function isGuestRequest(method?: Method) {
  return (method?.config?.meta as { hadToken?: boolean } | undefined)?.hadToken === false
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
 * 只有这一种才该进入「会话失效」流程并弹窗。
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
    const publicMsg = msg || translate('app.request.featureUnavailable')
    globalToast.error(publicMsg)
    throw new ApiError(publicMsg, statusCode, data)
  }

  // 权限不足：token 有效，只是缺少该接口所需权限。不清登录态、不跳登录页，
  // 否则用户会陷入「重新登录 → 仍被踢出」的死循环。
  if (isForbiddenResponse(statusCode, resp)) {
    const forbiddenMsg = msg || translate('app.request.forbidden')
    globalToast.error(forbiddenMsg)
    throw new ApiError(forbiddenMsg, 403, data)
  }

  // 登录态失效：进入会话失效态并弹窗（不 toast / 不跳转 / 不清 storage）。
  // `resp` 是完整响应体，失效原因在 `resp.data`（可能是对象，也可能是 JSON 字符串，
  // 由 extractSessionInvalidPayload 两种都兼容）。
  if (isUnauthorizedResponse(statusCode, resp)) {
    // 游客本来就没有可失效的会话：没带 token 的 401 只说明「这个接口需要登录」。
    // 只提示去登录，不置 invalid、不清量、不跳转，避免游客看到「登录状态已失效」。
    if (isGuestRequest(method)) {
      const needLoginMsg = translate('app.request.needLogin')
      globalToast.error(needLoginMsg)
      throw new ApiError(needLoginMsg, statusCode, data)
    }
    const target: SessionInvalidTarget = isDeliveryRequest(method) ? 'delivery' : 'mall'
    const { reason, subReason } = enterSessionInvalid(extractSessionInvalidPayload(resp.data), target)
    // 已经失效时的并发 401：不重复弹、不重复提示，直接静默抛错
    throw new ApiError(resolveSessionInvalidTitle({ reason, subReason, target }), statusCode, data)
  }

  // Handle HTTP error status codes
  if (statusCode >= 400) {
    globalToast.error(msg || translate('common.requestFailed'))
    throw new ApiError(msg || `Request failed with status: ${statusCode}`, statusCode, data)
  }
  // 处理业务层错误（例如 code != 0）
  if (typeof resp.code === 'number' && resp.code !== 0) {
    globalToast.error(msg || translate('common.requestFailed'))
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
    // 公开请求已在 handleAlovaResponse 里转为业务错误并抛过 toast，这里不再进入失效流程
    if (isPublicRequest(method)) {
      throw error
    }
    // 403 是权限不足而非登录失效：token 仍然有效，清掉它只会让用户白登一次
    if (error.code === 403) {
      throw error
    }
    // 游客请求：handleAlovaResponse 已提示过「请先登录」，这里只透传错误，
    // 不再二次 toast、也不进入会话失效流程（游客本来就没有可失效的会话）。
    if (isGuestRequest(method)) {
      throw error
    }
    // 已处于失效态：静默 reject（并发 401 / beforeRequest 短路产生的错误都会走到这里）
    if (useSessionStore().invalid) {
      throw error
    }
    // 兜底：异常可能是被包装过的（error.data 是完整响应体 `{code,msg,data}`），
    // extractSessionInvalidPayload 会继续拆一层 data 拿原因；拿不到就按无 reason 降级。
    const target: SessionInvalidTarget = isDeliveryRequest(method) ? 'delivery' : 'mall'
    const { reason, subReason } = enterSessionInvalid(extractSessionInvalidPayload(error.data), target)
    throw new ApiError(resolveSessionInvalidTitle({ reason, subReason, target }), error.code, error.data)
  }

  // Handle different types of errors
  const errMsg = typeof error === 'string' ? error : (error?.message || error?.errMsg || '')
  if (error?.name === 'NetworkError' || errMsg.includes('request:fail')) {
    globalToast.error(translate('common.networkError'))
  }
  else if (error?.name === 'TimeoutError' || errMsg.toLowerCase().includes('timeout')) {
    globalToast.error(translate('common.timeout'))
  }
  else if (error instanceof ApiError) {
    globalToast.error(error.message || translate('common.requestFailed'))
  }
  else {
    globalToast.error(errMsg || translate('common.requestFailed'))
  }

  throw error
}
