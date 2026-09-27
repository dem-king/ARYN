import { describe, expect, it } from 'vitest'

import {
  resolveDeliveryRow,
  resolvePurchaseDecision,
  resolveSpecRow,
} from './goods-purchase'

const singleSpecGoods = {
  enableSpecs: '0',
  goodsSkus: [{ specsArr: [{ specsValueName: '约600g/份' }] }],
}

const singleSpecWithoutSpecValue = {
  enableSpecs: '0',
  goodsSkus: [{ specsArr: [] }],
}

const multiSpecGoods = {
  enableSpecs: '1',
  goodsSkus: [
    { specsArr: [{ specsValueName: '256GB' }] },
    { specsArr: [{ specsValueName: '512GB' }] },
  ],
}

describe('resolvePurchaseDecision', () => {
  it('单规格商品不需要用户选规格', () => {
    expect(resolvePurchaseDecision(singleSpecGoods).needChoose).toBe(false)
  })

  it('多规格商品必须让用户选规格', () => {
    expect(resolvePurchaseDecision(multiSpecGoods).needChoose).toBe(true)
  })

  it('单规格商品带出唯一 SKU 的规格值用于只读展示', () => {
    expect(resolvePurchaseDecision(singleSpecGoods).specText).toBe('约600g/份')
  })

  it('多规格商品不预先取规格值（价格与库存随所选规格变化）', () => {
    expect(resolvePurchaseDecision(multiSpecGoods).specText).toBe('')
  })

  it('单规格多个规格值用中文分号拼接，与弹层 addCart 的 specsInfo 口径一致', () => {
    expect(
      resolvePurchaseDecision({
        enableSpecs: '0',
        goodsSkus: [{ specsArr: [{ specsValueName: '500ml' }, { specsValueName: '红色' }] }],
      }).specText,
    ).toBe('500ml；红色')
  })

  it('缺少 enableSpecs 时按需选规格处理，不盲目直购', () => {
    expect(resolvePurchaseDecision({}).needChoose).toBe(true)
    expect(resolvePurchaseDecision(null).needChoose).toBe(true)
    expect(resolvePurchaseDecision(undefined).needChoose).toBe(true)
  })

  it('未知 enableSpecs 取值同样按需选规格处理（与后端 SINGLE_SPEC 口径对齐）', () => {
    expect(resolvePurchaseDecision({ enableSpecs: 'whatever' }).needChoose).toBe(true)
  })

  it('单规格但缺 SKU 明细时不崩，规格值为空串', () => {
    expect(resolvePurchaseDecision({ enableSpecs: '0' }).specText).toBe('')
    expect(resolvePurchaseDecision({ enableSpecs: '0', goodsSkus: [] }).specText).toBe('')
  })

  it('规格值里的空值被过滤，不会拼出多余分隔符', () => {
    expect(
      resolvePurchaseDecision({
        enableSpecs: '0',
        goodsSkus: [{ specsArr: [{ specsValueName: '' }, { specsValueName: '红色' }] }],
      }).specText,
    ).toBe('红色')
  })
})

describe('resolveSpecRow', () => {
  it('多规格未选时提示选择规格且可点', () => {
    expect(resolveSpecRow(resolvePurchaseDecision(multiSpecGoods), '')).toEqual({
      visible: true,
      text: '选择规格',
      tappable: true,
    })
  })

  it('多规格选过之后显示已选规格', () => {
    expect(resolveSpecRow(resolvePurchaseDecision(multiSpecGoods), '256GB').text).toBe('256GB')
  })

  it('多规格已选值为纯空白时仍提示选择规格', () => {
    expect(resolveSpecRow(resolvePurchaseDecision(multiSpecGoods), '   ').text).toBe('选择规格')
  })

  it('单规格有真实规格值时展示该值（不再是「选择规格」闸门）', () => {
    const row = resolveSpecRow(resolvePurchaseDecision(singleSpecGoods), '')
    expect(row).toEqual({ visible: true, text: '约600g/份', tappable: true })
  })

  it('单规格没有规格值时整行隐藏（规格已在商品名/船供箱规中体现）', () => {
    const row = resolveSpecRow(resolvePurchaseDecision(singleSpecWithoutSpecValue), '')
    expect(row.visible).toBe(false)
  })

  it('单规格行不回显弹层选中的「默认」占位值', () => {
    // 回归：详情页原先用 selectArr !== '默认' 过滤，导致该行永远显示「选择规格」
    expect(resolveSpecRow(resolvePurchaseDecision(singleSpecGoods), '默认').text).toBe('约600g/份')
  })
})

describe('resolveDeliveryRow', () => {
  it('已绑定船舶上下文时展示内部配送，措辞与结算页一致', () => {
    const row = resolveDeliveryRow({
      hasVesselContext: true,
      vesselName: '悦航1号',
      portName: '上海港',
      berth: '3号泊位',
      deliveryWindowStart: '2026-09-28 08:00:00',
      deliveryWindowEnd: '2026-09-28 12:00:00',
    })
    expect(row.text).toBe('配送至 悦航1号')
    expect(row.detail).toBe('上海港 3号泊位（2026-09-28 08:00:00 ~ 2026-09-28 12:00:00）')
    expect(row.tappable).toBe(false)
  })

  it('内部配送不展示包邮/运费与收货地址', () => {
    const row = resolveDeliveryRow({
      hasVesselContext: true,
      vesselName: '悦航1号',
      freightType: '0',
      addressText: '北京市东城区某小区',
    })
    expect(row.text).not.toContain('包邮')
    expect(row.detail).not.toContain('北京市')
  })

  it('船舶名缺失时回落为通用文案，不渲染空占位', () => {
    const row = resolveDeliveryRow({ hasVesselContext: true })
    expect(row.text).toBe('配送至 所在船舶')
    expect(row.detail).toBe('')
  })

  it('普通场景保留包邮与收货地址，可点去改地址', () => {
    const row = resolveDeliveryRow({
      hasVesselContext: false,
      freightType: '0',
      addressText: '北京市东城区某小区',
    })
    expect(row).toEqual({
      text: '包邮',
      detail: '配送至：北京市东城区某小区',
      tappable: true,
    })
  })

  it('固定运费商品展示运费金额', () => {
    const row = resolveDeliveryRow({ hasVesselContext: false, freightType: '1', fixedFreightPrice: 8 })
    expect(row.text).toBe('运费：8元')
  })

  it('固定运费但金额缺失时按 0 元展示，不出现 undefined', () => {
    const row = resolveDeliveryRow({ hasVesselContext: false, freightType: '1' })
    expect(row.text).toBe('运费：0元')
  })

  it('普通场景未设置收货地址时副文案为空，但行仍可点去添加地址', () => {
    const row = resolveDeliveryRow({ hasVesselContext: false, freightType: '0' })
    expect(row.detail).toBe('')
    expect(row.tappable).toBe(true)
  })
})
