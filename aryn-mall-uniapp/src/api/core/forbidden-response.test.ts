import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { isForbiddenResponse, isPublicRequest, isUnauthorizedResponse, parseApiResponse } from './handlers'

/**
 * 权限不足（403）不得被当作「登录过期」—— 针对**真实响应处理器**的回归测试。
 *
 * 背景（真实缺陷）：小程序签到页调用了管理端接口 `/mall-user/signinrecord/page`，
 * 该接口标着 `@SaCheckPermission("user:signinrecord:page")`，而 C 端登录
 * （TocLoginService）从不给 token 灌权限，于是服务端返回 403。
 * 前端当时把 401 与 403 共用一个判定 `isUnauthorizedResponse`，
 * 403 因此走进 `handleAuthenticationExpired`：清 token、弹「登录已过期」、跳登录页。
 *
 * 后果比一次报错严重得多：用户重新登录也治不好 —— 重新登录不会凭空获得管理端权限，
 * 进页面仍被踢出，看起来像「登录一直失效」。
 *
 * 这里不重实现判定逻辑，而是直接驱动 `handleAlovaResponse`，断言**用户可见后果**：
 *   · 403 → 不清登录态、不弹会话失效、不跳登录页，只提示权限不足；
 *   · 401 → 仍必须进入「会话失效」态（一期改为弹窗，清态延后到用户动作，
 *     但绝不能被 403 的拆分修坏）。
 */

interface MockUni {
  removeStorageSync: ReturnType<typeof vi.fn>
  $emit: ReturnType<typeof vi.fn>
  reLaunch: ReturnType<typeof vi.fn>
}

/**
 * `vi.resetModules()` 之后模块注册表已清空：先动态 import pinia 建一份新的
 * activePinia，随后动态 import 的 handlers/sessionStore 会复用同一份 pinia 模块实例
 *（静态 import 的 pinia 是重置前的旧实例，setActivePinia 对它们无效）。
 */
async function installGlobals() {
  const removeStorageSync = vi.fn()
  const emit = vi.fn()
  const reLaunch = vi.fn()
  const toastError = vi.fn()

  const uni = { removeStorageSync, $emit: emit, reLaunch } as unknown as MockUni
  Object.assign(globalThis, {
    uni,
    useGlobalToast: () => ({ error: toastError, success: vi.fn(), show: vi.fn(), close: vi.fn() }),
    CommonUtil: { deepMerge: (a: object, b: object) => ({ ...a, ...b }), deepClone: (v: unknown) => v, isObj: () => false },
    getCurrentPages: () => [{ route: 'pages/home/index' }],
  })
  const pinia = await import('pinia')
  pinia.setActivePinia(pinia.createPinia())
  return { removeStorageSync, emit, reLaunch, toastError }
}

/** 后端 NotPermissionException 的真实响应形态：HTTP 403 + 业务 code 403 */
function forbiddenResponse() {
  return {
    statusCode: 403,
    data: { code: 403, msg: '权限不足，缺少所需权限: user:signinrecord:page', data: null },
  }
}

