import type { ListMemberPriceDisplay } from '../components/diy/retail-types'
import type { ClearancePriceDisplay } from './clearance-price'

import { describe, expect, it } from 'vitest'

import { emptyClearancePriceDisplay } from './clearance-price'
import { emptyListMemberPriceDisplay } from './member-price'
import { resolveListZonePrice, socialProofIcon, splitSocialProofText, zonePriceBadgeText } from './zone-price'

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
      priceTagText: null,
      socialProofKind: null,
      socialProofText: null,
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
      priceTagText: null,
      socialProofKind: null,
      socialProofText: null,
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

  it('秒杀价命中：主价秒杀价、划线当前售价、角标 seckill，压过会员/出清（硬互斥）', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      seckillPrice: 16.92,
      memberDisplay: memberDisplay(),
      clearanceDisplay: clearanceDisplay({ clearancePrice: 29.9 }),
    })
    expect(result).toEqual({
      badgeKind: 'seckill',
      guideText: null,
      mainPrice: 16.92,
      priceTagText: null,
      socialProofKind: null,
      socialProofText: null,
      strikePrice: 39.9,
    })
  })

  it('秒杀价倒挂（不低于售价）按未命中处理，回落会员/出清口径', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      seckillPrice: 45,
      memberDisplay: memberDisplay(),
    })
    expect(result.badgeKind).toBe('member')
    expect(result.mainPrice).toBe(35.91)
  })

  it('新人价命中（会员/出清/秒杀未命中）：主价新人价、划线当前售价、角标 newbie', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      originalPrice: 51.87,
      newbiePrice: 0.01,
    })
    expect(result).toEqual({
      badgeKind: 'newbie',
      guideText: null,
      mainPrice: 0.01,
      priceTagText: null,
      socialProofKind: null,
      socialProofText: null,
      strikePrice: 39.9,
    })
  })

  it('新人价低于会员价：新人价覆盖（2026-11-08 起身份价取低，¥1 钩子不再被会员价吞掉）', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      newbiePrice: 0.01,
      memberDisplay: memberDisplay(),
    })
    expect(result.badgeKind).toBe('newbie')
    expect(result.mainPrice).toBe(0.01)
    expect(result.strikePrice).toBe(39.9)
  })

  it('新人价高于会员价：会员价保留（取低口径，同价也归属会员）', () => {
    const higher = resolveListZonePrice({
      salesPrice: 39.9,
      newbiePrice: 38,
      memberDisplay: memberDisplay(),
    })
    expect(higher.badgeKind).toBe('member')
    expect(higher.mainPrice).toBe(35.91)

    const equal = resolveListZonePrice({
      salesPrice: 39.9,
      newbiePrice: 35.91,
      memberDisplay: memberDisplay(),
    })
    expect(equal.badgeKind).toBe('member')
    expect(equal.mainPrice).toBe(35.91)
  })

  it('新人价被秒杀压制（引擎优先级：秒杀硬互斥）', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      seckillPrice: 16.92,
      newbiePrice: 0.01,
    })
    expect(result.badgeKind).toBe('seckill')
    expect(result.mainPrice).toBe(16.92)
  })

  it('新人价倒挂/非正数按未命中处理，回落普通划线口径', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      originalPrice: 51.87,
      newbiePrice: 39.9,
    })
    expect(result.badgeKind).toBe('')
    expect(result.mainPrice).toBe(39.9)
    expect(result.strikePrice).toBe(51.87)
  })

  it('社证文案透传（任何价格组合都携带），空白/非字符串不展示', () => {
    const withProof = resolveListZonePrice({
      salesPrice: 39.9,
      socialProofText: ' 月4000+人已下单 ',
    })
    expect(withProof.socialProofText).toBe('月4000+人已下单')
    // 价签命中时同样透传
    const withSeckill = resolveListZonePrice({
      salesPrice: 39.9,
      seckillPrice: 16.92,
      socialProofText: '40%人回购',
    })
    expect(withSeckill.socialProofText).toBe('40%人回购')
    // 空白/非字符串不展示
    expect(resolveListZonePrice({ salesPrice: 39.9, socialProofText: '  ' }).socialProofText).toBeNull()
    expect(resolveListZonePrice({ salesPrice: 39.9, socialProofText: 123 }).socialProofText).toBeNull()
  })

  it('社证类型白名单透传：合法 kind 原样携带，脏数据/缺字段一律视为未知', () => {
    expect(
      resolveListZonePrice({ salesPrice: 39.9, socialProofText: '月售4000+', socialProofKind: 'buyer' }).socialProofKind,
    ).toBe('buyer')
    expect(
      resolveListZonePrice({ salesPrice: 39.9, socialProofText: '回购率40%', socialProofKind: 'repurchase' }).socialProofKind,
    ).toBe('repurchase')
    // 脏数据（伪造 kind）不透传，前端兜底图标
    expect(
      resolveListZonePrice({ salesPrice: 39.9, socialProofText: '月售4000+', socialProofKind: 'hack' }).socialProofKind,
    ).toBeNull()
    expect(resolveListZonePrice({ salesPrice: 39.9, socialProofText: '月售4000+' }).socialProofKind).toBeNull()
  })

  it('社证图标映射：回购率=refresh / 回头客=heart / 月售=人群，未知兜底人群', () => {
    expect(socialProofIcon('repurchase')).toBe('refresh')
    expect(socialProofIcon('repeat')).toBe('heart')
    expect(socialProofIcon('buyer')).toBe('usergroup')
    expect(socialProofIcon('hack')).toBe('usergroup')
    expect(socialProofIcon(null)).toBe('usergroup')
    expect(socialProofIcon(undefined)).toBe('usergroup')
  })
})

