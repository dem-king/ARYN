import { describe, expect, it } from 'vitest'
import {
  clearanceWinsOverMember,
  emptyClearancePriceDisplay,
  resolveClearancePriceDisplay,
} from './clearance-price'

describe('出清价展示口径', () => {
  const vo = {
    items: [
      { skuId: 'sku-1', salesPrice: 30, clearancePrice: 18, clearanceExpireTime: '2099-01-01T00:00:00' },
    ],
  }

  it('命中有效出清价时返回主价与划线价', () => {
    expect(resolveClearancePriceDisplay(vo, 'sku-1')).toMatchObject({
      showClearancePrice: true,
      clearancePrice: 18,
      originalPrice: 30,
    })
  })

  it('未命中/倒挂/已过期一律不展示', () => {
    expect(resolveClearancePriceDisplay(vo, 'sku-2')).toEqual(emptyClearancePriceDisplay())
    expect(resolveClearancePriceDisplay({ items: [{ skuId: 'sku-1', salesPrice: 18, clearancePrice: 18 }] }, 'sku-1'))
      .toEqual(emptyClearancePriceDisplay())
    expect(
      resolveClearancePriceDisplay(
        { items: [{ skuId: 'sku-1', salesPrice: 30, clearancePrice: 18, clearanceExpireTime: '2020-01-01T00:00:00' }] },
        'sku-1',
      ),
    ).toEqual(emptyClearancePriceDisplay())
  })

  it('与会员价取低：同价时归属会员价', () => {
    const clearance = resolveClearancePriceDisplay(vo, 'sku-1')
    expect(clearanceWinsOverMember(clearance, 20)).toBe(true)
    expect(clearanceWinsOverMember(clearance, 18)).toBe(false)
    expect(clearanceWinsOverMember(clearance, null)).toBe(true)
    expect(clearanceWinsOverMember(emptyClearancePriceDisplay(), 20)).toBe(false)
  })
})
