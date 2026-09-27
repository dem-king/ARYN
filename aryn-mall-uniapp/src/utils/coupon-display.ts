/**
 * 优惠券卡片的展示口径。
 *
 * 券卡在四处出现（领券中心 / 我的优惠券 / 下单选择券 / 首页装修领券组件），
 * 历史上各写各的：金额有的拼「￥5」有的拼「¥5」，折扣有的带「折」有的不带，
 * 门槛有的写「满100元可用」有的写「满100可用」，同一张券在三个页面显示成三种样子。
 * 这里收敛成一套纯函数，四处共用。
 *
 * 时间解析不经过 `new Date('2026-09-22 00:15:33')`：小程序端（iOS）
 * 对该格式返回 Invalid Date，与 `utils/vessel-call-time.ts` 同一口径手工解析。
 */

export interface CouponLike {
  amount?: number | string | null
  couponName?: string | null
  couponType?: number | string | null
  discount?: number | string | null
  receiveEndedAt?: string | null
  threshold?: number | string | null
  useDescription?: string | null
  useRange?: string | null
}

export interface CouponValueParts {
  /** 金额前缀符号：满减券为 ¥，折扣券为空 */
  symbol: string
  /** 主数值：5 / 8 */
  value: string
  /** 数值后缀单位：折扣券为 折，满减券为空 */
  unit: string
  /** 金额区副文案：满减券 / 折扣券 */
  sub: string
}

/** 券类型：1 满减、2 折扣（与 coupon_info.coupon_type 一致） */
const TYPE_AMOUNT = '1'
const TYPE_DISCOUNT = '2'

export type CouponTone = 'active' | 'disabled' | 'expired' | 'frozen' | 'used'

export interface CouponStatusMeta {
  /** 券状态中文名 */
  label: string
  /** 对应视觉色调，决定券卡配色 */
  tone: CouponTone
}

const DATETIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/

/** 去掉无意义的小数尾巴：5.00 → 5、5.50 → 5.5 */
function trimNumber(value: number | string | null | undefined): string {
  const parsed = Number(value ?? 0)
  if (!Number.isFinite(parsed))
    return '0'
  return String(Number(parsed.toFixed(2)))
}

/** 券类型：无法识别时按满减券处理，与后端默认值一致 */
export function couponType(coupon?: CouponLike | null): string {
  const raw = coupon?.couponType
  return raw === null || raw === undefined || raw === '' ? TYPE_AMOUNT : String(raw)
}

/** 金额区拆成 符号 / 数值 / 单位 / 副文案，便于分别控字号 */
export function couponValueParts(coupon?: CouponLike | null): CouponValueParts {
  if (couponType(coupon) === TYPE_DISCOUNT) {
    return {
      symbol: '',
      value: trimNumber(coupon?.discount),
      unit: '折',
      sub: '折扣券',
    }
  }
  return {
    symbol: '¥',
    value: trimNumber(coupon?.amount),
    unit: '',
    sub: '满减券',
  }
}

/** 单行金额文案：¥5 / 8折（给不便拆结构的场景使用） */
export function couponValueText(coupon?: CouponLike | null): string {
  const parts = couponValueParts(coupon)
  return `${parts.symbol}${parts.value}${parts.unit}`
}

/** 使用门槛文案 */
export function couponThresholdText(coupon?: CouponLike | null): string {
  const threshold = Number(coupon?.threshold ?? 0)
  if (!Number.isFinite(threshold) || threshold <= 0)
    return '无门槛'
  return `满${trimNumber(threshold)}元可用`
}

/** 适用范围文案 */
export function couponScopeText(coupon?: CouponLike | null): string {
  return coupon?.useRange === '1' ? '全部商品可用' : '部分商品可用'
}

/** 券的副标题：优先用后端下发的使用说明，否则回落到适用范围 */
export function couponSubtitle(coupon?: CouponLike | null): string {
  const description = (coupon?.useDescription ?? '').trim()
  return description || couponScopeText(coupon)
}

interface ParsedDate {
  day: number
  month: number
  year: number
}

function parseDate(value?: string | null): ParsedDate | null {
  if (!value)
    return null
  const match = String(value).match(DATETIME_PATTERN)
  if (!match)
    return null
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

/** 距今天数：按本地零点相减，避免跨时区与夏令时偏差 */
export function daysUntil(value?: string | null, now: Date = new Date()): number | null {
  const target = parseDate(value)
  if (!target)
    return null
  const targetMidnight = new Date(target.year, target.month - 1, target.day).getTime()
  const nowMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  return Math.round((targetMidnight - nowMidnight) / 86_400_000)
}

/**
 * 相对天数文案：今天X / 明天X / 还剩 N 天 / MM-DD X / 已X。
 *
 * 一周内用相对天数（用户关心「还来不来得及」），超过一周改用具体日期
 * ——「还剩 87 天」这种精度对用户没有意义，反而要自己换算。
 * 无法解析时返回空串，由调用方隐藏整条状态带，而不是显示「未知」占位。
 */
function formatRelativeDay(value: string | null | undefined, now: Date, word: string): string {
  const days = daysUntil(value, now)
  if (days === null)
    return ''
  if (days < 0)
    return `已${word}`
  if (days === 0)
    return `今天${word}`
  if (days === 1)
    return `明天${word}`
  if (days <= 7)
    return `还剩 ${days} 天`
  const parsed = parseDate(value)!
  return `${pad(parsed.month)}-${pad(parsed.day)} ${word}`
}

/** 有效期文案，用于「我的优惠券」等券本身有有效期的场景 */
export function formatCouponExpiry(value?: string | null, now: Date = new Date()): string {
  return formatRelativeDay(value, now, '过期')
}

/**
 * 领券截止文案，用于领券中心。
 *
 * 与有效期分开措辞：领券中心能看到的截止时间是「还能领到什么时候」，
 * 写成「今天过期」会让用户误以为券马上作废。
 */
export function formatCouponDeadline(value?: string | null, now: Date = new Date()): string {
  return formatRelativeDay(value, now, '截止')
}

/** 领券记录状态：0 未使用、1 已使用、2 已过期、3 冻结中 */
export function couponStatusMeta(status?: string | number | null): CouponStatusMeta {
  switch (String(status ?? '')) {
    case '1':
      return { label: '已使用', tone: 'used' }
    case '2':
      return { label: '已过期', tone: 'expired' }
    case '3':
      return { label: '冻结中', tone: 'frozen' }
    case '0':
      return { label: '待使用', tone: 'active' }
    default:
      return { label: '未知', tone: 'disabled' }
  }
}