describe('forbidden (403) handling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetModules()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('does not clear the session when an authenticated request is denied by permission', async () => {
    const g = await installGlobals()
    const { handleAlovaResponse } = await import('./handlers')
    const { useSessionStore } = await import('@/store/sessionStore')

    const method = { url: '/boot/mall-user/signinrecord/page', config: { meta: { publicRequest: false } } } as any

    await expect(handleAlovaResponse(forbiddenResponse() as any, method)).rejects.toThrow()

    // 关键断言：登录态必须原封不动，且不得被误判为会话失效
    expect(g.removeStorageSync).not.toHaveBeenCalledWith('auth')
    expect(g.emit).not.toHaveBeenCalledWith('auth-expired')
    expect(useSessionStore().invalid).toBe(false)
    // 但用户仍要看到失败提示，且不应被误导为「登录已过期」
    expect(g.toastError).toHaveBeenCalled()
    expect(g.toastError.mock.calls[0][0]).not.toContain('登录已过期')
    // 真断言：403 路径不排任何跳转定时器（旧空断言依赖定时器不推进，恒真）
    expect(vi.getTimerCount()).toBe(0)
    vi.advanceTimersByTime(5000)
    expect(g.reLaunch).not.toHaveBeenCalled()
  })

  it('enters the session-invalid state for an authenticated 401 (state clear deferred to the user action)', async () => {
    const g = await installGlobals()
    const { handleAlovaResponse } = await import('./handlers')
    const { useSessionStore } = await import('@/store/sessionStore')

    const method = { url: '/boot/mall-user/app/userinfo', config: { meta: { publicRequest: false } } } as any
    const unauthorized = { statusCode: 401, data: { code: 401, msg: 'token已过期', data: null } }

    await expect(handleAlovaResponse(unauthorized as any, method)).rejects.toThrow()

    // 真实过期必须照旧失效，不能因为 403 的拆分把这条路径改坏；
    // 一期把「清态」延后到用户点「重新登录/退出」时（README §2.4：语义不能改坏）。
    const session = useSessionStore()
    expect(session.invalid).toBe(true)
    expect(session.reason).toBe('')
    expect(session.isDegraded).toBe(true)
    // 弹窗期间不清态、不自动跳转、不再有 500ms toast + 1s reLaunch
    expect(g.removeStorageSync).not.toHaveBeenCalled()
    expect(g.emit).not.toHaveBeenCalled()
    expect(g.toastError).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
    vi.advanceTimersByTime(5000)
    expect(g.reLaunch).not.toHaveBeenCalled()
  })

  it('does not clear the session when a token-less request is denied by permission', async () => {
    const g = await installGlobals()
    const { handleAlovaResponse } = await import('./handlers')
    const { useSessionStore } = await import('@/store/sessionStore')

    // 免登请求拿到 403 同样只说明该接口没放行公开访问，与登录态无关
    const method = { url: '/boot/app/goodsbrand/list', config: { meta: { publicRequest: true } } } as any

    await expect(handleAlovaResponse(forbiddenResponse() as any, method)).rejects.toThrow()

    expect(g.removeStorageSync).not.toHaveBeenCalledWith('auth')
    expect(useSessionStore().invalid).toBe(false)
    expect(g.toastError).toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
    vi.advanceTimersByTime(5000)
    expect(g.reLaunch).not.toHaveBeenCalled()
  })

  it('separates 401 from 403 in the response predicates', () => {
    // HTTP 层
    expect(isUnauthorizedResponse(401, {})).toBe(true)
    expect(isUnauthorizedResponse(403, {})).toBe(false)
    expect(isForbiddenResponse(403, {})).toBe(true)
    expect(isForbiddenResponse(401, {})).toBe(false)

    // 业务 code 层（网关/过滤器可能用 HTTP 200 包业务 code）
    expect(isUnauthorizedResponse(200, { code: 401 })).toBe(true)
    expect(isUnauthorizedResponse(200, { code: 403 })).toBe(false)
    expect(isForbiddenResponse(200, { code: 403 })).toBe(true)
    expect(isForbiddenResponse(200, { code: 401 })).toBe(false)

    // 两者不得互相包含：这是本缺陷的直接成因
    const forbidden = parseApiResponse('{"code":403,"msg":"权限不足"}')
    expect(isForbiddenResponse(200, forbidden)).toBe(true)
    expect(isUnauthorizedResponse(200, forbidden)).toBe(false)
  })

  it('recognizes the marker only when the request declared skipToken', () => {
    expect(isPublicRequest({ config: { meta: { publicRequest: true } } } as any)).toBe(true)
    expect(isPublicRequest({ config: { meta: { publicRequest: false } } } as any)).toBe(false)
    expect(isPublicRequest({ config: {} } as any)).toBe(false)
    expect(isPublicRequest(undefined)).toBe(false)
  })
})
