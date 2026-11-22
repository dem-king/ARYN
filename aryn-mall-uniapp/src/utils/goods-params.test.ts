import { describe, expect, it } from 'vitest'
import { resolveGoodsParams } from './goods-params'

const single = { needChoose: false, singleSpecText: '', skuId: 'sku-1' }

describe('resolveGoodsParams', () => {
  it('keeps up to twelve custom parameter inputs and filters blank and duplicate rows', () => {
    const params = [
      { label: ' 保质期 ', value: ' 12 个月 ' },
      { label: '保质期', value: '重复标签' },
      { label: '净含量', value: ' ' },
      ...Array.from({ length: 10 }, (_, index) => ({ label: `参数 ${index}`, value: '有值' })),
    ]
    const rows = resolveGoodsParams({ brandName: '银鹭', params }, single)
    expect(rows[1]).toEqual({ key: 'custom:保质期', label: '保质期', value: '12 个月' })
    expect(rows).toHaveLength(11)
  })

  it('hides empty values, synthetic specs, and the default one-piece MOQ', () => {
    expect(resolveGoodsParams({
      brandName: ' ',
      goodsSkus: [{ id: 'sku-1', status: '0', salesUnit: '件', minOrderQty: 1, stepQty: 1 }],
    }, { ...single, singleSpecText: '默认' })).toEqual([])
  })

  it('uses SKU display specs when the raw single spec is absent', () => {
    expect(resolveGoodsParams({
      brandName: '银鹭',
      categoryName: '饮料',
      goodsSkus: [{ id: 'sku-1', status: '0', unitSpecText: '450 mL' }],
    }, single)).toEqual([
      { key: 'brand', value: '银鹭' },
      { key: 'category', value: '饮料' },
      { key: 'spec', value: '450 mL' },
    ])
  })

  it('collapses a duplicated category path and keeps distinct segments', () => {
    expect(resolveGoodsParams({ categoryName: '时令鲜果 / 时令鲜果' }, single)).toEqual([
      { key: 'category', value: '时令鲜果' },
    ])
    expect(resolveGoodsParams({ categoryName: '饮料 / 植物蛋白饮料' }, single)).toEqual([
      { key: 'category', value: '饮料 › 植物蛋白饮料' },
    ])
  })

  it('counts distinct active variants and displays packaging only for the selected SKU', () => {
    const sku = {
      id: 'sku-1',
      status: '0',
      specsArr: [{ specsValueName: '450 mL' }],
      baseDimension: 'COUNT',
      quantityPrecision: 'EXACT',
      salesUnit: '箱',
      baseUnit: 'pcs',
      baseQtyPerSalesUnit: '15.00',
      minOrderQty: 2,
      stepQty: 3,
    }
    const goods = {
      goodsSkus: [
        sku,
        { ...sku, id: 'same-spec' },
        { ...sku, id: 'other-spec', specsArr: [{ specsValueName: '1 L' }] },
        { ...sku, status: '1', specsArr: [{ specsValueName: '2 L' }] },
      ],
    }
    expect(resolveGoodsParams(goods, { ...single, needChoose: true, skuId: '' })).toEqual([
      { key: 'spec', valueKey: 'product.params.specCount', args: { count: 2 }, selectable: true },
    ])
    const rows = resolveGoodsParams(goods, { ...single, needChoose: true })
    expect(rows.map(row => row.key)).toEqual(['spec', 'pack', 'order'])
    expect(rows[1]?.args).toEqual({ salesUnit: '箱', baseUnit: 'pcs', count: 15 })

    expect(resolveGoodsParams({ goodsSkus: [{ ...sku, baseDimension: 'VOLUME', baseUnit: 'ml' }] }, single).map(row => row.key))
      .toEqual(['order'])
  })
})
