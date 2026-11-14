/**
 * C 端采购档期与档期特供（需登录）
 * 对应后端 AppTradeCalendarController：
 * GET /promotion/app/calendar/list、GET /promotion/app/calendar/specials
 *
 * 展示口径与成交试算同源（活动窗 + 档期窗 + 剩余限量）。
 * 展示接入点（P1.5）：档期楼层（diy-trade-calendar）按 list 取档期、
 * specials 取特供商品横滑；商品卡特供价角标随 zone-price 契约一并接入。
 */
import { alovaInstance } from '@/api/core/instance'

/** 采购档期 VO */
export interface AppCalendar {
  /** 锚点类型：1周度 2靠港 3自然月 4自定义 */
  anchorType: string
  /** 档期结束 */
  endTime: string
  /** 档期 ID */
  id: string
  /** 档期名称 */
  calendarName: string
  /** 档期开始 */
  startTime: string
}

/** 档期特供商品项 */
export interface CalendarSpecialItem {
  /** 当前售价（划线基准；远程降级时为 null） */
  originalPrice?: null | number
  /** 每人限购件数（0/null=不限） */
  perUserLimit?: null | number
  /** 特供价 */
  price: number
  /** SKU ID */
  skuId: string
  /** 商品名（楼层卡片展示，远程回填；失败为 null 前端兜底 skuId） */
  spuName?: null | string
  /** SPU ID */
  spuId?: null | string
  /** 商品图（SKU 级图，楼层卡片展示） */
  picUrl?: null | string
}

/** 档期特供活动 VO */
export interface AppCalendarSpecial {
  activityId: string
  activityName: string
  calendarId?: null | string
  calendarName?: null | string
  endTime: string
  /** 剩余限量（件数；totalQuota 为 null 时不限量） */
  remainingQuota?: null | number
  /** 活动级限量（件数；null=不限） */
  totalQuota?: null | number
  items: CalendarSpecialItem[]
}

/** 生效中的采购档期列表 */
export function getTradeCalendars() {
  return alovaInstance.Get<AppCalendar[]>('/promotion/app/calendar/list')
}

/** 档期特供列表（calendarId 为空=全部生效档期） */
export function getCalendarSpecials(calendarId?: string) {
  return alovaInstance.Get<AppCalendarSpecial[]>('/promotion/app/calendar/specials', {
    params: { calendarId: calendarId || undefined },
  })
}
