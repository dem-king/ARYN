import { tabBar } from '@/pages.json'

/**
 * 路径归一化：去掉 query 与首尾斜杠。
 *
 * tabBar 配置里的 `pagePath` 形如 `pages/home/index`（无前导斜杠），
 * 而跳转 url 多为 `/pages/home/index?from=xxx`，两者必须先归一化才能比较。
 */
export function normalizeRoutePath(url: string) {
  const [path = ''] = (url || '').split('?')
  return path.replace(/^\/+/, '').replace(/\/+$/, '')
}

const TAB_BAR_PATHS = new Set(
  (tabBar?.list ?? []).map(item => normalizeRoutePath(item.pagePath)),
)

/**
 * 是否指向 tabBar 页面。
 *
 * tabBar 页面**只能用 `switchTab` 打开**：`navigateTo` / `redirectTo` 对
 * tabBar 页一律失败。常见错误写法是「先 navigateTo 试，失败回调里再 switchTab」——
 * 那会让微信路由在同一个 tick 内收到两次跳转，开发者工具报
 * `routeDone with a webviewId xxx is not found`（页面栈已切换，旧 webview 的
 * 路由回调无处可归）。因此这里在**跳转之前**就判定，而不是靠失败兜底。
 *
 * 动态目标（装修链接、公告跳转等）都由运营在后台配置，随时可能配成
 * tabBar 页，所以判定必须下沉到这里复用，不能只靠调用方自觉。
 */
export function isTabBarPage(url: string) {
  return TAB_BAR_PATHS.has(normalizeRoutePath(url))
}

/**
 * 把 url 整理成 `switchTab` 可接受的形态。
 *
 * `switchTab` 不接受 query（传参会直接失败），且 tabBar 页本就无法通过 URL
 * 携带参数，因此这里统一截断 query，避免「配了一个带参数的 tabBar 链接」
 * 就静默跳不过去。
 */
export function toTabBarUrl(url: string) {
  return `/${normalizeRoutePath(url)}`
}

/**
 * 按 url 打开页面，自动为 tabBar 页选择 `switchTab`。
 *
 * 所有「目标由数据决定」的跳转都应走这里：装修链接、公告、后台配置的入口
 * 都可能指向 tabBar 页，逐个调用方自己判断迟早会漏。
 *
 * 刻意**不使用 fail 回调做二次跳转**：失败回调与首次跳转发生在同一 tick，
 * 会让路由栈收到两次操作，正是 `routeDone with a webviewId ... is not found`
 * 的成因。页面栈满等情况一律**提前**判断，而不是失败后再补救。
 *
 * @param url 站内绝对路径（可带 query），非站内路径由调用方自行过滤
 */
export function navigateToUrl(url: string) {
  if (!url)
    return
  if (isTabBarPage(url)) {
    uni.switchTab({ url: toTabBarUrl(url) })
    return
  }
  // 小程序页面栈上限 10 层，预留 2 层空间；接近上限时用 redirectTo 顶替当前页，
  // 保证用户能到达目标页而不是静默失败（与 toJumpUrl 的既有口径一致）。
  const stackDepth = (typeof getCurrentPages === 'function' ? getCurrentPages() : []).length
  if (stackDepth >= 8) {
    uni.redirectTo({ url })
    return
  }
  uni.navigateTo({ url })
}
