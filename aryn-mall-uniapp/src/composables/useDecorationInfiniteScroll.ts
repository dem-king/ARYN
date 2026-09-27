import { onPageScroll, onReachBottom } from '@dcloudio/uni-app'

/**
 * 装修页（整页 DIY）无限滚动：把「页面滚动接近底部」转发给装修里的分页商品组件
 * （goods-group / goods-waterfall），触发 z-paging 提前加载下一页。
 *
 * 只靠页面 onReachBottom 兜不住无感加载：它要到滚动到绝对底部
 * （onReachBottomDistance，默认 50px）才触发，用户必然先看到底部的加载占位。
 * 这里改为在 onPageScroll 里按固定间隔测量「距页面底部的距离」，
 * 进入预加载区就提前派发事件；z-paging 侧由自身 loading 状态天然去重，
 * 加载完成后内容变高、距离拉大，不会连环请求。
 */
export const DIY_REACH_BOTTOM_EVENT = 'diy-reach-bottom'

/** 距页面底部多少 px 内就提前加载下一页（约一屏高，滚动过程中完成续页） */
const PRELOAD_DISTANCE_PX = 900
/** 距离检测节流间隔（ms）：onPageScroll 高频触发，节点查询不能每次滚动都做 */
const CHECK_INTERVAL_MS = 200

export function useDecorationInfiniteScroll() {
  let lastCheckAt = 0

  function checkNearBottom() {
    const now = Date.now()
    if (now - lastCheckAt < CHECK_INTERVAL_MS)
      return
    lastCheckAt = now
    uni
      .createSelectorQuery()
      .selectViewport()
      .scrollOffset((raw) => {
        const rect = (Array.isArray(raw) ? raw[0] : raw) as
          | { scrollTop: number, scrollHeight: number }
          | undefined
        if (!rect)
          return
        const { windowHeight } = uni.getSystemInfoSync()
        const distance = rect.scrollHeight - rect.scrollTop - windowHeight
        if (distance <= PRELOAD_DISTANCE_PX)
          uni.$emit(DIY_REACH_BOTTOM_EVENT)
      })
      .exec()
  }

  // onPageScroll 持续监测提前预加载；onReachBottom 兜底（滚动过快漏检时框架事件仍会触发）
  onPageScroll(checkNearBottom)
  onReachBottom(() => uni.$emit(DIY_REACH_BOTTOM_EVENT))
}
