import { describe, expect, it } from 'vitest'

import { mergeDuplicateSkuItems } from './order-sku-merge'

describe('mergeDuplicateSkuItems', () => {
  it('同一 SKU 的多行合并为一行且数量相加', () => {
    const items = [
      { skuId: 'sku-1', quantity: 2, spuName: '靠港滞留行' },
      { skuId: 'sku-1', quantity: 3, spuName: '当前无靠港行' },
    ]
    const merged = mergeDuplicateSkuItems(items)
    // 结算接口只接受一个 SKU 一行；数量取两行之和，展示字段取首行
    expect(merged).toHaveLength(1)
    expect(merged[0]).toMatchObject({ skuId: 'sku-1', quantity: 5, spuName: '靠港滞留行' })
  })

  it('保持首次出现的顺序，合并行落在首次出现的位置', () => {
    const merged = mergeDuplicateSkuItems([
      { skuId: 'b', quantity: 1 },
      { skuId: 'a', quantity: 1 },
      { skuId: 'b', quantity: 2 },
      { skuId: 'c', quantity: 1 },
    ])
    expect(merged.map(item => item.skuId)).toEqual(['b', 'a', 'c'])
    expect(merged[0].quantity).toBe(3)
  })

  it('不同 SKU 不受影响，各自保留一行', () => {
    const merged = mergeDuplicateSkuItems([
      { skuId: 'a', quantity: 1 },
      { skuId: 'b', quantity: 2 },
    ])
    expect(merged).toHaveLength(2)
    expect(merged.map(item => item.quantity)).toEqual([1, 2])
  })

  it('字符串数量按数字相加（后端数字字段可能以字符串下发）', () => {
    const merged = mergeDuplicateSkuItems([
      { skuId: 'a', quantity: '2' },
      { skuId: 'a', quantity: '3' },
    ])
    expect(merged).toHaveLength(1)
    expect(merged[0].quantity).toBe(5)
  })

  it('skuId 为空白的行原样保留、不参与合并', () => {
    const items = [
      { skuId: 'a', quantity: 1 },
      { skuId: '', quantity: 2 },
      { skuId: undefined, quantity: 4 },
      { skuId: 'a', quantity: 3 },
    ]
    const merged = mergeDuplicateSkuItems(items)
    // 空 skuId 没有可归并的身份，原样占据各自的位置
    expect(merged).toHaveLength(3)
    expect(merged[1]).toMatchObject({ skuId: '', quantity: 2 })
    expect(merged[2]).toMatchObject({ quantity: 4 })
    expect(merged[0].quantity).toBe(4)
  })

  it('不修改入参对象与数组（调用方仍要用原始列表做防串船校验）', () => {
    const first = { skuId: 'a', quantity: 2 }
    const second = { skuId: 'a', quantity: 3 }
    const items = [first, second]
    mergeDuplicateSkuItems(items)
    expect(items).toHaveLength(2)
    expect(first.quantity).toBe(2)
    expect(second.quantity).toBe(3)
  })

  it('空列表与缺省入参安全返回空数组', () => {
    expect(mergeDuplicateSkuItems([])).toEqual([])
    expect(mergeDuplicateSkuItems()).toEqual([])
  })
})
