import type { ClearancePriceDisplay } from './clearance-price'
import type { ListMemberPriceDisplay } from '../components/diy/retail-types'

import { describe, expect, it } from 'vitest'

import { emptyClearancePriceDisplay } from './clearance-price'
import { emptyListMemberPriceDisplay } from './member-price'
import { resolveListZonePrice, zonePriceBadgeText } from './zone-price'

/** 造一份「会员价可展示」的列表展示 */
function memberDisplay(overrides: Partial<ListMemberPriceDisplay> = {}): ListMemberPriceDisplay {
  return {
    ...emptyListMemberPriceDisplay(),
    matched: true,
    memberPrice: 35.91,
    showMemberPrice: true,
    strikePrice: 39.9,
    ...overrides,
  }
}

/** 造一份「出清价可展示」的展示 */
function clearanceDisplay(overrides: Partial<ClearancePriceDisplay> = {}): ClearancePriceDisplay {
  return {
    ...emptyClearancePriceDisplay(),
    showClearancePrice: true,
    clearancePrice: 5.9,
    originalPrice: 7.9,
    ...overrides,
  }
}

describe('resolveListZonePrice 专区价收敛', () => {
  it('会员价命中且达标：主价会员价、划线卡片价、角标 member、无引导', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      memberDisplay: memberDisplay(),
    })
    expect(result).toEqual({
      badgeKind: 'member',
      guideText: null,
      mainPrice: 35.91,
      strikePrice: 39.9,
    })
  })

  it('出清价低于会员价：出清价胜出，划线当前售价', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      memberDisplay: memberDisplay({ memberPrice: 35.91 }),
      clearanceDisplay: clearanceDisplay({ clearancePrice: 29.9 }),
    })
    expect(result.badgeKind).toBe('clearance')
    expect(result.mainPrice).toBe(29.9)
    expect(result.strikePrice).toBe(39.9)
    expect(result.guideText).toBeNull()
  })

  it('会员价低于出清价：会员价胜出', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      memberDisplay: memberDisplay({ memberPrice: 20 }),
      clearanceDisplay: clearanceDisplay({ clearancePrice: 29.9 }),
    })
    expect(result.badgeKind).toBe('member')
    expect(result.mainPrice).toBe(20)
  })

  it('会员价与出清价同价：归属会员价（与后端同价优先会员价一致）', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      memberDisplay: memberDisplay({ memberPrice: 25 }),
      clearanceDisplay: clearanceDisplay({ clearancePrice: 25 }),
    })
    expect(result.badgeKind).toBe('member')
    expect(result.mainPrice).toBe(25)
  })

  it('未达标/未登录：主价保持售价，透出会员专享引导，普通划线按原价口径', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      originalPrice: 51.87,
      memberDisplay: memberDisplay({
        memberPrice: 35.91,
        showMemberPrice: false,
        strikePrice: null,
        guideText: '会员专享 ¥35.91',
      }),
    })
    expect(result).toEqual({
      badgeKind: '',
      guideText: '会员专享 ¥35.91',
      mainPrice: 39.9,
      strikePrice: 51.87,
    })
  })

  it('出清价可展示时，不渲染会员引导（同一时刻只解释一个成交价）', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      memberDisplay: memberDisplay({
        showMemberPrice: false,
        strikePrice: null,
        guideText: '会员专享 ¥35.91',
      }),
      clearanceDisplay: clearanceDisplay({ clearancePrice: 19.9 }),
    })
    expect(result.badgeKind).toBe('clearance')
    expect(result.guideText).toBeNull()
    expect(result.mainPrice).toBe(19.9)
  })

  it('专区价倒挂（不低于售价）时按未命中处理，不回退成假优惠', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      originalPrice: 51.87,
      memberDisplay: memberDisplay({ memberPrice: 39.9 }),
      clearanceDisplay: clearanceDisplay({ clearancePrice: 45 }),
    })
    expect(result.badgeKind).toBe('')
    expect(result.mainPrice).toBe(39.9)
    expect(result.strikePrice).toBe(51.87)
  })

  it('showOriginal=false 只隐藏划线，不隐藏专区主价与角标', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      showOriginal: false,
      memberDisplay: memberDisplay(),
    })
    expect(result.badgeKind).toBe('member')
    expect(result.mainPrice).toBe(35.91)
    expect(result.strikePrice).toBeNull()
  })

  it('无任何专区价：主价售价，普通划线按「原价 > 售价」口径', () => {
    const result = resolveListZonePrice({
      salesPrice: 30,
      originalPrice: 30,
    })
    expect(result.mainPrice).toBe(30)
    expect(result.strikePrice).toBeNull()
    expect(result.badgeKind).toBe('')
    expect(result.guideText).toBeNull()
  })

  it('脏数据不编造价格（非数值/0/负数售价）', () => {
    const result = resolveListZonePrice({
      salesPrice: 'not-a-number',
      memberDisplay: memberDisplay(),
    })
    expect(result.mainPrice).toBe(0)
    expect(result.badgeKind).toBe('')
  })

  it('金额统一四舍五入到分', () => {
    const result = resolveListZonePrice({
      salesPrice: 10.333,
      originalPrice: 12.3456,
    })
    expect(result.mainPrice).toBe(10.333)
    expect(result.strikePrice).toBe(12.35)
  })
})

describe('zonePriceBadgeText 角标文案', () => {
  it('会员价/捡漏价/无角标', () => {
    expect(zonePriceBadgeText('member')).toBeTruthy()
    expect(zonePriceBadgeText('clearance')).toBeTruthy()
    expect(zonePriceBadgeText('')).toBe('')
  })
})
