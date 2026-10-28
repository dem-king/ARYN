/**
 * 下单确认页的券可用性判定（叠加规则口径）。
 *
 * 服务端结算接口下发每条明细的 couponStackable（活动是否允许叠券）与订单级
 * couponEligibleAmount（券门槛可计算金额）。券弹层与券行共用这一套判定，
 * 避免两处各写各的把同一张券判成两种结果。
 *
 * 判定口径与后端 orderCouponHandler 一致：
 *   - 指定商品券：券 SPU ∩ 本单「允许叠券」明细，交集为空则不可用；
 *   - 门槛基数：允许叠券（∩ 券范围）明细的成交金额合计；
 *   - 全部明细都不允许叠券时任何券都不可用。
 *
 * `reason` 是**展示判定码**：这里刻意保留中文源文案，由下单页
 * `CouponSelector#localizeCouponReason` 按当前语言映射成词条；不要在这个
 * 纯函数里直接翻译，否则字符串比较会失配、英文环境反而回落中文。
 */

export interface CouponUsability {
  unavailable: boolean
  reason: string
}

export interface UsabilityItemLike {
  spuId?: string | null
  totalPrice?: number | string | null
  /** 结算接口回填：该行活动是否允许叠券（null/undefined=普通商品，false=禁止） */
  couponStackable?: boolean | null
}

/** 明细是否允许叠券（缺省视为普通商品=允许，兼容老结算接口不下发该字段） */
export function isCouponStackable(item: UsabilityItemLike): boolean {
  return item.couponStackable !== false
}

/**
 * 单张券相对当前订单的可用性。
 *
 * @param coupon 券记录（couponUser），门槛取 couponInfo.threshold、范围取 couponInfo.useRange
 * @param orderItemList 结算明细（含 couponStackable 标记）
 * @param eligibleBase 服务端下发的券门槛可计算金额（couponEligibleAmount）；缺省时按明细本地汇总
 */
export function getCouponUnavailableInfo(
  coupon: any,
  orderItemList: UsabilityItemLike[],
  eligibleBase?: number | string | null,
): CouponUsability {
  const result: CouponUsability = { unavailable: false, reason: '' }
  const items = Array.isArray(orderItemList) ? orderItemList : []
  const threshold = Number(coupon?.couponInfo?.threshold ?? 0)
  const useRange = coupon?.couponInfo?.useRange
  const couponSpuIds = ((coupon?.couponGoodsList as Array<{ spuId?: string }> | undefined) || [])
    .map(i => i?.spuId)
    .filter(Boolean) as string[]
  const orderSpuIds = items.map(i => i?.spuId).filter(Boolean) as string[]

  // 允许叠券的明细（叠加规则：秒杀/新人价等强制不可叠）
  const stackableItems = items.filter(isCouponStackable)

  // 指定商品券：券 SPU ∩ 允许叠券明细
  if (useRange === '2') {
    if (couponSpuIds.length === 0) {
      result.unavailable = true
      result.reason = '仅部分商品可用（本单无可用商品）'
      return result
    }
    const matched = stackableItems.filter(
      item => item.spuId && couponSpuIds.includes(item.spuId),
    )
    if (matched.length === 0) {
      result.unavailable = true
      result.reason = orderSpuIds.some(spuId => couponSpuIds.includes(spuId))
        ? '活动商品不支持使用优惠券'
        : '仅部分商品可用（本单无可用商品）'
      return result
    }
  }
  else if (stackableItems.length === 0) {
    result.unavailable = true
    result.reason = '活动商品不支持使用优惠券'
    return result
  }

  // 门槛基数：优先服务端口径；本地兜底 = 允许叠券（∩ 券范围）明细的成交金额合计
  let base = Number(eligibleBase)
  if (!Number.isFinite(base) || base <= 0) {
    const scopeItems = useRange === '2'
      ? stackableItems.filter(item => item.spuId && couponSpuIds.includes(item.spuId))
      : stackableItems
    base = scopeItems.reduce(
      (sum, item) => sum + Number(item.totalPrice ?? 0),
      0,
    )
  }

  if (threshold > 0 && base < threshold) {
    result.unavailable = true
    const gap = Math.max(threshold - base, 0.01).toFixed(2)
    result.reason = `还差 ￥${gap} 可用该券`
    return result
  }

  return result
}
