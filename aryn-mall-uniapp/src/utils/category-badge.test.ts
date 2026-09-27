import { describe, expect, it } from 'vitest'
import { CATEGORY_BADGES, resolveCategoryBadge } from './category-badge'

describe('category badge', () => {
  it('maps the recommend badge to 荐', () => {
    expect(resolveCategoryBadge('1')?.text).toBe('荐')
  })

  it('maps the hot badge to 热', () => {
    expect(resolveCategoryBadge('2')?.text).toBe('热')
  })

  it('returns null when there is no badge so callers render no node', () => {
    // 无角标时不返回空角标：左栏用 v-if 控制，多一层空盒子会改变对齐
    expect(resolveCategoryBadge('0')).toBeNull()
    expect(resolveCategoryBadge('')).toBeNull()
    expect(resolveCategoryBadge(null)).toBeNull()
    expect(resolveCategoryBadge(undefined)).toBeNull()
  })

  it('returns null for unknown values instead of guessing a semantic', () => {
    expect(resolveCategoryBadge('9')).toBeNull()
    expect(resolveCategoryBadge('hot')).toBeNull()
  })

  it('accepts both string and number, since JSON serialization varies', () => {
    expect(resolveCategoryBadge(1)?.text).toBe('荐')
    expect(resolveCategoryBadge(2)?.text).toBe('热')
  })

  it('keeps recommend and hot visually distinct', () => {
    // 两种角标必须能一眼区分：参考图里「荐」是绿、「热」是红
    expect(CATEGORY_BADGES[1].bgColor).not.toBe(CATEGORY_BADGES[2].bgColor)
  })

  it('exposes only the two supported semantics', () => {
    expect(Object.keys(CATEGORY_BADGES)).toEqual(['1', '2'])
  })
})
