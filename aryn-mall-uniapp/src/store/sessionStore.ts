/**
 * 会话失效状态机（一期：被顶下线 / 登录过期弹窗；二期：KICK_OUT + subReason 细分）。
 *
 * 设计约束（一期实现契约 §3、README §4.3/§7.2；二期实现契约 §2/§4）：
 *  - 收到 401 只进入 INVALIDATED 并「冻结」请求/WS，**不清 storage、不跳转、不 toast**，
 *    避免用户看到页面数据（用户信息/购物车角标/主题）瞬间清空；
 *  - 幂等：INVALIDATED 状态下再来 401 一律静默，不重复弹、不重复清；
 *  - 唯一出口是用户点「重新登录」或「退出」——此时才真正清凭证并 reLaunch；
 *  - 纯内存状态，**不持久化**（见 `store/persist.ts` 的排除列表），冷启动不会弹出旧窗。
 *
 * 清凭证本身放在主入口（`main.ts`）通过 `registerSessionClearHandler` 注册：
 * 本 store 是请求层（handlers/instance）的依赖，若静态 import authStore 会形成
 * instance → handlers → sessionStore → authStore → api/auth → instance 的循环。
 */

import { defineStore } from 'pinia'
import { getCurrentFullPath } from '@/utils/current-page'
import type {
  SessionInvalidDevice,
  SessionInvalidReason,
  SessionInvalidSubReason,
  SessionInvalidTarget,
} from '@/utils/session-invalid'
import {
  normalizeSessionInvalidDevice,
  normalizeSessionInvalidReason,
  normalizeSessionInvalidSubReason,
  normalizeSessionInvalidTimestamp,
} from '@/utils/session-invalid'
import { Local } from '@/utils/storage'

/** 登录页回跳参数（登录页 `loginSuccess()`/`autoRedirect()` 已支持）。 */
export const SESSION_REDIRECT_PATH_KEY = 'redirectPath'
export const MALL_LOGIN_PATH = '/pages/login/index'
export const MALL_HOME_PATH = '/pages/home/index'
export const DELIVERY_LOGIN_PATH = '/pages/delivery/login'
export const DELIVERY_HOME_PATH = '/pages/delivery/index'
const DELIVERY_ROUTE_PREFIX = '/pages/delivery/'

export interface SessionInvalidateOptions {
  /** 后端 wire 值；未知/缺失按通用文案降级 */
  reason?: unknown
  /** 二期：仅 reason=KICK_OUT 时有意义；未知值丢弃（按无 subReason 降级） */
  subReason?: unknown
  invalidAt?: unknown
  device?: unknown
  target?: SessionInvalidTarget
}

/** 真正清凭证的实现（main.ts 注册；配送端只清配送身份，不影响商城登录态）。 */
export type SessionClearHandler = (target: SessionInvalidTarget) => void

let sessionClearHandler: SessionClearHandler | null = null

export function registerSessionClearHandler(handler: SessionClearHandler | null) {
  sessionClearHandler = handler
}

interface SessionState {
  invalid: boolean
  reason: SessionInvalidReason | ''
  /** 二期：KICK_OUT 的细分原因；无/未知时为 ''（文案降级为通用） */
  subReason: SessionInvalidSubReason | ''
  invalidAt: string
  device: SessionInvalidDevice | null
  target: SessionInvalidTarget
  detectedAt: number
}

/**
 * 「重新登录」应回跳的位置。
 *
 * - 商城：留在当前页（含 query）；当前就在登录页/配送页时不留回跳，登录页默认回首页；
 * - 配送：配送登录页只会回配送工作台，记录当前配送页（非配送页则回工作台）。
 */
function resolveReloginPath(target: SessionInvalidTarget): string {
  const current = getCurrentFullPath()
  const onLoginPage = current.startsWith(MALL_LOGIN_PATH) || current.startsWith(DELIVERY_LOGIN_PATH)
  if (target === 'delivery') {
    return current.startsWith(DELIVERY_ROUTE_PREFIX) && !onLoginPage ? current : DELIVERY_HOME_PATH
  }
  if (!current || onLoginPage || current.startsWith(DELIVERY_ROUTE_PREFIX))
    return ''
  return current
}

