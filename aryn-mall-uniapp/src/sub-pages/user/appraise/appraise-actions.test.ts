import { describe, expect, it } from 'vitest'
import {
  APPRAISE_ACTION_WINDOW_MS,
  canAdvanceAppraise,
  canDeleteAppraise,
  canModifyAppraise,
  hasAdvancedAppraise,
  hasAnyAppraiseAction,
  hasModifiedAppraise,
  isAnonymousAppraise,
  isSystemDefaultAppraise,
  isWithinAppraiseWindow,
  parseAppraiseTime,
} from './appraise-actions'

/** 固定「现在」，让窗口边界可断言 */
const NOW = Date.parse('2026-10-30T12:00:00')
const DAY_MS = 24 * 60 * 60 * 1000

function daysAgo(days: number) {
  const date = new Date(NOW - days * DAY_MS)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function record(overrides: Record<string, any> = {}) {
  return {
    createTime: daysAgo(1),
    appraiseType: 0,
    isAnonymous: 0,
    ...overrides,
  }
}

describe('parseAppraiseTime', () => {
  it('解析后端 yyyy-MM-dd HH:mm:ss（iOS 兼容），带时区 ISO 原样解析', () => {
    expect(parseAppraiseTime('2026-10-30 12:00:00')).toBe(Date.parse('2026-10-30T12:00:00'))
    expect(parseAppraiseTime('2026-10-30T04:00:00Z')).toBe(Date.parse('2026-10-30T04:00:00Z'))
  })

  it('空值/非法值返回 null', () => {
    expect(parseAppraiseTime(undefined)).toBeNull()
    expect(parseAppraiseTime('')).toBeNull()
    expect(parseAppraiseTime('not-a-time')).toBeNull()
  })
})

describe('isWithinAppraiseWindow', () => {
  it('30 天整仍在窗口内，超过 1 毫秒即出窗口', () => {
    const boundary = new Date(NOW - APPRAISE_ACTION_WINDOW_MS).toISOString()
    expect(isWithinAppraiseWindow(boundary, NOW)).toBe(true)
    expect(isWithinAppraiseWindow(new Date(NOW - APPRAISE_ACTION_WINDOW_MS - 1).toISOString(), NOW)).toBe(false)
  })

  it('刚发布的评价在窗口内；1 天内时钟偏差不误伤', () => {
    expect(isWithinAppraiseWindow(daysAgo(0), NOW)).toBe(true)
    expect(isWithinAppraiseWindow(new Date(NOW + DAY_MS / 2).toISOString(), NOW)).toBe(true)
    expect(isWithinAppraiseWindow(new Date(NOW + 2 * DAY_MS).toISOString(), NOW)).toBe(false)
  })

  it('无法解析的时间按不可操作处理', () => {
    expect(isWithinAppraiseWindow('', NOW)).toBe(false)
    expect(isWithinAppraiseWindow(null, NOW)).toBe(false)
  })
})

describe('追评可见性', () => {
  it('窗口内且未追评可追评；已追评不再展示', () => {
    expect(canAdvanceAppraise(record(), NOW)).toBe(true)
    expect(canAdvanceAppraise(record({ advanceTime: '2026-10-20 10:00:00' }), NOW)).toBe(false)
    expect(hasAdvancedAppraise(record({ advanceTime: '2026-10-20 10:00:00' }))).toBe(true)
  })

  it('系统默认评价也可以追评', () => {
    expect(canAdvanceAppraise(record({ appraiseType: 1 }), NOW)).toBe(true)
  })

  it('超过 30 天不可追评', () => {
    expect(canAdvanceAppraise(record({ createTime: daysAgo(31) }), NOW)).toBe(false)
  })
})

describe('修改可见性', () => {
  it('主动评价窗口内且未修改过可修改', () => {
    expect(canModifyAppraise(record(), NOW)).toBe(true)
    expect(canModifyAppraise(record({ modifyTime: '2026-10-25 10:00:00' }), NOW)).toBe(false)
    expect(hasModifiedAppraise(record({ modifyTime: '2026-10-25 10:00:00' }))).toBe(true)
  })

  it('系统默认评价不可修改；超窗口不可修改', () => {
    expect(canModifyAppraise(record({ appraiseType: 1 }), NOW)).toBe(false)
    expect(canModifyAppraise(record({ createTime: daysAgo(31) }), NOW)).toBe(false)
  })
})

describe('删除可见性', () => {
  it('主动评价窗口内可删除；系统默认评价不可删除', () => {
    expect(canDeleteAppraise(record(), NOW)).toBe(true)
    expect(canDeleteAppraise(record({ appraiseType: 1 }), NOW)).toBe(false)
  })

  it('超过 30 天不可删除', () => {
    expect(canDeleteAppraise(record({ createTime: daysAgo(31) }), NOW)).toBe(false)
  })
})

describe('辅助判定', () => {
  it('系统默认/匿名标记兼容字符串数字', () => {
    expect(isSystemDefaultAppraise({ appraiseType: '1' })).toBe(true)
    expect(isSystemDefaultAppraise({ appraiseType: 0 })).toBe(false)
    expect(isAnonymousAppraise({ isAnonymous: '1' })).toBe(true)
    expect(isAnonymousAppraise({ isAnonymous: 0 })).toBe(false)
  })

  it('hasAnyAppraiseAction 覆盖只有追评/完全没有操作两种情况', () => {
    expect(hasAnyAppraiseAction(record(), NOW)).toBe(true)
    expect(hasAnyAppraiseAction(record({ appraiseType: 1 }), NOW)).toBe(true)
    expect(hasAnyAppraiseAction(record({ createTime: daysAgo(40) }), NOW)).toBe(false)
    expect(hasAnyAppraiseAction(null, NOW)).toBe(false)
  })
})
