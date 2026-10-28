/**
 * 限时折扣活动价的展示口径（纯函数，便于单测）。
 *
 * 背景：折扣会场按活动展示折后价（8 折 ¥23.92），但商详 / 购物车 / 快捷加购
 * 确认弹层拿到的是 SKU 当前售价（¥29.90）。用户在会场看到 ¥23.92，点进商详
 * 却标 ¥29.90，加购弹层小计也是 ¥29.90 —— 「会场标折扣价、结算按原价」的
 * 价差观感来源。后端 `/promotion/app/discount/prices`（免登批量）下发每个 SKU
 * 命中的最优折扣后，各页面统一按这里的规则决定「主价切折后价 / 保持原价 / 角标」，
 * 避免商详、购物车、加购弹层各写一遍判定而漂移。
 *
 * 优先级与后端 `OrderPriceComputeService.resolvePromotionPricePlan` 一致：
 * **拼团 > 秒杀 > 会员价 > 新人价 > 限时折扣 > 原价**。拼团单价格由后端按拼团
 * 记录重算、商详/Cart 不参与展示，故前端展示层只覆盖「秒杀 > 会员价 > 新人价
 * > 限时折扣」四层；会员价/新人价的判定各自在 `member-price.ts` / `newbie-price.ts`，
 * 由 `resolvePromotionPriority`（member-price.ts）统一收敛后传入本工具产出的 display。
 */

import type { AppDiscountVO } from '@/api/promotion'

import { translate } from '@/locales'

/** 金额文案：最多两位小数并去掉无意义的尾零（4.00 → 4、4.5 → 4.5）。 */
export function formatStripAmount(value: unknown): string {
  const amount = Number(value)
  if (!Number.isFinite(amount))
    return '0'
  return String(Math.round(amount * 100) / 100)
}

/**
 * 折扣力度文案，与折扣会场页/首页活动入口条同口径。
 *
 * 打折：`Math.round(discountValue * 100) / 10` —— 0.95 → 9.5折、0.8 → 8折；
 * 力度 ≥10 折没有「省」的信息量，退化为中性「折扣」，角标永不出现「10折」。
 * 减价 / 固定价沿用后端语义：`减¥X` / `一口价`。
 */
export function formatDiscountValue(
  activity: { discountType?: unknown, discountValue?: unknown },
): string {
  switch (Number(activity.discountType)) {
    case 1: {
      const zhe = Math.round(Number(activity.discountValue) * 100) / 10
      if (!Number.isFinite(zhe) || zhe <= 0 || zhe >= 10)
        return translate('labels.discount')
      return translate('labels.discountRate', {
        value: Number.isInteger(zhe) ? zhe : zhe.toFixed(1),
        // 中文按「8折」表达，英文按「20% OFF」表达，同一折扣力度两种口径
        percentOff: Math.round((10 - zhe) * 10),
      })
    }
    case 2:
      return translate('labels.saveAmount', { amount: formatStripAmount(activity.discountValue) })
    case 3:
      return translate('labels.fixedPrice')
    default:
      return translate('labels.discount')
  }
}

/** 当前 SKU 的限时折扣展示结果 */
export interface DiscountPriceDisplay {
  /** 是否命中限时折扣活动（该 SKU 在活动范围内） */
  matched: boolean
  /** 主价是否应切为折后价 */
  showDiscountPrice: boolean
  /** 折后价；未命中/无优惠时为 null */
  discountPrice: number | null
  /** 划线价（活动基准价 = SKU 当前售价）；无意义划线时为 null */
  originalPrice: number | null
  /** 角标文案：8折 / 9.5折 / 减¥5 / 一口价 */
  badgeText: string
  /** 活动名称（商详说明条用） */
  activityName: string
  /** 活动 ID（用于防重复渲染/埋点） */
  activityId: string
}

function toFiniteNumber(value: unknown): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/** 未命中时的空展示结果（调用方直接沿用商品原价，无需判空） */
export function emptyDiscountDisplay(): DiscountPriceDisplay {
  return {
    activityId: '',
    activityName: '',
    badgeText: '',
    discountPrice: null,
    matched: false,
    originalPrice: null,
    showDiscountPrice: false,
  }
}

/**
 * 命中的折扣商品表：skuId → 最优折扣项。
 *
 * 页面一次请求带上多个 SKU（商详多规格、购物车整车），逐条 find 是 O(n²)；
 * 同一 SKU 在响应里重复时后者覆盖前者，行为确定。
 */
export function discountPriceMap(
  list: AppDiscountVO | AppDiscountVO[] | null | undefined,
): Map<string, AppDiscountVO> {
  const map = new Map<string, AppDiscountVO>()
  const items = Array.isArray(list) ? list : list ? [list] : []
  for (const item of items) {
    const skuId = String(item?.skuId ?? '').trim()
    if (!skuId)
      continue
    map.set(skuId, item)
  }
  return map
}

/**
 * 组装某个 SKU 的限时折扣展示结果。
 *
 * @param source 批量折扣价接口返回（可为 null：无活动 / 接口失败）
 * @param skuId 当前页面聚焦的 SKU；购物车 / 商详都必须按 SKU 精确匹配
 *              （同一 SPU 下不同规格可能只有部分参与活动）
 */
export function resolveDiscountPriceDisplay(
  source: AppDiscountVO | AppDiscountVO[] | null | undefined,
  skuId: unknown,
): DiscountPriceDisplay {
  const empty = emptyDiscountDisplay()
  const target = String(skuId ?? '').trim()
  if (!source || !target)
    return empty
  const hit = discountPriceMap(source).get(target)
  if (!hit)
    return empty

  const activityId = String(hit.activityId ?? '')
  const activityName = String(hit.activityName ?? '')
  const badgeText = formatDiscountValue(hit)
  const originalPrice = toFiniteNumber(hit.originalPrice)
  const discountPrice = toFiniteNumber(hit.discountPrice)
  // 折后价必须真的低于基准价：接口兜底或活动配置异常（折后 ≥ 原价）时
  // 不切换主价、不划线 —— 自己划自己/反向「优惠」比不展示更糟。
  if (originalPrice === null || discountPrice === null
    || originalPrice <= 0 || discountPrice <= 0
    || discountPrice >= originalPrice) {
    return {
      ...empty,
      activityId,
      activityName,
      badgeText,
      matched: true,
    }
  }
  return {
    activityId,
    activityName,
    badgeText,
    discountPrice,
    matched: true,
    originalPrice,
    showDiscountPrice: true,
  }
}

/**
 * 该 SKU 在「无秒杀、无会员价、无新人价」时的实际成交单价：命中折扣时取折后价，
 * 否则回落售价。
 *
 * 购物车合计、商详主价、加购确认弹层小计都走这里，保证「标价 × 数量 = 合计」
 * 与页面标价一致。秒杀/会员价/新人价有自己的优先级判定，调用方先判它们再落到本函数。
 */
export function discountUnitPrice(
  fallbackPrice: unknown,
  display: DiscountPriceDisplay,
): number {
  if (display.showDiscountPrice && display.discountPrice !== null)
    return display.discountPrice
  const fallback = toFiniteNumber(fallbackPrice)
  return fallback === null || fallback < 0 ? 0 : fallback
}

/** 「省 ¥x」：有划线价才展示；无优惠时返回空串（不造「省¥0」） */
export function discountSavingAmount(display: DiscountPriceDisplay): string {
  if (!display.showDiscountPrice || display.discountPrice === null || display.originalPrice === null)
    return ''
  const diff = display.originalPrice - display.discountPrice
  return diff > 0 ? diff.toFixed(2) : ''
}
