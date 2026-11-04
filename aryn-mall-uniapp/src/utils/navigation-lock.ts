import type { RouteBackLocation, RouteLocationRaw, Router } from 'uni-mini-router'

import { normalizeRoutePath } from '@/utils/tab-bar'

/**
 * 路由串行 / 去重锁。
 *
 * 背景（真实缺陷）：微信开发者工具报
 * `routeDone with a webviewId xxx is not found`（外层 `Page route 错误(system error)`）。
 * 基础库在收到 `onAppRouteDone` 时，会在当前页面栈里按 webviewId 找页面；
 * 找不到就说明**这次路由完成的回调回来时，对应 webview 已经被销毁/替换**，
 * 或这条回调来自一次本来就该被拦掉的重复路由。典型成因：
 *   1. 用户连点（双击）触发同一跳转 —— 两次路由挤在同一个 tick；
 *   2. 导航守卫里有 await，第一次点击「没反应」，用户必然再点一次，
 *      两个 async 守卫在同一批微任务恢复 → 同 tick 两次路由；
 *   3. 连点返回键 → 两次 `navigateBack`，第二层页面在第一次返回还没回包时就被弹掉；
 *   4. `switchTab` 到当前页（微信对同页切换的处理会留下无主的 routeDone）。
 *
 * 为什么必须在这一层做：`uni-mini-router@0.1.6` 的 `push/replace/...` 是
 * fire-and-forget（返回 undefined），守卫等异步逻辑全在内部 Promise 里，
 * 调用点没有可 await 的时机；锁只能包在实例方法最外层，**先于** `beforeEach`
 * 执行，才能保证「一次用户手势最多一条路由」。
 *
 * 注意：这里不做 `fail` 回调兜底跳转 —— 失败回调里再发一次路由正是本类报错的成因。
 */

/** 同 kind + 同目标的去重窗口：双击间隔通常 < 300ms，留一点余量 */
export const NAV_DEDUPE_MS = 500
/** 返回键去重窗口：返回动画本身约 300ms，双击会落在 600ms 内 */
export const BACK_DEDUPE_MS = 600

export type NavKind = 'back' | 'push' | 'pushTab' | 'replace' | 'replaceAll'

export interface NavigationLockOptions {
  /** 同目标去重窗口（毫秒） */
  dedupeMs?: number
  /** 返回键去重窗口（毫秒） */
  backDedupeMs?: number
  /** 时钟注入，便于测试 */
  now?: () => number
}

export interface NavigationLock {
  /** 是否放行本次路由；放行会记录时间戳，拦截不会 */
  allow: (kind: NavKind, to?: RouteBackLocation | RouteLocationRaw) => boolean
  /** 清空记录（页面栈被 reLaunch/reLaunch 重置后由调用方按需重置） */
  reset: () => void
}

/** 当前页面栈顶路由（归一化、无前导斜杠）；非页面上下文返回空串 */
export function currentRoutePath(): string {
  if (typeof getCurrentPages !== 'function')
    return ''
  const pages = getCurrentPages()
  const top = pages[pages.length - 1]
  return normalizeRoutePath(String((top as { route?: string } | undefined)?.route || ''))
}

/**
 * 当前页是否可以「返回」。
 *
 * 导航栏返回键的统一判定：栈深 1（首屏/被 redirectTo 顶成唯一页）不能返回，
 * 登录页也不能回跳成环。必须在**点击时**调用，不要在 onMounted 里拍快照：
 * replaceAll/redirectTo 会在页面挂载后改写页面栈，快照失真就会在首屏调
 * `navigateBack`（必然失败）或该返回时误退回首页。
 */
export function canGoBackFromCurrentPage(): boolean {
  if (!canNavigateBack())
    return false
  return currentRoutePath() !== 'pages/login/index'
}

/** 页面栈是否还有上一层；小程序首屏（栈深 1）调 navigateBack 必然失败 */
export function canNavigateBack(): boolean {
  return typeof getCurrentPages === 'function' && getCurrentPages().length > 1
}

/** 当前栈顶页面实例（非页面上下文/平台差异时返回 undefined） */
export function getTopPage(): unknown {
  if (typeof getCurrentPages !== 'function')
    return undefined
  return getCurrentPages().at(-1)
}

