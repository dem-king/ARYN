/**
 * 商详「订单优惠」展示模型：把后端逐活动下发的礼物提示收敛成可阅读的分档表。
 *
 * 后台习惯给同一场满额赠礼按档位建多条活动（「满额赠礼·椰子水（3千档）」、
 * 「（1万档）」、「（3万档）」），原样铺开会变成三段几乎一样的文案，占掉半屏。
 * 这里按「活动名去掉档位后缀 + 赠品清单」聚成一组，档位按门槛升序排列，
 * 让用户一眼看出「同一个赠品，买得越多送得越多」。
 *
 * 只做展示收敛，不改判定：能否享受仍由结算按符合条件的订单明细计算。
 */
import type { GoodsPromotionHints } from '@/api/promotion/goods-hints'

export interface HintGift {
  name: string
  quantity: number
}

export interface GiftTierRow {
  activityId: string
  thresholdAmount: number
  gifts: HintGift[]
}

/** 同一活动名、同一组赠品的多个档位 */
export interface GiftOfferGroup {
  key: string
  name: string
  rows: GiftTierRow[]
}

/** 档位后缀：结尾括号里带「档」的那一段，如「（3千档）」「(Tier 3)」 */
const TIER_SUFFIX_RE = /[（(][^（）()]*档[^（）()]*[）)]\s*$/u

const PERCENT_TIER_SUFFIX_RE = /(?:·\s*\d+(?:\.\d+)?\s*[%％]\s*档|[（(]\s*\d+(?:\.\d+)?\s*[%％]\s*档\s*[）)])$/u

/**
 * 差额封顶倍数：门槛超过当前金额 20 倍时不画进度条。
 *
 * 一盒 ¥24.9 的樱桃对着「满 ¥3,000」画一条 8% 的进度条，只会让用户
 * 觉得这个优惠和自己无关；退化成一行「满 ¥3,000 起赠」更诚实。
 */
export const MAX_GIFT_GAP_RATIO = 20

export type GiftProgressView =
  | { mode: 'progress', percent: number, gap: number, firstTier: boolean }
  | { mode: 'compact', threshold: number }
  | { mode: 'reached' }

/**
 * 满赠档位进度：只按当前选择数量 × 当前 SKU 售价做本地估算，不参与结算判定。
 *
 * - 金额无效或没有档位：不展示；
 * - 已超过最高档：只出「已满足最高档」，不画满格假进度；
 * - 差额超过当前金额 20 倍：退化成「满 ¥X 起赠」；
 * - 其余：给出百分比与差额，百分比夹在 1–99，避免 0% 空条和 100% 假满。
 */
export function resolveGiftProgress(
  amount: unknown,
  rows: GiftTierRow[],
  maxGapRatio = MAX_GIFT_GAP_RATIO,
): GiftProgressView | null {
  const total = Number(amount)
  if (!Number.isFinite(total) || total <= 0 || !rows.length)
    return null
  const nextIndex = rows.findIndex(row => row.thresholdAmount > total)
  if (nextIndex < 0)
    return { mode: 'reached' }
  const target = rows[nextIndex]
  if (target.thresholdAmount > total * maxGapRatio)
    return { mode: 'compact', threshold: rows[0].thresholdAmount }
  return {
    mode: 'progress',
    percent: Math.min(99, Math.max(1, Math.round((total / target.thresholdAmount) * 100))),
    gap: target.thresholdAmount - total,
    firstTier: nextIndex === 0,
  }
}

/**
 * 展示用活动名：去掉结尾的档位后缀。
 *
 * 只在这个位置收敛，后台的活动名保持原样；去掉后为空则回落原名。
 */
export function displayOfferName(name: unknown): string {
  const raw = typeof name === 'string' ? name.trim() : ''
  if (!raw)
    return ''
  const stripped = raw.replace(TIER_SUFFIX_RE, '').trim()
  return stripped || raw
}

/** 固定金额满减的活动名称只展示活动本身，减免金额由档位单独展示。 */
export function displayFixedDiscountName(name: unknown): string {
  const raw = typeof name === 'string' ? name.trim() : ''
  return raw.replace(PERCENT_TIER_SUFFIX_RE, '').trim()
}

/**
 * 金额文案：千分位 + 去掉无意义的尾零（3000.00 → 3,000，4.50 → 4.5）。
 *
 * 商详的订单门槛动辄四位数，`toFixed(2)` 出来的「¥3000.00」既长又像后台字段。
 */
export function formatHintAmount(value: unknown): string {
  const amount = Number(value)
  if (!Number.isFinite(amount))
    return '0'
  const rounded = Math.round(amount * 100) / 100
  const [integer, decimal] = String(Math.abs(rounded)).split('.')
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const text = decimal ? `${grouped}.${decimal}` : grouped
  return rounded < 0 ? `-${text}` : text
}

/**
 * 把逐活动的礼物提示聚成「同赠品多档位」的分组。
 *
 * - 门槛/赠品非法的提示直接丢弃（后端已过滤一次，这里再防御脏数据）；
 * - 同组内门槛相同的重复提示只留赠品数量更大的一条；
 * - 组按最低门槛升序，组内按门槛升序。
 */
export function groupGiftOffers(
  hints: GoodsPromotionHints['gifts'] | null | undefined,
): GiftOfferGroup[] {
  const groups = new Map<string, GiftOfferGroup>()

  for (const hint of hints ?? []) {
    const name = displayOfferName(hint?.name)
    const threshold = Number(hint?.thresholdAmount)
    if (!name || !Number.isFinite(threshold) || threshold < 0)
      continue

    const gifts: HintGift[] = []
    for (const gift of hint?.gifts ?? []) {
      const giftName = typeof gift?.name === 'string' ? gift.name.trim() : ''
      const quantity = Math.floor(Number(gift?.quantity))
      if (giftName && Number.isFinite(quantity) && quantity > 0)
        gifts.push({ name: giftName, quantity })
    }
    if (!gifts.length)
      continue

    // 赠品清单相同才视为同一场的不同档位；赠品不同则各自成组
    const signature = gifts.map(gift => gift.name).sort().join('|')
    const key = `${name}::${signature}`
    const group = groups.get(key) ?? { key, name, rows: [] }
    const existing = group.rows.find(row => row.thresholdAmount === threshold)
    if (existing) {
      const existingTotal = existing.gifts.reduce((sum, gift) => sum + gift.quantity, 0)
      const incomingTotal = gifts.reduce((sum, gift) => sum + gift.quantity, 0)
      if (incomingTotal > existingTotal)
        existing.gifts = gifts
    }
    else {
      group.rows.push({
        activityId: String(hint?.activityId ?? ''),
        thresholdAmount: threshold,
        gifts,
      })
    }
    groups.set(key, group)
  }

  return [...groups.values()]
    .map(group => ({ ...group, rows: [...group.rows].sort((a, b) => a.thresholdAmount - b.thresholdAmount) }))
    .sort((a, b) => (a.rows[0]?.thresholdAmount ?? 0) - (b.rows[0]?.thresholdAmount ?? 0))
}
