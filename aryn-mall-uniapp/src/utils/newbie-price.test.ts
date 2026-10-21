import type { AppNewbiePriceVO } from '@/api/promotion'

import { describe, expect, it } from 'vitest'

import {
  clampNewbieQuantity,
  effectiveUnitPrice,
  emptyNewbieDisplay,
  exceedsNewbieLimit,
  hasNewbiePrice,
  newbieIneligibleText,
  newbieIneligibleNotice,
  newbieLimitHint,
  newbieSkuPriceMap,
  resolveNewbiePriceDisplay,
} from './newbie-price'

/** 造一份「命中且可享」的响应 */
function vo(overrides: Partial<AppNewbiePriceVO> = {}): AppNewbiePriceVO {
  return {
    activityId: 'act-1',
    activityName: '新人专享',
    eligible: true,
    endTime: '2026-12-31 23:59:59',
    items: [
      { newbiePrice: 0.01, originalPrice: 5.9, skuId: 'sku-1', spuId: 'spu-1' },
    ],
    limitPerUser: 1,
    registerWithinDays: 30,
    ...overrides,
  }
}

describe('newbieSkuPriceMap', () => {
  it('空响应返回空表', () => {
    expect(newbieSkuPriceMap(null).size).toBe(0)
    expect(newbieSkuPriceMap(undefined).size).toBe(0)
    expect(newbieSkuPriceMap(vo({ items: [] })).size).toBe(0)
  })

  it('按 skuId 建索引，同 SKU 重复时后者覆盖', () => {
    const map = newbieSkuPriceMap(vo({
      items: [
        { newbiePrice: 0.01, originalPrice: 5.9, skuId: 'sku-1', spuId: 'spu-1' },
        { newbiePrice: 0.02, originalPrice: 5.9, skuId: 'sku-1', spuId: 'spu-1' },
      ],
    }))
    expect(map.size).toBe(1)
    expect(map.get('sku-1')?.newbiePrice).toBe(0.02)
  })

  it('跳过缺 SKU、非正价、非数值的脏数据', () => {
    const map = newbieSkuPriceMap(vo({
      items: [
        { newbiePrice: 0.01, originalPrice: 5.9, skuId: '', spuId: 'spu-1' },
        { newbiePrice: 0, originalPrice: 5.9, skuId: 'sku-2', spuId: 'spu-1' },
        { newbiePrice: -1, originalPrice: 5.9, skuId: 'sku-3', spuId: 'spu-1' },
        { newbiePrice: Number.NaN, originalPrice: 5.9, skuId: 'sku-4', spuId: 'spu-1' },
      ] as AppNewbiePriceVO['items'],
    }))
    expect(map.size).toBe(0)
  })

  it('原价缺失按 0 兜底（不伪造划线价）', () => {
    const map = newbieSkuPriceMap(vo({
      items: [{ newbiePrice: 1, originalPrice: undefined as unknown as number, skuId: 'sku-1', spuId: 'spu-1' }],
    }))
    expect(map.get('sku-1')?.originalPrice).toBe(0)
  })
})

describe('resolveNewbiePriceDisplay', () => {
  it('无活动/接口失败时返回空展示', () => {
    expect(resolveNewbiePriceDisplay(null, 'sku-1')).toEqual(emptyNewbieDisplay())
    expect(resolveNewbiePriceDisplay(undefined, 'sku-1')).toEqual(emptyNewbieDisplay())
  })

  it('缺少 skuId 时不下发新人价（宁可原价，也不张冠李戴）', () => {
    expect(resolveNewbiePriceDisplay(vo(), '').showNewbiePrice).toBe(false)
    expect(resolveNewbiePriceDisplay(vo(), undefined).matched).toBe(false)
  })

  it('请求 SKU 未被活动覆盖时不命中', () => {
    const display = resolveNewbiePriceDisplay(vo(), 'sku-other')
    expect(display.matched).toBe(false)
    expect(display.showNewbiePrice).toBe(false)
  })

  it('可享新人价：主价切新人价，原价划线', () => {
    const display = resolveNewbiePriceDisplay(vo(), 'sku-1')
    expect(display.matched).toBe(true)
    expect(display.showNewbiePrice).toBe(true)
    expect(display.newbiePrice).toBe(0.01)
    expect(display.originalPrice).toBe(5.9)
    expect(display.limitPerUser).toBe(1)
    expect(display.ineligibleText).toBe('')
  })

  it('未登录（eligible=null）按可享展示：营销吸引，资格留到下单校验', () => {
    const display = resolveNewbiePriceDisplay(vo({ eligible: null }), 'sku-1')
    expect(display.showNewbiePrice).toBe(true)
    expect(display.ineligibleText).toBe('')
  })

  it('已登录且确定不符合资格：不展示新人价，给出原因', () => {
    const display = resolveNewbiePriceDisplay(
      vo({ eligible: false, ineligibleReason: 'ALREADY_PAID' }),
      'sku-1',
    )
    expect(display.matched).toBe(true)
    expect(display.showNewbiePrice).toBe(false)
    // 不展示新人价时没有划线价：原价就是主价
    expect(display.originalPrice).toBeNull()
    expect(display.ineligibleText).toBe('新人专享价仅限首单，你已下过单')
  })

  it('注册超时按活动注册天数给出具体说明', () => {
    const display = resolveNewbiePriceDisplay(
      vo({ eligible: false, ineligibleReason: 'REGISTER_TIMEOUT', registerWithinDays: 30 }),
      'sku-1',
    )
    expect(display.ineligibleText).toBe('新人专享价仅限注册 30 天内的新用户')
  })

  it('新人价不低于售价时不切换（避免自己划自己）', () => {
    const display = resolveNewbiePriceDisplay(
      vo({ items: [{ newbiePrice: 5.9, originalPrice: 5.9, skuId: 'sku-1', spuId: 'spu-1' }] }),
      'sku-1',
    )
    expect(display.matched).toBe(true)
    expect(display.showNewbiePrice).toBe(false)
    expect(display.originalPrice).toBeNull()
    expect(display.ineligibleText).toBe('')
  })

  it('原价缺失时仍可展示新人价（不因划线价缺失而丢主价）', () => {
    const display = resolveNewbiePriceDisplay(
      vo({ items: [{ newbiePrice: 1, originalPrice: undefined as unknown as number, skuId: 'sku-1', spuId: 'spu-1' }] }),
      'sku-1',
    )
    expect(display.showNewbiePrice).toBe(true)
    expect(display.originalPrice).toBeNull()
  })
})

