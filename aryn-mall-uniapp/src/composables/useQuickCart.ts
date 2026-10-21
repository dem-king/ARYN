import type { SharedCart } from '@/api/order/sharedCart'
import type { QuickAddPromotion, QuickCartInfo } from '@/utils/quick-cart'

/**
 * 快捷加购 composable。
 *
 * 列表/推荐位的商品卡片没有 SKU 明细，点击加购时：
 * 1. 未登录 → 直接引导登录（加购接口需要 token，本地先拦截避免一次 401 跳转）
 * 2. 按需查询 `/product/app/goodsspu/quick-cart/{id}`
 * 3. 单规格 → 按 MOQ/步长算出合法数量，经 useCartDestination 弹出数量确认
 *    （有进行中的共享购物车时，数量与去向在同一次确认里选）
 * 4. 多规格 → 抛出「需选择规格」，由调用方打开 SKU 弹层
 *
 * 商品详情页与列表页共用同一份规则，避免两处数量校验漂移。
 */
import { ref } from 'vue'

import { getQuickCartInfo } from '@/api/product/spu'
import { useCartDestination } from '@/composables/useCartDestination'
import { useAuthStore } from '@/store/authStore'
import {
  QUICK_CART_MODE_DIRECT,
  QUICK_CART_MODE_UNAVAILABLE,
  quickAddBlockedText,
  resolveQuantityRule,
  resolveQuickAddMode,
  resolveQuickAddPrice,
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

export interface QuickAddOptions {
  /**
   * 页面已解析出的活动成交价（新人价 / 秒杀价）：确认弹层用它展示单价，
   * 并按活动限购封顶数量。只在 quick-cart 选中的 SKU 与活动 SKU 一致时生效。
   */
  promotion?: QuickAddPromotion | null
  /**
   * 共享购物车分类选货页传入：目的地锁定到该车，跳过去向询问，
   * 避免同一页里的加购被误投进个人购物车。
   */
  sharedCart?: SharedCart | null
}

export function useQuickCart() {
  const authStore = useAuthStore()
  const { submitCartAdd } = useCartDestination()
  const pending = ref(false)

  function goLogin() {
    uni.navigateTo({ url: '/pages/login/index' })
  }

  /**
   * 执行快捷加购。
   *
   * @param spuId 商品 SPU ID
   * @param options.promotion 页面已知的活动价（新人价/秒杀价），仅用于确认弹层展示与限购封顶
   * @returns 加购结果；需要选规格时由调用方接管后续交互
   */
  async function quickAdd(spuId: string, options: QuickAddOptions = {}): Promise<QuickCartResult> {
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

      const rule = resolveQuantityRule(info)
      const pricing = resolveQuickAddPrice(info, options.promotion)
      // 限购件数低于起订量时，任何合法数量都享不到活动价：不展示活动价也不封顶，
      // 交给弹层按常规商品处理（楼层入口已提前把这种冲突挡到商详）
      const promotionUsable = pricing.maxQuantity === null || pricing.maxQuantity >= rule.moq
      const finalPricing = promotionUsable
        ? pricing
        : { unitPrice: info.salesPrice, maxQuantity: null, hint: '' }
      const finalQuantity = finalPricing.maxQuantity === null
        ? quantity
        : Math.min(quantity, finalPricing.maxQuantity)

      // 去向与数量由统一入口决定：数量按 MOQ/步长预填并允许用户改大
      // （采购量大时一次输入到位，不必反复点击加购）；有进行中的共享车时
      // 在同一次确认里选择加入哪张车。skuId 已由 DIRECT 分支保证存在。
      const dest = await submitCartAdd(
        {
          skuId: String(info.skuId),
          quantity: finalQuantity,
          spuId,
          goodsName: info.name,
          unitPrice: finalPricing.unitPrice,
          maxQuantity: finalPricing.maxQuantity,
          quantityHint: finalPricing.hint,
        },
        { rule, sharedCart: options.sharedCart },
      )
      if (dest === 'abort') {
        // 用户关闭了确认弹层，本次不加
        return { added: false, needChoose: false, info }
      }
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
