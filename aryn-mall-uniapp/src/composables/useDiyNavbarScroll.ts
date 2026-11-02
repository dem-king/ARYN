import type { InjectionKey, ShallowRef } from 'vue'

import { onHide, onShow } from '@dcloudio/uni-app'
import { onBeforeUnmount, provide, shallowRef } from 'vue'

/**
 * 装修页「沉浸式导航」的页面侧接线。
 *
 * 导航材质是**一步切换**的：页面在顶部时保持起始外观，一旦开始滚动就立即变成
 * 目标底色（磨砂白 / 主题色 / 自定义色 + 毛玻璃），回到顶部立即还原；没有
 * 「透明 → 模糊」的过渡过程。因此这里只暴露一个「是否已滚动」的布尔状态：
 *   · 页面只负责把滚动状态写进 provide 出去的 shallowRef；
 *   · `hr-navbar` / `diy-search-bar` 各自 inject 需要的 ref，并在自身 computed
 *     里消费 —— 只有「顶部 ↔ 已滚动」翻转时才重渲染头部子树，滚动过程中不产生
 *     任何更新，`DiyPage` 与全部装修楼层不参与；
 *   · 其余 30+ 业务页不调用本 composable，`inject` 拿到 null 视为未滚动。
 *
 * **为什么宿主页必须写 `onPageScroll(handlePageScroll)` 字面量**：
 * 微信小程序的页面选项由 `@dcloudio/uni-mp-vite` 的 runtimeHooks 插件对**页面
 * SFC 源文本**做字符串匹配生成 `__runtimeHooks` 位标记，没有这个字面量时页面
 * 根本收不到滚动事件（H5 不受影响，所以浏览器里看不出问题）。把注册动作
 * 整个塞进本 composable 会静默失效，见 `useDecorationInfiniteScroll` 的前车之鉴。
 */

/** 页面滚动状态：false=顶部（起始外观）/ true=已滚动（目标外观） */
export const DIY_NAVBAR_SCROLL_KEY: InjectionKey<ShallowRef<boolean>>
  = Symbol('diy-navbar-scrolled')

/** 负值（iOS 回弹）按顶部处理：只有正滚动距离才算「已滚动」 */
function toScrolledState(value: number): boolean {
  return Math.max(0, value) > 0
}

/**
 * 滚动事件形态：页面 onPageScroll 是 `{ scrollTop }`；
 * scroll-view / z-paging 的 @scroll 是 `{ detail: { scrollTop } }`。
 */
export interface NavbarScrollEvent {
  scrollTop?: number
  detail?: { scrollTop?: number }
}

/** 两种事件形态归一化；脏值按 0（顶部）处理，避免把 NaN 写进状态 */
function readScrollTop(event: NavbarScrollEvent | undefined): number {
  const value = event?.scrollTop ?? event?.detail?.scrollTop ?? 0
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

/**
 * 切回本页后的视口校准时刻（ms）。
 *
 * 不能在 `onShow` 里立刻查：此刻本页往往还没真正露出（H5 实测仍是
 * `display:none`；小程序的露出时序端上不保证），视口读到的是上一页的滚动值
 * （通常是 0），会把仍停在原滚动位置的页面误判成「顶部」—— 「切 Tab 回来
 * 顶部效果消失」就是这么来的。页面露出并恢复滚动位置晚于 onShow，而恢复动作
 * 不保证派发 onPageScroll（H5 实测不派发），所以必须延后补读：第一次覆盖常规
 * 切换，第二次覆盖「页面内容重新渲染、视口高度变化后才恢复到位」的情况。
 */
const VIEWPORT_SYNC_DELAY_MS = [200, 800] as const

export function useDiyNavbarScroll() {
  const scrolled = shallowRef(false)
  let last = false
  let hidden = false
  let syncTimers: ReturnType<typeof setTimeout>[] = []

  function apply(next: boolean) {
    if (next === last)
      return
    last = next
    scrolled.value = next
  }

  /**
   * onPageScroll 处理器。
   *
   * 只在「顶部 ↔ 已滚动」翻转时赋值：滚动过程中状态不变，导航栏与常驻
   * 搜索栏都不重渲染（比逐帧写值省掉整条头部的重渲染）。
   * 页面隐藏期间的滚动（切 Tab 时窗口回顶）不属于本页，直接忽略。
   */
  function handlePageScroll(event: NavbarScrollEvent) {
    if (hidden)
      return
    apply(toScrolledState(readScrollTop(event)))
  }

  function clearViewportSync() {
    syncTimers.forEach(timer => clearTimeout(timer))
    syncTimers = []
  }

  /**
   * 读视口滚动距离校准状态；读不到（端上不支持 / 页面未露出）时保持现状。
   *
   * 只做单向补正：查到已滚动就补成已滚动，查到 0 不把状态打回顶部 —— 视口在
   * 页面尚未露出时读到的是上一页的滚动值（0），任何「0 即回顶部」的判定都会
   * 把仍停在原滚动位置的页面打回起始外观。回退到顶部由真实的 onPageScroll
   * （scrollTop=0）负责，那条路径不会误判。
   */
  function syncFromViewport() {
    if (hidden)
      return
    uni.createSelectorQuery()
      .selectViewport()
      .scrollOffset((result) => {
        if (hidden)
          return
        // viewport 查询只可能返回单个节点；数组分支是类型定义上的兜底
        const info = Array.isArray(result) ? result[0] : result
        const value = info?.scrollTop
        if (typeof value !== 'number' || !Number.isFinite(value))
          return
        if (toScrolledState(value))
          apply(true)
      })
      .exec()
  }

  onHide(() => {
    hidden = true
    clearViewportSync()
  })

  // 页面销毁后定时器不再有意义：留着会在下一个页面上白查一次视口
  onBeforeUnmount(clearViewportSync)

  onShow(() => {
    hidden = false
    clearViewportSync()
    // 补读只做「补成已滚动」的纠偏（见 syncFromViewport）：状态本来就对时
    // 查询结果与现状一致，不产生任何可见跳变
    VIEWPORT_SYNC_DELAY_MS.forEach(delay =>
      syncTimers.push(setTimeout(syncFromViewport, delay)),
    )
  })

  provide(DIY_NAVBAR_SCROLL_KEY, scrolled)
  return { handlePageScroll, scrolled }
}
