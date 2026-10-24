import type { AppDiscountVO } from '@/api/promotion'
import { describe, expect, it } from 'vitest'
import {
  discountPriceMap,
  discountSavingAmount,
  discountUnitPrice,
  emptyDiscountDisplay,
  formatDiscountValue,
  formatStripAmount,
  resolveDiscountPriceDisplay,
} from './discount-price'

function vo(overrides: Partial<AppDiscountVO> = {}): AppDiscountVO {
  return {
    activityId: 'act-1',
    activityName: '零食酒水狂欢8折专区',
    discountPrice: 23.92,
    discountType: 1,
    discountValue: 0.8,
    goodsImage: '',
    goodsName: '老李卤豆干小包装500g',
    originalPrice: 29.9,
    skuId: 'sku-1',
    spuId: 'spu-1',
    ...overrides,
  }
}

describe('formatDiscountValue 折扣力度口径', () => {
  it('0.95 → 9.5折、0.8 → 8折，与折扣会场/入口条同口径', () => {
    expect(formatDiscountValue({ discountType: 1, discountValue: 0.95 })).toBe('9.5折')
    expect(formatDiscountValue({ discountType: 1, discountValue: 0.8 })).toBe('8折')
    expect(formatDiscountValue({ discountType: 1, discountValue: 0.88 })).toBe('8.8折')
    expect(formatDiscountValue({ discountType: 1, discountValue: 0.855 })).toBe('8.6折')
  })

  it('力度达到原价时退回「折扣」，永不出现「10折」', () => {
    for (const value of [1, 1.2, 0.999, 0, Number.NaN]) {
      expect(formatDiscountValue({ discountType: 1, discountValue: value })).not.toContain('10折')
    }
    expect(formatDiscountValue({ discountType: 1, discountValue: 1 })).toBe('折扣')
  })

  it('减价 / 固定价沿用后端语义', () => {
    expect(formatDiscountValue({ discountType: 2, discountValue: 20 })).toBe('减¥20')
    expect(formatDiscountValue({ discountType: 2, discountValue: 5.5 })).toBe('减¥5.5')
    expect(formatDiscountValue({ discountType: 3, discountValue: 9.9 })).toBe('一口价')
    expect(formatDiscountValue({ discountType: 9, discountValue: 0.5 })).toBe('折扣')
  })
})

describe('formatStripAmount 金额口径', () => {
  it('去掉无意义尾零并收敛浮点误差', () => {
    expect(formatStripAmount(4)).toBe('4')
    expect(formatStripAmount(4.5)).toBe('4.5')
    expect(formatStripAmount(4.0)).toBe('4')
    expect(formatStripAmount(4.567)).toBe('4.57')
    expect(formatStripAmount(30.8 - 26.8)).toBe('4')
    expect(formatStripAmount(Number.NaN)).toBe('0')
  })
})

describe('resolveDiscountPriceDisplay 展示判定', () => {
  it('命中当前 SKU 时切换折后价并给出划线价/角标', () => {
    const display = resolveDiscountPriceDisplay([vo()], 'sku-1')
    expect(display).toMatchObject({
      activityId: 'act-1',
      activityName: '零食酒水狂欢8折专区',
      badgeText: '8折',
      discountPrice: 23.92,
      matched: true,
      originalPrice: 29.9,
      showDiscountPrice: true,
    })
    expect(discountSavingAmount(display)).toBe('5.98')
  })

  it('按 SKU 精确匹配：同 SPU 其他规格不串价', () => {
    const display = resolveDiscountPriceDisplay([vo({ skuId: 'sku-2' })], 'sku-1')
    expect(display.matched).toBe(false)
    expect(display.showDiscountPrice).toBe(false)
  })

  it('无活动 / 接口失败 / 空 skuId 时回落空展示', () => {
    expect(resolveDiscountPriceDisplay(null, 'sku-1')).toEqual(emptyDiscountDisplay())
    expect(resolveDiscountPriceDisplay([], 'sku-1')).toEqual(emptyDiscountDisplay())
    expect(resolveDiscountPriceDisplay([vo()], '')).toEqual(emptyDiscountDisplay())
  })

  it('折后价不低于基准价时不切换主价、不划线（拒绝假折扣）', () => {
    const display = resolveDiscountPriceDisplay([vo({ discountPrice: 29.9 })], 'sku-1')
    expect(display.matched).toBe(true)
    expect(display.showDiscountPrice).toBe(false)
    expect(display.discountPrice).toBeNull()
    expect(display.originalPrice).toBeNull()
    expect(discountSavingAmount(display)).toBe('')
  })

  it('价格字段缺失/非法时不进入展示态', () => {
    const display = resolveDiscountPriceDisplay(
      [vo({ originalPrice: Number.NaN, discountPrice: 0 })],
      'sku-1',
    )
    expect(display.showDiscountPrice).toBe(false)
    expect(display.discountPrice).toBeNull()
  })

  it('兼容单对象与数组，重复 SKU 以后者为准', () => {
    expect(resolveDiscountPriceDisplay(vo(), 'sku-1').showDiscountPrice).toBe(true)
    const map = discountPriceMap([vo({ discountPrice: 10 }), vo({ discountPrice: 9.9 })])
    expect(map.get('sku-1')?.discountPrice).toBe(9.9)
  })
})

describe('discountUnitPrice 成交单价', () => {
  it('命中折扣取折后价，未命中回落售价', () => {
    expect(discountUnitPrice(29.9, resolveDiscountPriceDisplay([vo()], 'sku-1'))).toBe(23.92)
    expect(discountUnitPrice(29.9, emptyDiscountDisplay())).toBe(29.9)
  })

  it('非法兜底价按 0 处理', () => {
    expect(discountUnitPrice('abc', emptyDiscountDisplay())).toBe(0)
  })
})
