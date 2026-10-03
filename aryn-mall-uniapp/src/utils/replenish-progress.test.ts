import type { SharedCartItem, SharedCartSummary } from '@/api/order/sharedCart'

import { describe, expect, it } from 'vitest'
import { buildReplenishProgressView, buildReplenishRowView, summarizeReplenishItems } from './replenish-progress'

function summary(overrides: Partial<SharedCartSummary> = {}): SharedCartSummary {
  return {
    cart: null,
    itemCount: 0,
    memberCount: 1,
    totalAmount: 0,
    progress: null,
    previewItems: [],
    previewTruncated: false,
    ...overrides,
  }
}

function progress(overrides: Partial<NonNullable<SharedCartSummary['progress']>> = {}) {
  return {
    totalItems: 12,
    plannedItems: 12,
    fulfilledItems: 8,
    remainingItems: 4,
    unplannedItems: 0,
    progressPercent: 67,
    totalAmount: 1286,
    ...overrides,
  }
}

describe('buildReplenishProgressView', () => {
  it('无摘要时返回空视图，不编造任何进度', () => {
    for (const input of [null, undefined]) {
      const view = buildReplenishProgressView(input)
      expect(view).toEqual({
        hasPlan: false,
        percent: 0,
        progressText: '',
        previewLineText: '',
      })
    }
  })

  it('已排计划 -> 用服务端百分比与「已采/还差」文案', () => {
    const view = buildReplenishProgressView(summary({ progress: progress() }))

    expect(view.hasPlan).toBe(true)
    expect(view.percent).toBe(67)
    expect(view.progressText).toBe('已采 8 项 · 还差 4 项')
  })

  it('一项计划都没排 -> 不画进度条、不显示 0%', () => {
    const view = buildReplenishProgressView(summary({
      itemCount: 5,
      progress: progress({
        totalItems: 5,
        plannedItems: 0,
        fulfilledItems: 0,
        remainingItems: 0,
        unplannedItems: 5,
        progressPercent: null,
      }),
    }))

    expect(view.hasPlan).toBe(false)
    expect(view.percent).toBe(0)
    expect(view.progressText).toBe('尚未排计划 · 5 项待安排')
    // 关键：绝不能出现 0%，那是「有计划但没采」的语义
    expect(view.progressText).not.toContain('0%')
  })

  it('部分排计划 -> 文案补出未排计划的项数', () => {
    const view = buildReplenishProgressView(summary({
      progress: progress({ plannedItems: 10, unplannedItems: 2, fulfilledItems: 6, remainingItems: 4 }),
    }))

    expect(view.progressText).toBe('已采 6 项 · 还差 4 项 · 2 项未排计划')
  })

  it('服务端百分比越界或缺失时收敛到 0，不让进度条溢出', () => {
    expect(buildReplenishProgressView(summary({ progress: progress({ progressPercent: 140 }) })).percent).toBe(100)
    expect(buildReplenishProgressView(summary({ progress: progress({ progressPercent: -20 }) })).percent).toBe(0)
    expect(buildReplenishProgressView(summary({ progress: progress({ progressPercent: null }) })).percent).toBe(0)
  })

  it('进度条宽度只来自服务端字段，不拿需求量二次计算', () => {
    // plannedItems > 0 但 progressPercent 缺失 -> 0，而不是 fulfilledPlanned/plannedItems
    const view = buildReplenishProgressView(summary({
      progress: progress({ progressPercent: null }),
    }))
    expect(view.percent).toBe(0)
  })

  it('预览行：已排计划的说还差多少', () => {
    const view = buildReplenishProgressView(summary({
      progress: progress(),
      previewItems: [
        { itemId: '1', spuId: 's1', skuId: 'sku1', spuName: '番茄', quantity: 3, plannedQuantity: 2, fulfilledQuantity: 0, remainingQuantity: 2, amount: 10 },
        { itemId: '2', spuId: 's2', skuId: 'sku2', spuName: '矿泉水', quantity: 5, plannedQuantity: 5, fulfilledQuantity: 2, remainingQuantity: 3, amount: 20 },
      ],
    }))

    expect(view.previewLineText).toBe('清单还差：番茄 还差 2 · 矿泉水 还差 3')
  })

  it('预览行：采满的显示已采满，未排计划的才回落需求量', () => {
    const view = buildReplenishProgressView(summary({
      progress: progress({ remainingItems: 0, fulfilledItems: 12 }),
      previewItems: [
        { itemId: '1', spuId: 's1', skuId: 'sku1', spuName: '鲜牛奶', quantity: 9, plannedQuantity: 4, fulfilledQuantity: 4, remainingQuantity: 0, completed: true, amount: 30 },
        { itemId: '2', spuId: 's2', skuId: 'sku2', spuName: '未安排商品', quantity: 7, plannedQuantity: null, fulfilledQuantity: 0, remainingQuantity: null, amount: 5 },
      ],
    }))

    expect(view.previewLineText).toBe('鲜牛奶 已采满 · 未安排商品 7')
    // 未排计划的行绝不能显示成「还差 7」
    expect(view.previewLineText).not.toContain('还差 7')
  })

  it('预览行：超采（还差 0 但已采 > 计划）不显示负数', () => {
    const view = buildReplenishProgressView(summary({
      progress: progress({ remainingItems: 0 }),
      previewItems: [
        { itemId: '1', spuId: 's1', skuId: 'sku1', spuName: '抽纸', quantity: 10, plannedQuantity: 2, fulfilledQuantity: 5, remainingQuantity: 0, completed: true, amount: 10 },
      ],
    }))

    expect(view.previewLineText).toBe('抽纸 已采满')
  })

  it('预览行：商品名缺失时回落 SKU ID 并保留截断省略号', () => {
    const view = buildReplenishProgressView(summary({
      previewTruncated: true,
      progress: progress(),
      previewItems: [
        { itemId: '1', spuId: 's1', skuId: 'SKU-9', quantity: 3, plannedQuantity: 2, fulfilledQuantity: 0, remainingQuantity: 2, amount: 0 },
      ],
    }))

    expect(view.previewLineText).toBe('清单还差：SKU-9 还差 2 …')
  })

  it('没有进度字段（老服务端）时按未排计划处理，不显示假百分比', () => {
    const view = buildReplenishProgressView(summary({ itemCount: 3, progress: null }))

    expect(view.hasPlan).toBe(false)
    expect(view.percent).toBe(0)
    expect(view.progressText).toBe('')
  })
})

