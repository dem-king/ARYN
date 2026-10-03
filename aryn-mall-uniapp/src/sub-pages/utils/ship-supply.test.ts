import type { ShipSupplyRow } from './ship-supply'

import { describe, expect, it } from 'vitest'
import {
  defaultQuantityOf,
  isRowAddable,
  quantityMaxOf,
  quantityRuleOf,
  salesPriceText,
  shipSupplyRowKey,
  stockHintOf,
  stockToneOf,
  unavailableReasonOf,
} from './ship-supply'

function row(overrides: Partial<ShipSupplyRow> = {}): ShipSupplyRow {
  return {
    spuId: 'spu-1',
    skuId: 'sku-1',
    name: '日清拉面',
    ...overrides,
  }
}

describe('shipSupplyRowKey', () => {
  it('includes the SKU dimension so multi-spec SPUs do not collide', () => {
    // 同一 SPU 的多规格会展开成多行，只用 spuId 会让两行共享同一个数量输入框
    expect(shipSupplyRowKey(row())).toBe('spu-1-sku-1')
    expect(shipSupplyRowKey(row({ skuId: 'sku-2' }))).toBe('spu-1-sku-2')
    expect(shipSupplyRowKey({})).toBe('-')
  })
})

describe('quantityRuleOf', () => {
  it('keeps MOQ/step at 1 after ship profile removal', () => {
    // 船供包装资料下线（2026-09-29）：数量规则恒为 1/1，步进器与校验口径不变
    expect(quantityRuleOf(row())).toEqual({ moq: 1, stepQty: 1, stock: null })
  })

  it('keeps stock 0 as a real value, unlike the quick-cart util', () => {
    // 快捷加购把 stock<=0 归成 null（"后端未下发"），选货页必须区分：0 就是缺货
    expect(quantityRuleOf(row({ stock: 0 })).stock).toBe(0)
    expect(quantityRuleOf(row({ stock: -3 })).stock).toBe(-3)
    expect(quantityRuleOf(row({ stock: null })).stock).toBeNull()
    expect(quantityRuleOf(row()).stock).toBeNull()
  })
})

describe('defaultQuantityOf / isRowAddable', () => {
  it('defaults to 1 so a single tap is always a legal quantity', () => {
    expect(defaultQuantityOf(row({ stock: 5 }))).toBe(1)
    expect(isRowAddable(row({ stock: 5 }))).toBe(true)
  })

  it('marks out-of-stock rows unaddable', () => {
    expect(defaultQuantityOf(row({ stock: 0 }))).toBe(0)
    expect(isRowAddable(row({ stock: 0 }))).toBe(false)
    expect(isRowAddable(row({ stock: -1 }))).toBe(false)
  })

  it('marks rows without a skuId unaddable even when stock is plentiful', () => {
    // 无 skuId = 无可售规格：库存再多也加不进去，必须点之前就置灰，
    // 而不是让用户点完收到后端/前端那句「暂无可售规格」
    expect(isRowAddable(row({ skuId: undefined, stock: 1000 }))).toBe(false)
    expect(isRowAddable(row({ skuId: '', stock: 1000 }))).toBe(false)
    expect(isRowAddable(row({ skuId: 'sku-1', stock: 1000 }))).toBe(true)
  })
})

describe('quantityMaxOf', () => {
  it('returns the stock as the upper bound', () => {
    expect(quantityMaxOf(row({ stock: 30 }))).toBe(30)
  })

  it('returns null when stock is unknown so the stepper does not cap arbitrarily', () => {
    expect(quantityMaxOf(row())).toBeNull()
  })

  it('returns null when there is no stock at all (row is already unaddable)', () => {
    // 0 不能当成上限，否则步进器会渲染出 min=MOQ / max=0 的矛盾区间
    expect(quantityMaxOf(row({ stock: 0 }))).toBeNull()
  })
})

describe('stockHintOf / stockToneOf', () => {
  it('stays silent when the backend did not send stock', () => {
    // 缺货文案不能在没有库存数据时凭空出现
    expect(stockHintOf(row())).toBe('')
    expect(stockToneOf(row())).toBe('normal')
  })

  it('escalates from normal to low to out', () => {
    expect(stockHintOf(row({ stock: 500 }))).toBe('库存 500')
    expect(stockToneOf(row({ stock: 500 }))).toBe('normal')
    expect(stockHintOf(row({ stock: 8 }))).toBe('仅剩 8')
    expect(stockToneOf(row({ stock: 8 }))).toBe('low')
    expect(stockHintOf(row({ stock: 0 }))).toBe('缺货')
    expect(stockToneOf(row({ stock: 0 }))).toBe('out')
  })
})

describe('unavailableReasonOf', () => {
  it('stays empty for addable rows', () => {
    expect(unavailableReasonOf(row({ stock: 5 }))).toBe('')
  })

  it('reports 缺货 only when stock is really exhausted', () => {
    expect(unavailableReasonOf(row({ stock: 0 }))).toBe('缺货')
  })

  it('names the missing spec rather than blaming stock', () => {
    // 无 skuId 的商品库存通常很充足（1000），报「缺货」会误导
    expect(unavailableReasonOf(row({ skuId: undefined, stock: 1000 }))).toBe('暂无可售规格')
  })
})

describe('salesPriceText', () => {
  it('renders a placeholder instead of a misleading zero', () => {
    expect(salesPriceText(row({ salesPrice: 88.5 }))).toBe('88.5')
    expect(salesPriceText(row({ salesPrice: 0 }))).toBe('0')
    expect(salesPriceText(row())).toBe('-')
    expect(salesPriceText(row({ salesPrice: null }))).toBe('-')
  })
})
