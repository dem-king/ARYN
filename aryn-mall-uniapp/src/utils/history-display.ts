import { parseLocalDateTimeTimestamp } from './datetime'

const DAY_MS = 24 * 60 * 60 * 1000

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

export interface HistoryDayParts {
  year: number
  month: number
  day: number
  /** 0=周日 … 6=周六，与 Date#getDay 一致 */
  weekday: number
  isToday: boolean
  isYesterday: boolean
}

/**
 * 足迹按天后端下发 `yyyy-MM-dd`：分组头需要年/月/日/星期与今天昨天判定。
 * 解析不了（脏数据）返回 null，由页面回退显示原始串。
 */
export function resolveHistoryDay(value?: string, now = new Date()): HistoryDayParts | null {
  const timestamp = parseLocalDateTimeTimestamp(value)
  if (timestamp === null)
    return null
  const date = new Date(timestamp)
  const todayStart = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  const dateStart = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate())
  const diffDays = Math.round((todayStart - dateStart) / DAY_MS)
  return {
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    day: date.getDate(),
    weekday: date.getDay(),
    isToday: diffDays === 0,
    isYesterday: diffDays === 1,
  }
}

/** 商品行只显示时分：日期已由分组头给出 */
export function formatClockTime(value?: string): string {
  const timestamp = parseLocalDateTimeTimestamp(value)
  if (timestamp === null)
    return ''
  const date = new Date(timestamp)
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 收藏时间：同年省略年份，保留到分钟；跨年补年份 */
export function formatShortDateTime(value?: string, now = new Date()): string {
  const timestamp = parseLocalDateTimeTimestamp(value)
  if (timestamp === null)
    return ''
  const date = new Date(timestamp)
  const datePart = date.getFullYear() === now.getFullYear()
    ? `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    : `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return `${datePart} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
