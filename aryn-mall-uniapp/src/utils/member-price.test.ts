import type { AppMemberPriceVO } from '@/api/promotion'

import { describe, expect, it } from 'vitest'

import { emptyDiscountDisplay } from './discount-price'
import {
  emptyListMemberPriceDisplay,
  emptyMemberPriceDisplay,
  MEMBER_LIST_PRICE_TOLERANCE,
  MEMBER_PRICE_BADGE_TEXT,
  memberPriceGuideText,
  memberPriceItemsBySpuId,
  memberPriceUnitPrice,
  memberSkuPriceMap,
  resolveListMemberPrice,
  resolveMemberPriceDisplay,
  resolvePromotionPriority,
} from './member-price'
import { emptyNewbieDisplay } from './newbie-price'

/** 造一份「命中且达标」的会员价响应 */
function vo(overrides: Partial<AppMemberPriceVO> = {}): AppMemberPriceVO {
  return {
    eligible: true,
    items: [
      { memberPrice: 23.92, minLevelId: 'level-gold', minLevelName: '金卡会员', originalPrice: 29.9, skuId: 'sku-1', spuId: 'spu-1' },
    ],
    userDiscountRate: null,
    userLevelId: 'level-gold',
    userLevelName: '金卡会员',
    ...overrides,
  }
}

describe('memberSkuPriceMap 索引', () => {
  it('空响应返回空表', () => {
    expect(memberSkuPriceMap(null).size).toBe(0)
    expect(memberSkuPriceMap(undefined).size).toBe(0)
    expect(memberSkuPriceMap(vo({ items: [] })).size).toBe(0)
  })

  it('按 skuId 建索引，同 SKU 重复时后者覆盖', () => {
    const map = memberSkuPriceMap(vo({
      items: [
        { memberPrice: 23.92, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice: 29.9, skuId: 'sku-1', spuId: 'spu-1' },
        { memberPrice: 22.5, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice: 29.9, skuId: 'sku-1', spuId: 'spu-1' },
      ],
    }))

    expect(map.size).toBe(1)
    expect(map.get('sku-1')?.memberPrice).toBe(22.5)
  })

  it('跳过缺 SKU、非正价、非数值的脏数据，不编造价格', () => {
    const map = memberSkuPriceMap({
      eligible: true,
      items: [
        { memberPrice: 9.9, minLevelId: '', minLevelName: '', originalPrice: 19.9, skuId: '', spuId: 'spu-1' },
        { memberPrice: 0, minLevelId: '', minLevelName: '', originalPrice: 19.9, skuId: 'sku-2', spuId: 'spu-1' },
        { memberPrice: -1, minLevelId: '', minLevelName: '', originalPrice: 19.9, skuId: 'sku-3', spuId: 'spu-1' },
        { memberPrice: Number.NaN, minLevelId: '', minLevelName: '', originalPrice: 19.9, skuId: 'sku-4', spuId: 'spu-1' },
      ],
      userDiscountRate: null,
      userLevelId: null,
      userLevelName: null,
    })

    expect(map.size).toBe(0)
  })
})

describe('memberPriceGuideText 引导文案', () => {
  it('统一「会员专享 ¥x」，不拼等级名，金额去无意义尾零', () => {
    expect(memberPriceGuideText(9.9)).toBe('会员专享 ¥9.9')
    expect(memberPriceGuideText(10)).toBe('会员专享 ¥10')
    expect(memberPriceGuideText(9.99)).toBe('会员专享 ¥9.99')
  })

  it('价格无效时返回空串（不造 ¥0）', () => {
    expect(memberPriceGuideText(0)).toBe('')
    expect(memberPriceGuideText(-1)).toBe('')
    expect(memberPriceGuideText(Number.NaN)).toBe('')
  })
})

