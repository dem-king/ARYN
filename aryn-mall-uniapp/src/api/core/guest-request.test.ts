import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 游客 401 不得被当作「登录状态已失效」—— 针对**真实响应处理器**的回归测试。
 *
 * 背景（真实缺陷）：购物车空态的「常购清单」按钮用裸 `uni.navigateTo` 打开
 * `/sub-pages/product/frequent/index`，绕过了 `uni-mini-router` 的 beforeEach 登录守卫；
 * 常购清单页 `onMounted(fetchPage)` 无条件请求需登录接口 `getFrequentPurchase()`，
 * 服务端返回 401 + `data.reason=NOT_TOKEN`，被 handlers 一律当成会话失效 →
 * 游客看到「登录状态已失效」弹窗。
 *
 * 设计文档《被顶下线弹窗提示》§1 第 5 行 / §7.4：未登录（游客）→ 直接去登录页，
 * 不弹「被顶」文案。修复原则：只有「请求实际携带了 token」的 401 才算登录态失效；
 * 没带 token 的 401 只是游客访问需登录接口。
 *
 * 这里不重实现判定逻辑，直接驱动真实处理器，断言**用户可见后果**：
 *   · hadToken=false 的 401 → 不进入会话失效、不清态、只 toast「请先登录」；
 *   · hadToken=true 的 401 → 仍进入会话失效（守住原语义）；
 *   · 错误兜底路径（handleAlovaError）对游客 401 同样短路；
 *   · 源码契约：instance.ts 打 hadToken 标记、常购页有登录守卫且不再无条件 onMounted 拉取。
 */

