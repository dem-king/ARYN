import { describe, expect, it } from 'vitest'

import {
  QUICK_CART_MODE_CHOOSE,
  QUICK_CART_MODE_DIRECT,
  QUICK_CART_MODE_UNAVAILABLE,
  quickAddBlockedText,
  resolveQuickAddMode,
  resolveQuickAddQuantity,
} from './quick-cart'

describe('resolveQuickAddMode', () => {
  it('单规格且带 SKU 时可直接加购', () => {
    expect(resolveQuickAddMode({ mode: 'direct', skuId: 'sku-1' })).toBe(QUICK_CART_MODE_DIRECT)
  })

  it('多规格返回需选择规格', () => {
    expect(resolveQuickAddMode({ mode: 'choose' })).toBe(QUICK_CART_MODE_CHOOSE)
  })

  it('下架或缺货返回不可加购', () => {
    expect(resolveQuickAddMode({ mode: 'unavailable', reason: '商品暂时缺货' })).toBe(
      QUICK_CART_MODE_UNAVAILABLE,
    )
  })

  it('请求失败（info 为空）退回选择规格，绝不盲目加购', () => {
    expect(resolveQuickAddMode(null)).toBe(QUICK_CART_MODE_CHOOSE)
    expect(resolveQuickAddMode(undefined)).toBe(QUICK_CART_MODE_CHOOSE)
  })

  it('声称可直购但缺少 SKU 时仍要求选择规格', () => {
    expect(resolveQuickAddMode({ mode: 'direct' })).toBe(QUICK_CART_MODE_CHOOSE)
  })

  it('未知 mode 值按选择规格处理', () => {
    expect(resolveQuickAddMode({ mode: 'whatever', skuId: 'sku-1' })).toBe(
      QUICK_CART_MODE_CHOOSE,
    )
  })
})

describe('resolveQuickAddQuantity', () => {
  it('无船供资料时默认加购 1 件', () => {
    expect(resolveQuickAddQuantity({ mode: 'direct', skuId: 'sku-1' })).toBe(1)
    expect(resolveQuickAddQuantity({ mode: 'direct', skuId: 'sku-1', moq: null, stepQty: null })).toBe(1)
  })

  it('按最小起订量取值', () => {
    expect(resolveQuickAddQuantity({ moq: 5 })).toBe(5)
  })

  it('最小起订量不是步长整数倍时向上取整到合法值', () => {
    expect(resolveQuickAddQuantity({ moq: 5, stepQty: 3 })).toBe(6)
    expect(resolveQuickAddQuantity({ moq: 20, stepQty: 10 })).toBe(20)
  })

  it('库存低于合法数量时返回 0（不可加购）', () => {
    expect(resolveQuickAddQuantity({ moq: 60, stepQty: 12, stock: 30 })).toBe(0)
  })

  it('库存刚好等于合法数量时仍可加购', () => {
    expect(resolveQuickAddQuantity({ moq: 60, stepQty: 12, stock: 60 })).toBe(60)
  })

  it('忽略非法 MOQ/步长，回落为 1', () => {
    expect(resolveQuickAddQuantity({ moq: 0, stepQty: -3 })).toBe(1)
    expect(resolveQuickAddQuantity({ moq: 'abc', stepQty: '' })).toBe(1)
  })
})

describe('quickAddBlockedText', () => {
  it('优先展示后端给出的原因', () => {
    expect(quickAddBlockedText({ reason: '商品暂时缺货' })).toBe('商品暂时缺货')
  })

  it('缺少原因时给出通用提示', () => {
    expect(quickAddBlockedText(null)).toBe('该商品暂时无法加购')
    expect(quickAddBlockedText({ reason: '   ' })).toBe('该商品暂时无法加购')
  })
})
