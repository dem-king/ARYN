/**
 * 满减券阶梯的档位取数口径。
 *
 * 档位不是运营手填的数字，而是 coupon_info 里的满减券：
 * - 只要 `coupon_type=1`（满减券）且 `threshold>0`（无门槛券不是「满 X 减 Y」）；
 * - 按门槛升序，同门槛按面额大的在前，保证「越买越省」的视觉递进；
 * - 去重后截断到组件配置的档数（2-4）。
 *
 * 这样运营改券面额、下架券，页面无需重新装修。
 */

export interface CouponLadderCoupon {
  amount?: null | number | string
  couponType?: null | number | string
  id?: null | string
  threshold?: null | number | string
}

export interface CouponLadderTier {
  /** 面额（已去尾零），如 30 */
  amount: string
  couponId: string
  /** 门槛（已去尾零），如 199 */
  threshold: string
}

export const COUPON_LADDER_MIN_TIERS = 2
export const COUPON_LADDER_MAX_TIERS = 4

const TYPE_AMOUNT = '1'

function trimNumber(value: number): string {
  return String(Number(value.toFixed(2)))
}

/** 展示档数兜底到 2-4：1 档没有对比意义，超过 4 档在 375px 下会挤断行 */
export function normalizeLadderShowNum(showNum: number | string | undefined): number {
  const value = Math.floor(Number(showNum))
  if (!Number.isFinite(value))
    return 3
  return Math.min(COUPON_LADDER_MAX_TIERS, Math.max(COUPON_LADDER_MIN_TIERS, value))
}

export function normalizeCouponLadderTiers(
  coupons: CouponLadderCoupon[] | null | undefined,
  showNum: number | string | undefined,
): CouponLadderTier[] {
  const limit = normalizeLadderShowNum(showNum)
  const seen = new Set<string>()
  const rows: Array<CouponLadderTier & { sortAmount: number, sortThreshold: number }> = []

  for (const coupon of coupons ?? []) {
    const id = String(coupon?.id ?? '').trim()
    if (!id || seen.has(id))
      continue

    // 类型缺省按满减券处理，与后端默认值、券卡展示口径一致
    const rawType = coupon?.couponType
    const type = rawType === null || rawType === undefined || rawType === ''
      ? TYPE_AMOUNT
      : String(rawType)
    if (type !== TYPE_AMOUNT)
      continue

    const threshold = Number(coupon?.threshold ?? 0)
    const amount = Number(coupon?.amount ?? 0)
    // 无门槛券（门槛 0）与零面额券进不了阶梯，否则会出现「满 0 元减 0」
    if (!Number.isFinite(threshold) || threshold <= 0)
      continue
    if (!Number.isFinite(amount) || amount <= 0)
      continue

    seen.add(id)
    rows.push({
      amount: trimNumber(amount),
      couponId: id,
      sortAmount: amount,
      sortThreshold: threshold,
      threshold: trimNumber(threshold),
    })
  }

  rows.sort(
    (left, right) =>
      left.sortThreshold - right.sortThreshold
      || right.sortAmount - left.sortAmount,
  )

  return rows
    .slice(0, limit)
    .map(({ sortAmount, sortThreshold, ...tier }) => tier)
}
