import type { RetailActivityItem } from '../retail-normalizers'
import type {
  DiscountActivityItem,
  SeckillSessionItem,
} from '../retail-types'

import { describe, expect, it } from 'vitest'

import {
  ACTIVITY_STRIP_HALLS,
  buildActivityStripEntries,
  formatDiscountValue,
  formatGroupBuyBadge,
  formatGroupBuyHook,
  formatGroupBuyTitle,
  formatSeckillHook,
  formatStripAmount,
  resolveStripFallback,
  stripBadgeTone,
  stripGridColumns,
} from './activity-strip'

/**
 * 活动入口条纯逻辑回归守卫（§5.5.3 / §5.5.6）。
 *
 * 这里钉的是「看模板看不出来」的口径问题：
 * 折扣力度曾写成 `discountValue * 10` 直接得到「10折」；省额曾在没有原价时
 * 算出「省¥0」；某类无进行中活动时曾渲染空卡。组件一改样式就容易顺手改坏。
 */

function createSeckillSession(
  overrides: Partial<SeckillSessionItem> = {},
): SeckillSessionItem {
  return {
    countdown: 3600,
    endTime: '2026-10-22 15:00:00',
    goodsList: [
      {
        goodsImage: 'https://cdn.example.com/chips.jpg',
        goodsName: '薯片',
        limitPerUser: 0,
        originalPrice: 9.9,
        remainingStock: 10,
        seckillPrice: 5.9,
        skuId: 'sku-1',
        soldCount: 20,
        seckillStock: 30,
        spuId: 'spu-1',
      },
    ],
    sessionId: 'session-1',
    sessionName: '14:00 场',
    startTime: '2026-10-22 14:00:00',
    status: 1,
    ...overrides,
  }
}

function createGroupBuy(
  overrides: Partial<RetailActivityItem> = {},
): RetailActivityItem {
  return {
    activityPrice: 26.8,
    endTime: '2026-10-23 00:00:00',
    groupNum: 2,
    id: 'activity-1',
    imageUrl: 'https://cdn.example.com/water.jpg',
    name: '农夫山泉 3 人团',
    originalPrice: 30.8,
    spuId: 'spu-2',
    status: 'active',
    ...overrides,
  }
}

function createDiscount(
  overrides: Partial<DiscountActivityItem> = {},
): DiscountActivityItem {
  return {
    activityId: 'discount-1',
    activityName: '好吃零食节',
    countdown: 7200,
    coverImage: '',
    discountType: 1,
    discountValue: 0.95,
    endTime: '2026-10-22 23:00:00',
    goodsList: [
      {
        discountPrice: 9.4,
        goodsImage: 'https://cdn.example.com/snack.jpg',
        goodsName: '零食',
        originalPrice: 9.9,
        skuId: 'sku-3',
        spuId: 'spu-3',
      },
    ],
    scope: 2,
    startTime: '2026-10-22 12:00:00',
    status: 1,
    ...overrides,
  }
}

describe('formatDiscountValue 折扣力度口径', () => {
  it('0.95 折出 9.5 折（不是 10 折，也不是 9 折）', () => {
    expect(formatDiscountValue({ discountType: 1, discountValue: 0.95 })).toBe('9.5折')
  })

  it('一位小数收敛：0.8 → 8折、0.88 → 8.8折、0.855 → 8.6折', () => {
    expect(formatDiscountValue({ discountType: 1, discountValue: 0.8 })).toBe('8折')
    expect(formatDiscountValue({ discountType: 1, discountValue: 0.88 })).toBe('8.8折')
    // Math.round(85.5)/10 → 86/10 = 8.6（四舍五入到一位小数）
    expect(formatDiscountValue({ discountType: 1, discountValue: 0.855 })).toBe('8.6折')
  })

  it('力度 >= 10 折时退化为中性「折扣」，任何输入都不出现「10折」', () => {
    expect(formatDiscountValue({ discountType: 1, discountValue: 1 })).toBe('折扣')
    expect(formatDiscountValue({ discountType: 1, discountValue: 0.999 })).toBe('折扣')
    expect(formatDiscountValue({ discountType: 1, discountValue: 0 })).toBe('折扣')
    for (let value = 0.9; value <= 1.001; value += 0.001)
      expect(formatDiscountValue({ discountType: 1, discountValue: value })).not.toContain('10折')
  })

  it('减价 / 固定价沿用后端语义', () => {
    expect(formatDiscountValue({ discountType: 2, discountValue: 20 })).toBe('减¥20')
    expect(formatDiscountValue({ discountType: 2, discountValue: 5.5 })).toBe('减¥5.5')
    expect(formatDiscountValue({ discountType: 3, discountValue: 9.9 })).toBe('一口价')
    expect(formatDiscountValue({ discountType: 9, discountValue: 0.5 })).toBe('折扣')
  })
})

