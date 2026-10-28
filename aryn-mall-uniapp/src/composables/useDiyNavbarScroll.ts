import type { InjectionKey, ShallowRef } from 'vue'

import { onShow } from '@dcloudio/uni-app'
import { provide, shallowRef } from 'vue'

/**
 * 装修页「导航栏滚动渐显」的页面侧接线。
 *
 * 分工（详见 docs/50-需求文档/2026-10-27-小程序沉浸式导航栏/README.md §4.5.2）：
 *   · 页面只负责把滚动距离写进 provide 出去的 shallowRef；
 *   · `hr-navbar` / `diy-search-bar` 各自 inject 需要的 ref，并在自身 computed 里
 *     消费 —— 滚动时只有头部子树重渲染，`DiyPage` 与全部装修楼层不参与；
 *   · 其余 30+ 业务页不调用本 composable，`inject` 拿到 null 视为 scrollTop=0。
 *
 * **为什么宿主页必须写 `onPageScroll(handlePageScroll)` 字面量**：
 * 微信小程序的页面选项由 `@dcloudio/uni-mp-vite` 的 runtimeHooks 插件对**页面
 * SFC 源文本**做字符串匹配生成 `__runtimeHooks` 位标记，没有这个字面量时页面
 * 根本收不到滚动事件（H5 不受影响，所以浏览器里看不出问题）。把注册动作
 * 整个塞进本 composable 会静默失效，见 `useDecorationInfiniteScroll` 的前车之鉴。
 */

/** 8px 量化的滚动值：导航栏渐显用，限制重渲染次数（低端机最敏感的是整条导航） */
export const DIY_NAVBAR_SCROLL_KEY: InjectionKey<ShallowRef<number>>
  = Symbol('diy-navbar-scroll')

/**
 * 逐帧原始滚动值（负值按 0 收敛，不做量化）。
 *
 * 给「常驻搜索栏」这类大面积纯色材质用：色块面积比导航栏大，8px 一档会看出台阶，
 * 且与导航栏的渐显进度不同步（用户反馈「比导航栏慢一步/不够顺滑」）。这里不做
 * CSS transition（时间过渡会额外滞后 ~180ms），而是让材质直接跟随 onPageScroll
 * 的每帧原始值——滚动手感与内容 1:1，量化台阶消失。消费方仍只有叶子组件，
 * DiyPage 与装修楼层不参与重渲染。
 */
export const DIY_NAVBAR_RAW_SCROLL_KEY: InjectionKey<ShallowRef<number>>
  = Symbol('diy-navbar-raw-scroll')

/**
 * 滚动量化步长（px）：140px 渐显全程最多约 18 次更新。
 * 微信 onPageScroll 每帧都触发，不量化会让导航每帧重渲染，低端机掉帧。
 */
const SCROLL_QUANTUM = 8

function quantizeScrollTop(value: number): number {
  // 负值（iOS 回弹）按 0 处理：渐显进度不允许出现负值
  return Math.round(Math.max(0, value) / SCROLL_QUANTUM) * SCROLL_QUANTUM
}

export function useDiyNavbarScroll() {
  const scrollTop = shallowRef(0)
  const rawScrollTop = shallowRef(0)
  let last = 0
  let lastRaw = 0

  /**
   * onPageScroll 处理器。
   *
   * 两个 ref 同源分频：原始值逐帧给「常驻搜索栏材质」，量化值给导航栏渐显。
   * 同值不赋值，避免无意义的响应式触发。
   */
  function handlePageScroll({ scrollTop: value }: { scrollTop: number }) {
    const raw = Math.max(0, value)
    if (raw !== lastRaw) {
      lastRaw = raw
      rawScrollTop.value = raw
    }
    const next = quantizeScrollTop(raw)
    if (next !== last) {
      last = next
      scrollTop.value = next
    }
  }

  /**
   * Tab 页切回、页面返回时不产生新的滚动事件，头部会停在上次滚到的状态
   * （或闪回顶部配色）。onShow 用 viewport 的 scrollOffset 补一次同步。
   * 回调是异步的：H5 / 部分端不支持 selectViewport 时不会触发，等下一次
   * onPageScroll 自然校正即可，不做重试。
   */
  onShow(() => {
    uni.createSelectorQuery()
      .selectViewport()
      .scrollOffset((result) => {
        // viewport 查询只可能返回单个节点；数组分支是类型定义上的兜底
        const info = Array.isArray(result) ? result[0] : result
        const value = Math.max(0, info?.scrollTop ?? 0)
        last = quantizeScrollTop(value)
        scrollTop.value = last
        lastRaw = value
        rawScrollTop.value = value
      })
      .exec()
  })

  provide(DIY_NAVBAR_SCROLL_KEY, scrollTop)
  provide(DIY_NAVBAR_RAW_SCROLL_KEY, rawScrollTop)
  return { handlePageScroll, rawScrollTop, scrollTop }
}
