import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  DELIVERY_HOME_PATH,
  DELIVERY_LOGIN_PATH,
  MALL_HOME_PATH,
  MALL_LOGIN_PATH,
  registerSessionClearHandler,
  SESSION_REDIRECT_PATH_KEY,
  useSessionStore,
} from './sessionStore'

/**
 * 会话失效状态机测试。
 *
 * 一期契约（README §4.3 / §7.2）：
 *  · 收到 401 → 只进入 INVALIDATED（幂等），**不清凭证、不跳转**；
 *  · 用户点「重新登录」→ 写 redirectPath（带 query）→ 清凭证 → reLaunch 登录页；
 *  · 用户点「退出」→ 清凭证 → reLaunch 首页/配送登录页，**不写** redirectPath；
 *  · 商城与配送是两套身份，清凭证按 target 分流（清态实现由 main.ts 注册）。
 */

let pages: Array<{ route: string, options?: Record<string, unknown> }> = []
const storage = new Map<string, unknown>()
const reLaunch = vi.fn()
const emit = vi.fn()
const clearCredentials = vi.fn()

function stubUni() {
  vi.stubGlobal('uni', {
    getStorageSync: (key: string) => storage.get(key) ?? '',
    setStorageSync: (key: string, value: unknown) => storage.set(key, value),
    removeStorageSync: (key: string) => storage.delete(key),
    reLaunch,
    $emit: emit,
  })
  vi.stubGlobal('getCurrentPages', () => pages)
}

