import { describe, expect, it } from 'vitest'

import { formatCallTime, formatDeclareTime } from './vessel-call-time'

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

describe('formatDeclareTime', () => {
  it('按后端 LocalDateTime 口径输出 yyyy-MM-dd HH:mm:ss', () => {
    // 申报接口只认这一种格式，少一段或换成 T 分隔都会被反序列化拒掉
    expect(formatDeclareTime(new Date(2026, 8, 22, 8, 30, 5).getTime())).toBe('2026-09-22 08:30:05')
  })

  it('月日时分秒一律补零到两位', () => {
    expect(formatDeclareTime(new Date(2026, 0, 5, 6, 7, 8).getTime())).toBe('2026-01-05 06:07:08')
  })

  it('零点整保留完整时分秒，不被省略', () => {
    expect(formatDeclareTime(new Date(2026, 8, 22, 0, 0, 0).getTime())).toBe('2026-09-22 00:00:00')
  })

  it('与 formatCallTime 同一口径取本地时间，跨时区不偏移', () => {
    // 本地 getter 取值：年末最后一秒不应被折算成次年的 01-01
    const timestamp = new Date(2026, 11, 31, 23, 59, 59).getTime()
    expect(formatDeclareTime(timestamp)).toBe('2026-12-31 23:59:59')
    // 同一年内 formatCallTime 只给月日时分，回读不丢日期
    expect(formatCallTime(formatDeclareTime(timestamp), NOW)).toBe('12-31 23:59')
  })
})
