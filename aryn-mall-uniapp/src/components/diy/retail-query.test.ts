import { describe, expect, it } from 'vitest'
import { createGoodsSortParams, createRetailSortParams } from './retail-query'

describe('装修商品排序参数', () => {
  it('商品分页排序带 goods_spu.id 兜底，避免并列时翻页重复或漏数据', () => {
    expect(createGoodsSortParams('sales')).toEqual({
      desc: 'sales_volume,goods_spu.id',
    })
    expect(createGoodsSortParams('create_time')).toEqual({
      desc: 'create_time,goods_spu.id',
    })
    expect(createGoodsSortParams('sales_price', true)).toEqual({
      asc: 'sales_price,goods_spu.id',
    })
  })

  it('未知排序回落到销量优先', () => {
    expect(createGoodsSortParams('unknown')).toEqual({
      desc: 'sales_volume,goods_spu.id',
    })
  })

  it('活动分页仍用不带表别名的 id 兜底（单表查询）', () => {
    expect(createRetailSortParams('create_time')).toEqual({
      desc: 'create_time,id',
    })
  })
})
