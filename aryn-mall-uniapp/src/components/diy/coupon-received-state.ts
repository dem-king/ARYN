export interface CouponReceiveStatus {
  id?: string
  userReceiveCount?: number | string | null
}

/** Merge server-reported receive state without dropping optimistic local state. */
export function mergeReceivedCouponIds(
  current: Set<string>,
  coupons: CouponReceiveStatus[],
) {
  const next = new Set(current)
  for (const coupon of coupons) {
    if (coupon.id && Number(coupon.userReceiveCount) > 0)
      next.add(coupon.id)
  }
  return next
}