describe('formatStripAmount 角标金额口径', () => {
  it('去掉无意义尾零，只保留最多两位小数', () => {
    expect(formatStripAmount(4)).toBe('4')
    expect(formatStripAmount(4.5)).toBe('4.5')
    expect(formatStripAmount(4.0)).toBe('4')
    expect(formatStripAmount(4.567)).toBe('4.57')
  })

  it('浮点噪声收敛到两位小数', () => {
    expect(formatStripAmount(30.8 - 26.8)).toBe('4')
  })

  it('非法值不抛错，回落 0', () => {
    expect(formatStripAmount(Number.NaN)).toBe('0')
  })
})

describe('拼团角标与钩子', () => {
  it('原价严格大于拼团价才显示省额', () => {
    // 30.8 - 26.8 的浮点差必须收敛成「省¥4」
    expect(formatGroupBuyBadge({ activityPrice: 26.8, originalPrice: 30.8 })).toBe('省¥4')
    expect(formatGroupBuyBadge({ activityPrice: 26.8, originalPrice: 40 })).toBe('省¥13.2')
  })

  it('无原价 / 原价不高于拼团价时退化为「拼」，不编造省额', () => {
    expect(formatGroupBuyBadge({ activityPrice: 26.8, originalPrice: 0 })).toBe('拼')
    expect(formatGroupBuyBadge({ activityPrice: 26.8, originalPrice: 26.8 })).toBe('拼')
    expect(formatGroupBuyBadge({ activityPrice: 26.8, originalPrice: 20 })).toBe('拼')
  })

  it('钩子 {groupNum}人成团立省，groupNum 缺失退化「多人成团」', () => {
    expect(formatGroupBuyHook({ groupNum: 2 })).toBe('2人成团立省')
    expect(formatGroupBuyHook({ groupNum: 3 })).toBe('3人成团立省')
    expect(formatGroupBuyHook({ groupNum: 0 })).toBe('多人成团')
  })

  it('卡名默认「2人拼团」，多人团按真实人数表达', () => {
    expect(formatGroupBuyTitle({ groupNum: 2 })).toBe('2人拼团')
    expect(formatGroupBuyTitle({ groupNum: 3 })).toBe('3人拼团')
    expect(formatGroupBuyTitle({ groupNum: 0 })).toBe('2人拼团')
  })
})

describe('秒杀钩子', () => {
  it('场次名 + 整点开抢', () => {
    expect(formatSeckillHook({ sessionName: '14:00 场' })).toBe('14:00 场·整点开抢')
  })

  it('场次名缺失只留节奏词', () => {
    expect(formatSeckillHook({ sessionName: '' })).toBe('整点开抢')
    expect(formatSeckillHook({ sessionName: '   ' })).toBe('整点开抢')
  })
})

