import { describe, expect, it } from 'vitest'

import { parseLocalDateTimeTimestamp, resolveTargetTimestamp } from './datetime'

describe('parseLocalDateTimeTimestamp', () => {
  it('按本地时间解析后端 yyyy-MM-dd HH:mm:ss', () => {
    expect(parseLocalDateTimeTimestamp('2026-09-22 09:43:40'))
      .toBe(new Date(2026, 8, 22, 9, 43, 40).getTime())
  })

  it('t 分隔与缺省秒同样解析', () => {
    expect(parseLocalDateTimeTimestamp('2026-09-22T09:43:40'))
      .toBe(new Date(2026, 8, 22, 9, 43, 40).getTime())
    expect(parseLocalDateTimeTimestamp('2026-09-22 09:43'))
      .toBe(new Date(2026, 8, 22, 9, 43, 0).getTime())
  })

  it('只给日期按本地零点算', () => {
    expect(parseLocalDateTimeTimestamp('2026-09-22'))
      .toBe(new Date(2026, 8, 22, 0, 0, 0).getTime())
  })

  it('空值与无法解析的值返回 null，调用方自行兜底', () => {
    expect(parseLocalDateTimeTimestamp('')).toBeNull()
    expect(parseLocalDateTimeTimestamp(undefined)).toBeNull()
    expect(parseLocalDateTimeTimestamp('待定')).toBeNull()
  })

  it('不经过 Date 字符串构造，iOS 不支持的空格分隔格式也能解析', () => {
    // 小程序 iOS 端 new Date('2026-09-22 09:43:40') 会得到 Invalid Date
    const timestamp = parseLocalDateTimeTimestamp('2026-09-22 09:43:40')
    expect(timestamp).not.toBeNull()
    const date = new Date(timestamp!)
    expect(date.getFullYear()).toBe(2026)
    expect(date.getMonth()).toBe(8)
    expect(date.getDate()).toBe(22)
    expect(date.getHours()).toBe(9)
  })
})

describe('resolveTargetTimestamp', () => {
  it('date 实例直取时间戳', () => {
    const date = new Date(2026, 8, 22, 9, 0, 0)
    expect(resolveTargetTimestamp(date)).toBe(date.getTime())
  })

  it('字符串走后端时间口径解析', () => {
    expect(resolveTargetTimestamp('2026-09-22 09:43:40'))
      .toBe(new Date(2026, 8, 22, 9, 43, 40).getTime())
  })

  it('解析不了的字符串返回 null，倒计时按已结束处理', () => {
    expect(resolveTargetTimestamp('待定')).toBeNull()
  })
})
