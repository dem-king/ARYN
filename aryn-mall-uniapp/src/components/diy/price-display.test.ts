import { describe, expect, it } from 'vitest'

import { shouldShowOriginalPrice } from './price-display'
import { normalizeRetailGoods } from './retail-normalizers'

describe('shouldShowOriginalPrice 划线原价显示判定', () => {
  it('原价高于售价时显示', () => {
    expect(shouldShowOriginalPrice(3.8, 23.8)).toBe(true)
    expect(shouldShowOriginalPrice('3.9', '4.9')).toBe(true)
  })

  it('原价未填（0）不显示——否则会划出「￥0」', () => {
    // 存量商品大量未填原价，这是最关键的一条
    expect(shouldShowOriginalPrice(12.9, 0)).toBe(false)
    expect(shouldShowOriginalPrice(12.9, undefined)).toBe(false)
    expect(shouldShowOriginalPrice(12.9, null)).toBe(false)
  })

  it('原价等于售价不显示——无折扣的划线没有信息量', () => {
    expect(shouldShowOriginalPrice(9.9, 9.9)).toBe(false)
  })

  it('原价低于售价不显示——避免出现「涨价」的误导划线', () => {
    expect(shouldShowOriginalPrice(23.8, 3.8)).toBe(false)
  })

  it('非数值输入不显示，不抛错', () => {
    expect(shouldShowOriginalPrice('abc', 10)).toBe(false)
    expect(shouldShowOriginalPrice(10, 'abc')).toBe(false)
    expect(shouldShowOriginalPrice(undefined, undefined)).toBe(false)
  })
})

describe('normalizeRetailGoods 透传原价', () => {
  it('保留接口下发的 originalPrice', () => {
    const [item] = normalizeRetailGoods([{ id: 'a', salesPrice: 3.8, originalPrice: 23.8 }])

    expect(item.price).toBe(3.8)
    expect(item.originalPrice).toBe(23.8)
  })

  it('接口未下发原价时落 0（渲染侧据此隐藏划线）', () => {
    const [item] = normalizeRetailGoods([{ id: 'a', salesPrice: 12.9 }])

    expect(item.originalPrice).toBe(0)
    expect(shouldShowOriginalPrice(item.price, item.originalPrice)).toBe(false)
  })

  it('字符串形态的价格同样归一化为数字（JSON 下 BigDecimal 可能为字符串）', () => {
    const [item] = normalizeRetailGoods([{ id: 'a', salesPrice: '3.80', originalPrice: '23.80' }])

    expect(item.originalPrice).toBe(23.8)
    expect(shouldShowOriginalPrice(item.price, item.originalPrice)).toBe(true)
  })
})
