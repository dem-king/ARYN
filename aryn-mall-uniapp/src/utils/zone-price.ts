/**
 * 商品列表卡片的「专区价」展示契约：会员价 / 捡漏价统一收敛。
 *
 * 背景：会员专区、低价捡漏专区展示了会员价/捡漏价，同一商品出现在其它页面
 * （首页其它 Tab、分类、搜索、收藏、足迹、推荐流……）时若只显示原售价，
 * 用户会看到同一个商品两种价格。各页面各写一遍判定必然漂移，因此把
 * 「会员价 vs 出清价 vs 原价」的卡片展示口径收敛到这里。
 *
 * **价格优先级（与后端 `OrderPriceComputeService` / 促销引擎一致）**：
 * 拼团 > 秒杀 > 会员价 / 出清价（同 SKU 取低，同价归属会员价）> 新人价 > 限时折扣 > 原价。
 * 列表卡片不承载拼团/秒杀（各自有会场），本函数只负责三类身份价：
 *
 * 1. 出清价胜出：主价=出清价，划线=当前售价，角标「捡漏价」；
 * 2. 会员价胜出：主价=会员价，划线=会员价展示的划线基准（缺省为当前售价），角标「会员价」；
 * 3. 都未命中：主价=当前售价；未达标/未登录时保留「会员专享 ¥x」引导文案，
 *    普通划线仍按「原价 > 售价」口径（与既有楼层一致）。
 *
 * 出清价与会员价取低而不是叠加，且同价时会员优先——与后端
 * 「同 SKU 两者都命中取低者、同价优先会员价」同一口径；展示与结算不漂移。
 */

import type { ClearancePriceDisplay } from './clearance-price'
import type { ListMemberPriceDisplay } from '../components/diy/retail-types'

import { translate } from '@/locales'

/** 角标类型：空串=无角标，由调用方映射成 i18n 文案 */
export type ZonePriceBadgeKind = '' | 'clearance' | 'member'

/** 列表卡片专区价展示结果（调用方直接按字段渲染） */
export interface ListZonePrice {
  /** 卡片主价（元） */
  mainPrice: number
  /** 划线价（元）；null=不划线 */
  strikePrice: number | null
  /** 角标类型 */
  badgeKind: ZonePriceBadgeKind
  /** 未达标/未登录的引导文案「会员专享 ¥x」；null=不展示 */
  guideText: string | null
}

export interface ListZonePriceInput {
  /** 卡片当前展示价（SPU salesPrice / 已归一化列表项的 price） */
  salesPrice: unknown
  /** 商品原价（普通划线用；仅在原价 > 售价时渲染） */
  originalPrice?: unknown
  /**
   * 是否允许渲染划线价：装修「显示原价」开关关闭时传 false。
   *
   * 只影响划线，不影响主价与角标——专区价本身是成交价，必须展示。
   */
  showOriginal?: boolean
  /** 会员价展示（列表口径：`resolveListMemberPrice` 的返回值） */
  memberDisplay?: ListMemberPriceDisplay | null
  /** 出清价展示（SPU 聚合口径，`resolveListClearanceDisplay` 的返回值） */
  clearanceDisplay?: ClearancePriceDisplay | null
}

function toFiniteNumber(value: unknown): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function roundToCent(value: number): number {
  return Math.round(value * 100) / 100
}

/** 有效成交价：正数且严格低于当前售价（倒挂/等价一律不展示） */
function validLowerPrice(value: unknown, salesPrice: number): number | null {
  const price = toFiniteNumber(value)
  if (price === null || price <= 0 || price >= salesPrice)
    return null
  return roundToCent(price)
}

/** 未命中时的空展示（主价回落到调用方传入的售价） */
export function emptyListZonePrice(salesPrice: unknown = 0): ListZonePrice {
  const sales = toFiniteNumber(salesPrice) ?? 0
  return {
    badgeKind: '',
    guideText: null,
    mainPrice: sales,
    strikePrice: null,
  }
}

/** 角标文案（会员价/捡漏价）；无角标返回空串 */
export function zonePriceBadgeText(kind: ZonePriceBadgeKind): string {
  if (kind === 'member')
    return translate('labels.memberPriceBadge') || '会员价'
  if (kind === 'clearance')
    return translate('product.clearancePrice') || '捡漏价'
  return ''
}

/**
 * 解析列表卡片的专区价展示。
 *
 * 入参都做防御校验：接口脏数据（非数值/0/负数/倒挂）一律视为未命中，
 * 绝不把原价标成会员价/捡漏价。
 */
export function resolveListZonePrice(input: ListZonePriceInput): ListZonePrice {
  const sales = toFiniteNumber(input.salesPrice)
  const showStrike = input.showOriginal !== false
  if (sales === null || sales <= 0)
    return emptyListZonePrice(sales ?? 0)

  const member = input.memberDisplay
  const memberPrice = member?.showMemberPrice
    ? validLowerPrice(member.memberPrice, sales)
    : null

  const clearance = input.clearanceDisplay
  const clearancePrice = clearance?.showClearancePrice
    ? validLowerPrice(clearance.clearancePrice, sales)
    : null

  // 出清价与会员价取低；同价归属会员价（与后端引擎一致）
  const clearanceWins = clearancePrice !== null
    && (memberPrice === null || clearancePrice < memberPrice)

  if (clearanceWins) {
    return {
      badgeKind: 'clearance',
      guideText: null,
      mainPrice: clearancePrice,
      strikePrice: showStrike ? roundToCent(sales) : null,
    }
  }

  if (memberPrice !== null) {
    const memberStrike = toFiniteNumber(member?.strikePrice)
    const strike = memberStrike !== null && memberStrike > memberPrice
      ? roundToCent(memberStrike)
      : roundToCent(sales)
    return {
      badgeKind: 'member',
      guideText: null,
      mainPrice: memberPrice,
      strikePrice: showStrike ? strike : null,
    }
  }

  // 未命中身份价：保持售价；普通划线遵循「原价 > 售价」的既有口径
  const original = toFiniteNumber(input.originalPrice)
  const strikePrice = showStrike && original !== null && original > sales
    ? roundToCent(original)
    : null
  const guide = member?.guideText
  return {
    badgeKind: '',
    guideText: guide && guide.trim() ? guide.trim() : null,
    mainPrice: sales,
    strikePrice,
  }
}
