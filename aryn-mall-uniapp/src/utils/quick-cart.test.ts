import { describe, expect, it } from 'vitest'

import {
  normalizeQuantity,
  QUICK_CART_MODE_CHOOSE,
  QUICK_CART_MODE_DIRECT,
  QUICK_CART_MODE_UNAVAILABLE,
  quickAddBlockedText,
  resolveQuantityRule,
  resolveQuantityRuleForSku,
  resolveQuickAddMode,
  resolveQuickAddQuantity,
  validateQuantity,
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

describe('resolveQuantityRule', () => {
  it('无船供资料时回落为 MOQ=1、步长=1、无库存上限', () => {
    expect(resolveQuantityRule(null)).toEqual({ moq: 1, stepQty: 1, stock: null, purchaseUnit: '' })
    expect(resolveQuantityRule({ moq: null, stepQty: '' })).toEqual({
      moq: 1,
      stepQty: 1,
      stock: null,
      purchaseUnit: '',
    })
  })

  it('读取船供包装资料与采购单位', () => {
    expect(resolveQuantityRule({ moq: 5, stepQty: 5, stock: 120, purchaseUnit: '斤' })).toEqual({
      moq: 5,
      stepQty: 5,
      stock: 120,
      purchaseUnit: '斤',
    })
  })

  it('忽略非法规则值', () => {
    expect(resolveQuantityRule({ moq: 0, stepQty: -3, stock: 0 })).toEqual({
      moq: 1,
      stepQty: 1,
      stock: null,
      purchaseUnit: '',
    })
  })
})

describe('resolveQuantityRuleForSku', () => {
  it('多规格 SPU 下按 skuId 命中对应 SKU 的规则', () => {
    const info = {
      mode: 'choose',
      goodsSkus: [
        { skuId: 'sku-a', moq: 5, stepQty: 5, stock: 100, purchaseUnit: '斤' },
        { skuId: 'sku-b', moq: 2, stepQty: 2, stock: 40, purchaseUnit: '袋' },
      ],
    }
    expect(resolveQuantityRuleForSku(info, 'sku-b')).toEqual({
      moq: 2,
      stepQty: 2,
      stock: 40,
      purchaseUnit: '袋',
    })
  })

  it('单规格商品命中顶层规则', () => {
    const info = { mode: 'direct', skuId: 'sku-1', moq: 3, stepQty: 3, stock: 30, purchaseUnit: '件' }
    expect(resolveQuantityRuleForSku(info, 'sku-1')?.moq).toBe(3)
  })

  it('单规格商品未维护船供资料时返回 null，不凭空造规则', () => {
    const info = { mode: 'direct', skuId: 'sku-1' }
    expect(resolveQuantityRuleForSku(info, 'sku-1')).toBeNull()
  })

  it('skuId 不在返回集里时返回 null（多规格层级不混用 SPU 字段）', () => {
    const info = {
      mode: 'choose',
      moq: 5,
      stepQty: 5,
      goodsSkus: [{ skuId: 'sku-a', moq: 5, stepQty: 5 }],
    }
    expect(resolveQuantityRuleForSku(info, 'sku-zzz')).toBeNull()
    expect(resolveQuantityRuleForSku(info, '')).toBeNull()
    expect(resolveQuantityRuleForSku(null, 'sku-a')).toBeNull()
  })
})

describe('normalizeQuantity', () => {
  const plain = { moq: 1, stepQty: 1, stock: null, purchaseUnit: '件' }
  /** 小青菜式的大宗采购：5 斤起订、每次按 5 斤递增 */
  const weighed = { moq: 5, stepQty: 5, stock: 500, purchaseUnit: '斤' }

  it('按步长向上取整到合法值，不因手输 37 而拒绝', () => {
    expect(normalizeQuantity(37, weighed)).toBe(40)
    expect(normalizeQuantity(40, weighed)).toBe(40)
  })

  it('低于起订量时抬到最小合法值', () => {
    expect(normalizeQuantity(1, weighed)).toBe(5)
    expect(normalizeQuantity('', weighed)).toBe(5)
    expect(normalizeQuantity(0, weighed)).toBe(5)
    expect(normalizeQuantity(-3, weighed)).toBe(5)
  })

  it('超过库存时向下回落到步长整数倍', () => {
    expect(normalizeQuantity(999, weighed)).toBe(500)
    expect(normalizeQuantity(999, { ...weighed, stock: 480 })).toBe(480)
    expect(normalizeQuantity(999, { ...weighed, stock: 12 })).toBe(10)
  })

  it('库存不足以满足起订量时返回 0，交由调用方提示', () => {
    expect(normalizeQuantity(9, { ...weighed, stock: 3 })).toBe(0)
  })

  it('无船供资料的商品按原值取整', () => {
    expect(normalizeQuantity(37, plain)).toBe(37)
    expect(normalizeQuantity(37.9, plain)).toBe(37)
  })

  it('起订量不是步长整数倍时抬到两者共同满足的最小值', () => {
    expect(normalizeQuantity(1, { moq: 5, stepQty: 3, stock: null, purchaseUnit: '' })).toBe(6)
  })
})

describe('validateQuantity', () => {
  const weighed = { moq: 5, stepQty: 5, stock: 100, purchaseUnit: '斤' }

  it('合法数量通过', () => {
    expect(validateQuantity(45, weighed)).toEqual({ ok: true, reason: '' })
  })

  it('未达起订量给出带单位的提示', () => {
    expect(validateQuantity(3, weighed)).toEqual({ ok: false, reason: '最少起订 5斤' })
  })

  it('非步长整数倍给出倍数提示', () => {
    expect(validateQuantity(42, weighed)).toEqual({ ok: false, reason: '数量需为 5 的整数倍' })
  })

  it('超库存给出剩余库存提示', () => {
    expect(validateQuantity(105, weighed)).toEqual({ ok: false, reason: '库存仅剩 100斤' })
  })

  it('空值与非数字视为未填写', () => {
    expect(validateQuantity('', weighed).ok).toBe(false)
    expect(validateQuantity('abc', weighed).reason).toBe('请输入采购数量')
  })
})
