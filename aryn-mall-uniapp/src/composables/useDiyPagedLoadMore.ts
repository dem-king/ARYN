import type { Ref } from 'vue'

import { getCurrentInstance, onBeforeUnmount, onMounted } from 'vue'

import { translate } from '@/locales'

import { DIY_REACH_BOTTOM_CHECK_EVENT } from './useDecorationInfiniteScroll'

/** 距视口底部多少 px 内就提前加载下一页（约半屏；过大时会在用户看到楼层前连翻多页） */
const PRELOAD_DISTANCE_PX = 400
/** 加载完成后自愈重测的延迟（ms）：等 z-paging 把新数据渲染出来再测，避免拿到旧布局 */
const SELF_HEAL_DELAY_MS = 200
/** 自愈触发前等待 z-paging 空闲的重试参数：z-paging 清内部 loading 晚于数据生效，触发太早会被丢弃 */
const BUSY_POLL_INTERVAL_MS = 120
const BUSY_POLL_MAX_ATTEMPTS = 8

/**
 * 页面内分页商品流的自动续页（装修商品楼层 + 推荐商品流）。
 *
 * 调用方：装修楼层 `goods-group` / `goods-waterfall` / `goods-tabs`，以及宿主页
 * 推荐位 `waterfall-goods`（购物车/商详/个人中心/订单详情/支付结果）——后者虽不是
 * 装修组件，但同样是 `use-page-scroll` 的 z-paging，续页诉求完全一致，故共用。
 *
 * 页面滚动时收到检查广播后，测组件自身底边与视口底的距离：进入预加载区就调
 * z-paging 提前加载下一页。加载完成后若仍在预加载区（用户停在底部、或上一次
 * 触发恰好撞上加载中被丢弃）会继续自愈补页，直到底边被推离预加载区；z-paging
 * 判定没有更多时不再发出查询，链路自然终止。查询失败不自动续链，
 * 保留「加载失败，点击重新加载」的手动重试。
 *
 * `task` 里对 complete* 的调用需要 await，确保自愈重测发生在数据生效之后。
 */
export interface PagedLoadMoreOptions {
  /**
   * 挂载后是否立即测距补页（默认 true）。
   *
   * 装修楼层首屏可能不满一屏，靠「挂载即测距」补齐。但若 z-paging 的首次 reload
   * 由组件内部在 nextTick 触发、而组件首帧就落在视口预加载区内，挂载即触发会与
   * 首次 reload 竞态：先发出 pageNo=2，随后 reload 把 pageNo 重置回 1，出现
   * 「第 2 页请求先于第 1 页」。
   *
   * 这类组件传 false：既不在挂载时测距，也在首查（第 1 页）成功结束前忽略所有
   * 预加载广播，页 2 只会在页 1 成功返回后由自愈链路发出。
   */
  preloadOnMount?: boolean
}

export function useDiyPagedLoadMore(
  pagingRef: Ref<any>,
  rootSelector: string,
  options: PagedLoadMoreOptions = {},
) {
  const { preloadOnMount = true } = options
  // getCurrentInstance 只能在 setup 同步阶段取，事件回调里拿到的是 null
  const instance = getCurrentInstance()
  // 滚动广播是 uni.$emit 全局事件：记录所属页面，避免后台页面（如 tabbar 首页）
  // 响应前台 DIY 页的滚动而偷偷加载下一页
  const ownerPage = getCurrentPages().at(-1)
  let queryInFlight = 0
  let disposed = false
  /** 首次查询是否已成功结束：见 PagedLoadMoreOptions.preloadOnMount */
  let firstQuerySettled = false

  function ownedByActivePage() {
    return !ownerPage || getCurrentPages().at(-1) === ownerPage
  }

  function scheduleLoad(attempt = 0) {
    if (queryInFlight > 0 || disposed || !ownedByActivePage())
      return
    // 首查（第 1 页）成功前一律不预加载：此时 z-paging 的 pageNo 还停在 1，滚动广播若先到，
    // pageReachBottom 会先发出第 2 页，等首查返回又把列表重置回第 1 页（观察到的抢跑）。
    // 只约束 preloadOnMount=false 的组件；装修楼层保持原有「挂载即补页」行为。
    if (!preloadOnMount && !firstQuerySettled)
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
        const { windowHeight } = uni.getWindowInfo()
        callback(rect.bottom - windowHeight)
      })
      .exec()
  }

  /**
   * 供 @query 处理器包装：标记查询生命周期，成功结束后自愈续页。
   *
   * `isCurrent` 供多 Tab 商品流使用：快速切 Tab 后旧请求可能后返回，
   * 此时不得用旧结果结束新 Tab 的分页或渲染错误态。
   */
  async function runQuery(task: () => Promise<void>, isCurrent: () => boolean = () => true) {
    queryInFlight++
    let failed = false
    try {
      await task()
    }
    catch {
      failed = true
      if (!isCurrent())
        return
      // completeByError 的 promise 按 callNetworkReject 配置可能永远 pending，
      // 只触发不等待；错误视图由 z-paging 渲染为「加载失败，点击重新加载」
      const result = pagingRef.value?.completeByError(translate('diy.common.loadFailed'))
      result?.catch?.(() => {})
    }
    finally {
      queryInFlight = Math.max(0, queryInFlight - 1)
      if (!failed && !disposed && isCurrent()) {
        firstQuerySettled = true
        setTimeout(scheduleLoad, SELF_HEAL_DELAY_MS)
      }
    }
  }

  onMounted(() => {
    uni.$on(DIY_REACH_BOTTOM_CHECK_EVENT, scheduleLoad)
    // 首屏列表不足一屏时页面可能根本没有滚动事件，挂载后主动补一次；
    // 会与 z-paging 首次 reload 抢跑的宿主组件传 false（见 PagedLoadMoreOptions）
    if (preloadOnMount)
      scheduleLoad()
  })
  onBeforeUnmount(() => {
    disposed = true
    uni.$off(DIY_REACH_BOTTOM_CHECK_EVENT, scheduleLoad)
  })

  return { runQuery }
}
