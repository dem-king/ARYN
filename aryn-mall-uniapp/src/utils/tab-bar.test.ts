import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  isTabBarPage,
  navigateToUrl,
  normalizeRoutePath,
  toTabBarUrl,
} from './tab-bar'

/**
 * 回归测试：tabBar 页的跳转方式。
 *
 * 背景（真实缺陷）：装修链接（金刚区/轮播/公告等）的目标页由运营在后台配置，
 * 可以指向 tabBar 页（首页/分类/购物车/我的）。此前的跳转实现是
 * `uni.navigateTo({ url, fail: () => uni.switchTab({ url }) })`：
 * 对 tabBar 页 navigateTo 必然失败，失败回调紧接着又发 switchTab，
 * 两次路由挤在同一 tick，微信开发者工具报
 * `routeDone with a webviewId xxx is not found`（页面栈已切换，
 * 旧 webview 的路由完成回调无处可归）。
 *
 * 这里断言的是「一次点击只产生一次标签正确的路由调用」这一用户可见后果：
 *   · tabBar 页 → 只调 switchTab，**不得**出现 navigateTo；
 *   · 普通页 → 只调 navigateTo，不得出现 switchTab；
 *   · 带 query 的 tabBar 链接 → 截断 query 后 switchTab（switchTab 不接受参数）。
 */

interface MockUni {
  getCurrentPages: ReturnType<typeof vi.fn>
  navigateTo: ReturnType<typeof vi.fn>
  redirectTo: ReturnType<typeof vi.fn>
  switchTab: ReturnType<typeof vi.fn>
}

/** 页面栈默认 2 层（首页 + 当前页），未触达 8 层的降级阈值 */
function installGlobals(stackDepth = 2) {
  const navigateTo = vi.fn()
  const redirectTo = vi.fn()
  const switchTab = vi.fn()
  const pages = Array.from({ length: stackDepth }, () => ({ route: 'pages/home/index' }))
  Object.assign(globalThis, {
    getCurrentPages: () => pages,
    uni: { navigateTo, redirectTo, switchTab } as unknown as MockUni,
  })
  return { navigateTo, redirectTo, switchTab }
}

describe('tabBar 路径判定', () => {
  it('识别 tabBar 页，忽略 query 与首尾斜杠', () => {
    // 来自 src/pages.json 的真实 tabBar 清单
    expect(isTabBarPage('/pages/home/index')).toBe(true)
    expect(isTabBarPage('pages/home/index')).toBe(true)
    expect(isTabBarPage('/pages/product/category/index')).toBe(true)
    expect(isTabBarPage('/pages/user/shopping-cart/index')).toBe(true)
    expect(isTabBarPage('/pages/user/user-center/index')).toBe(true)
    // 带 query 也必须命中：旧实现用全等比较，这里正是漏判点
    expect(isTabBarPage('/pages/home/index?scene=1&id=2')).toBe(true)
    // 相似前缀不是 tabBar 页，避免误判把普通页也走 switchTab
    expect(isTabBarPage('/pages/home/index-detail')).toBe(false)
    expect(isTabBarPage('/sub-pages/order/shared-cart/list')).toBe(false)
    expect(isTabBarPage('')).toBe(false)
  })

  it('归一化与 switchTab 目标不含 query', () => {
    expect(normalizeRoutePath('/pages/home/index?from=share')).toBe('pages/home/index')
    expect(normalizeRoutePath('pages/home/index')).toBe('pages/home/index')
    expect(toTabBarUrl('/pages/user/user-center/index?tab=order')).toBe('/pages/user/user-center/index')
  })
})

describe('navigateToUrl 自动选择跳转 API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('tabBar 页只调 switchTab，不产生多余的 navigateTo', () => {
    const g = installGlobals()

    navigateToUrl('/pages/home/index')

    expect(g.switchTab).toHaveBeenCalledTimes(1)
    expect(g.switchTab).toHaveBeenCalledWith({ url: '/pages/home/index' })
    // 关键断言：没有失败的 navigateTo，也就没有同 tick 的第二次路由
    expect(g.navigateTo).not.toHaveBeenCalled()
    expect(g.redirectTo).not.toHaveBeenCalled()
  })

  it('带 query 的 tabBar 链接先截断参数再 switchTab', () => {
    const g = installGlobals()

    navigateToUrl('/pages/user/shopping-cart/index?from=reorder')

    expect(g.switchTab).toHaveBeenCalledWith({ url: '/pages/user/shopping-cart/index' })
    expect(g.navigateTo).not.toHaveBeenCalled()
  })

  it('普通页只调 navigateTo，且不附加 fail 兜底跳转', () => {
    const g = installGlobals()

    navigateToUrl('/sub-pages/order/shared-cart/detail?id=123')

    expect(g.navigateTo).toHaveBeenCalledTimes(1)
    const [options] = g.navigateTo.mock.calls[0]
    expect(options.url).toBe('/sub-pages/order/shared-cart/detail?id=123')
    // fail 里再发一次路由就是这个 bug 的来源，必须不存在
    expect(options.fail).toBeUndefined()
    expect(g.switchTab).not.toHaveBeenCalled()
  })

  it('页面栈接近上限时用 redirectTo 提前降级，而不是失败后再补救', () => {
    const g = installGlobals(8)

    navigateToUrl('/sub-pages/product/goods-detail/index?id=9')

    expect(g.redirectTo).toHaveBeenCalledWith({
      url: '/sub-pages/product/goods-detail/index?id=9',
    })
    expect(g.navigateTo).not.toHaveBeenCalled()
    expect(g.switchTab).not.toHaveBeenCalled()
  })

  it('空 url 不发任何路由调用', () => {
    const g = installGlobals()

    navigateToUrl('')

    expect(g.navigateTo).not.toHaveBeenCalled()
    expect(g.switchTab).not.toHaveBeenCalled()
    expect(g.redirectTo).not.toHaveBeenCalled()
  })
})
