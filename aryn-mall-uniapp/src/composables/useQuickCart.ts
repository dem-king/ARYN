import type { QuickCartInfo } from '@/utils/quick-cart'

/**
 * 快捷加购 composable。
 *
 * 列表/推荐位的商品卡片没有 SKU 明细，点击加购时：
 * 1. 未登录 → 直接引导登录（加购接口需要 token，本地先拦截避免一次 401 跳转）
 * 2. 按需查询 `/product/app/goodsspu/quick-cart/{id}`
 * 3. 单规格 → 按 MOQ/步长算出合法数量直接加购
 * 4. 多规格 → 抛出「需选择规格」，由调用方打开 SKU 弹层
 *
 * 商品详情页与列表页共用同一份规则，避免两处数量校验漂移。
 */
import { ref } from 'vue'
import { addShoppingCart } from '@/api/order/shoppingCart'
import { getQuickCartInfo } from '@/api/product/spu'
import { useAuthStore } from '@/store/authStore'
import { useShoppingCartStore } from '@/store/shoppingCartStore'
import {
  QUICK_CART_MODE_DIRECT,
  QUICK_CART_MODE_UNAVAILABLE,
  quickAddBlockedText,
  resolveQuickAddMode,
  resolveQuickAddQuantity,
} from '@/utils/quick-cart'

export interface QuickCartResult {
  /** 已直接加购成功 */
  added: boolean
  /** 需要用户选择规格，调用方应打开 SKU 弹层 */
  needChoose: boolean
  /** 后端返回的加购信息（needChoose 时携带 goodsSkus） */
  info: QuickCartInfo | null
}

export function useQuickCart() {
  const authStore = useAuthStore()
  const shoppingCartStore = useShoppingCartStore()
  const pending = ref(false)

  function goLogin() {
    uni.navigateTo({ url: '/pages/login/index' })
  }

  /**
   * 执行快捷加购。
   *
   * @param spuId 商品 SPU ID
   * @returns 加购结果；需要选规格时由调用方接管后续交互
   */
  async function quickAdd(spuId: string): Promise<QuickCartResult> {
    if (!spuId) {
      return { added: false, needChoose: false, info: null }
    }
    // 加购接口要求登录：先本地拦截，避免 401 全局跳转丢失当前页面
    if (!authStore.isLoggedIn) {
      goLogin()
      return { added: false, needChoose: false, info: null }
    }
    // 同一商品的并发点击只发一次请求
    if (pending.value) {
      return { added: false, needChoose: false, info: null }
    }

    pending.value = true
    try {
      const info: QuickCartInfo = await getQuickCartInfo(spuId)
      const mode = resolveQuickAddMode(info)

      if (mode === QUICK_CART_MODE_UNAVAILABLE) {
        uni.showToast({ title: quickAddBlockedText(info), icon: 'none' })
        return { added: false, needChoose: false, info }
      }

      if (mode !== QUICK_CART_MODE_DIRECT) {
        return { added: false, needChoose: true, info }
      }

      const quantity = resolveQuickAddQuantity(info)
      if (quantity <= 0) {
        uni.showToast({ title: '库存不足', icon: 'none' })
        return { added: false, needChoose: false, info }
      }

      await addShoppingCart({ skuId: info.skuId, quantity })
      uni.showToast({ title: '已加入购物车', icon: 'success' })
      // 角标刷新失败不影响加购结果本身
      shoppingCartStore.fetchCartCount().catch(() => {})
      return { added: true, needChoose: false, info }
    }
    catch {
      // 请求异常已由统一拦截器提示，这里只保证不误报「已加入购物车」
      return { added: false, needChoose: false, info: null }
    }
    finally {
      pending.value = false
    }
  }

  return { pending, quickAdd }
}