describe('sessionStore', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-28T22:00:00+08:00'))
    setActivePinia(createPinia())
    pages = [{ route: 'pages/home/index' }]
    storage.clear()
    reLaunch.mockReset()
    emit.mockReset()
    clearCredentials.mockReset()
    stubUni()
    registerSessionClearHandler(clearCredentials)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    registerSessionClearHandler(null)
  })

  describe('invalidate（只置状态，不清态/不跳转）', () => {
    it('records the frozen payload and flips the isInvalid getter', () => {
      const session = useSessionStore()

      const accepted = session.invalidate({
        reason: 'BE_REPLACED',
        invalidAt: '2026-10-28T21:32:07+08:00',
        device: { os: 'iOS', browser: '微信', location: '上海市', loginTime: '2026-10-28T21:32:05+08:00' },
        target: 'mall',
      })

      expect(accepted).toBe(true)
      expect(session.invalid).toBe(true)
      expect(session.isInvalid).toBe(true)
      expect(session.isDegraded).toBe(false)
      expect(session.reason).toBe('BE_REPLACED')
      expect(session.subReason).toBe('')
      expect(session.invalidAt).toBe('2026-10-28T21:32:07+08:00')
      expect(session.device).toEqual({ os: 'iOS', browser: '微信', location: '上海市', loginTime: '2026-10-28T21:32:05+08:00' })
      expect(session.target).toBe('mall')
      expect(session.detectedAt).toBe(Date.now())

      // 检测瞬间不得清凭证、不得跳转、不得排定时器
      expect(clearCredentials).not.toHaveBeenCalled()
      expect(reLaunch).not.toHaveBeenCalled()
      expect(vi.getTimerCount()).toBe(0)
    })

    it('degrades unknown/missing reason to the generic copy', () => {
      const session = useSessionStore()

      session.invalidate({ reason: 'SOME_NEW_REASON_FROM_FUTURE', target: 'mall', device: { os: '', browser: '  ' } })

      expect(session.reason).toBe('')
      expect(session.isDegraded).toBe(true)
      expect(session.device).toBeNull()
      expect(session.invalidAt).toBe('')
    })

    it('is idempotent: concurrent 401s keep the first reason instead of overwriting it', () => {
      const session = useSessionStore()
      session.invalidate({ reason: 'BE_REPLACED', invalidAt: '2026-10-28T21:32:07+08:00', target: 'mall' })
      const firstDetectedAt = session.detectedAt

      vi.advanceTimersByTime(1000)
      const accepted = session.invalidate({ reason: 'TOKEN_EXPIRED', target: 'delivery' })

      expect(accepted).toBe(false)
      expect(session.reason).toBe('BE_REPLACED')
      expect(session.target).toBe('mall')
      expect(session.detectedAt).toBe(firstDetectedAt)
    })

    it('stores subReason for KICK_OUT and drops it for other reasons / unknown values (二期)', () => {
      const session = useSessionStore()
      session.invalidate({ reason: 'KICK_OUT', subReason: 'PASSWORD_CHANGED', target: 'mall' })
      expect(session.reason).toBe('KICK_OUT')
      expect(session.subReason).toBe('PASSWORD_CHANGED')
      session.reset()

      // 未知 subReason：丢弃，按无 subReason 降级，不抛错
      session.invalidate({ reason: 'KICK_OUT', subReason: 'SOME_FUTURE_VALUE', target: 'mall' })
      expect(session.reason).toBe('KICK_OUT')
      expect(session.subReason).toBe('')
      session.reset()

      // 契约外组合（非 KICK_OUT 带 subReason）同样丢弃
      session.invalidate({ reason: 'BE_REPLACED', subReason: 'ADMIN_KICK', target: 'mall' })
      expect(session.reason).toBe('BE_REPLACED')
      expect(session.subReason).toBe('')
    })

    it('keeps the first subReason on concurrent 401s', () => {
      const session = useSessionStore()
      session.invalidate({ reason: 'KICK_OUT', subReason: 'ACCOUNT_DISABLED', target: 'mall' })
      session.invalidate({ reason: 'KICK_OUT', subReason: 'ADMIN_KICK', target: 'mall' })
      expect(session.subReason).toBe('ACCOUNT_DISABLED')
    })
  })

  describe('confirmRelogin（写 redirectPath → 清态 → reLaunch 登录页）', () => {
    it('saves the current full path (with query) then clears and navigates for mall', () => {
      pages = [{
        route: 'sub-pages/product/goods-detail/index',
        options: { id: '42', from: 'home list' },
      }]
      const session = useSessionStore()
      session.invalidate({ reason: 'BE_REPLACED', target: 'mall' })

      session.confirmRelogin()

      expect(storage.get(SESSION_REDIRECT_PATH_KEY)).toBe('/sub-pages/product/goods-detail/index?id=42&from=home%20list')
      expect(clearCredentials).toHaveBeenCalledTimes(1)
      expect(clearCredentials).toHaveBeenCalledWith('mall')
      expect(reLaunch).toHaveBeenCalledTimes(1)
      expect(reLaunch).toHaveBeenCalledWith({ url: MALL_LOGIN_PATH })
      // 清态/跳转后弹窗状态复位，页面栈被 reLaunch 重建
      expect(session.invalid).toBe(false)
      expect(session.reason).toBe('')
      expect(vi.getTimerCount()).toBe(0)
    })

    it('falls back to home (no redirectPath) when the invalid state happened on the login page', () => {
      pages = [{ route: 'pages/login/index' }]
      const session = useSessionStore()
      session.invalidate({ reason: 'TOKEN_EXPIRED', target: 'mall' })

      session.confirmRelogin()

      expect(storage.has(SESSION_REDIRECT_PATH_KEY)).toBe(false)
      expect(clearCredentials).toHaveBeenCalledWith('mall')
      expect(reLaunch).toHaveBeenCalledWith({ url: MALL_LOGIN_PATH })
    })

    it('keeps the current delivery page for the delivery target', () => {
      pages = [{ route: 'pages/delivery/task-detail', options: { id: '9' } }]
      const session = useSessionStore()
      session.invalidate({ reason: 'BE_REPLACED', target: 'delivery' })

      session.confirmRelogin()

      expect(storage.get(SESSION_REDIRECT_PATH_KEY)).toBe('/pages/delivery/task-detail?id=9')
      expect(clearCredentials).toHaveBeenCalledWith('delivery')
      expect(reLaunch).toHaveBeenCalledWith({ url: DELIVERY_LOGIN_PATH })
      expect(emit).not.toHaveBeenCalled()
    })

    it('falls back to the delivery workbench when the current page is not a delivery page', () => {
      pages = [{ route: 'pages/home/index' }]
      const session = useSessionStore()
      session.invalidate({ reason: 'KICK_OUT', target: 'delivery' })

      session.confirmRelogin()

      expect(storage.get(SESSION_REDIRECT_PATH_KEY)).toBe(DELIVERY_HOME_PATH)
      expect(reLaunch).toHaveBeenCalledWith({ url: DELIVERY_LOGIN_PATH })
    })
  })

  describe('exit（清态 → 首页/配送登录页，不写 redirectPath）', () => {
    it('clears and returns to the mall home without writing redirectPath', () => {
      const session = useSessionStore()
      session.invalidate({ reason: 'BE_REPLACED', target: 'mall' })

      session.exit()

      expect(storage.has(SESSION_REDIRECT_PATH_KEY)).toBe(false)
      expect(clearCredentials).toHaveBeenCalledWith('mall')
      expect(reLaunch).toHaveBeenCalledTimes(1)
      expect(reLaunch).toHaveBeenCalledWith({ url: MALL_HOME_PATH })
      expect(session.invalid).toBe(false)
      expect(vi.getTimerCount()).toBe(0)
    })

    it('returns the delivery target to the delivery login page', () => {
      const session = useSessionStore()
      session.invalidate({ reason: 'TOKEN_EXPIRED', target: 'delivery' })

      session.exit()

      expect(storage.has(SESSION_REDIRECT_PATH_KEY)).toBe(false)
      expect(clearCredentials).toHaveBeenCalledWith('delivery')
      expect(reLaunch).toHaveBeenCalledWith({ url: DELIVERY_LOGIN_PATH })
    })
  })

  describe('exitToLogin（二期单按钮「返回登录」：清态 → 登录页，不写 redirectPath）', () => {
    it('returns the mall target to the login page (not the logged-in home)', () => {
      pages = [{ route: 'sub-pages/user/member/index' }]
      const session = useSessionStore()
      session.invalidate({ reason: 'KICK_OUT', subReason: 'ACCOUNT_DISABLED', target: 'mall' })

      session.exitToLogin()

      // 落点必须是登录页：这些账号重登必然失败，首页属「登录后内容」语义不符
      expect(clearCredentials).toHaveBeenCalledTimes(1)
      expect(clearCredentials).toHaveBeenCalledWith('mall')
      expect(reLaunch).toHaveBeenCalledTimes(1)
      expect(reLaunch).toHaveBeenCalledWith({ url: MALL_LOGIN_PATH })
      expect(storage.has(SESSION_REDIRECT_PATH_KEY)).toBe(false)
      expect(session.invalid).toBe(false)
      expect(session.subReason).toBe('')
      expect(vi.getTimerCount()).toBe(0)
    })

    it('returns the delivery target to the delivery login page', () => {
      const session = useSessionStore()
      session.invalidate({ reason: 'KICK_OUT', subReason: 'TENANT_DISABLED', target: 'delivery' })

      session.exitToLogin()

      expect(clearCredentials).toHaveBeenCalledWith('delivery')
      expect(reLaunch).toHaveBeenCalledWith({ url: DELIVERY_LOGIN_PATH })
      expect(storage.has(SESSION_REDIRECT_PATH_KEY)).toBe(false)
    })

    it('is a no-op when the session is not invalid', () => {
      const session = useSessionStore()

      session.exitToLogin()

      expect(clearCredentials).not.toHaveBeenCalled()
      expect(reLaunch).not.toHaveBeenCalled()
    })
  })

  describe('reset / no-op guards', () => {
    it('reset returns the state machine to VALID', () => {
      const session = useSessionStore()
      session.invalidate({ reason: 'KICK_OUT', subReason: 'TENANT_DISABLED', target: 'delivery', invalidAt: '2026-10-28T21:32:07+08:00' })
      expect(session.subReason).toBe('TENANT_DISABLED')

      session.reset()

      expect(session.invalid).toBe(false)
      expect(session.reason).toBe('')
      expect(session.subReason).toBe('')
      expect(session.invalidAt).toBe('')
      expect(session.device).toBeNull()
      expect(session.target).toBe('mall')
      expect(session.detectedAt).toBe(0)
      expect(reLaunch).not.toHaveBeenCalled()
      expect(clearCredentials).not.toHaveBeenCalled()
    })

    it('ignores confirmRelogin/exit when the session is not invalid', () => {
      const session = useSessionStore()

      session.confirmRelogin()
      session.exit()

      expect(clearCredentials).not.toHaveBeenCalled()
      expect(reLaunch).not.toHaveBeenCalled()
      expect(storage.has(SESSION_REDIRECT_PATH_KEY)).toBe(false)
    })
  })
})
