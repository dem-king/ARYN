import { describe, expect, it } from 'vitest'
import { DIY_PAGED_ITEM_LIMIT, mergePagedItems } from './retail-paged-list'

describe('装修分页列表合并', () => {
  it('按 id 去掉跨页重复项', () => {
    const result = mergePagedItems(
      [{ id: '1' }, { id: '2' }],
      [{ id: '2' }, { id: '3' }],
    )
    expect(result.items.map(item => item.id)).toEqual(['3'])
    expect(result.reachedLimit).toBe(false)
  })

  it('超过上限时截断并标记到底', () => {
    const existing = Array.from({ length: 199 }, (_, index) => ({
      id: String(index),
    }))
    const result = mergePagedItems(existing, [{ id: '199' }, { id: '200' }])
    expect(result.items.map(item => item.id)).toEqual(['199'])
    expect(result.reachedLimit).toBe(true)
  })

  it('默认上限保护长列表', () => {
    const existing = Array.from({ length: DIY_PAGED_ITEM_LIMIT }, (_, index) => ({
      id: String(index),
    }))
    expect(mergePagedItems(existing, [{ id: 'new' }])).toEqual({
      items: [],
      reachedLimit: true,
    })
  })
})
