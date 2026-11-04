import type { Router } from 'uni-mini-router'

import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  BACK_DEDUPE_MS,
  canGoBackFromCurrentPage,
  canNavigateBack,
  createNavigationLock,
  currentRoutePath,
  installNavigationLock,
  NAV_DEDUPE_MS,
  toNavigationKey,
} from './navigation-lock'

/**
 * 回归测试：路由串行/去重锁。
 *
 * 背景见 navigation-lock.ts 文件头：微信开发者工具
 * `routeDone with a webviewId xxx is not found` 的两个高发成因是
 * 「同一 tick 两次路由」和「switchTab 当前页」，这里锁定用户可见后果——
 * 一次手势/一次双击只产生一条 `uni.*` 路由调用。
 */

interface StubStack {
  stack: Array<{ route: string }>
}

function stubPageStack(routes: string[]): StubStack {
  const stack = routes.map(route => ({ route }))
  Object.assign(globalThis, { getCurrentPages: () => stack })
  return { stack }
}

interface FakeRouter extends Router {
  calls: Array<{ method: string, target?: unknown }>
}

/** 最小可用的 Router 桩：只记录调用，不真正跳转 */
function createFakeRouter(routes: Array<{ name: string, path: string }>): FakeRouter {
  const calls: FakeRouter['calls'] = []
  const record = (method: string) => (target?: unknown) => {
    calls.push({ method, target })
  }
  const router = {
    calls,
    route: { value: { path: '/' } },
    routes,
    guardHooks: { beforeHooks: [], afterHooks: [] },
    push: record('push'),
    replace: record('replace'),
    replaceAll: record('replaceAll'),
    pushTab: record('pushTab'),
    back: record('back'),
    beforeEach: vi.fn(),
    afterEach: vi.fn(),
    install: vi.fn(),
  } as unknown as FakeRouter
  return router
}

const ROUTES = [
  { name: 'home', path: '/pages/home/index' },
  { name: 'user-center', path: '/pages/user/user-center/index' },
  { name: 'member-center', path: '/sub-pages/user/member/index' },
]

describe('toNavigationKey 去重键', () => {
  it('字符串 / {path} / {name} 归一化为同一键，忽略 query 与首尾斜杠', () => {
    expect(toNavigationKey('push', '/sub-pages/user/member/index?id=1')).toBe('push:sub-pages/user/member/index')
    expect(toNavigationKey('push', { path: '/sub-pages/user/member/index' })).toBe('push:sub-pages/user/member/index')
    expect(toNavigationKey('push', { name: 'member-center' }, ROUTES)).toBe('push:sub-pages/user/member/index')
  })

  it('路由表里没有的 name 退回 name 本身，仍然保证同名去重', () => {
    expect(toNavigationKey('push', { name: 'not-registered' }, ROUTES)).toBe('push:not-registered')
  })

  it('back 按 delta 区分，不同 delta 不算重复', () => {
    expect(toNavigationKey('back')).toBe('back:1')
    expect(toNavigationKey('back', { delta: 2 } as any)).toBe('back:2')
  })
})

describe('createNavigationLock 时间窗口', () => {
  it('同目标在窗口内只放行一次，窗口外再次放行', () => {
    let now = 0
    const lock = createNavigationLock({ now: () => now })

    expect(lock.allow('push', '/sub-pages/user/member/index')).toBe(true)
    now = NAV_DEDUPE_MS - 1
    expect(lock.allow('push', '/sub-pages/user/member/index')).toBe(false)
    now = NAV_DEDUPE_MS
    expect(lock.allow('push', '/sub-pages/user/member/index')).toBe(true)
  })

  it('不同目标互不影响（快速点两个入口仍然各自生效）', () => {
    const lock = createNavigationLock({ now: () => 0 })

    expect(lock.allow('push', '/sub-pages/user/member/index')).toBe(true)
    expect(lock.allow('push', '/sub-pages/user/address/index')).toBe(true)
  })

  it('返回键用更长的独立窗口', () => {
    let now = 0
    const lock = createNavigationLock({ now: () => now })

    expect(lock.allow('back')).toBe(true)
    now = NAV_DEDUPE_MS
    expect(lock.allow('back')).toBe(false)
    now = BACK_DEDUPE_MS
    expect(lock.allow('back')).toBe(true)
  })

  it('被拦截的调用不刷新时间戳（不制造新的静默期）', () => {
    let now = 0
    const lock = createNavigationLock({ now: () => now })

    expect(lock.allow('push', { path: '/pages/home/index' })).toBe(true)
    now = 100
    expect(lock.allow('push', { path: '/pages/home/index' })).toBe(false)
    now = NAV_DEDUPE_MS
    expect(lock.allow('push', { path: '/pages/home/index' })).toBe(true)
  })
})