describe('zonePriceBadgeText 角标文案', () => {
  it('会员价/捡漏价/无角标', () => {
    expect(zonePriceBadgeText('member')).toBeTruthy()
    expect(zonePriceBadgeText('clearance')).toBeTruthy()
    expect(zonePriceBadgeText('')).toBe('')
  })

  it('秒杀价/新人价', () => {
    expect(zonePriceBadgeText('seckill')).toBeTruthy()
    expect(zonePriceBadgeText('newbie')).toBeTruthy()
  })
})

describe('splitSocialProofText 社证数字强调拆分', () => {
  it('formatter 全部成文形态：数字段（含万+/%）拆出加粗', () => {
    expect(splitSocialProofText('月1000+人已下单')).toEqual({ before: '月', number: '1000+', after: '人已下单' })
    expect(splitSocialProofText('月6万+人已下单')).toEqual({ before: '月', number: '6万+', after: '人已下单' })
    expect(splitSocialProofText('月回头客1000+人')).toEqual({ before: '月回头客', number: '1000+', after: '人' })
    expect(splitSocialProofText('30%人回购')).toEqual({ before: '', number: '30%', after: '人回购' })
  })

  it('无数字/脏数据：整句落后缀，渲染等价原文直出', () => {
    expect(splitSocialProofText('热销中')).toEqual({ before: '', number: '', after: '热销中' })
    expect(splitSocialProofText('')).toEqual({ before: '', number: '', after: '' })
    expect(splitSocialProofText(null)).toEqual({ before: '', number: '', after: '' })
    expect(splitSocialProofText(123)).toEqual({ before: '', number: '', after: '' })
  })

  it('拆分不丢字：三段拼接恒等于原文', () => {
    for (const text of ['月1000+人已下单', '月6万+人已下单', '30%人回购', '月回头客4000+人']) {
      const parts = splitSocialProofText(text)
      expect(parts.before + parts.number + parts.after).toBe(text)
    }
  })
})

describe('resolveListZonePrice 协议价（2026-11-14 B2B）', () => {
  it('协议价命中：主价协议价、划线当前售价、角标 policy，压过秒杀/会员/出清（合同价锁定）', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      policyPrice: 33,
      seckillPrice: 30,
      memberDisplay: memberDisplay(),
      clearanceDisplay: clearanceDisplay(),
    })
    expect(result.badgeKind).toBe('policy')
    expect(result.mainPrice).toBe(33)
    expect(result.strikePrice).toBe(39.9)
    expect(result.guideText).toBeNull()
  })

  it('协议价倒挂（≥售价）/非法值视为未命中：回退秒杀价', () => {
    const inverted = resolveListZonePrice({ salesPrice: 39.9, policyPrice: 39.9 })
    expect(inverted.badgeKind).toBe('')
    const invalid = resolveListZonePrice({ salesPrice: 39.9, policyPrice: 0 })
    expect(invalid.badgeKind).toBe('')
    const withSeckill = resolveListZonePrice({ salesPrice: 39.9, policyPrice: -1, seckillPrice: 30 })
    expect(withSeckill.badgeKind).toBe('seckill')
  })

  it('角标文案：协议价', () => {
    expect(zonePriceBadgeText('policy')).toBe('协议价')
  })
})