describe('buildReplenishRowView', () => {
  it('已排计划 -> 展示目标与已采', () => {
    const row = buildReplenishRowView({ requestedQuantity: 9, plannedQuantity: 4, fulfilledQuantity: 3 })
    expect(row.text).toBe('申请 9 · 目标 4 · 已采 3')
    expect(row.remaining).toBe(1)
    expect(row.completed).toBe(false)
  })

  it('未排计划 -> 说未排计划并展示申请量，不冒充目标', () => {
    const row = buildReplenishRowView({ requestedQuantity: 9, plannedQuantity: null, fulfilledQuantity: 0 })
    expect(row.text).toBe('申请 9 · 未排计划')
    expect(row.remaining).toBeNull()
    expect(row.completed).toBe(false)
    // 绝不能把申请量当计划量
    expect(row.planned).toBeNull()
    expect(row.text).not.toContain('目标 9')
  })

  it('存量行的已采量为 null/负数 -> 按 0 处理', () => {
    expect(buildReplenishRowView({ requestedQuantity: 2, plannedQuantity: 4, fulfilledQuantity: null }).text)
      .toBe('申请 2 · 目标 4 · 已采 0')
    expect(buildReplenishRowView({ requestedQuantity: 2, plannedQuantity: 4, fulfilledQuantity: -3 }).fulfilled).toBe(0)
  })

  it('超采 -> 还差为 0 且标记完成，不出现负数', () => {
    const row = buildReplenishRowView({ requestedQuantity: 2, plannedQuantity: 4, fulfilledQuantity: 7 })
    expect(row.remaining).toBe(0)
    expect(row.completed).toBe(true)
  })

  it('计划量 0 -> 视为已采满（与后端 fulfilled >= planned 一致）', () => {
    const row = buildReplenishRowView({ requestedQuantity: 2, plannedQuantity: 0, fulfilledQuantity: 0 })
    expect(row.completed).toBe(true)
    expect(row.remaining).toBe(0)
  })

  it('行进度百分比按已采/目标取整', () => {
    expect(buildReplenishRowView({ requestedQuantity: 1, plannedQuantity: 4, fulfilledQuantity: 3 }).percent).toBe(75)
    expect(buildReplenishRowView({ requestedQuantity: 1, plannedQuantity: 3, fulfilledQuantity: 2 }).percent).toBe(67)
  })

  it('未排计划的行不给百分比，避免画成空进度条', () => {
    expect(buildReplenishRowView({ requestedQuantity: 9, plannedQuantity: null, fulfilledQuantity: 0 }).percent).toBe(0)
  })

  it('计划量为 0 不出现除零，超采不超过 100', () => {
    expect(buildReplenishRowView({ requestedQuantity: 1, plannedQuantity: 0, fulfilledQuantity: 0 }).percent).toBe(0)
    expect(buildReplenishRowView({ requestedQuantity: 1, plannedQuantity: 4, fulfilledQuantity: 9 }).percent).toBe(100)
  })
})

