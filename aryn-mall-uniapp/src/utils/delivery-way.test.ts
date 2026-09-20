import { describe, expect, it } from 'vitest'

import {
  DELIVERY_WAY_EXPRESS,
  DELIVERY_WAY_MALL_DELIVERY,
  DELIVERY_WAY_SELF_PICKUP,
  DELIVERY_WAY_VESSEL_INTERNAL,
  deliveryWayLabel,
  deliveryWayOptions,
} from './delivery-way'

describe('deliveryWayLabel', () => {
  it('covers all four backend delivery ways', () => {
    expect(deliveryWayLabel(DELIVERY_WAY_EXPRESS)).toBe('普通快递')
    expect(deliveryWayLabel(DELIVERY_WAY_SELF_PICKUP)).toBe('上门自提')
    expect(deliveryWayLabel(DELIVERY_WAY_MALL_DELIVERY)).toBe('商城配送')
    expect(deliveryWayLabel(DELIVERY_WAY_VESSEL_INTERNAL)).toBe('公司港口/船舶内部配送')
  })

  it('labels way=4 explicitly instead of falling through to a default branch', () => {
    // 回归守门：历史上 way=4 被三处三元链的 else 兜底显示成
    // 「普通快递」「无需配送」「等待提货」，且都不是内部配送的正确文案。
    const internal = deliveryWayLabel(DELIVERY_WAY_VESSEL_INTERNAL)
    expect(internal).not.toBe('普通快递')
    expect(internal).not.toBe('无需配送')
    expect(internal).not.toBe('等待提货')
    expect(internal).toContain('内部配送')
  })

  it('returns empty string for missing value and an explicit label for unknown ones', () => {
    expect(deliveryWayLabel('')).toBe('')
    expect(deliveryWayLabel(null)).toBe('')
    expect(deliveryWayLabel(undefined)).toBe('')
    // 未知取值不得静默兜底为「普通快递」，否则会误导用户
    expect(deliveryWayLabel('9')).toBe('未知配送方式')
  })
})

describe('deliveryWayOptions', () => {
  it('omits internal delivery when the user has no vessel context', () => {
    const options = deliveryWayOptions()
    expect(options.map(option => option.value)).toEqual([
      DELIVERY_WAY_EXPRESS,
      DELIVERY_WAY_SELF_PICKUP,
      DELIVERY_WAY_MALL_DELIVERY,
    ])
  })

  it('offers internal delivery first when vessel context exists', () => {
    const options = deliveryWayOptions({ withVesselInternal: true })
    expect(options[0]).toEqual({
      value: DELIVERY_WAY_VESSEL_INTERNAL,
      name: '公司港口/船舶内部配送',
    })
    expect(options).toHaveLength(4)
  })

  it('never exposes an option without a label', () => {
    for (const option of deliveryWayOptions({ withVesselInternal: true })) {
      expect(option.name).toBeTruthy()
      expect(option.name).not.toBe('未知配送方式')
    }
  })
})