describe('resolveMemberPriceDisplay 资格三态', () => {
  it('达标（eligible=true）：主价切会员价 + 「会员价」角标 + 划线当前售价', () => {
    const display = resolveMemberPriceDisplay(vo(), 'sku-1')

    expect(display).toMatchObject({
      badgeText: MEMBER_PRICE_BADGE_TEXT,
      eligible: true,
      guideText: null,
      matched: true,
      memberPrice: 23.92,
      minLevelName: '金卡会员',
      originalPrice: 29.9,
      showMemberPrice: true,
      userLevelName: '金卡会员',
    })
    expect(memberPriceUnitPrice(29.9, display)).toBe(23.92)
  })

  it('已登录但不达标（eligible=false）：保持原价 + 引导文案，不切主价', () => {
    const display = resolveMemberPriceDisplay(vo({ eligible: false }), 'sku-1')

    expect(display).toMatchObject({
      badgeText: '',
      eligible: false,
      guideText: '会员专享 ¥23.92',
      matched: true,
      originalPrice: null,
      showMemberPrice: false,
    })
    // 未达标时成交单价仍是商品当前售价
    expect(memberPriceUnitPrice(29.9, display)).toBe(29.9)
  })

  it('未登录（eligible=null）：保持原价 + 引导文案（后端对未登录也下发活动价）', () => {
    const display = resolveMemberPriceDisplay(vo({ eligible: null, userLevelId: null, userLevelName: null }), 'sku-1')

    expect(display).toMatchObject({
      eligible: null,
      guideText: '会员专享 ¥23.92',
      showMemberPrice: false,
      userLevelName: '',
    })
    expect(memberPriceUnitPrice(29.9, display)).toBe(29.9)
  })

  it('未命中活动 / 无响应：空展示，成交价回落售价', () => {
    for (const empty of [
      resolveMemberPriceDisplay(null, 'sku-1'),
      resolveMemberPriceDisplay(undefined, 'sku-1'),
      resolveMemberPriceDisplay(vo(), 'sku-2'),
      resolveMemberPriceDisplay(vo(), ''),
    ]) {
      expect(empty.matched).toBe(false)
      expect(empty.showMemberPrice).toBe(false)
      expect(empty.badgeText).toBe('')
      expect(empty.guideText).toBeNull()
      expect(memberPriceUnitPrice(29.9, empty)).toBe(29.9)
    }
  })

  it('价格倒挂（会员价 ≥ 当前售价）回退原价：不切换、不划线、不出角标/引导', () => {
    for (const memberPrice of [29.9, 35]) {
      const display = resolveMemberPriceDisplay(vo({
        items: [{ memberPrice, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice: 29.9, skuId: 'sku-1', spuId: 'spu-1' }],
      }), 'sku-1')

      expect(display.matched).toBe(true)
      expect(display.showMemberPrice).toBe(false)
      expect(display.badgeText).toBe('')
      expect(display.originalPrice).toBeNull()
      // 没有真实优惠时不引导「专享 ¥35」（比原价还贵）
      expect(display.guideText).toBeNull()
      expect(memberPriceUnitPrice(29.9, display)).toBe(29.9)
    }
  })

  it('缺少划线基准价时不冒险切换主价（无法证明更低价）', () => {
    const display = resolveMemberPriceDisplay(vo({
      items: [{ memberPrice: 23.92, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice: 0, skuId: 'sku-1', spuId: 'spu-1' }],
    }), 'sku-1')

    expect(display.matched).toBe(true)
    expect(display.showMemberPrice).toBe(false)
    expect(display.originalPrice).toBeNull()
  })

  it('按 SKU 精确匹配：同 SPU 其他规格不串价', () => {
    const display = resolveMemberPriceDisplay(vo(), 'sku-2')

    expect(display.matched).toBe(false)
    expect(display.showMemberPrice).toBe(false)
  })
})