const projectRoot = fileURLToPath(new URL('../../..', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

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

  vi.stubGlobal('uni', {
    removeStorageSync,
    $emit: emit,
    reLaunch,
  } as unknown as MockUni)
  vi.stubGlobal('useGlobalToast', () => ({
    error: toastError,
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

  const pinia = await import('pinia')
  pinia.setActivePinia(pinia.createPinia())
  return { removeStorageSync, emit, reLaunch, toastError }
}

/** 常购清单接口地址：需登录接口，游客请求会拿到 401 + reason=NOT_TOKEN */
const FREQUENT_PURCHASE_URL = '/boot/mall-order/app/orderinfo/frequent-purchase'

/** 游客（未携带 token）的请求标记：instance.ts beforeRequest 写入 meta.hadToken=false */
const GUEST_METHOD = {
  url: FREQUENT_PURCHASE_URL,
  config: { meta: { publicRequest: false, hadToken: false } },
} as any

/** 已登录请求的标记：hadToken=true */
const AUTHENTICATED_METHOD = {
  url: FREQUENT_PURCHASE_URL,
  config: { meta: { publicRequest: false, hadToken: true } },
} as any

/** 游客打开需登录页面时后端的真实响应形态：401 + reason=NOT_TOKEN（「没带 token」而非过期） */
function guestUnauthorizedResponse() {
  return {
    statusCode: 401,
    data: {
      code: 401,
      msg: '登录状态已失效，请重新登录',
      data: { reason: 'NOT_TOKEN' },
    },
  }
}

describe('guest request 401 handling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetModules()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('does not enter the session-invalid state for a token-less (guest) 401', async () => {
    const g = await installGlobals()
    const { ApiError, handleAlovaResponse } = await import('./handlers')
    const { useSessionStore } = await import('@/store/sessionStore')

    let error: unknown
    try {
      await handleAlovaResponse(guestUnauthorizedResponse() as any, GUEST_METHOD)
    }
    catch (e) {
      error = e
    }

    // 抛出的仍是 401 ApiError，页面据此展示登录引导（不是「登录状态已失效」标题）
    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as InstanceType<typeof ApiError>
    expect(apiError.code).toBe(401)
    expect(apiError.message).toBe('请先登录')

    // 关键断言：游客本来就没有可失效的会话，不得进入 invalid 态/清态/跳转
    const session = useSessionStore()
    expect(session.invalid).toBe(false)
    expect(session.isDegraded).toBe(false)
    expect(g.removeStorageSync).not.toHaveBeenCalled()
    expect(g.emit).not.toHaveBeenCalled()
    expect(g.reLaunch).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)

    // 但用户仍要看到明确提示：只 toast 一次「请先登录」，不弹会话失效窗
    expect(g.toastError).toHaveBeenCalledTimes(1)
    expect(g.toastError.mock.calls[0][0]).toBe('请先登录')
  })

  it('still enters the session-invalid state when the request carried a token', async () => {
    const g = await installGlobals()
    const { handleAlovaResponse } = await import('./handlers')
    const { useSessionStore } = await import('@/store/sessionStore')

    await expect(handleAlovaResponse(
      {
        statusCode: 401,
        data: { code: 401, msg: '登录状态已失效', data: { reason: 'BE_REPLACED' } },
      } as any,
      AUTHENTICATED_METHOD,
    )).rejects.toThrow()

    // 真实过期必须照旧失效，不能因为游客豁免把这条路径改坏
    const session = useSessionStore()
    expect(session.invalid).toBe(true)
    expect(session.reason).toBe('BE_REPLACED')
    expect(g.toastError).not.toHaveBeenCalled()
    expect(g.reLaunch).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('keeps a guest 401 out of the session-invalid fallback path', async () => {
    const g = await installGlobals()
    const { ApiError, handleAlovaError } = await import('./handlers')
    const { useSessionStore } = await import('@/store/sessionStore')

    // 响应处理器已把游客 401 转成 ApiError 抛出；错误兜底路径只透传，不再二次 toast/失效
    const error = new ApiError('请先登录', 401, guestUnauthorizedResponse().data)

    expect(() => handleAlovaError(error, GUEST_METHOD)).toThrow()

    expect(useSessionStore().invalid).toBe(false)
    expect(g.toastError).not.toHaveBeenCalled()
    expect(g.reLaunch).not.toHaveBeenCalled()
  })

  it('recognizes the guest marker only when the request explicitly had no token', async () => {
    const { isGuestRequest } = await import('./handlers')

    expect(isGuestRequest({ config: { meta: { hadToken: false } } } as any)).toBe(true)
    expect(isGuestRequest({ config: { meta: { hadToken: true } } } as any)).toBe(false)
    // undefined：旧调用方/旧测试未打标记，按「携带凭证」保持既有 401 语义
    expect(isGuestRequest({ config: { meta: {} } } as any)).toBe(false)
    expect(isGuestRequest({ config: {} } as any)).toBe(false)
    expect(isGuestRequest(undefined)).toBe(false)
  })
})

describe('guest request source contracts', () => {
  it('marks whether the request actually carried a token', () => {
    const instanceSource = source('src/api/core/instance.ts')
    expect(instanceSource).toContain('const token = skipToken ? \'\' :')
    expect(instanceSource).toContain('hadToken: Boolean(token)')

    const handlersSource = source('src/api/core/handlers.ts')
    expect(handlersSource).toContain('export function isGuestRequest')
    expect(handlersSource).toContain('translate(\'app.request.needLogin\')')
  })

  it('guards the frequent-purchase page for guests instead of fetching on mount unconditionally', () => {
    const pageSource = source('src/sub-pages/product/frequent/index.vue')

    // 未登录不请求需登录接口（补上登录后 onShow 重新加载）
    expect(pageSource).toContain('if (!authStore.isLoggedIn)')
    expect(pageSource).toMatch(/onShow\(\(\) => \{[\s\S]*?authStore\.isLoggedIn[\s\S]*?fetchPage\(\)/)
    // 无条件 onMounted(fetchPage) 是缺陷成因，必须不再出现
    expect(pageSource).not.toMatch(/\bonMounted\(/)

    // 游客态给出登录引导与登录后回跳路径
    expect(pageSource).toContain('catalog.frequentGuestTip')
    expect(pageSource).toContain('catalog.frequentGoLogin')
    expect(pageSource).toContain('Local.set(\'redirectPath\', \'/sub-pages/product/frequent/index\')')
  })

  it('adds the guest copy to both locales', () => {
    expect(source('src/locales/modules/app.zh-CN.ts')).toContain('needLogin: \'请先登录\'')
    expect(source('src/locales/modules/app.en-US.ts')).toContain('needLogin: \'Please sign in first\'')
    expect(source('src/locales/modules/catalog.zh-CN.ts')).toContain('frequentGuestTip: \'登录后查看常购清单\'')
    expect(source('src/locales/modules/catalog.en-US.ts')).toContain('frequentGoLogin: \'Sign In\'')
  })
})
