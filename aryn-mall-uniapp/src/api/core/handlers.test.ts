import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSessionStore } from '@/store/sessionStore'
import { ApiError, handleAlovaError, handleAlovaResponse, isDeliveryRequest, isUnauthorizedResponse, parseApiResponse } from './handlers'

/**
 * 401 会话失效分流（一期契约）。
 *
 * 三条不可回归的既有语义（分别在 forbidden-response.test.ts / public-request.test.ts 固化）：
 *  · 免登 401 不清态、不弹窗，只 toast；
 *  · 403 不清态、不弹窗，只 toast；
 *  · 带凭证 401 必须进入「会话失效」——一期把「清态」从检测瞬间挪到用户动作
 *    （README §2.4：清态可以保留/延后，语义不能被改坏），所以这里断言的是：
 *    进入 invalid 态、且**不**即时清 storage/不 emit/不 reLaunch/不 toast/不排定时器；
 *    真正 clearAuthData + reLaunch 的断言在 sessionStore.test.ts（用户点按钮后）。
 */

interface Mocks {
  removeStorageSync: ReturnType<typeof vi.fn>
  emit: ReturnType<typeof vi.fn>
  reLaunch: ReturnType<typeof vi.fn>
  toastError: ReturnType<typeof vi.fn>
}

function installGlobals(): Mocks {
  const mocks = {
    removeStorageSync: vi.fn(),
    emit: vi.fn(),
    reLaunch: vi.fn(),
    toastError: vi.fn(),
  }
  vi.stubGlobal('uni', {
    getStorageSync: vi.fn(() => ''),
    setStorageSync: vi.fn(),
    removeStorageSync: mocks.removeStorageSync,
    $emit: mocks.emit,
    reLaunch: mocks.reLaunch,
  })
  vi.stubGlobal('useGlobalToast', () => ({
    error: mocks.toastError,
    success: vi.fn(),
    show: vi.fn(),
    close: vi.fn(),
  }))
  vi.stubGlobal('CommonUtil', {
    deepMerge: (a: object, b: object) => ({ ...a, ...b }),
    deepClone: (v: unknown) => v,
    isObj: () => false,
  })
  vi.stubGlobal('getCurrentPages', () => [{ route: 'pages/home/index' }])
  return mocks
}

const AUTHENTICATED_METHOD = {
  url: '/boot/mall-user/app/userinfo',
  config: { meta: { publicRequest: false } },
} as any

const DELIVERY_METHOD = {
  url: '/boot/file/staff/delivery-evidence/upload',
  config: { meta: { publicRequest: false, deliveryRequest: true } },
} as any

function unauthorizedBody(payload: unknown) {
  return { statusCode: 200, data: { code: 401, msg: '登录状态已失效，请重新登录', data: payload } }
}

describe('api response parsing', () => {
  it('detects a business 401 from a JSON string response', () => {
    const response = parseApiResponse('{"code":401,"msg":"expired"}')

    expect(response).toMatchObject({ code: 401, msg: 'expired' })
    expect(isUnauthorizedResponse(200, response)).toBe(true)
  })

  it('preserves non-JSON gateway responses instead of throwing SyntaxError', () => {
    expect(parseApiResponse('<html>Bad Gateway</html>')).toEqual({
      data: '<html>Bad Gateway</html>',
    })
  })

  it('recognizes delivery requests for isolated session expiry handling', () => {
    expect(isDeliveryRequest({ url: '/boot/app/delivery/task/1/arrive' } as any)).toBe(true)
    expect(isDeliveryRequest({ url: '/boot/file/staff/delivery-evidence/upload' } as any)).toBe(true)
    expect(isDeliveryRequest({ url: '/boot/app/order/1/detail' } as any)).toBe(false)
  })
})

