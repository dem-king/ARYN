/**
 * 阶梯价展示口径（商详 / 结算进度提示同源）。
 *
 * 阶梯价与数量相关：档位按 minQty 升序，件数达到某档才享该档单价。
 * 卡片不算阶梯价（数量未定）；商详按当前数量取档后与身份价家族取低，
 * 协议价锁定成交基价时阶梯价不覆盖（口径见 `utils/zone-price.ts`）。
 * 这里只做「取档 / 下一档」的纯计算，脏数据（非数值/非正数/乱序）一律防御。
 */
import type { LadderPriceItem, LadderTier } from '@/api/promotion/ladder-price'

/** 档位清洗：只保留正数 minQty/unitPrice，并按 minQty 升序（服务端已排序，前端再防御） */
function sanitizeTiers(tiers: LadderTier[] | null | undefined): LadderTier[] {
  const list: LadderTier[] = []
  for (const tier of tiers || []) {
    const minQty = Math.floor(Number(tier?.minQty))
    const unitPrice = Number(tier?.unitPrice)
    if (!Number.isFinite(minQty) || minQty <= 0 || !Number.isFinite(unitPrice) || unitPrice <= 0)
      continue
    list.push({ minQty, unitPrice })
  }
  return list.sort((a, b) => a.minQty - b.minQty)
}

/**
 * 某 SKU 的阶梯档位。
 *
 * 同 SKU 命中多个活动时取「档位更优」的一份（最低单价），与特供取低同口径；
 * 未命中/无有效档位返回空数组（调用方按无阶梯价处理）。
 */
export function ladderTiersOf(items: LadderPriceItem[] | null | undefined, skuId: string): LadderTier[] {
  const id = String(skuId ?? '').trim()
  if (!id)
    return []
  let best: LadderTier[] = []
  let bestPrice = Number.POSITIVE_INFINITY
  for (const item of items || []) {
    if (String(item?.skuId ?? '').trim() !== id)
      continue
    const tiers = sanitizeTiers(item?.ladders)
    if (!tiers.length)
      continue
    const lowest = tiers.reduce((min, tier) => Math.min(min, tier.unitPrice), Number.POSITIVE_INFINITY)
    if (lowest < bestPrice) {
      bestPrice = lowest
      best = tiers
    }
  }
  return best
}

/** 当前件数命中的阶梯单价（minQty ≤ 件数 的最高档）；未达首档/无档位返回 null */
export function resolveLadderUnitPrice(tiers: LadderTier[] | null | undefined, quantity: unknown): number | null {
  const qty = Math.floor(Number(quantity))
  if (!Number.isFinite(qty) || qty <= 0)
    return null
  let price: number | null = null
  for (const tier of sanitizeTiers(tiers)) {
    if (tier.minQty > qty)
      break
    price = tier.unitPrice
  }
  return price
}

/**
 * 下一档进度：再买 needQty 件可降至 unitPrice。
 *
 * 只返回「件数更高的最近一档」，是否真的更便宜由调用方与当前成交价比较后决定
 * （协议价/秒杀锁定时不展示阶梯进度）。已达最高档返回 null。
 */
export function nextLadderTier(
  tiers: LadderTier[] | null | undefined,
  quantity: unknown,
): { needQty: number, unitPrice: number } | null {
  const qty = Math.floor(Number(quantity))
  if (!Number.isFinite(qty) || qty <= 0)
    return null
  for (const tier of sanitizeTiers(tiers)) {
    if (tier.minQty > qty)
      return { needQty: tier.minQty - qty, unitPrice: tier.unitPrice }
  }
  return null
}

export function nextPurchasableLadderTier(
  tiers: LadderTier[],
  quantity: number,
  minimum: number,
  step: number,
  stock: number,
): { needQty: number, unitPrice: number } | null {
  const next = nextLadderTier(tiers, quantity)
  if (!next || !Number.isSafeInteger(quantity) || !Number.isSafeInteger(minimum) || minimum <= 0
    || !Number.isSafeInteger(step) || step <= 0 || quantity < minimum || (quantity - minimum) % step !== 0) {
    return null
  }

  const target = minimum + Math.ceil((quantity + next.needQty - minimum) / step) * step
  const unitPrice = resolveLadderUnitPrice(tiers, target)
  return target <= stock && unitPrice !== null ? { needQty: target - quantity, unitPrice } : null
}