describe('resolveMemberPriceDisplay 达标取优（min(会员价, 售价×折扣率)，不叠加）', () => {
  /** 造「会员价 / 售价 / 折扣率」场景的响应与当前 SKU */
  function priceVo(memberPrice: number, originalPrice: number, userDiscountRate: number | null) {
    return vo({
      items: [{ memberPrice, minLevelId: 'l1', minLevelName: '金卡会员', originalPrice, skuId: 'sku-1', spuId: 'spu-1' }],
      userDiscountRate,
    })
  }

  it('会员价 90 / 售价 100 / 折扣率 0.9 → 显示 90（两者相等取会员价）', () => {
    const display = resolveMemberPriceDisplay(priceVo(90, 100, 0.9), 'sku-1')

    expect(display.showMemberPrice).toBe(true)
    expect(display.memberPrice).toBe(90)
    // 划线仍取当前售价
    expect(display.originalPrice).toBe(100)
    expect(display.badgeText).toBe('会员价')
  })

  it('会员价 95 / 售价 100 / 折扣率 0.9 → 显示 90（折扣率更低，取优）', () => {
    const display = resolveMemberPriceDisplay(priceVo(95, 100, 0.9), 'sku-1')

    expect(display.memberPrice).toBe(90)
    expect(display.originalPrice).toBe(100)
    expect(display.badgeText).toBe('会员价')
  })

  it('会员价 90 / 售价 100 / 折扣率 0.85 → 显示 85（取优不是叠加，不做折上折）', () => {
    // 取优 = min(90, 100×0.85) = 85；若错误地「会员价再打折」会得到 76.5
    const display = resolveMemberPriceDisplay(priceVo(90, 100, 0.85), 'sku-1')

    expect(display.memberPrice).toBe(85)
    expect(display.memberPrice).not.toBe(76.5)
  })

  it('折扣率 null / 1 / 越界（≤0、>1）→ 维持会员价', () => {
    for (const rate of [null, 1, 0, -0.5, 1.2]) {
      const display = resolveMemberPriceDisplay(priceVo(95, 100, rate as number | null), 'sku-1')
      expect(display.memberPrice).toBe(95)
    }
  })

  it('折后价按分四舍五入（售价 99.99 × 0.9）', () => {
    const display = resolveMemberPriceDisplay(priceVo(95, 99.99, 0.9), 'sku-1')

    // round2(99.99 × 0.9) = round2(89.991) = 89.99，低于会员价 95 → 展示 89.99
    expect(display.memberPrice).toBe(89.99)
  })

  it('未达标/未登录的引导价不取优（仍用活动会员价）', () => {
    for (const eligible of [false, null]) {
      const view = resolveMemberPriceDisplay(
        { ...priceVo(95, 100, 0.9), eligible },
        'sku-1',
      )

      expect(view.showMemberPrice).toBe(false)
      // 引导文案用活动会员价 95，不取优成 90
      expect(view.guideText).toBe('会员专享 ¥95')
    }
  })

  it('取优后仍低于当前售价才展示；倒挂（会员价 ≥ 售价）保持不展示', () => {
    const display = resolveMemberPriceDisplay(priceVo(100, 100, 0.9), 'sku-1')

    expect(display.matched).toBe(true)
    expect(display.showMemberPrice).toBe(false)
    expect(display.badgeText).toBe('')
  })
})

describe('resolvePromotionPriority 聚合优先级', () => {
  const memberOn = resolveMemberPriceDisplay(vo(), 'sku-1')
  const newbieOn = { ...emptyNewbieDisplay(), matched: true, newbiePrice: 19.9, showNewbiePrice: true }
  const discountOn = { ...emptyDiscountDisplay(), matched: true, discountPrice: 25.9, showDiscountPrice: true }

  it('会员价压过新人价与限时折扣（后者清空，只标会员价）', () => {
    const priority = resolvePromotionPriority({
      discount: discountOn,
      member: memberOn,
      newbie: newbieOn,
    })

    expect(priority.member.showMemberPrice).toBe(true)
    expect(priority.newbie.showNewbiePrice).toBe(false)
    expect(priority.discount.showDiscountPrice).toBe(false)
  })

  it('未达标/未登录的会员价不压新人价与折扣（仍显示可享的最低价）', () => {
    const memberOff = resolveMemberPriceDisplay(vo({ eligible: false }), 'sku-1')
    const withNewbie = resolvePromotionPriority({
      discount: discountOn,
      member: memberOff,
      newbie: newbieOn,
    })
    expect(withNewbie.newbie.showNewbiePrice).toBe(true)
    expect(withNewbie.discount.showDiscountPrice).toBe(false)
    // 引导文案保留，用户仍能看到「会员专享 ¥x」
    expect(withNewbie.member.guideText).toBe('会员专享 ¥23.92')

    // 无新人价时才落到限时折扣
    const withDiscount = resolvePromotionPriority({
      discount: discountOn,
      member: memberOff,
      newbie: emptyNewbieDisplay(),
    })
    expect(withDiscount.discount.showDiscountPrice).toBe(true)
  })

  it('秒杀进行中压过会员价/新人价/折扣', () => {
    const priority = resolvePromotionPriority({
      discount: discountOn,
      member: memberOn,
      newbie: newbieOn,
      seckill: true,
    })

    expect(priority.member.showMemberPrice).toBe(false)
    expect(priority.newbie.showNewbiePrice).toBe(false)
    expect(priority.discount.showDiscountPrice).toBe(false)
  })

  it('无任何活动时保持原价展示（全部空展示）', () => {
    const priority = resolvePromotionPriority({
      discount: emptyDiscountDisplay(),
      member: emptyMemberPriceDisplay(),
      newbie: emptyNewbieDisplay(),
    })

    expect(priority.member.matched).toBe(false)
    expect(priority.newbie.showNewbiePrice).toBe(false)
    expect(priority.discount.showDiscountPrice).toBe(false)
  })
})

// ============ 列表卡片（会员专区 Tab，SPU 维度） ============

type ListPriceItem = Parameters<typeof resolveListMemberPrice>[0] extends (infer T)[] | null | undefined ? T : never

