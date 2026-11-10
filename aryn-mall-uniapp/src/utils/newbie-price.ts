/**
 * 新人专享价的展示口径（纯函数，便于单测）。
 *
 * 背景：楼层（营销面，免登）固定展示活动价，但商详/购物车/结算各自拿到的是
 * SKU 当前售价 —— 用户从楼层点进商详看到原价，是「页面标 ¥0.01、结算按 ¥5.90」
 * 的价差观感来源。后端新增 `/promotion/app/newbie/prices`（可选登录）后，
 * 各页面都按这里的同一份规则决定「主价切新人价 / 保持原价 / 给出说明」，
 * 避免三处各写一遍判定而漂移。
 *
 * 三态资格（与后端 AppNewbiePriceVO.eligible 一一对应）：
 * - `true`  ：可享 → 主价用新人价，原价划线；
 * - `false` ：已登录且确定不符合 → **不展示新人价**，主价保持原价，并给出说明
 *              （这是「结算恢复原价并提示」在前端的落点）；
 * - `null`  ：未登录/未知 → 按可享展示（营销吸引），资格由下单链路硬校验。
 */

import type { AppNewbiePriceVO } from '@/api/promotion'

import { translate } from '@/locales'

/** 未命中新人价活动时的展示结果（调用方沿用商品自身售价） */
export interface NewbiePriceDisplay {
  /** 是否命中新人价活动（该 SKU 在活动配置里） */
  matched: boolean
  /** 主价是否应切为新人价 */
  showNewbiePrice: boolean
  /** 新人价；未命中/不展示时为 null */
  newbiePrice: number | null
  /** 划线原价；无意义的划线价时为 null */
  originalPrice: number | null
  /** 每人每 SKU 限购件数（后端固定 1） */
  limitPerUser: number
  /** 资格说明文案；仅在「确定不符合资格」时非空 */
  ineligibleText: string
  /** 活动名称（角标/说明用） */
  activityName: string
}

/** 新人价角标文案（楼层与商详同款口径） */
export const NEWBIE_BADGE_TEXT = '新人价'

/** 新人价资格的补充说明前缀（与楼层「新人首单专享价」一致） */
export const NEWBIE_PRICE_SUBTITLE = '新人首单专享价'

const ALREADY_PAID = 'ALREADY_PAID'
const REGISTER_TIMEOUT = 'REGISTER_TIMEOUT'

function toFiniteNumber(value: unknown): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function toPositiveLimit(value: unknown): number {
  const parsed = Math.floor(Number(value))
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 1
}

/** 未命中时的空展示结果（调用方直接沿用商品原价，无需判空） */
export function emptyNewbieDisplay(): NewbiePriceDisplay {
  return {
    activityName: '',
    ineligibleText: '',
    limitPerUser: 1,
    matched: false,
    newbiePrice: null,
    originalPrice: null,
    showNewbiePrice: false,
  }
}

/**
 * 资格不满足的原因 → 用户能看懂的一句话。
 *
 * 未知原因（含远程判定失败的 `ERROR`）返回空串：此时无法确认用户是不是新人，
 * 宁可静默按原价展示，也不能凭空告诉一个新用户「你没有资格」。
 */
export function newbieIneligibleText(
  reason: string | null | undefined,
  registerWithinDays?: number | null,
): string {
  switch (String(reason || '').trim()) {
    case ALREADY_PAID:
      return translate('labels.newbieAlreadyPaid')
    case REGISTER_TIMEOUT: {
      const days = toFiniteNumber(registerWithinDays)
      return days && days > 0
        ? translate('labels.newbieRegisterWithin', { days: Math.floor(days) })
        : translate('labels.newbieRegisterOnly')
    }
    default:
      return ''
  }
}

/**
 * 命中的新人价商品表：skuId → 商品价格项。
 *
 * 页面一次请求会带上多个 SKU（商详多规格、购物车整车），逐条 find 是 O(n²)，
 * 且同一 SKU 在响应里重复时后者覆盖前者，行为确定。
 */
