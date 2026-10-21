import { describe, expect, it } from 'vitest'
import { shouldShowSubtitle } from './subtitle-display'

describe('shouldShowSubtitle', () => {
  it('子标题与商品名不同才显示', () => {
    expect(shouldShowSubtitle('泰国山竹1kg', '新鲜进口｜个大香甜')).toBe(true)
  })

  it('子标题与商品名相同（含首尾空白）时不显示', () => {
    expect(shouldShowSubtitle('黄豆芽500g', '黄豆芽500g')).toBe(false)
    expect(shouldShowSubtitle('黄豆芽500g', ' 黄豆芽500g ')).toBe(false)
  })

  it('子标题为空时不显示，商品名缺失时不误伤有效子标题', () => {
    expect(shouldShowSubtitle('黄豆芽500g', '   ')).toBe(false)
    expect(shouldShowSubtitle('', '新鲜进口｜个大香甜')).toBe(true)
  })
})