describe('resolveListZonePrice 档期特供 / 阶梯价（2026-11-14 B2B）', () => {
  it('特供价低于会员价：身份价家族取低，角标 special、划线当前售价', () => {
    const result = resolveListZonePrice({
      salesPrice: 39.9,
      specialPrice: 30,
      memberDisplay: memberDisplay(),
    })
    expect(result.badgeKind).toBe('special')
    expect(result.mainPrice).toBe(30)
    expect(result.strikePrice).toBe(39.9)
    expect(result.guideText).toBeNull()
  })

  it('特供价高于会员价/出清价：家族仍取最低者（特供不无条件覆盖）', () => {
    const memberWins = resolveListZonePrice({
      salesPrice: 39.9,
      specialPrice: 38,
      memberDisplay: memberDisplay(),
    })
    expect(memberWins.badgeKind).toBe('member')
    expect(memberWins.mainPrice).toBe(35.91)

    const clearanceWins = resolveListZonePrice({
      salesPrice: 39.9,
      specialPrice: 38,
      memberDisplay: memberDisplay(),
      clearanceDisplay: clearanceDisplay({ clearancePrice: 20, originalPrice: 39.9 }),
    })
    expect(clearanceWins.badgeKind).toBe('clearance')
    expect(clearanceWins.mainPrice).toBe(20)
  })

  it('同价归属：特供 > 会员 > 出清', () => {
    const specialTie = resolveListZonePrice({
      salesPrice: 39.9,
      specialPrice: 35.91,
      memberDisplay: memberDisplay(),
    })
    expect(specialTie.badgeKind).toBe('special')

    const memberTie = resolveListZonePrice({
      salesPrice: 39.9,
      memberDisplay: memberDisplay({ memberPrice: 20 }),
      clearanceDisplay: clearanceDisplay({ clearancePrice: 20, originalPrice: 39.9 }),
    })
    expect(memberTie.badgeKind).toBe('member')
  })

  it('特供不覆盖协议价与秒杀（两者命中即终态）', () => {
    const policy = resolveListZonePrice({ salesPrice: 39.9, specialPrice: 20, policyPrice: 33 })
    expect(policy.badgeKind).toBe('policy')
    expect(policy.mainPrice).toBe(33)

    const seckill = resolveListZonePrice({ salesPrice: 39.9, specialPrice: 20, seckillPrice: 30 })
    expect(seckill.badgeKind).toBe('seckill')
    expect(seckill.mainPrice).toBe(30)
  })

  it('新人价仅严格低于特供价才覆盖；同价保留特供角标', () => {
    const lower = resolveListZonePrice({ salesPrice: 39.9, specialPrice: 20, newbiePrice: 1 })
    expect(lower.badgeKind).toBe('newbie')
    expect(lower.mainPrice).toBe(1)

    const tie = resolveListZonePrice({ salesPrice: 39.9, specialPrice: 20, newbiePrice: 20 })
    expect(tie.badgeKind).toBe('special')
  })

  it('特供价倒挂/非法值视为未命中：回落会员价口径', () => {
    expect(resolveListZonePrice({ salesPrice: 39.9, specialPrice: 39.9, memberDisplay: memberDisplay() }).badgeKind)
      .toBe('member')
    expect(resolveListZonePrice({ salesPrice: 39.9, specialPrice: 0, memberDisplay: memberDisplay() }).badgeKind)
      .toBe('member')
    expect(resolveListZonePrice({ salesPrice: 39.9, specialPrice: 'abc' }).badgeKind).toBe('')
  })

  it('阶梯价（商详按当前数量取档）严格更低才接管，同价保留身份角标', () => {
    const ladderWins = resolveListZonePrice({
      salesPrice: 39.9,
      ladderPrice: 28,
      memberDisplay: memberDisplay(),
      specialPrice: 30,
    })
    expect(ladderWins.badgeKind).toBe('ladder')
    expect(ladderWins.mainPrice).toBe(28)
    expect(ladderWins.strikePrice).toBe(39.9)

    const identityTie = resolveListZonePrice({ salesPrice: 39.9, ladderPrice: 30, specialPrice: 30 })
    expect(identityTie.badgeKind).toBe('special')

    // 卡片不传 ladderPrice：数量未定，阶梯价不参与卡面判定
    expect(resolveListZonePrice({ salesPrice: 39.9, memberDisplay: memberDisplay() }).badgeKind).toBe('member')
  })

  it('协议价锁定后阶梯价/特供价一律不覆盖；未达 minQty 时调用方不传协议价', () => {
    const locked = resolveListZonePrice({ salesPrice: 39.9, policyPrice: 33, ladderPrice: 20, specialPrice: 25 })
    expect(locked.badgeKind).toBe('policy')
    expect(locked.mainPrice).toBe(33)

    // 件数 < minQty：调用方不传 policyPrice，身份价家族（含阶梯）照常生效
    const belowMinQty = resolveListZonePrice({ salesPrice: 39.9, ladderPrice: 20, specialPrice: 25 })
    expect(belowMinQty.badgeKind).toBe('ladder')
    expect(belowMinQty.mainPrice).toBe(20)
  })

  it('阶梯价倒挂/非法值视为未命中；新人价仍可严格更低覆盖', () => {
    expect(resolveListZonePrice({ salesPrice: 39.9, ladderPrice: 40 }).badgeKind).toBe('')
    expect(resolveListZonePrice({ salesPrice: 39.9, ladderPrice: -1 }).badgeKind).toBe('')
    const newbie = resolveListZonePrice({ salesPrice: 39.9, ladderPrice: 30, newbiePrice: 20 })
    expect(newbie.badgeKind).toBe('newbie')
  })

  it('角标文案：档期特供 / 阶梯价', () => {
    expect(zonePriceBadgeText('special')).toBe('档期特供')
    expect(zonePriceBadgeText('ladder')).toBe('阶梯价')
  })
})
