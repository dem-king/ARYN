import { describe, expect, it } from 'vitest'
import {
  CATEGORY_FALLBACK_COLORS,
  resolveCategoryFallback,
} from './category-icon'

describe('category icon fallback', () => {
  it('takes the first character of the category name', () => {
    expect(resolveCategoryFallback('蔬菜', 'x').text).toBe('蔬')
    expect(resolveCategoryFallback('海鲜水产', 'y').text).toBe('海')
  })

  it('does not split surrogate pairs (emoji / rare characters)', () => {
    // Array.from 按码点切分；直接用 name[0] 会取到半个代理对
    expect(resolveCategoryFallback('🐟水产', 'z').text).toBe('🐟')
  })

  it('falls back to a placeholder when the name is missing', () => {
    expect(resolveCategoryFallback('', 'id').text).toBe('类')
    expect(resolveCategoryFallback(null, null).text).toBe('类')
  })

  it('keeps the color stable for the same id and independent of the name', () => {
    const renamed = resolveCategoryFallback('蔬菜', '9510000000000000001')
    const original = resolveCategoryFallback('蔬菜类', '9510000000000000001')
    // 重命名后色块不应跳色：哈希只取 id
    expect(renamed.bgColor).toBe(original.bgColor)
  })

  it('always returns a color from the palette', () => {
    for (const id of ['a', 'b', 'c', '9510000000000000001', '', '长id'.repeat(20)]) {
      expect(CATEGORY_FALLBACK_COLORS).toContain(resolveCategoryFallback('测试', id).bgColor)
    }
  })

  it('distributes different ids across the palette', () => {
    const colors = new Set(
      ['9510000000000000001', '9510000000000000002', '9510000000000000003', '9510000000000000004']
        .map(id => resolveCategoryFallback('类目', id).bgColor),
    )
    // 4 个相邻 id 不应该全部撞成同一个颜色
    expect(colors.size).toBeGreaterThan(1)
  })
})
