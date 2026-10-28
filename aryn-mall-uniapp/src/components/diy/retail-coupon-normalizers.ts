/**
 * 优惠券数据归一化（仅 C 端使用）。
 *
 * 券类型 / 门槛 / 面额单位是前端固定标签，需要随语言实时切换；而
 * `retail-normalizers.ts` 会被管理端 typecheck 直接引用（管理端运行环境没有
 * `uni` 全局，也不能经 `@/` 别名引入 i18n 运行时），因此把这部分带翻译的
 * 归一化单独放在本文件，管理端只引用不依赖 i18n 的商家 / 商品归一化。
 */
import { translate } from '@/locales'

export interface RetailCouponItem {
  id: string
  thresholdText: string
  title: string
  /** 券类型标签：满减券 / 折扣券 */
  typeLabel: string
  value: string
  /**
   * 当前登录用户对该券的领取次数；游客请求时为 null/undefined。
   * 领券组件的「已领取」标记依赖它，刷新后才能由服务端还原。
   */
  userReceiveCount?: number | null
}

/** 券面额：折扣券把「X折」换算成百分比表达，中文仍按「X折」展示 */
function formatCouponValue(couponType: string, item: any): string {
  if (couponType !== '2')
    return `¥${Number(item.amount ?? 0)}`
  const zhe = Number(item.discount ?? 0)
  const percentOff = Number.isFinite(zhe) && zhe > 0 && zhe < 10
    ? Math.round((10 - zhe) * 10)
    : 0
  return translate('diy.coupon.discountValue', { percentOff, value: zhe })
}

/** 优惠券数据归一化：兼容 couponType/amount/discount/threshold 的存量字段 */
export function normalizeCoupons(value: unknown): RetailCouponItem[] {
  const records = Array.isArray(value)
    ? value
    : (isRecordLike(value) && Array.isArray((value as any).records) ? (value as any).records : [])
  if (!Array.isArray(records))
    return []
  return records
    .map((item: any): RetailCouponItem | null => {
      if (!item || typeof item !== 'object')
        return null
      const id = String(item.id ?? '')
      if (!id)
        return null
      const title = String(item.couponName ?? item.title ?? translate('diy.coupon.fallbackTitle'))
      const couponType = String(item.couponType ?? '1')
      const threshold = Number(item.threshold ?? 0)
      // 保留服务端下发的领取次数，否则领券组件刷新后无法还原「已领取」
      const userReceiveCount = item.userReceiveCount == null ? null : Number(item.userReceiveCount)
      return {
        id,
        thresholdText: threshold > 0
          ? translate('diy.coupon.thresholdRequired', { threshold })
          : translate('diy.coupon.noThreshold'),
        title,
        typeLabel: translate(
          couponType === '2' ? 'diy.coupon.typeDiscount' : 'diy.coupon.typeAmount',
        ),
        userReceiveCount,
        value: formatCouponValue(couponType, item),
      }
    })
    .filter((item): item is RetailCouponItem => item !== null)
}

function isRecordLike(value: unknown): boolean {
  return typeof value === 'object' && value !== null
}
