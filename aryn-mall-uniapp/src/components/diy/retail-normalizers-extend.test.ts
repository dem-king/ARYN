import { describe, expect, it } from 'vitest'

import { normalizeCoupons, normalizeRetailGoods, normalizeRetailNewbieFloor } from './retail-normalizers'

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

  it('保留商品子标题，供卡片介绍行渲染', () => {
    const [item] = normalizeRetailGoods([{ id: 'a', subTitle: '低温冷藏 每日直送' }])
    expect(item.subTitle).toBe('低温冷藏 每日直送')
  })

  it('子标题缺失、为空或仅空白时不生成空介绍行', () => {
    const [absent] = normalizeRetailGoods([{ id: 'a' }])
    const [empty] = normalizeRetailGoods([{ id: 'b', subTitle: '' }])
    const [blank] = normalizeRetailGoods([{ id: 'c', subTitle: '   ' }])
    expect(absent.subTitle).toBeUndefined()
    expect(empty.subTitle).toBeUndefined()
    expect(blank.subTitle).toBeUndefined()
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

describe('normalizeRetailNewbieFloor', () => {
  it('单对象响应归一化为单元素列表并保留新人价与划线原价', () => {
    const items = normalizeRetailNewbieFloor({
      activityId: 'act-1',
      activityName: '新人专享',
      endTime: '2026-11-01 00:00:00',
      limitPerUser: 1,
      registerWithinDays: 30,
      goodsList: [{
        spuId: 'spu-1',
        skuId: 'sku-1',
        goodsName: '黄豆芽500g',
        goodsImage: 'a.jpg',
        newbiePrice: 3.5,
        originalPrice: 4.55,
      }],
    })

    expect(items).toHaveLength(1)
    expect(items[0]).toMatchObject({
      activityId: 'act-1',
      limitPerUser: 1,
      registerWithinDays: 30,
    })
    expect(items[0].goodsList[0]).toMatchObject({
      skuId: 'sku-1',
      newbiePrice: 3.5,
      originalPrice: 4.55,
    })
  })

  it('无进行中活动（null）返回空列表', () => {
    expect(normalizeRetailNewbieFloor(null)).toEqual([])
    expect(normalizeRetailNewbieFloor({})).toEqual([])
  })

  it('限购数缺失时兜底为 1（与后端固定值口径一致）', () => {
    const items = normalizeRetailNewbieFloor({ activityId: 'act-1' })
    expect(items[0].limitPerUser).toBe(1)
  })
})