describe('summarizeReplenishItems', () => {
  const item = (over: Partial<SharedCartItem>): SharedCartItem => ({
    id: 'i',
    cartId: 'c',
    userId: 'u',
    spuId: 's',
    skuId: 'k',
    requestedQuantity: 1,
    plannedQuantity: null,
    fulfilledQuantity: 0,
    status: '1',
    ...over,
  })

  it('空列表 -> 全零且百分比为 null，不是 0%', () => {
    const summary = summarizeReplenishItems([])
    expect(summary.totalItems).toBe(0)
    expect(summary.progressPercent).toBeNull()
  })

  it('一项计划都没排 -> 百分比为 null，全部计入 unplannedItems', () => {
    const summary = summarizeReplenishItems([
      item({ id: 'a', plannedQuantity: null }),
      item({ id: 'b', plannedQuantity: null }),
    ])
    expect(summary.unplannedItems).toBe(2)
    expect(summary.plannedItems).toBe(0)
    expect(summary.progressPercent).toBeNull()
  })

  it('部分排计划 -> 百分比只按已排计划项数算，且四舍五入', () => {
    const summary = summarizeReplenishItems([
      item({ id: 'a', plannedQuantity: 2, fulfilledQuantity: 2 }),
      item({ id: 'b', plannedQuantity: 2, fulfilledQuantity: 0 }),
      item({ id: 'c', plannedQuantity: 2, fulfilledQuantity: 0 }),
      item({ id: 'd', plannedQuantity: null }),
    ])
    expect(summary.totalItems).toBe(4)
    expect(summary.plannedItems).toBe(3)
    expect(summary.fulfilledItems).toBe(1)
    expect(summary.remainingItems).toBe(2)
    expect(summary.unplannedItems).toBe(1)
    // 1/3 -> 33%，而不是拿数量 2/6
    expect(summary.progressPercent).toBe(33)
  })

  it('超采的行也算已采满，remainingItems 不为负', () => {
    const summary = summarizeReplenishItems([
      item({ id: 'a', plannedQuantity: 2, fulfilledQuantity: 5 }),
    ])
    expect(summary.fulfilledItems).toBe(1)
    expect(summary.remainingItems).toBe(0)
    expect(summary.progressPercent).toBe(100)
  })

  it('与卡片聚合口径一致：项数相同输入产出相同百分比', () => {
    // 卡片接口的 progress 由后端同口径计算，这里验证前端详情页算出的一样
    const summary = summarizeReplenishItems([
      item({ id: 'a', plannedQuantity: 2, fulfilledQuantity: 2 }),
      item({ id: 'b', plannedQuantity: 2, fulfilledQuantity: 0 }),
    ])
    expect(summary.progressPercent).toBe(50)
    expect(summary.remainingItems).toBe(1)
  })
})