describe('installNavigationLock 接线', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('双击会员中心只产生一次 navigateTo（守卫生效）', () => {
    stubPageStack(['pages/user/user-center/index'])
    const router = createFakeRouter(ROUTES)
    installNavigationLock(router)

    router.push({ path: '/sub-pages/user/member/index' })
    router.push({ path: '/sub-pages/user/member/index' })

    expect(router.calls).toEqual([
      { method: 'push', target: { path: '/sub-pages/user/member/index' } },
    ])
  })

  it('目标为当前 tab 时 pushTab 不产生 switchTab', () => {
    stubPageStack(['pages/user/user-center/index'])
    const router = createFakeRouter(ROUTES)
    installNavigationLock(router)

    router.pushTab({ name: 'user-center' })
    router.pushTab({ name: 'home' })

    expect(router.calls).toEqual([{ method: 'pushTab', target: { name: 'home' } }])
  })

  it('首屏（页面栈 1 层）的返回被拦下，不产生 navigateBack', () => {
    stubPageStack(['pages/user/user-center/index'])
    const router = createFakeRouter(ROUTES)
    installNavigationLock(router)

    router.back()

    expect(router.calls).toEqual([])
  })

  it('连点返回键只回退一次（连同旧页面一起被弹掉才是真 bug）', () => {
    stubPageStack(['pages/home/index', 'pages/user/user-center/index', 'sub-pages/user/member/index'])
    const router = createFakeRouter(ROUTES)
    installNavigationLock(router)

    router.back()
    router.back()

    expect(router.calls).toEqual([{ method: 'back', target: undefined }])
  })

  it('间隔足够久的两次正常返回都会执行', () => {
    stubPageStack(['pages/home/index', 'pages/user/user-center/index', 'sub-pages/user/member/index'])
    const router = createFakeRouter(ROUTES)
    let now = 0
    installNavigationLock(router, { now: () => now })

    router.back()
    now = BACK_DEDUPE_MS
    router.back()

    expect(router.calls).toHaveLength(2)
  })

  it('replace / replaceAll 保持原样转发（锁不改变既定跳转语义）', () => {
    stubPageStack(['sub-pages/user/member/index'])
    const router = createFakeRouter(ROUTES)
    installNavigationLock(router)

    router.replace({ path: '/pages/login/index' })
    router.replaceAll({ path: '/pages/home/index' })

    expect(router.calls).toEqual([
      { method: 'replace', target: { path: '/pages/login/index' } },
      { method: 'replaceAll', target: { path: '/pages/home/index' } },
    ])
  })
})

describe('页面栈辅助函数', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('currentRoutePath 取栈顶并归一化', () => {
    stubPageStack(['pages/home/index', 'pages/user/user-center/index'])
    expect(currentRoutePath()).toBe('pages/user/user-center/index')
  })

  it('canNavigateBack 只在栈深 > 1 时为真', () => {
    stubPageStack(['pages/home/index'])
    expect(canNavigateBack()).toBe(false)
    stubPageStack(['pages/home/index', 'pages/login/index'])
    expect(canNavigateBack()).toBe(true)
  })

  it('canGoBackFromCurrentPage 首屏与登录页为 false，其余非首屏为 true', () => {
    // 首屏：没有上一层，点返回只能回首页
    stubPageStack(['pages/home/index'])
    expect(canGoBackFromCurrentPage()).toBe(false)
    // 登录页：回跳会成环
    stubPageStack(['pages/home/index', 'pages/login/index'])
    expect(canGoBackFromCurrentPage()).toBe(false)
    // 正常二级页：可返回
    stubPageStack(['pages/user/user-center/index', 'sub-pages/user/member/index'])
    expect(canGoBackFromCurrentPage()).toBe(true)
  })
})
