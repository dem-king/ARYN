import { describe, expect, it } from 'vitest'
import { LOW_STOCK_RATIO, LOW_STOCK_THRESHOLD, normalizeStock, resolveSeckillProgress } from './seckill-progress'

describe('normalizeStock', () => {
  it('把 null/undefined/空串 归一为 NaN（未知），而不是 0', () => {
    expect(Number.isNaN(normalizeStock(null))).toBe(true)
    expect(Number.isNaN(normalizeStock(undefined))).toBe(true)
    expect(Number.isNaN(normalizeStock(''))).toBe(true)
  })

  it('保留 0 与负数（调用方据此判售罄/未知哨兵）', () => {
    expect(normalizeStock(0)).toBe(0)
    expect(normalizeStock(-1)).toBe(-1)
    expect(normalizeStock('12')).toBe(12)
  })
})

describe('resolveSeckillProgress', () => {
  it('库存未知返回 null（整块隐藏）', () => {
    expect(resolveSeckillProgress(10, null)).toBeNull()
    expect(resolveSeckillProgress(null, 3)).toBeNull()
    expect(resolveSeckillProgress(10, -1)).toBeNull()
    expect(resolveSeckillProgress(0, 0)).toBeNull()
  })

  it('售罄：pct 100、文案「已抢光」', () => {
    const result = resolveSeckillProgress(10, 0)
    expect(result).toMatchObject({ pct: 100, soldOut: true, low: true, shortText: '已抢光', fullText: '已抢光' })
  })

  it('一件没卖：文案「限量 N 件」，不说「仅剩」也不说「已抢 0%」', () => {
    const result = resolveSeckillProgress(25, 25)
    expect(result).toMatchObject({ pct: 0, soldOut: false, low: false, shortText: '限量 25 件', fullText: '限量 25 件' })
  })

  it('剩余超过库存（脏数据）按一件没卖处理', () => {
    expect(resolveSeckillProgress(10, 12)?.shortText).toBe('限量 10 件')
  })

  it('绝对阈值：剩余 ≤5 件切「仅剩 N 件」', () => {
    expect(LOW_STOCK_THRESHOLD).toBe(5)
    expect(resolveSeckillProgress(100, 4)).toMatchObject({ low: true, shortText: '仅剩 4 件', fullText: '仅剩 4 件' })
  })

  it('比例阈值：剩余 ≤20% 切「仅剩 N 件」', () => {
    expect(LOW_STOCK_RATIO).toBe(0.2)
    expect(resolveSeckillProgress(10, 2)).toMatchObject({ low: true, shortText: '仅剩 2 件' })
  })

  it('常规区间：短文案只出百分比，长文案补剩余量', () => {
    expect(resolveSeckillProgress(100, 50)).toMatchObject({
      low: false,
      pct: 50,
      shortText: '已抢 50%',
      fullText: '已抢 50% · 仅剩 50 件',
    })
  })

  it('四舍五入：3 件卖 2 件为 67%', () => {
    expect(resolveSeckillProgress(3, 1)?.pct).toBe(67)
  })
})