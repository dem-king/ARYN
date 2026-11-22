import { describe, expect, it } from 'vitest'
import { resolvePurchaseTags } from './goods-purchase-tags'

const cherry = {
  params: [
    { label: '产地', value: '山东烟台 · 美早' },
    { label: '储存', value: '0–4℃ 冷藏' },
    { label: '保质期', value: '冷藏 7 天' },
    { label: '包装', value: '泡沫箱 + 冰袋 + 吸水纸' },
  ],
  goodsSkus: [{
    id: 'sku-1',
    status: '0',
    salesUnit: '盒',
    baseUnit: '盒',
    baseQtyPerSalesUnit: 1,
    unitSpecText: '约 500g / 盒 · 10 盒 / 箱',
    minOrderQty: 1,
    stepQty: 1,
  }],
}

const single = { needChoose: false, singleSpecText: '', skuId: 'sku-1' }

describe('resolvePurchaseTags', () => {
  it('turns cherry params and unit spec into four purchase facts', () => {
    expect(resolvePurchaseTags(cherry, single)).toEqual([
      { key: 'storage', value: '0–4℃', icon: 'snowflake', tone: 'blue' },
      { key: 'spec', value: '500g/盒', icon: 'package', tone: 'green' },
      { key: 'box', value: '10盒/箱', icon: 'package', tone: 'gray' },
      { key: 'origin', value: '山东烟台', icon: 'location', tone: 'orange' },
    ])
  })

  it('shows the MOQ only when it is a real constraint', () => {
    const goods = {
      goodsSkus: [{ ...cherry.goodsSkus[0], minOrderQty: 3, stepQty: 2, unitSpecText: '' }],
    }
    const tags = resolvePurchaseTags(goods, single)
    expect(tags.map(tag => tag.key)).toEqual(['order'])
    expect(tags[0]?.value).toBe('3盒起')
  })

  it('falls back to the selected single spec and never invents a default one', () => {
    expect(resolvePurchaseTags({ goodsSkus: [{ ...cherry.goodsSkus[0], unitSpecText: '' }] }, {
      ...single,
      singleSpecText: '500g/盒',
    }).map(tag => tag.value)).toContain('500g/盒')

    expect(resolvePurchaseTags({ goodsSkus: [{ ...cherry.goodsSkus[0], unitSpecText: '' }] }, single)).toEqual([])
  })

  it('uses the selected SKU for multi-spec goods and hides downstream facts when none is selected', () => {
    const goods = {
      params: cherry.params,
      goodsSkus: [
        cherry.goodsSkus[0],
        { ...cherry.goodsSkus[0], id: 'sku-2', status: '0', unitSpecText: '约 250g / 盒 · 20 盒 / 箱' },
      ],
    }
    expect(resolvePurchaseTags(goods, { ...single, needChoose: true, skuId: '' }).map(tag => tag.key))
      .toEqual(['storage', 'origin', 'shelf'])
    expect(resolvePurchaseTags(goods, { ...single, needChoose: true, skuId: 'sku-2' }).map(tag => tag.value))
      .toContain('250g/盒')
  })
})
