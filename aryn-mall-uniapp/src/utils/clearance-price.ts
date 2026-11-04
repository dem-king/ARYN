/**
 * 出清价展示契约（商详 / 购物车共用）。
 *
 * 后端 `/app/clearance/prices` 只返回「已打标、未到期、且真实低于当前售价」的 SKU，
 * 这里再做一次防御校验：过期或倒挂的数据绝不标价，避免把原价误说成捡漏价。
 */

export interface AppClearancePriceItem {
  skuId?: string
  /**
   * 按 `spuIds` 批量查价时下发：该 SKU 所属 SPU。
   *
   * 商品列表卡片只拿得到 SPU（列表接口不返回 SKU 明细），必须由 SKU 级价格
   * 聚合出「卡片价对应的最低有效出清价」；按 `skuIds` 查价时该字段为空。
   */
  spuId?: string
  salesPrice?: number | string
  clearancePrice?: number | string
  clearanceExpireTime?: string
}

export interface AppClearancePriceVO {
  items?: AppClearancePriceItem[]
}

export interface ClearancePriceDisplay {
  showClearancePrice: boolean
  clearancePrice: number | null
  /** 划线基准：SKU 当前售价 */
  originalPrice: number | null
  expireTime?: string
}

const EMPTY: ClearancePriceDisplay = {
  showClearancePrice: false,
  clearancePrice: null,
  originalPrice: null,
}

export function emptyClearancePriceDisplay(): ClearancePriceDisplay {
  return EMPTY
}

function toFiniteNumber(value: unknown): number | null {
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function isExpired(expireTime: unknown, now: number): boolean {
  if (typeof expireTime !== 'string' || !expireTime.trim())
    return false
  const time = new Date(expireTime.replace(' ', 'T')).getTime()
  return Number.isFinite(time) && !Number.isNaN(time) && time <= now
}

/**
 * 取指定 SKU 的有效出清价；接口未返回/已到期/倒挂时视为未命中。
 */
export function resolveClearancePriceDisplay(
  vo: AppClearancePriceVO | null | undefined,
  skuId: string,
  now: number = Date.now(),
): ClearancePriceDisplay {
  const id = String(skuId || '').trim()
  if (!id || !vo?.items?.length)
    return EMPTY
  const item = vo.items.find(entry => String(entry?.skuId || '').trim() === id)
  if (!item)
    return EMPTY
  const price = toFiniteNumber(item.clearancePrice)
  const sales = toFiniteNumber(item.salesPrice)
  if (price === null || price <= 0 || sales === null || price >= sales)
    return EMPTY
  if (isExpired(item.clearanceExpireTime, now))
    return EMPTY
  return {
    showClearancePrice: true,
    clearancePrice: price,
    originalPrice: sales,
    expireTime: item.clearanceExpireTime,
  }
}

/**
 * 身份价取优：出清价与会员价取低（同价归属会员价，与后端「同价优先会员价」一致）。
 *
 * @returns true 表示出清价胜出
 */
export function clearanceWinsOverMember(
  clearance: ClearancePriceDisplay,
  memberPrice: number | null,
): boolean {
  if (!clearance.showClearancePrice || clearance.clearancePrice === null)
    return false
  if (memberPrice === null || !Number.isFinite(memberPrice))
    return true
  return clearance.clearancePrice < memberPrice
}

/**
 * 按 spuId 分组 SKU 级出清价（按 spuIds 批量查价的响应）。
 *
 * 后端按「每个有效 SKU 一行」下发，卡片展示需要按 SPU 聚合；
 * 没有 spuId 的行（按 skuIds 查价的旧契约）直接忽略。
 */
export function clearanceItemsBySpuId(
  vo: AppClearancePriceVO | null | undefined,
): Map<string, AppClearancePriceItem[]> {
  const map = new Map<string, AppClearancePriceItem[]>()
  if (!vo?.items?.length)
    return map
  for (const item of vo.items) {
    const spuId = String(item?.spuId || '').trim()
    if (!spuId)
      continue
    const list = map.get(spuId) || []
    list.push(item)
    map.set(spuId, list)
  }
  return map
}

/** 卡片价与规格价比较的容差（价格两位小数，留半分钱浮点误差） */
const LIST_PRICE_TOLERANCE = 0.005

/**
 * 列表卡片的出清价展示（SPU 维度聚合）。
 *
 * 口径与低价捡漏专区 `clearancePage` 的 `finalSpuPrice` 一致：只统计
 * 「SKU 售价不高于卡片价」的有效规格，取其中最低出清价——卡片价是 SPU 聚合价，
 * 贵规格的出清价不能拿来标低价规格的卡片。
 *
 * @param vo        按 spuIds 批量查价的响应
 * @param spuId     卡片 SPU ID
 * @param cardPrice 卡片当前展示价（SPU salesPrice）
 * @param now       当前时间（到期校验，测试可注入）
 */
export function resolveListClearanceDisplay(
  vo: AppClearancePriceVO | null | undefined,
  spuId: string,
  cardPrice: unknown,
  now: number = Date.now(),
): ClearancePriceDisplay {
  const id = String(spuId || '').trim()
  if (!id || !vo?.items?.length)
    return EMPTY
  const card = toFiniteNumber(cardPrice)
  const cardValid = card !== null && card > 0
  let best: number | null = null
  let bestExpire: string | undefined
  for (const item of vo.items) {
    if (String(item?.spuId || '').trim() !== id)
      continue
    const price = toFiniteNumber(item.clearancePrice)
    const sales = toFiniteNumber(item.salesPrice)
    if (price === null || price <= 0 || sales === null || sales <= 0)
      continue
    // 倒挂（出清价不低于当前售价）不展示，避免「先涨后降」的假优惠
    if (price >= sales)
      continue
    if (isExpired(item.clearanceExpireTime, now))
      continue
    // 贵规格的出清价不能标到低价规格的卡片上（与专区列表 finalSpuPrice 一致）
    if (cardValid && sales > card + LIST_PRICE_TOLERANCE)
      continue
    if (best === null || price < best) {
      best = price
      bestExpire = item.clearanceExpireTime
    }
  }
  if (best === null)
    return EMPTY
  return {
    showClearancePrice: true,
    clearancePrice: best,
    originalPrice: cardValid ? Number(card.toFixed(2)) : null,
    expireTime: bestExpire,
  }
}

/**
 * SKU 级有效出清价表（供多规格弹层/快捷加购按 SKU 展示成交价）。
 *
 * 只收未到期、未倒挂的行；同一 SKU 多行时取最低价。
 */
export function clearanceSkuPriceMap(
  vo: AppClearancePriceVO | null | undefined,
  now: number = Date.now(),
): Map<string, number> {
  const map = new Map<string, number>()
  if (!vo?.items?.length)
    return map
  for (const item of vo.items) {
    const skuId = String(item?.skuId || '').trim()
    const price = toFiniteNumber(item.clearancePrice)
    const sales = toFiniteNumber(item.salesPrice)
    if (!skuId || price === null || price <= 0 || sales === null || price >= sales)
      continue
    if (isExpired(item.clearanceExpireTime, now))
      continue
    const current = map.get(skuId)
    if (current === undefined || price < current)
      map.set(skuId, price)
  }
  return map
}
