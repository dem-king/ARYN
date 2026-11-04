import { describe, expect, it } from 'vitest'

import {
  isQuickAddPromotionApplicable,
  minZoneSkuPrice,
  resolveQuickAddZonePrice,
  resolveZoneSkuPrice,
} from './quick-cart'

/**
 * 快捷加购「专区成交价」口径（会员价 / 捡漏价）。
 *
 * 背景：列表卡片上标了会员价/捡漏价，但 quick-cart 接口只回普通售价。
 * 确认弹层与规格弹层若不按卡片成交价展示，就会出现
 * 「卡片 ¥5.90、弹层小计 ¥7.90」的价差。这里固化三条规则：
 *  · 专区价只在 SKU 精确匹配时生效（拿 A 的价格标 B 的货比不标更糟）；
 *  · 专区价必须低于普通售价（脏数据/倒挂一律不采用）；
 *  · 活动价适用时优先活动价，专区价只是活动价缺失时的兜底。
 */

const info = { mode: 'direct', salesPrice: 12.9, skuId: 'sku-1' }

describe('resolveZoneSkuPrice / minZoneSkuPrice', () => {
  it('按 SKU 取专区价；未命中/脏数据返回 null', () => {
    expect(resolveZoneSkuPrice({ 'sku-1': 9.9 }, 'sku-1')).toBe(9.9)
    expect(resolveZoneSkuPrice({ 'sku-1': '9.90' }, 'sku-1')).toBe(9.9)
    expect(resolveZoneSkuPrice({ 'sku-1': 9.9 }, 'sku-2')).toBeNull()
    expect(resolveZoneSkuPrice(null, 'sku-1')).toBeNull()
    expect(resolveZoneSkuPrice({ 'sku-1': 0 }, 'sku-1')).toBeNull()
    expect(resolveZoneSkuPrice({ 'sku-1': -1 }, 'sku-1')).toBeNull()
    expect(resolveZoneSkuPrice({ 'sku-1': 'abc' }, 'sku-1')).toBeNull()
  })

  it('专区价统一四舍五入到分', () => {
    expect(resolveZoneSkuPrice({ 'sku-1': 9.999 }, 'sku-1')).toBe(10)
    expect(resolveZoneSkuPrice({ 'sku-1': 9.994 }, 'sku-1')).toBe(9.99)
  })

  it('取专区价表最低有效价（多规格弹层未选规格时的头价）', () => {
    expect(minZoneSkuPrice({ 'sku-1': 9.9, 'sku-2': 5.5, 'sku-3': 'abc' })).toBe(5.5)
    expect(minZoneSkuPrice({})).toBeNull()
    expect(minZoneSkuPrice(null)).toBeNull()
  })
})

describe('resolveQuickAddZonePrice 确认弹层单价', () => {
  it('SKU 匹配时用专区价，不限购、无角标说明', () => {
    expect(resolveQuickAddZonePrice(info, { 'sku-1': 9.9 })).toEqual({
      hint: '',
      maxQuantity: null,
      unitPrice: 9.9,
    })
  })

  it('专区价表项可携带角标说明（如「会员价」）', () => {
    expect(resolveQuickAddZonePrice(info, { 'sku-1': { hint: '会员价', price: 9.9 } })).toEqual({
      hint: '会员价',
      maxQuantity: null,
      unitPrice: 9.9,
    })
  })

  it('SKU 不匹配时不采用（后端 quick-cart 可能选了别的规格）', () => {
    expect(resolveQuickAddZonePrice(info, { 'sku-2': 9.9 })).toBeNull()
  })

  it('专区价不低于普通售价（等价/倒挂）时不采用，回落普通价', () => {
    expect(resolveQuickAddZonePrice(info, { 'sku-1': 12.9 })).toBeNull()
    expect(resolveQuickAddZonePrice(info, { 'sku-1': 19.9 })).toBeNull()
  })

  it('缺少 skuId / 未传专区价时不采用', () => {
    expect(resolveQuickAddZonePrice({ salesPrice: 12.9 }, { 'sku-1': 9.9 })).toBeNull()
    expect(resolveQuickAddZonePrice(info, null)).toBeNull()
    expect(resolveQuickAddZonePrice(info, {})).toBeNull()
  })
})

describe('isQuickAddPromotionApplicable 活动价优先级', () => {
  it('活动 SKU 一致且单价合法时可用', () => {
    expect(isQuickAddPromotionApplicable(info, { skuId: 'sku-1', unitPrice: 8.8 })).toBe(true)
  })

  it('活动 SKU 不一致 / 单价非法 / 未传活动时不可用', () => {
    expect(isQuickAddPromotionApplicable(info, { skuId: 'sku-2', unitPrice: 8.8 })).toBe(false)
    expect(isQuickAddPromotionApplicable(info, { skuId: 'sku-1', unitPrice: 0 })).toBe(false)
    expect(isQuickAddPromotionApplicable(info, null)).toBe(false)
    expect(isQuickAddPromotionApplicable({ salesPrice: 12.9 }, { skuId: 'sku-1', unitPrice: 8.8 })).toBe(false)
  })
})
