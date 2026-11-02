/**
 * fixed 顶栏列表页的「顶栏等高占位」高度。
 *
 * 列表页（z-paging 整屏滚动）把导航栏从 `#top` 槽上移成页面级 fixed 覆盖层后，
 * 首屏内容必须自己从顶栏下沿开始，否则会直接压在标题上。
 *
 * **高度必须用运行时实测的状态栏高度，不能读 CSS 变量**：小程序端 uni-app 注入的
 * `page{--status-bar-height:25px}` 是写死的 25px（见 `@dcloudio/uni-mp-vite` 的
 * cssVars），与设备无关；刘海屏 / 灵动岛真机是 44~59px，按 25px 占位会短 20~35px，
 * 首行卡片顶到导航栏上（H5 该变量为 0，恰好等于真实值，所以浏览器里看不出来）。
 */

/**
 * 导航条内容区高度。
 *
 * 与 wd-navbar（`hr-navbar` 内部）的占位口径一致：真实状态栏高度 + 44px；
 * wd-navbar 的 placeholder 实测高度也是这个值。
 */
export const NAVBAR_CONTENT_HEIGHT = 44

/**
 * 占位高度 = 真实状态栏高度 + 44px 内容区。
 *
 * @param statusBarHeight `uni.getWindowInfo().statusBarHeight`（px）；取不到时按 0 处理
 */
export function resolveNavbarSpaceHeight(statusBarHeight?: null | number): number {
  const measured
    = typeof statusBarHeight === 'number' && Number.isFinite(statusBarHeight)
      ? statusBarHeight
      : 0
  return Math.max(measured, 0) + NAVBAR_CONTENT_HEIGHT
}
