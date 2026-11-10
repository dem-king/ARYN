/**
 * 新人专享商品的展示价与「抢」加购统一口径（首页楼层 / 新人专享页共用）。
 *
 * 价格优先级复用列表卡的 `resolveListZonePrice`：会员价、出清价与新人价取低，
 * 新人价严格更低才覆盖身份价。展示与加购必须同源，否则会出现
 * 「卡片标新人价、确认弹层按原价」或「会员价商品被新人限购拦住」。
 *
 * 与 `zone.cardPriceOf` 的差异：这里把活动下发的新人价**显式**传给判定，
 * 不依赖 card-tags 的 newbiePrice。card-tags 是给普通商品流补标签用的，
 * 命中时无法区分「新人价」与身份价，会把新人限购误判成身份价而跳过。
 */
import type { ListZonePrice } from '@/utils/zone-price'
import { useI18n } from 'vue-i18n'
import { getQuickCartInfo } from '@/api/product/spu'
import { useQuickCart } from '@/composables/useQuickCart'
import { useZonePrices } from '@/composables/useZonePrices'
import { useAuthStore } from '@/store/authStore'
import {
  QUICK_CART_MODE_DIRECT,
  resolveQuickAddMode,
  resolveQuickAddQuantity,
} from '@/utils/quick-cart'
import { resolveListZonePrice, zonePriceBadgeText } from '@/utils/zone-price'

/** 楼层/专享页需要的最小商品字段（活动接口的商品项结构兼容） */
export interface NewbieActionGoods {
  newbiePrice?: number
  originalPrice?: number
  skuId?: string
  spuId?: string
}

export interface NewbieGoodsActionsOptions {
  /** 活动级「每人每款限购件数」；取不到时按 1 件兜底 */
  limitPerUser: () => number
  /** 多规格/活动 SKU 对不上时回退商详 */
  openGoods: (spuId: string) => void
}