export function newbieSkuPriceMap(
  vo: AppNewbiePriceVO | null | undefined,
): Map<string, { newbiePrice: number, originalPrice: number, spuId: string, skuId: string }> {
  const map = new Map<string, { newbiePrice: number, originalPrice: number, spuId: string, skuId: string }>()
  if (!vo?.items?.length)
    return map
  for (const item of vo.items) {
    const skuId = String(item?.skuId ?? '').trim()
    if (!skuId)
      continue
    const newbiePrice = toFiniteNumber(item.newbiePrice)
    const originalPrice = toFiniteNumber(item.originalPrice)
    if (newbiePrice === null || newbiePrice <= 0)
      continue
    map.set(skuId, {
      newbiePrice,
      originalPrice: originalPrice === null ? 0 : originalPrice,
      skuId,
      spuId: String(item.spuId ?? ''),
    })
  }
  return map
}

/**
 * 组装某个 SKU 的新人价展示结果。
 *
 * @param vo 后端返回的新人价（可为 null：无活动 / 接口失败）
 * @param skuId 当前页面聚焦的 SKU；购物车/商详都必须按 SKU 精确匹配
 *              （同一 SPU 下不同规格可能只有一个配了新人价）
 */
export function resolveNewbiePriceDisplay(
  vo: AppNewbiePriceVO | null | undefined,
  skuId: unknown,
): NewbiePriceDisplay {
  const empty = emptyNewbieDisplay()
  const target = String(skuId ?? '').trim()
  if (!vo || !target)
    return empty
  const hit = newbieSkuPriceMap(vo).get(target)
  if (!hit)
    return empty

  const limitPerUser = toPositiveLimit(vo.limitPerUser)
  const activityName = String(vo.activityName ?? '')
  // 资格：只有明确的 false 才是「确定不符合」；null 视为未知（按可享展示）
  const explicitlyIneligible = vo.eligible === false
  if (explicitlyIneligible) {
    return {
      activityName,
      ineligibleText: newbieIneligibleText(vo.ineligibleReason, vo.registerWithinDays),
      limitPerUser,
      matched: true,
      newbiePrice: hit.newbiePrice,
      // 划线价在「不展示新人价」时没有意义：原价就是主价
      originalPrice: null,
      showNewbiePrice: false,
    }
  }

  // 新人价不低于当前售价时不做切换：无优惠的「新人价」只是把原价换个标签，
  // 划线原价也会变成自己划自己（与 diy/price-display.ts 的 shouldShowOriginalPrice 同口径）
  const original = hit.originalPrice > 0 ? hit.originalPrice : null
  if (original !== null && hit.newbiePrice >= original) {
    return {
      ...empty,
      activityName,
      limitPerUser,
      matched: true,
    }
  }
  return {
    activityName,
    ineligibleText: '',
    limitPerUser,
    matched: true,
    newbiePrice: hit.newbiePrice,
    originalPrice: original,
    showNewbiePrice: true,
  }
}

/**
 * 该 SKU 的实际成交单价：可享新人价时取新人价，否则回落商品自身售价。
 *
 * 行单价展示（购物车/弹层主价）走这里；行小计/合计须用 {@link newbieLineSubtotal}，
 * 首件享新人价后小计不再等于单价 × 数量。
 */
export function effectiveUnitPrice(
  fallbackPrice: unknown,
  display: NewbiePriceDisplay,
): number {
  if (display.showNewbiePrice && display.newbiePrice !== null)
    return display.newbiePrice
  const fallback = toFiniteNumber(fallbackPrice)
  return fallback === null || fallback < 0 ? 0 : fallback
}

/** 限购提示文案（仅在可享新人价时出现；否则新人限购与用户无关） */
export function newbieLimitHint(display: NewbiePriceDisplay): string {
  if (!display.showNewbiePrice)
    return ''
  return translate('labels.newbieLimitHint', { count: toPositiveLimit(display.limitPerUser) })
}