describe('buildActivityStripEntries 三类卡组装与隐藏降级', () => {
  it('三类都有进行中活动时按「秒杀 → 拼团 → 折扣」组装', () => {
    const entries = buildActivityStripEntries({
      discount: createDiscount(),
      groupBuy: createGroupBuy(),
      seckill: createSeckillSession(),
    })

    expect(entries.map(entry => entry.key)).toEqual(['seckill', 'group-buy', 'discount'])
    expect(entries.map(entry => entry.title)).toEqual(['限时秒杀', '2人拼团', '限时折扣'])
    expect(entries.map(entry => entry.badge)).toEqual(['抢', '省¥4', '9.5折'])
    expect(entries.map(entry => entry.hook)).toEqual([
      '14:00 场·整点开抢',
      '2人成团立省',
      '9.5折专区',
    ])
  })

  it('跳转固定指向会场聚合页，不指向单场/单活动', () => {
    const entries = buildActivityStripEntries({
      discount: createDiscount(),
      groupBuy: createGroupBuy(),
      seckill: createSeckillSession(),
    })

    expect(entries[0].link).toEqual({ params: {}, path: ACTIVITY_STRIP_HALLS.seckill, type: 'custom' })
    expect(entries[1].link).toEqual({ params: {}, path: ACTIVITY_STRIP_HALLS['group-buy'], type: 'custom' })
    expect(entries[2].link).toEqual({ params: {}, path: ACTIVITY_STRIP_HALLS.discount, type: 'custom' })
    expect(ACTIVITY_STRIP_HALLS).toEqual({
      'discount': '/pages/promotion/discount',
      'group-buy': '/sub-pages/promotion/group-buy/group-buy-list/index',
      'seckill': '/pages/promotion/seckill',
    })
  })

  it('图取活动首件商品图', () => {
    const entries = buildActivityStripEntries({
      discount: createDiscount(),
      groupBuy: createGroupBuy(),
      seckill: createSeckillSession(),
    })

    expect(entries[0].imageUrl).toBe('https://cdn.example.com/chips.jpg')
    expect(entries[1].imageUrl).toBe('https://cdn.example.com/water.jpg')
    expect(entries[2].imageUrl).toBe('https://cdn.example.com/snack.jpg')
  })

  it('全场折扣没有商品清单时用取数层补的代表商品图，不落「限」字色块', () => {
    const [entry] = buildActivityStripEntries({
      discount: createDiscount({
        coverImage: 'https://cdn.example.com/sales-top.jpg',
        goodsList: [],
        scope: 1,
      }),
    })

    expect(entry.imageUrl).toBe('https://cdn.example.com/sales-top.jpg')
  })

  it('商品图优先于代表图（指定商品活动按首件商品展示）', () => {
    const [entry] = buildActivityStripEntries({
      discount: createDiscount({ coverImage: 'https://cdn.example.com/sales-top.jpg' }),
    })

    expect(entry.imageUrl).toBe('https://cdn.example.com/snack.jpg')
  })

  it('商品图与代表图都没有时保持空串，交给首字色块兜底', () => {
    const [entry] = buildActivityStripEntries({
      discount: createDiscount({ coverImage: '', goodsList: [], scope: 1 }),
    })

    expect(entry.imageUrl).toBe('')
  })

  it('某类无进行中活动时该卡不产出（剩余卡由渲染侧等宽重排）', () => {
    const onlyDiscount = buildActivityStripEntries({
      discount: createDiscount(),
      groupBuy: createGroupBuy({ status: 'ended' }),
      seckill: createSeckillSession({ status: 0 }),
    })
    expect(onlyDiscount.map(entry => entry.key)).toEqual(['discount'])
  })

  it('三类全无进行中活动时返回空数组（整条不渲染，不留占位）', () => {
    expect(buildActivityStripEntries({
      discount: createDiscount({ status: 2 }),
      groupBuy: createGroupBuy({ status: 'pending' }),
      seckill: createSeckillSession({ status: 2 }),
    })).toEqual([])
    expect(buildActivityStripEntries({})).toEqual([])
  })

  it('单类 loader 失败（传空）只隐藏该卡，其余照常', () => {
    const entries = buildActivityStripEntries({
      discount: createDiscount(),
      groupBuy: null,
      seckill: createSeckillSession(),
    })
    expect(entries.map(entry => entry.key)).toEqual(['seckill', 'discount'])
  })

  it('拼团无省额时角标退化为「拼」而不是省额', () => {
    const [entry] = buildActivityStripEntries({
      groupBuy: createGroupBuy({ activityPrice: 26.8, originalPrice: 26.8 }),
    })
    expect(entry.badge).toBe('拼')
  })
})

describe('stripGridColumns 按实际卡数自动排布', () => {
  it('三卡都在时三列铺满', () => {
    expect(stripGridColumns(3)).toBe(3)
  })

  it('少一张卡时按实际卡数收缩，铺满整行', () => {
    expect(stripGridColumns(2)).toBe(2)
    expect(stripGridColumns(1)).toBe(1)
  })

  it('手配超过三张时一行三张换行，全部展示', () => {
    expect(stripGridColumns(4)).toBe(3)
    expect(stripGridColumns(10)).toBe(3)
  })

  it('非法卡数回落单列', () => {
    expect(stripGridColumns(Number.NaN)).toBe(1)
    expect(stripGridColumns(0)).toBe(1)
  })
})

describe('缺图首字兜底与角标强度', () => {
  it('缺图时取标题首字并给出色板颜色', () => {
    const fallback = resolveStripFallback({ key: 'seckill', title: '限时秒杀' })
    expect(fallback.text).toBe('限')
    expect(fallback.bgColor).toMatch(/^#[0-9A-F]{6}$/i)
    expect(fallback.color).toMatch(/^#[0-9A-F]{6}$/i)
  })

  it('同一张卡的兜底色稳定（哈希取 key 而不是标题文本）', () => {
    const first = resolveStripFallback({ key: 'discount', title: '限时折扣' })
    const renamed = resolveStripFallback({ key: 'discount', title: '全场折扣专区' })
    expect(first.bgColor).toBe(renamed.bgColor)
  })

  it('空标题不抛错，给出兜底文字', () => {
    expect(resolveStripFallback({ key: 'custom', title: '' }).text).toBeTruthy()
  })

  it('秒杀角标走强促销，拼团/折扣/自定义走弱促销', () => {
    expect(stripBadgeTone('seckill')).toBe('strong')
    expect(stripBadgeTone('group-buy')).toBe('soft')
    expect(stripBadgeTone('discount')).toBe('soft')
    expect(stripBadgeTone('custom')).toBe('soft')
  })
})