export const useSessionStore = defineStore('session', {
  state: (): SessionState => ({
    invalid: false,
    reason: '',
    subReason: '',
    invalidAt: '',
    device: null,
    target: 'mall',
    detectedAt: 0,
  }),

  getters: {
    /** 请求层 / WS 层据此短路：失效后不再发业务请求、不再重连。 */
    isInvalid: state => state.invalid,
    /** 无 reason（旧后端 / 业务 401）时为真，供测试与排障区分降级路径。 */
    isDegraded: state => state.invalid && !state.reason,
  },

  actions: {
    /**
     * 进入 INVALIDATED。幂等：已失效时保留首次原因，返回 false。
     *
     * 只记录状态，不清凭证、不跳转、不 toast —— 那些都发生在用户动作之后。
     */
    invalidate(options: SessionInvalidateOptions = {}): boolean {
      if (this.invalid)
        return false
      this.invalid = true
      this.reason = normalizeSessionInvalidReason(options.reason)
      // 二期契约：subReason 只在 reason=KICK_OUT 时有效，其它组合/未知值一律丢弃
      this.subReason = this.reason === 'KICK_OUT'
        ? normalizeSessionInvalidSubReason(options.subReason)
        : ''
      this.invalidAt = normalizeSessionInvalidTimestamp(options.invalidAt)
      this.device = normalizeSessionInvalidDevice(options.device)
      this.target = options.target === 'delivery' ? 'delivery' : 'mall'
      this.detectedAt = Date.now()
      return true
    },

    /** 用户点「重新登录」：写 redirectPath → 清凭证 → reLaunch 登录页。 */
    confirmRelogin(): void {
      if (!this.invalid)
        return
      const target = this.target
      const redirectPath = resolveReloginPath(target)
      if (redirectPath)
        Local.set(SESSION_REDIRECT_PATH_KEY, redirectPath)
      this.clearCredentials(target)
      this.reset()
      uni.reLaunch({ url: target === 'delivery' ? DELIVERY_LOGIN_PATH : MALL_LOGIN_PATH })
    },

    /** 用户点「退出」：清凭证 → reLaunch 首页/配送登录页（**不写** redirectPath）。 */
    exit(): void {
      if (!this.invalid)
        return
      const target = this.target
      this.clearCredentials(target)
      this.reset()
      uni.reLaunch({ url: target === 'delivery' ? DELIVERY_LOGIN_PATH : MALL_HOME_PATH })
    },

    /**
     * 「返回登录」：canRelogin=false 的单按钮出口（账号停用/删除、租户停用、会员禁用）。
     *
     * 与 `exit()` 的差异只有落点：这些账号重登必然失败，商城端首页也是「登录后内容」，
     * 落首页语义不符，必须回登录页。清态逻辑完全复用（同一 `registerSessionClearHandler`），
     * 同样**不写** redirectPath（回跳只会跳回登录不上的页面）。
     */
    exitToLogin(): void {
      if (!this.invalid)
        return
      const target = this.target
      this.clearCredentials(target)
      this.reset()
      uni.reLaunch({ url: target === 'delivery' ? DELIVERY_LOGIN_PATH : MALL_LOGIN_PATH })
    },

    /** 清凭证：商城走 authStore.clearAuthData（主入口注册），配送只清 TOB 身份。 */
    clearCredentials(target: SessionInvalidTarget) {
      if (sessionClearHandler) {
        sessionClearHandler(target)
        return
      }
      // 兜底（主入口未注册时，如单测）：配送身份可以就地清；商城凭证必须由主入口的清态
      // 实现处理（购物车/主题/用户信息在同一条链路上），缺失时不动，避免清一半。
      if (target === 'delivery') {
        Local.remove('deliveryToken')
        Local.remove('deliveryStaffInfo')
        uni.$emit('delivery-auth-expired')
      }
    },

    /** 回到 VALID（重登成功/手动复位）。 */
    reset() {
      this.$reset()
    },
  },
})