describe('authenticated 401 enters the session-invalid state (no eager side effects)', () => {
  let mocks: Mocks

  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    mocks = installGlobals()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('records reason/invalidAt/device then throws, without clearing, navigating or toasting', async () => {
    const response = unauthorizedBody({
      reason: 'BE_REPLACED',
      invalidAt: '2026-10-28T21:32:07+08:00',
      device: {
        os: 'Windows 10',
        browser: 'Chrome 120',
        location: '上海市',
        loginTime: '2026-10-28T21:32:05+08:00',
      },
    })

    await expect(handleAlovaResponse(response as any, AUTHENTICATED_METHOD)).rejects.toThrow('账号已在其他设备登录')

    const session = useSessionStore()
    expect(session.invalid).toBe(true)
    expect(session.reason).toBe('BE_REPLACED')
    expect(session.invalidAt).toBe('2026-10-28T21:32:07+08:00')
    expect(session.device).toEqual({
      os: 'Windows 10',
      browser: 'Chrome 120',
      location: '上海市',
      loginTime: '2026-10-28T21:32:05+08:00',
    })
    expect(session.target).toBe('mall')
    expect(session.detectedAt).toBeGreaterThan(0)

    // 弹窗期间不清态：页面数据（用户信息/购物车角标/主题）必须保持原样
    expect(mocks.removeStorageSync).not.toHaveBeenCalled()
    expect(mocks.emit).not.toHaveBeenCalled()
    // 不自动跳转；旧实现的 500ms toast + setTimeout(1000) + reLaunch 全部移除
    expect(mocks.reLaunch).not.toHaveBeenCalled()
    expect(mocks.toastError).not.toHaveBeenCalled()
    // 真断言：没有任何 pending timer，推进 5s 也不会跳（原断言因定时器不推进而恒真）
    expect(vi.getTimerCount()).toBe(0)
    vi.advanceTimersByTime(5000)
    expect(mocks.reLaunch).not.toHaveBeenCalled()
    expect(mocks.toastError).not.toHaveBeenCalled()
  })

  it('reads the payload when the whole body is a JSON string', async () => {
    const response = {
      statusCode: 200,
      data: JSON.stringify({
        code: 401,
        msg: '登录状态已失效',
        data: { reason: 'TOKEN_EXPIRED', invalidAt: '2026-10-28T21:32:07+08:00' },
      }),
    }

    await expect(handleAlovaResponse(response as any, AUTHENTICATED_METHOD)).rejects.toThrow('登录已过期')

    const session = useSessionStore()
    expect(session.invalid).toBe(true)
    expect(session.reason).toBe('TOKEN_EXPIRED')
  })

  it('reads the payload when only the payload is a JSON string (second layer)', async () => {
    const response = {
      statusCode: 200,
      data: { code: 401, msg: '登录状态已失效', data: '{"reason":"BE_REPLACED","device":{"os":"iOS","browser":"微信"}}' },
    }

    await expect(handleAlovaResponse(response as any, AUTHENTICATED_METHOD)).rejects.toThrow('账号已在其他设备登录')

    expect(useSessionStore().reason).toBe('BE_REPLACED')
    expect(useSessionStore().device).toEqual({ os: 'iOS', browser: '微信' })
  })

  it('degrades to the generic copy when a 401 has no reason (old backend / business 401)', async () => {
    const response = { statusCode: 401, data: { code: 401, msg: 'token已过期', data: null } }

    await expect(handleAlovaResponse(response as any, AUTHENTICATED_METHOD)).rejects.toThrow('登录状态已失效')

    const session = useSessionStore()
    expect(session.invalid).toBe(true)
    expect(session.reason).toBe('')
    expect(session.isDegraded).toBe(true)
    expect(session.target).toBe('mall')
    expect(mocks.reLaunch).not.toHaveBeenCalled()
    expect(mocks.toastError).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('keeps the first reason when concurrent 401s arrive (idempotent, single dialog)', async () => {
    await expect(handleAlovaResponse(unauthorizedBody({ reason: 'BE_REPLACED' }) as any, AUTHENTICATED_METHOD)).rejects.toThrow()
    const first = useSessionStore().detectedAt
    vi.advanceTimersByTime(1000)

    await expect(handleAlovaResponse(unauthorizedBody({ reason: 'TOKEN_EXPIRED' }) as any, AUTHENTICATED_METHOD)).rejects.toThrow()

    const session = useSessionStore()
    expect(session.reason).toBe('BE_REPLACED')
    expect(session.detectedAt).toBe(first)
    expect(mocks.toastError).not.toHaveBeenCalled()
    expect(mocks.reLaunch).not.toHaveBeenCalled()
  })

  it('routes delivery 401 to the delivery target without clearing delivery credentials eagerly', async () => {
    await expect(handleAlovaResponse(
      unauthorizedBody({ reason: 'BE_REPLACED', device: { os: 'Android' } }) as any,
      DELIVERY_METHOD,
    )).rejects.toThrow('配送账号已在其他设备登录')

    const session = useSessionStore()
    expect(session.target).toBe('delivery')
    expect(session.reason).toBe('BE_REPLACED')
    expect(mocks.removeStorageSync).not.toHaveBeenCalled()
  })

  it('wires subReason through the 401 payload and uses the subReason title (二期)', async () => {
    const response = unauthorizedBody({
      reason: 'KICK_OUT',
      subReason: 'ACCOUNT_DISABLED',
      invalidAt: '2026-10-28T23:50:12+08:00',
    })

    await expect(handleAlovaResponse(response as any, AUTHENTICATED_METHOD)).rejects.toThrow('账号已被停用')

    const session = useSessionStore()
    expect(session.reason).toBe('KICK_OUT')
    expect(session.subReason).toBe('ACCOUNT_DISABLED')
    expect(session.invalid).toBe(true)
    // 仍不即时清态/不跳转/不 toast
    expect(mocks.removeStorageSync).not.toHaveBeenCalled()
    expect(mocks.reLaunch).not.toHaveBeenCalled()
    expect(mocks.toastError).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('drops an unknown subReason and degrades to the generic KICK_OUT copy (二期)', async () => {
    const response = unauthorizedBody({ reason: 'KICK_OUT', subReason: 'BRAND_NEW_REASON' })

    await expect(handleAlovaResponse(response as any, AUTHENTICATED_METHOD)).rejects.toThrow('登录状态已失效')

    const session = useSessionStore()
    expect(session.reason).toBe('KICK_OUT')
    expect(session.subReason).toBe('')
    expect(session.invalid).toBe(true)
  })
})

describe('session-invalid short circuit in the error path', () => {
  let mocks: Mocks

  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    mocks = installGlobals()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('enters the invalid state once for a raw 401 ApiError (fallback path)', () => {
    const error = new ApiError('token已过期', 401, {
      code: 401,
      msg: 'token已过期',
      data: { reason: 'KICK_OUT', invalidAt: '2026-10-28T20:00:00+08:00' },
    })

    expect(() => handleAlovaError(error, AUTHENTICATED_METHOD)).toThrow()

    const session = useSessionStore()
    expect(session.invalid).toBe(true)
    expect(session.reason).toBe('KICK_OUT')
    expect(mocks.reLaunch).not.toHaveBeenCalled()
    expect(mocks.toastError).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('stays silent when another 401 arrives after the session is already invalid', () => {
    useSessionStore().invalidate({ reason: 'BE_REPLACED', target: 'mall' })

    expect(() => handleAlovaError(new ApiError('登录状态已失效', 401), AUTHENTICATED_METHOD)).toThrow()
    expect(mocks.toastError).not.toHaveBeenCalled()
    expect(mocks.removeStorageSync).not.toHaveBeenCalled()
    expect(useSessionStore().reason).toBe('BE_REPLACED')
  })
})