/**
 * 行小计：首 `limitPerUser` 件按新人价、超出件按销售价（与后端拆行结算同一口径）。
 *
 * 2026-11-08 放宽限购后超出件不再被拦截，而是按替换前生效价正常成交（对齐小象超市
 * 「超过 1 盒按原价算」的混价模式）；身份价（会员/出清）被新人价取低替换时超出件保留
 * 身份价，调用方经 `overUnitPrice` 传入同一口径，未传时回落划线原价/售价。
 * 未命中新人价时回落 单价 × 数量。
 */
export function newbieLineSubtotal(
  fallbackPrice: unknown,
  display: NewbiePriceDisplay,
  quantity: unknown,
  overUnitPrice?: number | null,
): number {
  const parsed = Math.floor(Number(quantity))
  const count = Number.isFinite(parsed) && parsed > 0 ? parsed : 1
  const fallback = toFiniteNumber(fallbackPrice)
  const unit = fallback === null || fallback < 0 ? 0 : fallback
  if (!display.showNewbiePrice || display.newbiePrice === null)
    return round2(unit * count)
  const overUnit = resolveOverUnit(display, unit, overUnitPrice)
  return mixedLineSubtotal(display.newbiePrice, overUnit, toPositiveLimit(display.limitPerUser), count)
}

/**
 * 混价小计共享公式：首 limit 件按 unit、超出件按 overUnit（limit 空=全部按 unit）。
 * 购物车/加购弹层共用，避免两处各写一遍漂移（与后端拆行结算同口径）。
 */
export function mixedLineSubtotal(
  unit: number,
  overUnit: number,
  limit: number | null,
  count: number,
): number {
  const discounted = limit === null ? count : Math.min(limit, count)
  const over = count - discounted
  return round2(unit * discounted + overUnit * over)
}

/** 超出件单价：调用方传入（身份感知）优先，其次划线原价，最后回落行单价 */
function resolveOverUnit(
  display: NewbiePriceDisplay,
  unit: number,
  overUnitPrice?: number | null,
): number {
  const passed = toFiniteNumber(overUnitPrice)
  if (passed !== null && passed > 0)
    return passed
  return display.originalPrice !== null && display.originalPrice > 0
    ? display.originalPrice
    : unit
}

/**
 * 超出优惠件数后的说明文案（小象式：「超过 1 件，按 ¥5.9/件算」）。
 *
 * 数量未超限时空串（此时展示限购提示 {@link newbieLimitHint} 即可）；
 * 原价缺失时也返回空串，避免出现「按 ¥0/件算」的怪文案。
 */
export function newbieOverLimitHint(
  display: NewbiePriceDisplay,
  quantity: unknown,
  overUnitPrice?: number | null,
): string {
  if (!display.showNewbiePrice)
    return ''
  const count = toFiniteNumber(quantity)
  if (count === null)
    return ''
  const limit = toPositiveLimit(display.limitPerUser)
  if (count <= limit)
    return ''
  const passed = toFiniteNumber(overUnitPrice)
  const overUnit = passed !== null && passed > 0 ? passed : display.originalPrice
  if (overUnit === null || overUnit <= 0)
    return ''
  return translate('labels.newbieOverLimitHint', { count: limit, price: trimPrice(overUnit) })
}

export function trimPrice(value: number): string {
  return (Math.round(value * 100) / 100).toString()
}

function round2(value: number): number {
  return Math.round(value * 100) / 100
}

/** 页面级汇总：本次展示是否出现了新人价（决定整页提示条是否渲染） */
export function hasNewbiePrice(displays: Array<NewbiePriceDisplay | null | undefined>): boolean {
  return displays.some(display => display?.showNewbiePrice === true)
}

/**
 * 页面级汇总：是否出现了「确定不符合资格」的说明（结算页提示条用）。
 *
 * 与 hasNewbiePrice 分开判断：老用户页面同时存在「未命中的商品」与
 * 「命中了但不展示」的商品，只有后者需要解释价格为何不是楼层看到的活动价。
 */
export function newbieIneligibleNotice(
  displays: Array<NewbiePriceDisplay | null | undefined>,
): string {
  for (const display of displays) {
    if (display?.matched && !display.showNewbiePrice && display.ineligibleText)
      return display.ineligibleText
  }
  return ''
}
