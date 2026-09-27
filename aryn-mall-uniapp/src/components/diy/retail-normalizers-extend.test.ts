import { describe, expect, it } from 'vitest'

import { normalizeCoupons, normalizeRetailGoods } from './retail-normalizers'

describe('normalizeRetailGoods 扩展字段', () => {
  it('保留原有 6 个字段（不破坏既有调用方）', () => {
    const [item] = normalizeRetailGoods({
      records: [{
        id: 'spu-1',
        name: '鲜牛奶',
        spuUrls: ['a.jpg'],
        salesPrice: 12.9,
        salesVolume: 30,
        stock: 64,
      }],
    })

    expect(item).toMatchObject({
      id: 'spu-1',
      imageUrl: 'a.jpg',
      name: '鲜牛奶',
      price: 12.9,
      sales: 30,
      stock: 64,
    })
  })

  it('freight_type=0 -> freeShipping=true，其它值不设置', () => {
    const [free] = normalizeRetailGoods([{ id: 'a', freightType: '0' }])
    const [paid] = normalizeRetailGoods([{ id: 'b', freightType: '1' }])
    const [none] = normalizeRetailGoods([{ id: 'c' }])

    expect(free.freeShipping).toBe(true)
    expect(paid.freeShipping).toBeUndefined()
    expect(none.freeShipping).toBeUndefined()
  })

  it('优先取 specsInfo 字段', () => {
    const [item] = normalizeRetailGoods([{ id: 'a', specsInfo: '950ml/瓶' }])
    expect(item.specsInfo).toBe('950ml/瓶')
  })

  it('无 specsInfo 时从 goodsSkus[0].specsArr 拼接', () => {
    const [item] = normalizeRetailGoods([{
      id: 'a',
      goodsSkus: [{ specsArr: [{ specsValueName: '2.5kg' }, { specsValueName: '袋装' }] }],
    }])
    expect(item.specsInfo).toBe('2.5kg；袋装')
  })

  it('单规格的「默认」不当作规格展示（避免噪音）', () => {
    const [fromField] = normalizeRetailGoods([{ id: 'a', specsInfo: '默认' }])
    const [fromSku] = normalizeRetailGoods([{ id: 'b', goodsSkus: [{ specsArr: [{ specsValueName: '默认' }] }] }])

    expect(fromField.specsInfo).toBeUndefined()
    expect(fromSku.specsInfo).toBeUndefined()
  })

  it('接口未返回规格时不做编造', () => {
    const [item] = normalizeRetailGoods([{ id: 'a', name: '商品' }])
    expect(item.specsInfo).toBeUndefined()
  })
})

describe('normalizeCoupons 已领取状态透传', () => {
  it('保留服务端下发的 userReceiveCount，供刷新后还原「已领取」', () => {
    const [claim] = normalizeCoupons({ records: [{ id: 'c1', couponName: '满减券', userReceiveCount: 2 }] })
    const [guest] = normalizeCoupons({ records: [{ id: 'c2', couponName: '满减券' }] })

    expect(claim.userReceiveCount).toBe(2)
    expect(guest.userReceiveCount).toBeNull()
  })

  it('字符串计数据同样归一化为数字，避免 Number 比较失效', () => {
    const [item] = normalizeCoupons([{ id: 'c1', userReceiveCount: '1' }])

    expect(item.userReceiveCount).toBe(1)
  })

  it('输出券类型标签，供票面胶囊展示', () => {
    const [amountCoupon] = normalizeCoupons([{ id: 'c1', couponType: '1', amount: 5 }])
    const [discountCoupon] = normalizeCoupons([{ id: 'c2', couponType: '2', discount: 8 }])
    const [legacy] = normalizeCoupons([{ id: 'c3' }])

    expect(amountCoupon.typeLabel).toBe('满减券')
    expect(discountCoupon.typeLabel).toBe('折扣券')
    // 老数据未下发 couponType 时按满减券处理，与后端默认值一致
    expect(legacy.typeLabel).toBe('满减券')
  })

  it('不影响既有字段（normalizeCoupons 只做增量透传）', () => {
    const [item] = normalizeCoupons([{ id: 'c1', couponName: '折扣券', couponType: '2', discount: 8, threshold: 100 }])

    expect(item).toMatchObject({
      id: 'c1',
      thresholdText: '满100可用',
      title: '折扣券',
      value: '8折',
    })
  })
})
