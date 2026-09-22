import { describe, expect, it } from 'vitest'

import { formatCallTime } from './vessel-call-time'

const NOW = new Date(2026, 8, 21, 10, 0, 0)

describe('formatCallTime', () => {
  it('今天的靠港只显示到分钟', () => {
    expect(formatCallTime('2026-09-21 00:15:33', NOW)).toBe('今天 00:15')
  })

  it('明天的靠港用「明天」表达', () => {
    expect(formatCallTime('2026-09-22 08:30:00', NOW)).toBe('明天 08:30')
  })

  it('更远的靠港显示月日与时间', () => {
    expect(formatCallTime('2026-09-25 23:05:00', NOW)).toBe('09-25 23:05')
  })

  it('跨年的靠港补上年份', () => {
    expect(formatCallTime('2027-01-05 09:00:00', NOW)).toBe('2027-01-05 09:00')
  })

  it('不带秒的时间同样可以解析', () => {
    expect(formatCallTime('2026-09-22 08:30', NOW)).toBe('明天 08:30')
  })

  it('空值返回空串，无法解析的值原样返回', () => {
    expect(formatCallTime('', NOW)).toBe('')
    expect(formatCallTime(undefined, NOW)).toBe('')
    expect(formatCallTime('待定', NOW)).toBe('待定')
  })

  it('不依赖 Date 字符串构造，iOS 不支持的格式也能解析', () => {
    // 小程序 iOS 端 new Date('2026-09-22 00:15:33') 会得到 Invalid Date
    expect(formatCallTime('2026-09-22T00:15:33', NOW)).toBe('明天 00:15')
  })
})
