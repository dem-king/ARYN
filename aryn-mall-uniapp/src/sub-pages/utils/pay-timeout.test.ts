import { describe, expect, it } from 'vitest'
import { DEFAULT_PAY_TIMEOUT_MINUTES, formatPayTimeout } from './pay-timeout'

describe('formatPayTimeout', () => {
  it('字典分钟档位按分钟展示', () => {
    expect(formatPayTimeout(5)).toBe('5分钟')
    expect(formatPayTimeout(20)).toBe('20分钟')
  })

  it('整小时档位以小时展示', () => {
    expect(formatPayTimeout(60)).toBe('1小时')
    expect(formatPayTimeout(120)).toBe('2小时')
  })

  it('缺省或非法值回落默认 30 分钟', () => {
    expect(formatPayTimeout(undefined)).toBe(`${DEFAULT_PAY_TIMEOUT_MINUTES}分钟`)
    expect(formatPayTimeout(null)).toBe('30分钟')
    expect(formatPayTimeout(0)).toBe('30分钟')
    expect(formatPayTimeout(-5)).toBe('30分钟')
  })
})
