import type { QuickCartInfo } from './quick-cart'

import { describe, expect, it } from 'vitest'
import { batchAddSummaryText, buildBatchAddPlan, needsBatchAddDetail } from './batch-cart'

function direct(overrides: Partial<QuickCartInfo> = {}): QuickCartInfo {
  return {
    mode: 'direct',
    name: '测试商品',
    skuId: 'sku-1',
    stock: 100,
    ...overrides,
  }
}

describe('buildBatchAddPlan', () => {
  it('单规格且库存充足 -> 归入 ready，数量按 MOQ/步长取最小合法值', () => {
    const plan = buildBatchAddPlan(['spu-1'], {
      'spu-1': direct({ moq: 5, stepQty: 5, skuId: 'sku-a' }),
    })

    expect(plan.ready).toHaveLength(1)
    expect(plan.ready[0]).toMatchObject({ spuId: 'spu-1', skuId: 'sku-a', quantity: 5 })
    expect(plan.needChoose).toHaveLength(0)
    expect(plan.blocked).toHaveLength(0)
  })

  it('多规格 -> 归入 needChoose，不塞进批量接口', () => {
    const plan = buildBatchAddPlan(['spu-1'], {
      'spu-1': { mode: 'choose', name: '多规格商品' },
    })

    expect(plan.ready).toHaveLength(0)
    expect(plan.needChoose).toHaveLength(1)
    expect(plan.needChoose[0].spuId).toBe('spu-1')
  })

  it('售罄 -> 归入 blocked 并带后端原因', () => {
    const plan = buildBatchAddPlan(['spu-1'], {
      'spu-1': { mode: 'unavailable', reason: '该商品暂时缺货' },
    })

    expect(plan.blocked).toHaveLength(1)
    expect(plan.blocked[0].reason).toBe('该商品暂时缺货')
  })

  it('单规格但库存低于最小起订量 -> blocked 而不是 ready', () => {
    const plan = buildBatchAddPlan(['spu-1'], {
      'spu-1': direct({ moq: 10, stock: 3 }),
    })

    expect(plan.ready).toHaveLength(0)
    expect(plan.blocked).toHaveLength(1)
  })

  it('查询失败（信息缺失）-> blocked，不能当成可加购', () => {
    const plan = buildBatchAddPlan(['spu-1'], {})

    expect(plan.ready).toHaveLength(0)
    expect(plan.blocked).toHaveLength(1)
    expect(plan.blocked[0].reason).toContain('失败')
  })

  it('勾选顺序被保留，且重复 ID 只处理一次', () => {
    const plan = buildBatchAddPlan(['spu-b', 'spu-a', 'spu-b'], {
      'spu-a': direct({ skuId: 'sku-a' }),
      'spu-b': direct({ skuId: 'sku-b' }),
    })

    expect(plan.ready.map(item => item.spuId)).toEqual(['spu-b', 'spu-a'])
  })

  it('空勾选 -> 三类都为空，不抛异常', () => {
    const plan = buildBatchAddPlan([], {})

    expect(plan.ready).toHaveLength(0)
    expect(plan.needChoose).toHaveLength(0)
    expect(plan.blocked).toHaveLength(0)
  })

  it('混合场景一次性分拣正确', () => {
    const plan = buildBatchAddPlan(
      ['spu-ok', 'spu-spec', 'spu-sold', 'spu-missing'],
      {
        'spu-ok': direct({ skuId: 'sku-ok' }),
        'spu-spec': { mode: 'choose' },
        'spu-sold': { mode: 'unavailable' },
      },
    )

    expect(plan.ready.map(i => i.spuId)).toEqual(['spu-ok'])
    expect(plan.needChoose.map(i => i.spuId)).toEqual(['spu-spec'])
    expect(plan.blocked.map(i => i.spuId)).toEqual(['spu-sold', 'spu-missing'])
  })

  it('未知 mode 按需选规格处理（fail-safe，不误加购）', () => {
    const plan = buildBatchAddPlan(['spu-1'], {
      'spu-1': { mode: 'something-new', skuId: 'sku-1' },
    })

    expect(plan.ready).toHaveLength(0)
    expect(plan.needChoose).toHaveLength(1)
  })

  it('name 缺失时回落为 spuId，避免汇总文案出现空白项', () => {
    const plan = buildBatchAddPlan(['spu-1'], {
      'spu-1': direct({ name: '', skuId: 'sku-1' }),
    })

    expect(plan.ready[0].name).toBe('spu-1')
  })
})

describe('batchAddSummaryText', () => {
  it('全部成功只报已加入', () => {
    expect(batchAddSummaryText({ added: 3, needChoose: 0, blocked: 0, failed: 0 }))
      .toBe('已加入 3 项')
  })

  it('存在需选规格/不可加购/失败时全部列出，不谎报全成功', () => {
    const text = batchAddSummaryText({ added: 2, needChoose: 1, blocked: 1, failed: 1 })

    expect(text).toContain('已加入 2 项')
    expect(text).toContain('1 项需选规格')
    expect(text).toContain('1 项不可加购')
    expect(text).toContain('1 项加入失败')
  })

  it('一项都没加入时给出明确结论', () => {
    expect(batchAddSummaryText({ added: 0, needChoose: 0, blocked: 0, failed: 0 }))
      .toBe('没有可加购的商品')
  })
})

describe('needsBatchAddDetail', () => {
  it('全部成功不需要明细', () => {
    expect(needsBatchAddDetail({ needChoose: 0, blocked: 0, failed: 0 })).toBe(false)
  })

  it('有任何未进购物车的项就需要明细', () => {
    expect(needsBatchAddDetail({ needChoose: 1, blocked: 0, failed: 0 })).toBe(true)
    expect(needsBatchAddDetail({ needChoose: 0, blocked: 1, failed: 0 })).toBe(true)
    expect(needsBatchAddDetail({ needChoose: 0, blocked: 0, failed: 1 })).toBe(true)
  })
})
