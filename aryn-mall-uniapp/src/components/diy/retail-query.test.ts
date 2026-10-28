import { describe, expect, it } from 'vitest'
import {
  createGoodsSortParams,
  createRetailSortParams,
  DEFAULT_BARGAIN_RATE,
  normalizeBargainRate,
} from './retail-query'

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

describe('自动检漏（sort=bargain）请求参数', () => {
  it('只下发 bargainRate 绑定值，不发 asc/desc（排序由后端固定）', () => {
    const params = createGoodsSortParams('bargain', true, 1.3)

    expect(params).toEqual({ bargainRate: 1.3 })
    expect(params).not.toHaveProperty('asc')
    expect(params).not.toHaveProperty('desc')
    // 活动分页同样只发阈值，不混入列名拼接
    expect(createRetailSortParams('bargain', true, 'id', 1.3)).toEqual({ bargainRate: 1.3 })
  })

  it('未配置阈值时按缺省 1.15 下发，与后端缺省一致', () => {
    expect(createGoodsSortParams('bargain')).toEqual({ bargainRate: DEFAULT_BARGAIN_RATE })
    expect(createGoodsSortParams('bargain', true, undefined)).toEqual({ bargainRate: 1.15 })
    expect(createGoodsSortParams('bargain', true, null)).toEqual({ bargainRate: 1.15 })
  })

  it('合法区间 (1.00, 9.99] 的边界值原样透传', () => {
    expect(createGoodsSortParams('bargain', true, 1.01)).toEqual({ bargainRate: 1.01 })
    expect(createGoodsSortParams('bargain', true, 1.15)).toEqual({ bargainRate: 1.15 })
    expect(createGoodsSortParams('bargain', true, 9.99)).toEqual({ bargainRate: 9.99 })
    // 运营输入的字符串同样可用
    expect(createGoodsSortParams('bargain', true, '1.5')).toEqual({ bargainRate: 1.5 })
  })

  it('越界/非法阈值回落 1.15，避免脏配置让整个 Tab 空掉', () => {
    for (const invalid of [1, 0.5, 0, -1, 10, 9.991, Number.NaN, Number.POSITIVE_INFINITY, 'abc', {}]) {
      expect(createGoodsSortParams('bargain', true, invalid)).toEqual({
        bargainRate: DEFAULT_BARGAIN_RATE,
      })
    }
    // 直接使用归一化函数的结果也一致
    expect(normalizeBargainRate(9.995)).toBe(1.15)
  })

  it('阈值按两位小数收敛浮点误差（管理端步长 0.01）', () => {
    expect(createGoodsSortParams('bargain', true, 1.1500000000000001)).toEqual({
      bargainRate: 1.15,
    })
    expect(createGoodsSortParams('bargain', true, 1.234)).toEqual({ bargainRate: 1.23 })
  })

  it('其它排序不受 bargainRate 影响（存量配置零影响）', () => {
    expect(createGoodsSortParams('sales', true, 1.3)).toEqual({
      desc: 'sales_volume,goods_spu.id',
    })
    expect(createGoodsSortParams('sales_price', true, 1.3)).toEqual({
      asc: 'sales_price,goods_spu.id',
    })
  })
})
