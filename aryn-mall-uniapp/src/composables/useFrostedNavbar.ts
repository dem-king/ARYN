import type { InjectionKey } from 'vue'

import { provide } from 'vue'

import { useDiyNavbarScroll } from './useDiyNavbarScroll'

/**
 * 业务页「滚动 → 磨砂玻璃顶栏」的页面侧接线。
 *
 * 装修页（首页 / 落地页）的沉浸式导航由页面配置驱动，配置缺省是「滚动不变化」；
 * 其余 40+ 业务页不需要也不该出现在装修器里，但顶栏要与首页同一套玻璃材质。
 * 这里用一个独立的 provide 开关把两类页面分开：
 *   · 页面调用 useFrostedNavbar() → 开关为 true，hr-navbar / hr-search-navbar
 *     在未收到装修配置时自动套用 FROSTED_SCROLL_EFFECT / FROSTED_SCROLL_SURFACE；
 *   · 未调用的页面（含装修页）→ 开关缺省 false，渲染结果与改造前逐像素一致。
 *
 * 滚动状态本身复用 useDiyNavbarScroll：同一条「顶部 ↔ 已滚动」布尔状态、
 * 同一套切回本页的视口校准，避免两套状态机在同一个顶栏上打架。
 *
 * **宿主页必须把 handlePageScroll 接到真实滚动源上**：
 *   · 页面滚动页写 `onPageScroll(handlePageScroll)` 字面量（小程序编译期按页面
 *     源码字符串匹配生成滚动位标记，包进 composable 会静默收不到事件）；
 *   · 内部列表滚动页（z-paging / scroll-view）把 `handlePageScroll` 绑到
 *     列表的 @scroll 上，事件形态差异由 composable 内部归一化。
 */
export const FROSTED_NAVBAR_KEY: InjectionKey<boolean> = Symbol('frosted-navbar')

export function useFrostedNavbar() {
  const { handlePageScroll, scrolled } = useDiyNavbarScroll()
  provide(FROSTED_NAVBAR_KEY, true)
  return { handlePageScroll, scrolled }
}