/** 造一条「卡片价对应规格」的会员价 item */
function listItem(overrides: Record<string, unknown> = {}): ListPriceItem {
  return {
    memberPrice: 23.92,
    minLevelId: 'level-gold',
    minLevelName: '金卡会员',
    originalPrice: 29.9,
    skuId: 'sku-1',
    spuId: 'spu-1',
    ...overrides,
  } as ListPriceItem
}

describe('memberPriceItemsBySpuId 分组', () => {
  it('按 spuId 分组（同 SPU 多规格都保留），空响应返回空表', () => {
    const map = memberPriceItemsBySpuId(vo({
      items: [
        listItem({ skuId: 'sku-1' }),
        listItem({ skuId: 'sku-2', originalPrice: 39.9 }),
        listItem({ skuId: 'sku-3', spuId: 'spu-2' }),
      ],
    }))

    expect(map.size).toBe(2)
    expect(map.get('spu-1')).toHaveLength(2)
    expect(map.get('spu-2')).toHaveLength(1)
    expect(memberPriceItemsBySpuId(null).size).toBe(0)
    expect(memberPriceItemsBySpuId(vo({ items: [] })).size).toBe(0)
  })

  it('跳过缺 spuId/skuId、非正会员价的脏数据', () => {
    const map = memberPriceItemsBySpuId(vo({
      items: [
        listItem({ spuId: '' }),
        listItem({ skuId: '' }),
        listItem({ memberPrice: 0 }),
        listItem({ memberPrice: -1 }),
        listItem({ memberPrice: Number.NaN }),
      ],
    }))

    expect(map.size).toBe(0)
  })
})

describe('resolveListMemberPrice 卡片展示判定', () => {
  it('规格价与卡片价一致且达标：主价切会员价 + 角标 + 划卡片价', () => {
    const display = resolveListMemberPrice([listItem()], 'spu-1', 29.9, true)

    expect(display).toEqual({
      badgeText: MEMBER_PRICE_BADGE_TEXT,
      guideText: null,
      matched: true,
      memberPrice: 23.92,
      minLevelName: '金卡会员',
      showMemberPrice: true,
      strikePrice: 29.9,
    })
  })

  it('容差 0.005 内视为同一规格价；超出则不匹配（绝不错配规格）', () => {
    // 浮点/两位小数误差内：命中
    expect(resolveListMemberPrice([listItem({ originalPrice: 29.902 })], 'spu-1', 29.9, true).matched).toBe(true)
    // 差 1 分钱即视为不同规格，不展示
    const mismatch = resolveListMemberPrice([listItem({ originalPrice: 29.91 })], 'spu-1', 29.9, true)
    expect(mismatch.matched).toBe(false)
    expect(mismatch.showMemberPrice).toBe(false)
    expect(mismatch.guideText).toBeNull()
    expect(MEMBER_LIST_PRICE_TOLERANCE).toBe(0.005)
  })

  it('已登录但不达标：主价不动，只给「会员专享 ¥X」引导', () => {
    const display = resolveListMemberPrice([listItem()], 'spu-1', 29.9, false)

    expect(display).toMatchObject({
      badgeText: '',
      guideText: '会员专享 ¥23.92',
      matched: true,
      showMemberPrice: false,
      strikePrice: null,
    })
  })

  it('未登录（eligible=null）：同样只给引导文案', () => {
    const display = resolveListMemberPrice([listItem()], 'spu-1', 29.9, null)

    expect(display.showMemberPrice).toBe(false)
    expect(display.guideText).toBe('会员专享 ¥23.92')
  })

  it('等级名缺失时引导回落「会员专享」', () => {
    const display = resolveListMemberPrice([listItem({ minLevelName: '' })], 'spu-1', 29.9, false)

    expect(display.guideText).toBe('会员专享 ¥23.92')
  })

  it('会员价配在更贵规格上（规格价 ≠ 卡片价）：完全不动', () => {
    // 卡片价 29.9，但只有 39.9 那个规格配了会员价
    const display = resolveListMemberPrice([listItem({ originalPrice: 39.9, memberPrice: 30 })], 'spu-1', 29.9, true)

    expect(display).toEqual(emptyListMemberPriceDisplay())
    expect(display.matched).toBe(false)
  })

  it('价格倒挂（会员价 ≥ 卡片价）：不切换、不划线、不引导', () => {
    for (const memberPrice of [29.9, 35]) {
      const display = resolveListMemberPrice([listItem({ memberPrice })], 'spu-1', 29.9, true)

      expect(display.matched).toBe(true)
      expect(display.showMemberPrice).toBe(false)
      expect(display.badgeText).toBe('')
      expect(display.strikePrice).toBeNull()
      expect(display.guideText).toBeNull()
    }
  })

  it('未登录时倒挂同样不引导（不展示比卡片价更贵的「专享价」）', () => {
    const display = resolveListMemberPrice([listItem({ memberPrice: 35 })], 'spu-1', 29.9, null)

    expect(display.showMemberPrice).toBe(false)
    expect(display.guideText).toBeNull()
  })

  it('同一 SPU 同价多规格：取最低会员价，避免同价商品标高价', () => {
    const display = resolveListMemberPrice(
      [
        listItem({ memberPrice: 25, skuId: 'sku-1' }),
        listItem({ memberPrice: 22, skuId: 'sku-2' }),
      ],
      'spu-1',
      29.9,
      true,
    )

    expect(display.memberPrice).toBe(22)
    expect(display.showMemberPrice).toBe(true)
  })

  it('忽略其它 SPU 的 item（防串价）', () => {
    const display = resolveListMemberPrice([listItem({ spuId: 'spu-2' })], 'spu-1', 29.9, true)

    expect(display.matched).toBe(false)
  })

  it('无活动 / 无响应 / 非法卡片价：全部不展示', () => {
    for (const result of [
      resolveListMemberPrice([], 'spu-1', 29.9, true),
      resolveListMemberPrice(null, 'spu-1', 29.9, true),
      resolveListMemberPrice(undefined, 'spu-1', 29.9, true),
      resolveListMemberPrice([listItem()], 'spu-1', 0, true),
      resolveListMemberPrice([listItem()], 'spu-1', Number.NaN, true),
      resolveListMemberPrice([listItem()], '', 29.9, true),
    ]) {
      expect(result).toEqual(emptyListMemberPriceDisplay())
    }
  })
})

