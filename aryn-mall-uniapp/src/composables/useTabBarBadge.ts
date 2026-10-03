import { watch } from 'vue'
import { tabBar } from '@/pages.json'
import { useShoppingCartStore } from '@/store/shoppingCartStore'
import { formatCartBadgeText, resolveCartTabIndex } from '@/utils/cart-badge'

/**
 * 底部 tabBar 购物车角标：把 shoppingCartStore.cartCount 同步到原生 tabBar。
 *
 * 之前加购只有一闪而过的 toast，底部「购物车」tab 永远无角标，用户切 tab
 * 前对车里已有商品毫无感知。数量写入口（登录、分类页 onShow、购物车页变更、
 * 统一加购入口、下单后）已经全部落在 shoppingCartStore.cartCount 上，这里
 * watch 同一份状态即可，任何页面都不需要自己打角标。
 *
 * 挂载位置固定在根组件 `src/App.ku.vue`（全局一份）；tab 下标从 pages.json
 * 推导而非写死，见 utils/cart-badge.ts。
 */

/** H5 冷启动时 tabBar 晚于根组件就绪，setTabBarBadge 可能失败，按此间隔重试 */
const BADGE_RETRY_DELAY_MS = 1000
/** 连续失败重试上限：放弃后等下一次数量变化，按最新值重新走一遍 */
const BADGE_MAX_RETRIES = 3

export function useTabBarBadge() {
  const shoppingCartStore = useShoppingCartStore()

  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let retries = 0

  function applyBadge(count: number, isRetry = false) {
    // 数量变化触发的调用重置重试计数；重试自身触发的调用保留计数
    if (!isRetry) {
      if (retryTimer) {
        clearTimeout(retryTimer)
        retryTimer = null
      }
      retries = 0
    }

    const index = resolveCartTabIndex(tabBar?.list ?? [])
    if (index < 0) {
      return
    }

    const text = formatCartBadgeText(count)
    if (text === null) {
      // 数量为 0：「no badge」「not TabBar page」这类失败是常态，必须显式吞掉——
      // 小程序端 API 失败不传 fail 回调会走 App.onError 变成未捕获错误，而非
      // tabBar 页（配送端各页、商详页等）每次挂载根组件都会走到这里
      uni.removeTabBarBadge({
        index,
        fail: () => {},
      })
      return
    }

    uni.setTabBarBadge({
      index,
      text,
      fail: () => {
        if (retries >= BADGE_MAX_RETRIES) {
          return
        }
        retries += 1
        if (retryTimer) {
          return
        }
        retryTimer = setTimeout(() => {
          retryTimer = null
          // 重读 store：等待期间数量可能又变了（如用户登出清零）
          applyBadge(shoppingCartStore.cartCount, true)
        }, BADGE_RETRY_DELAY_MS)
      },
    })
  }

  // immediate：token 持久化恢复登录态后自动拉取数量，可能早于首个 tabBar 页
  // 渲染完成，启动时也要按当前值补一次角标。
  watch(() => shoppingCartStore.cartCount, count => applyBadge(count), { immediate: true })
}