/**
 * setup 时用 `getTopPage()` 记下的页面是否仍是栈顶。
 *
 * 异步回调（请求返回、延时跳转）落地时页面可能已被用户返回/被新页面顶替；
 * 那时再发路由就是给已销毁的 webview 发指令，微信会报
 * `routeDone with a webviewId xxx is not found`。
 * 拿不到页面实例（非页面上下文）时放行，避免把正常流程误杀。
 */
export function isPageAlive(page: unknown): boolean {
  if (!page)
    return true
  return getTopPage() === page
}

/**
 * 去重键：`kind:目标路径`。
 *
 * 目标可能是字符串、`{ path }` 或 `{ name }`；`{ name }` 优先按路由表换成路径，
 * 换不到时退回 name（路由表缺失时仍然保证同名去重）。
 */
export function toNavigationKey(
  kind: NavKind,
  to?: RouteBackLocation | RouteLocationRaw,
  routes?: unknown,
): string {
  if (kind === 'back')
    return `back:${String((to as RouteBackLocation | undefined)?.delta ?? 1)}`
  if (typeof to === 'string')
    return `${kind}:${normalizeRoutePath(to)}`
  if (to && typeof to === 'object') {
    const location = to as Record<string, any>
    const path = location.path ?? location.name
    if (typeof location.name === 'string' && Array.isArray(routes)) {
      const hit = routes.find((route: any) => route?.name === location.name)
      if (hit?.path)
        return `${kind}:${normalizeRoutePath(String(hit.path))}`
    }
    if (path)
      return `${kind}:${normalizeRoutePath(String(path))}`
  }
  return `${kind}:`
}

/**
 * 锁本体：只依赖注入的时钟和路由表，可在 Node 环境下单测。
 */
export function createNavigationLock(options: NavigationLockOptions = {}): NavigationLock {
  const { dedupeMs = NAV_DEDUPE_MS, backDedupeMs = BACK_DEDUPE_MS, now = Date.now } = options
  const lastAt = new Map<string, number>()

  return {
    allow(kind, to) {
      const at = now()
      const key = toNavigationKey(kind, to)
      const window = kind === 'back' ? backDedupeMs : dedupeMs
      const previous = lastAt.get(key)
      if (previous !== undefined && at - previous < window)
        return false
      // 条目按「kind:目标」封顶，正常会话里只有几十个页面，不会无限增长
      if (lastAt.size > 100)
        lastAt.clear()
      lastAt.set(key, at)
      return true
    },
    reset() {
      lastAt.clear()
    },
  }
}

/**
 * 把锁装到 uni-mini-router 实例上。
 *
 * 包在实例方法最外层的原因：`useRouter()` 返回的就是这个实例，页面里所有
 * `router.push/replace/pushTab/replaceAll/back` 都会先经过这里；
 * 而 `back` 在 uni-mini-router 里直接调用 `uni.navigateBack`（绕过 beforeEach），
 * 不在这里拦就只能靠每个调用点自己防抖。
 */
export function installNavigationLock(
  router: Router,
  options: NavigationLockOptions = {},
): NavigationLock {
  const lock = createNavigationLock(options)
  const routes = (router as unknown as { routes?: unknown }).routes
  const originals = {
    back: router.back.bind(router),
    push: router.push.bind(router),
    pushTab: router.pushTab.bind(router),
    replace: router.replace.bind(router),
    replaceAll: router.replaceAll.bind(router),
  }

  router.push = (to: RouteLocationRaw) => {
    if (lock.allow('push', to))
      originals.push(to)
  }
  router.replace = (to: RouteLocationRaw) => {
    if (lock.allow('replace', to))
      originals.replace(to)
  }
  router.replaceAll = (to: RouteLocationRaw) => {
    if (lock.allow('replaceAll', to))
      originals.replaceAll(to)
  }
  router.pushTab = (to: RouteLocationRaw) => {
    // switchTab 到当前页会留下无主的 routeDone：目标就是当前路由时根本不发路由
    const target = toNavigationKey('pushTab', to, routes).slice('pushTab:'.length)
    if (target && target === currentRoutePath())
      return
    if (lock.allow('pushTab', to))
      originals.pushTab(to)
  }
  router.back = (to?: RouteBackLocation) => {
    // 首屏没有上一层：navigateBack 必然失败，失败回调同样可能污染路由栈
    if (!canNavigateBack())
      return
    if (lock.allow('back', to))
      originals.back(to)
  }

  return lock
}