describe('resolveListMemberPrice 达标取优（列表侧：主价=min，划线=卡片价）', () => {
  it('会员价 95 / 卡片价 100 / 折扣率 0.9 → 主价 90、划线 100、角标「会员价」', () => {
    const display = resolveListMemberPrice(
      [listItem({ memberPrice: 95, originalPrice: 100 })],
      'spu-1',
      100,
      true,
      0.9,
    )

    expect(display.showMemberPrice).toBe(true)
    expect(display.memberPrice).toBe(90)
    // 划线仍是卡片当前价（不是 SPU 原价的二次叠加），取优不叠加
    expect(display.strikePrice).toBe(100)
    expect(display.badgeText).toBe('会员价')
    expect(display.guideText).toBeNull()
  })

  it('会员价 90 / 卡片价 100 / 折扣率 0.85 → 主价 85（取优不是折上折）', () => {
    const display = resolveListMemberPrice(
      [listItem({ memberPrice: 90, originalPrice: 100 })],
      'spu-1',
      100,
      true,
      0.85,
    )

    expect(display.memberPrice).toBe(85)
    expect(display.memberPrice).not.toBe(76.5)
    expect(display.strikePrice).toBe(100)
  })

  it('折扣率 null / 1 / 无效 → 维持会员价', () => {
    for (const rate of [null, 1, 0, -1, 2]) {
      const display = resolveListMemberPrice(
        [listItem({ memberPrice: 95, originalPrice: 100 })],
        'spu-1',
        100,
        true,
        rate,
      )
      expect(display.memberPrice).toBe(95)
    }
  })

  it('未达标/未登录引导价不取优（仍用活动会员价）', () => {
    const display = resolveListMemberPrice(
      [listItem({ memberPrice: 95, originalPrice: 100 })],
      'spu-1',
      100,
      false,
      0.9,
    )

    expect(display.showMemberPrice).toBe(false)
    expect(display.guideText).toBe('会员专享 ¥95')
    expect(display.strikePrice).toBeNull()
  })

  it('取优后仍不改变倒挂/规格不匹配的「完全不动」判定', () => {
    // 倒挂：会员价 ≥ 卡片价
    const inverted = resolveListMemberPrice(
      [listItem({ memberPrice: 100, originalPrice: 100 })],
      'spu-1',
      100,
      true,
      0.9,
    )
    expect(inverted.showMemberPrice).toBe(false)
    expect(inverted.guideText).toBeNull()

    // 规格不匹配：会员价配在更贵规格上，折扣率再低也不展示
    const mismatch = resolveListMemberPrice(
      [listItem({ memberPrice: 60, originalPrice: 200 })],
      'spu-1',
      100,
      true,
      0.9,
    )
    expect(mismatch).toEqual(emptyListMemberPriceDisplay())
  })
})
