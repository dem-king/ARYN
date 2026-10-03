import type { Ref } from 'vue'

import { getCurrentInstance, onBeforeUnmount, onMounted } from 'vue'

import { DIY_REACH_BOTTOM_CHECK_EVENT } from './useDecorationInfiniteScroll'

/** 距视口底部多少 px 内就提前加载下一页（约一屏，滚动过程中完成续页） */
const PRELOAD_DISTANCE_PX = 900
/** 加载完成后自愈重测的延迟（ms）：等 z-paging 把新数据渲染出来再测，避免拿到旧布局 */
const SELF_HEAL_DELAY_MS = 200
/** 自愈触发前等待 z-paging 空闲的重试参数：z-paging 清内部 loading 晚于数据生效，触发太早会被丢弃 */
const BUSY_POLL_INTERVAL_MS = 120
const BUSY_POLL_MAX_ATTEMPTS = 8

/**
 * 分页商品楼层（goods-group / goods-waterfall）的自动续页。
 *
 * 页面滚动时收到检查广播后，测组件自身底边与视口底的距离：进入预加载区就调
 * z-paging 提前加载下一页。加载完成后若仍在预加载区（用户停在底部、或上一次
 * 触发恰好撞上加载中被丢弃）会继续自愈补页，直到底边被推离预加载区；z-paging
 * 判定没有更多时不再发出查询，链路自然终止。查询失败不自动续链，
 * 保留「加载失败，点击重新加载」的手动重试。
 *
 * `task` 里对 complete* 的调用需要 await，确保自愈重测发生在数据生效之后。
 */
export function useDiyPagedLoadMore(pagingRef: Ref<any>, rootSelector: string) {
  // getCurrentInstance 只能在 setup 同步阶段取，事件回调里拿到的是 null
  const instance = getCurrentInstance()
  let queryInFlight = false
  let disposed = false

  function scheduleLoad(attempt = 0) {
    if (queryInFlight || disposed)
      return
    // z-paging 内部清 loading 晚于数据生效（_refresherEnd 带 100ms+10ms 延迟），
    // 触发太早会被它的 loading 守卫静默丢弃，等它空闲后再触发
    if (pagingRef.value?.loading) {
      if (attempt < BUSY_POLL_MAX_ATTEMPTS)
        setTimeout(() => scheduleLoad(attempt + 1), BUSY_POLL_INTERVAL_MS)
      return
    }
    measureDistanceToViewportBottom((distance) => {
      if (disposed || distance > PRELOAD_DISTANCE_PX)
        return
      // z-paging 判定没有更多时这句话是 no-op（不会有查询发出），
      // 没有 runQuery 的 finally 就不会有下一次自愈，链路自然终止
      pagingRef.value?.pageReachBottom()
    })
  }

  function measureDistanceToViewportBottom(callback: (distance: number) => void) {
    uni
      .createSelectorQuery()
      .in(instance?.proxy ?? instance)
      .select(rootSelector)
      .boundingClientRect((raw) => {
        const rect = (Array.isArray(raw) ? raw[0] : raw) as { bottom?: number } | null
        // 节点拿不到（已卸载等）按「远离底部」处理；底边滚出视口上方的整层也不预加载
        if (!rect || typeof rect.bottom !== 'number' || rect.bottom < 0) {
          callback(Number.POSITIVE_INFINITY)
          return
        }
        const { windowHeight } = uni.getSystemInfoSync()
        callback(rect.bottom - windowHeight)
      })
      .exec()
  }

  /** 供 @query 处理器包装：标记查询生命周期，成功结束后自愈续页 */
  async function runQuery(task: () => Promise<void>) {
    queryInFlight = true
    let failed = false
    try {
      await task()
    }
    catch {
      failed = true
      // completeByError 的 promise 按 callNetworkReject 配置可能永远 pending，
      // 只触发不等待；错误视图由 z-paging 渲染为「加载失败，点击重新加载」
      const result = pagingRef.value?.completeByError('加载失败')
      result?.catch?.(() => {})
    }
    finally {
      queryInFlight = false
      if (!failed && !disposed)
        setTimeout(scheduleLoad, SELF_HEAL_DELAY_MS)
    }
  }

  onMounted(() => {
    uni.$on(DIY_REACH_BOTTOM_CHECK_EVENT, scheduleLoad)
    // 首屏列表不足一屏时页面可能根本没有滚动事件，挂载后主动补一次
    scheduleLoad()
  })
  onBeforeUnmount(() => {
    disposed = true
    uni.$off(DIY_REACH_BOTTOM_CHECK_EVENT, scheduleLoad)
  })

  return { runQuery }
}
