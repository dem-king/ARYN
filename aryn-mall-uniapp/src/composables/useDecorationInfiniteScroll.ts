import { onPageScroll, onReachBottom } from '@dcloudio/uni-app'
import { onUnmounted } from 'vue'

/**
 * 装修页（整页 DIY）无限滚动的页面侧接线。
 *
 * 页面只负责广播「有滚动活动」：onPageScroll 里限频广播，滚动停止后再兜底
 * 广播一次——快速滑动时最后一帧可能落在限频窗口内，用户停在底部附近却没有任何
 * 检查发生，必须往回滚一下才触发，就是这个洞。
 *
 * 「是否该翻页」由各分页商品组件自己测距决定（见 useDiyPagedLoadMore）：
 * 组件量自己的底边位置，不依赖 selectViewport scrollOffset 在各端字段
 * 不一致的问题（微信端 scrollOffset 不保证返回 scrollHeight）。
 */
export const DIY_REACH_BOTTOM_CHECK_EVENT = 'diy-reach-bottom-check'

/** 滚动过程中广播检查的限频间隔（ms） */
const CHECK_INTERVAL_MS = 200
/** 滚动停止后延迟兜底广播的时间（ms） */
const SETTLE_CHECK_DELAY_MS = 150

export function useDecorationInfiniteScroll() {
  let lastCheckAt = 0
  let settleTimer: ReturnType<typeof setTimeout> | undefined

  function broadcastCheck() {
    lastCheckAt = Date.now()
    uni.$emit(DIY_REACH_BOTTOM_CHECK_EVENT)
  }

  function handlePageScroll() {
    if (Date.now() - lastCheckAt >= CHECK_INTERVAL_MS)
      broadcastCheck()
    if (settleTimer)
      clearTimeout(settleTimer)
    settleTimer = setTimeout(broadcastCheck, SETTLE_CHECK_DELAY_MS)
  }

  onPageScroll(handlePageScroll)
  // 滚动到底的框架事件同样只当「该检查了」用，测距与去重都在组件侧
  onReachBottom(broadcastCheck)
  onUnmounted(() => {
    if (settleTimer)
      clearTimeout(settleTimer)
  })
}
