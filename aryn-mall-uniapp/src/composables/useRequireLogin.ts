/**
 * C 端「需要登录才能继续」的统一入口。
 *
 * 为什么需要它：路由守卫（`src/router/index.ts` 的 `beforeEach`）只在
 * `uni-mini-router` 的 `router.push/replace/...` 上执行；页面里的裸 `uni.navigateTo`、
 * 装修组件链接、微信分享卡片直达都不会经过守卫，游客因此能打开需要登录的页面、
 * 点到需要登录的按钮。请求层已经把「没带 token 的 401」从会话失效弹窗里摘出来了
 * （`handlers.isGuestRequest` 只提示「请先登录」），但请求层不负责引导登录 ——
 * 少了入口守卫，游客点完按钮只看到一条 toast，没有任何后续动作。
 *
 * 两类用法（与本项目既有约定一致）：
 *  1. 点击型入口（领券、收藏、开团、客服、加购确认…）：调用 `ensureLoggedIn()`，
 *     未登录时自动记录回跳路径并打开登录页，返回 false 让调用方直接 return。
 *  2. 页面级入口（onLoad/onShow 就拉登录数据的页面）：用 `authStore.isLoggedIn` 判空
 *     + 游客态（不发请求、展示登录引导），不要自动跳转 —— 深链/分享打开就被弹走很突兀。
 *
 * 回跳：写 `redirectPath`，登录页 `loginSuccess()/autoRedirect()` 读取后
 * `router.replaceAll` 回原页（含 query 参数，商详/共享车分享页这类页面才不会丢参）。
 */
import { useAuthStore } from '@/store/authStore'
import { getCurrentFullPath } from '@/utils/current-page'
import { Local } from '@/utils/storage'

/** 商城登录页（与 `store/sessionStore.ts` 的 `MALL_LOGIN_PATH` 同值） */
export const LOGIN_PAGE_PATH = '/pages/login/index'
/** 与登录页同源的 redirectPath key（登录页 `Local.get('redirectPath')`） */
export const LOGIN_REDIRECT_KEY = 'redirectPath'

const LOGIN_PAGE_ROUTE = LOGIN_PAGE_PATH.replace(/^\/+/, '')

/** 上一次跳登录是否仍在进行：连点券卡/收藏时避免叠出多个登录页 */
let navigating = false

/**
 * 记录回跳路径并打开登录页（幂等）。
 *
 * @param redirectPath 登录成功后要回到的页面；默认取当前页（含 query）。
 *   当前就在登录页时不记录、不重复跳转，避免登录页回跳成环。
 */
export function goLogin(redirectPath: string = getCurrentFullPath()) {
  if (navigating)
    return
  const topRoute = getCurrentPages().at(-1)?.route || ''
  if (topRoute === LOGIN_PAGE_ROUTE)
    return
  if (redirectPath && !redirectPath.startsWith(LOGIN_PAGE_PATH))
    Local.set(LOGIN_REDIRECT_KEY, redirectPath)
  navigating = true
  uni.navigateTo({
    url: LOGIN_PAGE_PATH,
    complete: () => {
      navigating = false
    },
  })
}

export function useRequireLogin() {
  const authStore = useAuthStore()

  /**
   * 需要登录的点击入口守卫。
   *
   * @param redirectPath 可选：显式回跳路径（默认当前页含 query）。
   * @returns 已登录 → true（调用方继续执行）；未登录 → 已记录回跳并打开登录页，返回 false。
   */
  function ensureLoggedIn(redirectPath?: string): boolean {
    if (authStore.isLoggedIn)
      return true
    goLogin(redirectPath)
    return false
  }

  return { ensureLoggedIn, goLogin }
}
