import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 登录入口守卫（useRequireLogin）的回归测试。
 *
 * 背景：路由守卫只拦 `router.push/replace`，游客可通过裸 `uni.navigateTo`、装修链接、
 * 分享卡片直达进入需要登录的页面/按钮。请求层（handlers.isGuestRequest）只保证 401
 * 不再误弹「登录状态已失效」，但「引导登录」必须由入口自己做。这里固定三件事：
 *  · 未登录 → 记录 redirectPath（含 query）+ 打开登录页，返回 false；
 *  · 已登录 → 直接放行，不写 redirectPath、不跳转；
 *  · 已在登录页 / 连续点按 → 不记录、不重复叠登录页。
 */

const { authState } = vi.hoisted(() => ({
  authState: { isLoggedIn: false },
}))

vi.mock('@/store/authStore', () => ({
  useAuthStore: () => authState,
}))

interface PageStub {
  route: string
  options?: Record<string, unknown>
}

const storage: Record<string, unknown> = {}
const navigateToOptions: any[] = []
const pages: PageStub[] = []

function installGlobals() {
  navigateToOptions.length = 0
  vi.stubGlobal('uni', {
    setStorageSync: (key: string, value: unknown) => {
      storage[key] = value
    },
    getStorageSync: (key: string) => storage[key],
    removeStorageSync: (key: string) => {
      delete storage[key]
    },
    navigateTo: (options: any) => {
      // 真实 uni.navigateTo 的 complete 在跳转结束时才回调；这里留到用例手动触发，
      // 才能断言「连续点按期间只跳一次」。
      navigateToOptions.push(options)
    },
  })
  vi.stubGlobal('getCurrentPages', () => pages)
}

async function loadGuard() {
  return import('./useRequireLogin')
}

describe('useRequireLogin', () => {
  beforeEach(() => {
    vi.resetModules()
    for (const key of Object.keys(storage))
      delete storage[key]
    pages.length = 0
    pages.push({ route: 'sub-pages/product/goods-detail/index', options: { id: '9' } })
    authState.isLoggedIn = false
    installGlobals()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('records the current page (with query) and opens login for a guest', async () => {
    const { useRequireLogin } = await loadGuard()
    const { ensureLoggedIn } = useRequireLogin()

    expect(ensureLoggedIn()).toBe(false)
    expect(storage.redirectPath).toBe('/sub-pages/product/goods-detail/index?id=9')
    expect(navigateToOptions).toHaveLength(1)
    expect(navigateToOptions[0].url).toBe('/pages/login/index')
  })

  it('lets a logged-in user continue without navigation or redirect write', async () => {
    authState.isLoggedIn = true
    const { useRequireLogin } = await loadGuard()
    const { ensureLoggedIn } = useRequireLogin()

    expect(ensureLoggedIn()).toBe(true)
    expect(storage.redirectPath).toBeUndefined()
    expect(navigateToOptions).toHaveLength(0)
  })

  it('honors an explicit redirect path', async () => {
    const { useRequireLogin } = await loadGuard()
    const { ensureLoggedIn } = useRequireLogin()

    expect(ensureLoggedIn('/sub-pages/user/member/index')).toBe(false)
    expect(storage.redirectPath).toBe('/sub-pages/user/member/index')
  })

  it('does not navigate again while already on the login page', async () => {
    pages.length = 0
    pages.push({ route: 'pages/login/index' })
    const { useRequireLogin } = await loadGuard()
    const { ensureLoggedIn } = useRequireLogin()

    expect(ensureLoggedIn()).toBe(false)
    expect(navigateToOptions).toHaveLength(0)
    expect(storage.redirectPath).toBeUndefined()
  })

  it('does not stack multiple login pages on repeated taps', async () => {
    const { useRequireLogin } = await loadGuard()
    const { ensureLoggedIn } = useRequireLogin()

    expect(ensureLoggedIn()).toBe(false)
    expect(ensureLoggedIn()).toBe(false)
    expect(navigateToOptions).toHaveLength(1)

    // 跳转结束（complete）后恢复可再次引导
    navigateToOptions[0].complete()
    expect(ensureLoggedIn()).toBe(false)
    expect(navigateToOptions).toHaveLength(2)
  })

  it('never stores the login page itself as a redirect target', async () => {
    const { goLogin } = await loadGuard()

    goLogin('/pages/login/index')

    expect(storage.redirectPath).toBeUndefined()
    expect(navigateToOptions).toHaveLength(1)
  })
})
