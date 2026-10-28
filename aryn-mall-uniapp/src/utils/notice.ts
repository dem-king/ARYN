/**
 * 站内通知的展示口径（C 端消息中心 / 通知详情 / 客服会话共用）。
 *
 * 后端 `message_notice` 的 `category` / `source_type` / `priority` 是自由字符串
 * （订单、退款、营销、船舶等业务域各自写入，历史数据还混用两套字段），
 * 直接把枚举码渲染进页面会把 `VESSEL_BIND_AUDIT`、`NORMAL` 这类内部标识暴露给用户，
 * 因此统一在这里收敛成用户能看懂的文案；未知值兜底为「通知」，永不回显原始码。
 *
 * 分类判定优先级：业务来源 `sourceType` > 类目 `category` > 标题关键词（老数据兜底）。
 * 与管理端 `@vben/utils` 的 `noticeTypeOf` 同源思路，C 端只保留一位粗粒度分类。
 */
import type { NoticeInboxItem } from '@/api/message/types'
import { translate } from '@/locales'
import { parseLocalDateTimeTimestamp } from './datetime'

/** 通知分类：只决定列表/详情里那枚标签的文案。 */
export type NoticeCategory
  = | 'delivery'
    | 'order'
    | 'other'
    | 'promotion'
    | 'refund'
    | 'system'
    | 'vessel'

export interface NoticeCategoryInput {
  /** 后端类目：ORDER / REFUND / PROMOTION / VESSEL_BIND_AUDIT / SYSTEM… */
  category?: null | string
  /** 后端业务来源：ORDER_PAY / COUPON_RECEIVE / VESSEL_BIND_APPLY… */
  sourceType?: null | string
  /** 兜底用：老通知可能没有 sourceType，用标题关键词补足分类。 */
  title?: null | string
}

function includes(value: null | string | undefined, keyword: string) {
  return (value ?? '').toUpperCase().includes(keyword)
}

/** 业务来源判定：同一类目下退款/营销/船舶靠港等事件优先靠 sourceType 拆开。 */
function categoryOfSource(sourceType: null | string | undefined): NoticeCategory | null {
  if (includes(sourceType, 'REFUND'))
    return 'refund'
  if (includes(sourceType, 'COUPON') || includes(sourceType, 'PROMOTION'))
    return 'promotion'
  if (includes(sourceType, 'VESSEL'))
    return 'vessel'
  if (includes(sourceType, 'DELIVERY'))
    return 'delivery'
  if (includes(sourceType, 'COD') || (sourceType ?? '').toUpperCase().startsWith('ORDER'))
    return 'order'
  if (includes(sourceType, 'SYSTEM'))
    return 'system'
  return null
}

/** 类目判定：`category` 是粗粒度业务域，比 sourceType 低频但更稳定。 */
function categoryOfCategory(category: null | string | undefined): NoticeCategory | null {
  const value = (category ?? '').toUpperCase()
  if (!value)
    return null
  if (value.includes('REFUND'))
    return 'refund'
  if (value.includes('PROMOTION') || value.includes('COUPON'))
    return 'promotion'
  if (value.includes('VESSEL'))
    return 'vessel'
  if (value.includes('DELIVERY'))
    return 'delivery'
  if (value === 'ORDER' || value.includes('ORDER'))
    return 'order'
  if (value === 'SYSTEM' || value.includes('SYSTEM'))
    return 'system'
  return null
}

/** 老数据兜底：标题里的业务关键词是最后可用的判据。 */
function categoryOfTitle(title: null | string | undefined): NoticeCategory | null {
  const text = title ?? ''
  if (text.includes('退款'))
    return 'refund'
  if (text.includes('优惠券') || text.includes('优惠'))
    return 'promotion'
  if (text.includes('船舶') || text.includes('绑定') || text.includes('靠港'))
    return 'vessel'
  if (text.includes('配送') || text.includes('发货'))
    return 'delivery'
  if (text.includes('订单'))
    return 'order'
  return null
}

export function noticeCategoryOf(
  input: NoticeCategoryInput = {},
): NoticeCategory {
  return (
    categoryOfSource(input.sourceType)
    ?? categoryOfCategory(input.category)
    ?? categoryOfTitle(input.title)
    ?? 'other'
  )
}

/** i18n key：分类标签文案统一走 `message.notice.category.*`，中英各一份。 */
export function noticeCategoryLabelKey(category: NoticeCategory): string {
  return `message.notice.category.${category}`
}

/** 列表/详情直接可用的分类标签（未知分类返回「通知」，不显示内部枚举码）。 */
export function noticeCategoryLabel(input: NoticeCategoryInput): string {
  return translate(noticeCategoryLabelKey(noticeCategoryOf(input)))
}

/** 收件箱条目的便捷重载：字段名与后端 VO 对齐，调用方不用手拼。 */
export function noticeCategoryLabelOf(item: Pick<NoticeInboxItem, 'category' | 'sourceType' | 'title'>): string {
  return noticeCategoryLabel(item)
}

/**
 * 优先级是否需要在页面上露出来。
 *
 * 后端缺省 `NORMAL`，只有运营显式置 `HIGH` 才展示「重要」标签；
 * 其余值（含历史脏数据）一律不露码，避免把 `NORMAL` 直接渲染给用户。
 */
export function isImportantNotice(priority?: null | string): boolean {
  return (priority ?? '').trim().toUpperCase() === 'HIGH'
}

const pad = (value: number) => String(value).padStart(2, '0')

function partsOf(raw: string) {
  const timestamp = parseLocalDateTimeTimestamp(raw)
  if (timestamp === null)
    return null
  const date = new Date(timestamp)
  return {
    day: pad(date.getDate()),
    hour: pad(date.getHours()),
    minute: pad(date.getMinutes()),
    month: pad(date.getMonth() + 1),
    year: date.getFullYear(),
  }
}

/**
 * 列表/会话里的紧凑时间：今天只给时分，昨天带「昨天」，同年 `MM-DD HH:mm`，
 * 跨年补年份。后端整串 `2026-10-24 17:18:01` 对消息列表太重，且秒级精度无意义。
 *
 * 解析失败时原样返回，调用方不需要为脏数据加分支。
 */
export function formatNoticeTime(
  raw?: null | string,
  now: number = Date.now(),
): string {
  if (!raw)
    return ''
  const parts = partsOf(raw)
  if (!parts)
    return raw
  const current = new Date(now)
  const sameDay
    = parts.year === current.getFullYear()
      && parts.month === pad(current.getMonth() + 1)
      && parts.day === pad(current.getDate())
  if (sameDay)
    return `${parts.hour}:${parts.minute}`

  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  const isYesterday
    = parts.year === yesterday.getFullYear()
      && parts.month === pad(yesterday.getMonth() + 1)
      && parts.day === pad(yesterday.getDate())
  if (isYesterday)
    return `${translate('labels.yesterday')} ${parts.hour}:${parts.minute}`

  if (parts.year !== current.getFullYear())
    return `${parts.year}-${parts.month}-${parts.day}`
  return `${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`
}

/** 详情页完整时间：`YYYY-MM-DD HH:mm`（秒级精度对用户没有意义）。 */
export function formatNoticeDateTime(raw?: null | string): string {
  if (!raw)
    return ''
  const parts = partsOf(raw)
  if (!parts)
    return raw
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`
}
