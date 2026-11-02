import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { isPublicRequest, isUnauthorizedResponse, parseApiResponse } from './handlers'

/**
 * 免登请求的 401 不得被当作「登录过期」—— 针对**真实响应处理器**的回归测试。
 *
 * 背景（真实缺陷）：小程序登录成功后点首页金刚区分类，被提示「登录已过期」跳回登录页。
 * 根因不是登录态失效：金刚区落地页（goods-list）会以**免登**方式（`skipToken: true`，
 * 不带 satoken 头）请求商品品牌列表，而该接口未登记进后端免登白名单 → 服务端返回
 * `code: 401` → `handleAlovaResponse` 把 401 一律当成登录态失效，调
 * `uni.removeStorageSync('auth')` 并 reLaunch 到登录页。
 *
 * 这里不重实现判定逻辑，而是直接驱动 `handleAlovaResponse`，断言**用户可见后果**：
 *   · 公开请求收到 401 → 不得清登录态、不得进入会话失效、不得跳登录页（只作为普通错误提示）；
 *   · 带凭证请求收到 401 → 仍要进入会话失效态（一期改为弹窗，清态延后到用户动作）。
 */

interface MockUni {
  removeStorageSync: ReturnType<typeof vi.fn>
  $emit: ReturnType<typeof vi.fn>
  reLaunch: ReturnType<typeof vi.fn>
}

/**
 * `vi.resetModules()` 之后模块注册表已清空：先动态 import pinia 建一份新的
 * activePinia，随后动态 import 的 handlers/sessionStore 会复用同一份 pinia 模块实例。
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

/** HTTP 200 + 业务 code 401，即后端缺白名单时的真实响应形态 */
function unauthorizedResponse() {
  return {
    statusCode: 200,
    data: { code: 401, msg: '未能读取到有效 token', data: null },
  }
}

describe('public request 401 handling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetModules()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('does not clear the session when a token-less request gets 401', async () => {
    const g = await installGlobals()
    const { handleAlovaResponse } = await import('./handlers')
    const { useSessionStore } = await import('@/store/sessionStore')

    const method = { url: '/boot/app/goodsbrand/list', config: { meta: { publicRequest: true } } } as any

    await expect(handleAlovaResponse(unauthorizedResponse() as any, method)).rejects.toThrow()

    // 关键断言：登录态没有被清、没有进入会话失效、也没有跳登录页
    expect(g.removeStorageSync).not.toHaveBeenCalledWith('auth')
    expect(g.emit).not.toHaveBeenCalledWith('auth-expired')
    expect(useSessionStore().invalid).toBe(false)
    // 但用户仍要看到失败提示（静默失败会让页面空白无从排查）
    expect(g.toastError).toHaveBeenCalled()
    // 真断言：不排任何跳转定时器，推进时间也不会 reLaunch（旧空断言因定时器不推进恒真）
    expect(vi.getTimerCount()).toBe(0)
    vi.advanceTimersByTime(5000)
    expect(g.reLaunch).not.toHaveBeenCalled()
  })

  it('recognizes the marker only when the request declared skipToken', () => {
    // instance.ts 的 beforeRequest 写入该标记；响应处理器必须读同一字段
    expect(isPublicRequest({ config: { meta: { publicRequest: true } } } as any)).toBe(true)
    expect(isPublicRequest({ config: { meta: { publicRequest: false } } } as any)).toBe(false)
    expect(isPublicRequest({ config: {} } as any)).toBe(false)
    expect(isPublicRequest(undefined)).toBe(false)
    // 不能因为 meta 里存在别的字段就误判为公开请求
    expect(isPublicRequest({ config: { meta: { someOtherFlag: true } } } as any)).toBe(false)
  })

  it('still recognizes the whitelist-miss response that triggered the bug', () => {
    // 服务端缺白名单时的真实响应：HTTP 200 + 业务 code 401
    const resp = parseApiResponse('{"code":401,"msg":"未能读取到有效 token","data":null}')
    expect(isUnauthorizedResponse(200, resp)).toBe(true)
    expect(resp.msg).toBe('未能读取到有效 token')
  })

  it('enters the session-invalid state when an authenticated request gets 401', async () => {
    const g = await installGlobals()
    const { handleAlovaResponse } = await import('./handlers')
    const { useSessionStore } = await import('@/store/sessionStore')

    const method = { url: '/boot/mall-user/app/userinfo', config: { meta: { publicRequest: false } } } as any

    await expect(handleAlovaResponse(unauthorizedResponse() as any, method)).rejects.toThrow()

    // 真实过期必须照旧失效，不能因为上面的豁免把这条路径改坏。
    // 一期改造后「清态」发生在用户点「重新登录/退出」时（见 sessionStore.test.ts），
    // 检测瞬间只进入 invalid 态并冻结请求，不再有 removeStorageSync/emit/1s reLaunch。
    const session = useSessionStore()
    expect(session.invalid).toBe(true)
    expect(session.isDegraded).toBe(true)
    expect(g.removeStorageSync).not.toHaveBeenCalled()
    expect(g.emit).not.toHaveBeenCalled()
    expect(g.toastError).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
    vi.advanceTimersByTime(5000)
    expect(g.reLaunch).not.toHaveBeenCalled()
  })
})