export function useNewbieGoodsActions(options: NewbieGoodsActionsOptions) {
  const { t } = useI18n()
  const zone = useZonePrices()
  const { pending, quickAdd } = useQuickCart()
  const authStore = useAuthStore()

  /** 补拉会员价/出清价/秒杀标签（失败静默，卡片先按活动价渲染） */
  function loadZonePrices(list: NewbieActionGoods[]) {
    const ids = list.map(goods => String(goods?.spuId || '').trim()).filter(Boolean)
    if (!ids.length)
      return
    void zone.loadForSpuIds(ids)
  }

  /**
   * 卡片成交价展示。
   *
   * 新人价取活动配置价（活动 SKU 的真实成交价），秒杀/会员/出清价取专区补价结果；
   * 新人价命中但未严格更低时回落当前售价，不把原价标成促销价。
   */
  function cardDisplay(goods: NewbieActionGoods): ListZonePrice {
    const sales = Number(goods?.originalPrice) || 0
    const spuId = String(goods?.spuId || '').trim()
    const tags = zone.cardTagOf(spuId)
    const newbiePrice = Number(goods?.newbiePrice)
    return resolveListZonePrice({
      salesPrice: sales,
      originalPrice: sales,
      showOriginal: true,
      newbiePrice: Number.isFinite(newbiePrice) && newbiePrice > 0
        ? newbiePrice
        : tags?.newbiePrice,
      memberDisplay: zone.memberDisplayOf(spuId, sales),
      clearanceDisplay: zone.clearanceDisplayOf(spuId, sales),
      seckillPrice: tags?.seckillPrice,
    })
  }

  /** 会员价/出清价命中：成交价已是身份价，新人限购不适用 */
  function identityPriced(display: ListZonePrice) {
    return display.badgeKind === 'member' || display.badgeKind === 'clearance'
  }

  /** 该活动 SKU 的身份成交价（会员价/出清价取低后的 SKU 级价格）；无命中返回 null */
  function skuZonePrice(goods: NewbieActionGoods): number | null {
    const spuId = String(goods?.spuId || '').trim()
    const skuId = String(goods?.skuId || '').trim()
    if (!spuId || !skuId)
      return null
    return zone.skuZonePriceOf(spuId, skuId)
  }

  /** 卡片主价：身份价命中时用 SKU 级成交价，其余用专区判定结果（新人价/售价） */
  function cardMainPrice(goods: NewbieActionGoods): number {
    const display = cardDisplay(goods)
    if (identityPriced(display))
      return skuZonePrice(goods) ?? display.mainPrice
    return display.mainPrice
  }

  /** 卡片划线价；null=不划线 */
  function cardStrikePrice(goods: NewbieActionGoods): number | null {
    return cardDisplay(goods).strikePrice
  }

  /** 身份价角标（会员价/捡漏价/秒杀价）；未命中返回空串 */
  function cardZoneBadgeText(goods: NewbieActionGoods): string {
    return zonePriceBadgeText(cardDisplay(goods).badgeKind)
  }

  /** 角标文案：命中专区价时显示对应身份，否则维持「新人价」 */
  function cardBadgeText(goods: NewbieActionGoods): string {
    return cardZoneBadgeText(goods) || t('diy.newbieFloor.priceBadge')
  }

  /** 省额：划线价 - 主价（按最终成交价重算，弹层/卡片口径一致） */
  function savedAmount(goods: NewbieActionGoods): number {
    const strike = cardStrikePrice(goods)
    const main = cardMainPrice(goods)
    return strike !== null && main < strike ? strike - main : 0
  }

  function limitPerUser(): number {
    const limit = Math.floor(Number(options.limitPerUser()))
    return Number.isFinite(limit) && limit > 0 ? limit : 1
  }

  /**
   * 「抢」= 直接加购。
   *
   * 价格与限购必须与卡片同一口径：
   * - 会员价/出清价命中：按身份价加购，不限新人件数；
   * - 新人价命中：按活动价加购，首件享新人价、超出件由后端按销售价拆行结算
   *   （2026-11-08 放宽，不再封顶数量）；
   * - 秒杀/价格未命中：不伪造活动价，回商详处理（秒杀有独立购买链路）。
   *
   * 活动 SKU 与 quick-cart 选中的 SKU 不一致时回商详：后者按库存挑 SKU，
   * 两者不是同一行会「看到 A 的价、入车却是 B」，价差要到结算才暴露。
   */
  async function handleGrab(goods: NewbieActionGoods) {
    const spuId = String(goods?.spuId || '')
    if (!spuId)
      return
    if (pending.value)
      return

    const display = cardDisplay(goods)
    const identity = identityPriced(display)
    const newbie = display.badgeKind === 'newbie'
    const activitySkuId = String(goods?.skuId || '')

    // 秒杀价有独立购买链路，按新人价加购会造成价差；直接回商详
    if (display.badgeKind === 'seckill') {
      options.openGoods(spuId)
      return
    }

    // 未登录时 quickAdd 会统一引导登录并记录回跳，无需预查询
    if (authStore.isLoggedIn) {
      const limit = limitPerUser()
      try {
        const info = await getQuickCartInfo(spuId)
        if (resolveQuickAddMode(info) === QUICK_CART_MODE_DIRECT) {
          if (activitySkuId && String(info?.skuId || '') !== activitySkuId) {
            options.openGoods(spuId)
            return
          }
          // 新人限购与起订量/步长冲突：任何合规数量都享不到新人价，回商详
          if (newbie && resolveQuickAddQuantity(info) > limit) {
            uni.showToast({ title: t('diy.newbieFloor.limitToast', { limit }), icon: 'none' })
            options.openGoods(spuId)
            return
          }
        }
      }
      catch {
        // 预查询失败不拦：quickAdd 会再取一次，异常由统一拦截器提示
      }
    }

    const promotion = identity
      ? {
          skuId: activitySkuId,
          unitPrice: cardMainPrice(goods),
          maxQuantity: null,
          hint: zonePriceBadgeText(display.badgeKind),
        }
      : newbie
        ? {
            skuId: activitySkuId,
            unitPrice: Number(goods?.newbiePrice) || 0,
            maxQuantity: limitPerUser(),
            // 超出件保留身份价（会员/出清被取低替换时），与后端拆行结算同口径；无身份价回落售价
            originalPrice: skuZonePrice(goods),
            hint: t('diy.newbieFloor.limitToast', { limit: limitPerUser() }),
          }
        : null

    const result = await quickAdd(spuId, promotion ? { promotion } : {})
    if (result.needChoose) {
      uni.showToast({ title: t('diy.newbieFloor.selectSpecsToast'), icon: 'none' })
      options.openGoods(spuId)
    }
  }

  return {
    cardBadgeText,
    cardDisplay,
    cardMainPrice,
    cardStrikePrice,
    cardZoneBadgeText,
    handleGrab,
    limitPerUser,
    loadZonePrices,
    pending,
    savedAmount,
  }
}
