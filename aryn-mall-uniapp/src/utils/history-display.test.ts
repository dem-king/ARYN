import { describe, expect, it } from 'vitest'

import { formatClockTime, formatShortDateTime, resolveHistoryDay } from './history-display'

describe('resolveHistoryDay', () => {
  // 2026-10-31 是周六
  const now = new Date(2026, 9, 31, 12, 0, 0)

  it('识别今天与昨天并给出星期', () => {
    expect(resolveHistoryDay('2026-10-31', now)).toMatchObject({
      year: 2026,
      month: 10,
      day: 31,
      weekday: 6,
      isToday: true,
      isYesterday: false,
    })
    expect(resolveHistoryDay('2026-10-30', now)).toMatchObject({
      weekday: 5,
      isToday: false,
      isYesterday: true,
    })
    expect(resolveHistoryDay('2026-10-29', now)).toMatchObject({
      isToday: false,
      isYesterday: false,
    })
  })

  it('跨年按日历天判断，不误判为昨天', () => {
    expect(resolveHistoryDay('2026-12-31', new Date(2027, 0, 1, 8, 0, 0))).toMatchObject({
      year: 2026,
      month: 12,
      day: 31,
      isToday: false,
      isYesterday: true,
    })
  })

  it('解析失败返回 null，由页面回退原始日期串', () => {
    expect(resolveHistoryDay('', now)).toBeNull()
    expect(resolveHistoryDay(undefined, now)).toBeNull()
    expect(resolveHistoryDay('待定', now)).toBeNull()
  })
})

describe('formatClockTime', () => {
  it('输出 HH:mm 并补零', () => {
    expect(formatClockTime('2026-10-31 09:05:20')).toBe('09:05')
    expect(formatClockTime('2026-10-31 10:48')).toBe('10:48')
  })

  it('解析失败返回空串，由调用方回退原值', () => {
    expect(formatClockTime('')).toBe('')
    expect(formatClockTime('待定')).toBe('')
  })
})

describe('formatShortDateTime', () => {
  const now = new Date(2026, 9, 31, 12, 0, 0)

  it('同年省略年份且去掉秒', () => {
    expect(formatShortDateTime('2026-10-26 22:50:04', now)).toBe('10-26 22:50')
  })

  it('跨年补年份', () => {
    expect(formatShortDateTime('2025-12-31 08:00:00', now)).toBe('2025-12-31 08:00')
  })

  it('解析失败返回空串', () => {
    expect(formatShortDateTime('待定', now)).toBe('')
  })
})
