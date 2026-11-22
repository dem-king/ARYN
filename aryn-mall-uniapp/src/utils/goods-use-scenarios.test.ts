import { describe, expect, it } from 'vitest'
import { resolveUseScenarios } from './goods-use-scenarios'

describe('resolveUseScenarios', () => {
  it('builds storage and usage cards for fruit', () => {
    const cards = resolveUseScenarios({
      categoryName: '时令鲜果 / 时令鲜果',
      storage: '0–4℃',
      shelfLife: '5–7 天',
    })
    expect(cards.map(card => card.key)).toEqual(['storage', 'usage'])
    expect(cards[0]?.content).toContain('0–4℃保存')
    expect(cards[0]?.content).toContain('5–7 天内食用完')
    expect(cards[1]?.content).toContain('值班加餐')
  })

  it('uses 饮用 for drinks', () => {
    const cards = resolveUseScenarios({
      categoryName: '水饮饮料 / 包装水饮',
      storage: '常温避光',
      shelfLife: '12 个月',
    })
    expect(cards[0]?.content).toContain('12 个月内饮用完')
    expect(cards[1]?.title).toBe('船上怎么喝')
  })

  it('does not show cards for non-food categories', () => {
    expect(resolveUseScenarios({
      categoryName: '个护清洁 / 生活用纸',
      storage: '常温',
      shelfLife: '3 年',
    })).toEqual([])
  })

  it('falls back to packaging storage when facts are missing', () => {
    const cards = resolveUseScenarios({ categoryName: '新鲜蔬菜' })
    expect(cards[0]?.content).toContain('按包装标识储存')
    expect(cards[1]?.content).toContain('食堂配菜')
  })

  it('ignores incomplete category names', () => {
    expect(resolveUseScenarios(null)).toEqual([])
    expect(resolveUseScenarios({ categoryName: '' })).toEqual([])
  })
})
