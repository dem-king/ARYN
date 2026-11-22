import { describe, expect, it } from 'vitest'
import { displayFixedDiscountName, displayOfferName, formatHintAmount, groupGiftOffers, resolveGiftProgress } from './goods-hints'

const UNAVAILABLE: unknown = undefined

function giftTier(activityId: string, name: string, thresholdAmount: number, quantity: number) {
  return {
    activityId,
    name,
    thresholdAmount,
    gifts: [{ name: '100%椰子水 1L/盒', quantity }],
  }
}

describe('displayOfferName', () => {
  it('strips the tier suffix and keeps names without one', () => {
    expect(displayOfferName('满额赠礼·椰子水（3千档）')).toBe('满额赠礼·椰子水')
    expect(displayOfferName('满额赠礼·椰子水')).toBe('满额赠礼·椰子水')
    // 括号里不是档位描述时原样保留，避免误伤「（限量）」这类正常后缀
    expect(displayOfferName('满额赠礼·椰子水（限量）')).toBe('满额赠礼·椰子水（限量）')
    expect(displayOfferName(UNAVAILABLE)).toBe('')
  })
})

describe('displayFixedDiscountName', () => {
  it('hides percentage tier suffixes and preserves the activity name', () => {
    expect(displayFixedDiscountName('海员整船满减·4%档')).toBe('海员整船满减')
    expect(displayFixedDiscountName('企业采购满减（3.5％档）')).toBe('企业采购满减')
    expect(displayFixedDiscountName('企业采购满减（指定商品）')).toBe('企业采购满减（指定商品）')
  })
})

describe('formatHintAmount', () => {
  it('adds thousand separators and drops meaningless trailing zeros', () => {
    expect(formatHintAmount(3000)).toBe('3,000')
    expect(formatHintAmount('10000.00')).toBe('10,000')
    expect(formatHintAmount(4.5)).toBe('4.5')
    expect(formatHintAmount(0)).toBe('0')
    expect(formatHintAmount('abc')).toBe('0')
    expect(formatHintAmount(-1200)).toBe('-1,200')
  })
})

describe('groupGiftOffers', () => {
  it('merges the same activity tiers into one group sorted by threshold', () => {
    const groups = groupGiftOffers([
      giftTier('a3', '满额赠礼·椰子水（3万档）', 30000, 12),
      giftTier('a1', '满额赠礼·椰子水（3千档）', 3000, 2),
      giftTier('a2', '满额赠礼·椰子水（1万档）', 10000, 6),
    ])
    expect(groups).toHaveLength(1)
    expect(groups[0]?.name).toBe('满额赠礼·椰子水')
    expect(groups[0]?.rows.map(row => row.thresholdAmount)).toEqual([3000, 10000, 30000])
    expect(groups[0]?.rows[2]?.gifts).toEqual([{ name: '100%椰子水 1L/盒', quantity: 12 }])
  })

  it('keeps offers with different gifts in separate groups', () => {
    const groups = groupGiftOffers([
      giftTier('a1', '满额赠礼·椰子水', 3000, 2),
      {
        activityId: 'b1',
        name: '满额赠礼·矿泉水',
        thresholdAmount: 5000,
        gifts: [{ name: '矿泉水 550ml', quantity: 12 }],
      },
    ])
    expect(groups.map(group => group.name)).toEqual(['满额赠礼·椰子水', '满额赠礼·矿泉水'])
  })

  it('drops invalid hints and keeps the larger quantity for duplicate thresholds', () => {
    const groups = groupGiftOffers([
      giftTier('a1', '满额赠礼·椰子水', 3000, 2),
      giftTier('a2', '满额赠礼·椰子水', 3000, 6),
      { activityId: 'bad', name: '', thresholdAmount: 100, gifts: [] },
      { activityId: 'bad2', name: '坏数据', thresholdAmount: -1, gifts: [{ name: 'x', quantity: 1 }] },
    ])
    expect(groups).toHaveLength(1)
    expect(groups[0]?.rows).toHaveLength(1)
    expect(groups[0]?.rows[0]?.gifts[0]?.quantity).toBe(6)
  })

  it('returns an empty list for empty input', () => {
    expect(groupGiftOffers(null)).toEqual([])
    expect(groupGiftOffers([])).toEqual([])
  })
})

describe('resolveGiftProgress', () => {
  const rows = [3000, 10000, 30000].map((thresholdAmount, index) => ({
    activityId: `a${index}`,
    thresholdAmount,
    gifts: [{ name: '100%椰子水 1L/盒', quantity: index + 2 }],
  }))

  it('returns null without a valid amount or tiers', () => {
    expect(resolveGiftProgress(0, rows)).toBeNull()
    expect(resolveGiftProgress('abc', rows)).toBeNull()
    expect(resolveGiftProgress(249, [])).toBeNull()
  })

  it('reports the gap to the first tier and clamps the percentage', () => {
    expect(resolveGiftProgress(249, rows)).toEqual({
      mode: 'progress',
      percent: 8,
      gap: 2751,
      firstTier: true,
    })
    // 2999.99 / 3000 四舍五入是 100，进度条最多停在 99，避免假满
    expect(resolveGiftProgress(2999.99, rows)).toMatchObject({ percent: 99 })
  })

  it('switches to the next tier once the first is passed', () => {
    expect(resolveGiftProgress(5000, rows)).toEqual({
      mode: 'progress',
      percent: 50,
      gap: 5000,
      firstTier: false,
    })
  })

  it('degrades to a single line when the gap is over 20x the current amount', () => {
    expect(resolveGiftProgress(24.9, rows)).toEqual({ mode: 'compact', threshold: 3000 })
  })

  it('reports the highest tier instead of drawing a full bar', () => {
    expect(resolveGiftProgress(40000, rows)).toEqual({ mode: 'reached' })
  })
})