describe('newbieIneligibleText', () => {
  it('已下过单给出首单口径说明', () => {
    expect(newbieIneligibleText('ALREADY_PAID')).toBe('新人专享价仅限首单，你已下过单')
  })

  it('注册超时带天数；天数缺失时用通用文案', () => {
    expect(newbieIneligibleText('REGISTER_TIMEOUT', 30)).toContain('30 天')
    expect(newbieIneligibleText('REGISTER_TIMEOUT', null)).toBe('新人专享价仅限新注册用户')
    expect(newbieIneligibleText('REGISTER_TIMEOUT', 0)).toBe('新人专享价仅限新注册用户')
  })

  it('未知原因/判定失败返回空串：不能凭空判定新用户没资格', () => {
    expect(newbieIneligibleText('ERROR')).toBe('')
    expect(newbieIneligibleText('PARAM_INVALID')).toBe('')
    expect(newbieIneligibleText(null)).toBe('')
    expect(newbieIneligibleText(undefined)).toBe('')
  })
})

describe('effectiveUnitPrice', () => {
  it('可享新人价时行价取新人价', () => {
    expect(effectiveUnitPrice(5.9, resolveNewbiePriceDisplay(vo(), 'sku-1'))).toBe(0.01)
  })

  it('不展示新人价时回落商品售价', () => {
    const display = resolveNewbiePriceDisplay(vo({ eligible: false, ineligibleReason: 'ALREADY_PAID' }), 'sku-1')
    expect(effectiveUnitPrice(5.9, display)).toBe(5.9)
  })

  it('售价缺失按 0，不返回 NaN 污染合计', () => {
    expect(effectiveUnitPrice(undefined, emptyNewbieDisplay())).toBe(0)
    expect(effectiveUnitPrice('abc', emptyNewbieDisplay())).toBe(0)
    expect(effectiveUnitPrice(-1, emptyNewbieDisplay())).toBe(0)
  })

  it('售价字符串按数值处理（后端 BigDecimal 常序列化为字符串）', () => {
    expect(effectiveUnitPrice('5.90', emptyNewbieDisplay())).toBe(5.9)
  })
})

describe('限购口径', () => {
  it('可享新人价且超限时拦截', () => {
    const display = resolveNewbiePriceDisplay(vo(), 'sku-1')
    expect(exceedsNewbieLimit(1, display)).toBe(false)
    expect(exceedsNewbieLimit(2, display)).toBe(true)
  })

  it('不展示新人价时不判限购（与后端只在命中时才校验一致）', () => {
    const display = resolveNewbiePriceDisplay(vo({ eligible: false }), 'sku-1')
    expect(exceedsNewbieLimit(5, display)).toBe(false)
  })

  it('数量非法时不算超限', () => {
    const display = resolveNewbiePriceDisplay(vo(), 'sku-1')
    expect(exceedsNewbieLimit(undefined, display)).toBe(false)
    expect(exceedsNewbieLimit('abc', display)).toBe(false)
  })

  it('限购提示仅在可享时出现', () => {
    expect(newbieLimitHint(resolveNewbiePriceDisplay(vo(), 'sku-1'))).toBe('新人专享价每人限购 1 件')
    expect(newbieLimitHint(emptyNewbieDisplay())).toBe('')
  })

  it('历史超限数量被拉回限购件数', () => {
    const display = resolveNewbiePriceDisplay(vo(), 'sku-1')
    expect(clampNewbieQuantity(3, display)).toBe(1)
    expect(clampNewbieQuantity(1, display)).toBe(1)
    expect(clampNewbieQuantity(0, display)).toBe(1)
  })

  it('不可享新人价时原样保留数量（不做无依据的收敛）', () => {
    expect(clampNewbieQuantity(3, emptyNewbieDisplay())).toBe(3)
  })
})

describe('页面级汇总', () => {
  it('hasNewbiePrice 任一可享即为真', () => {
    expect(hasNewbiePrice([emptyNewbieDisplay(), resolveNewbiePriceDisplay(vo(), 'sku-1')])).toBe(true)
    expect(hasNewbiePrice([emptyNewbieDisplay(), null, undefined])).toBe(false)
  })

  it('不合格说明取第一条有效文案', () => {
    const ineligible = resolveNewbiePriceDisplay(
      vo({ eligible: false, ineligibleReason: 'ALREADY_PAID' }),
      'sku-1',
    )
    expect(newbieIneligibleNotice([emptyNewbieDisplay(), ineligible]))
      .toBe('新人专享价仅限首单，你已下过单')
    expect(newbieIneligibleNotice([emptyNewbieDisplay()])).toBe('')
    expect(newbieIneligibleNotice([])).toBe('')
  })

  it('未命中的商品不产生说明（普通商品没有资格问题）', () => {
    expect(newbieIneligibleNotice([resolveNewbiePriceDisplay(vo(), 'sku-other')])).toBe('')
  })
})
