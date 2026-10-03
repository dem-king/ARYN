import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useCategoryLocateStore } from './categoryLocateStore'

/**
 * 商品列表页 → 分类页定位中转的契约。
 *
 * switchTab 带不了 query（分类页是 tabBar 页），定位目标只能经这个 store
 * 中转：跳转前写入，分类页 onShow 消费。消费语义必须「读后即清」，
 * 否则之后任何一次普通进入分类页都会被过时目标误定位。
 */
describe('categoryLocateStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('初始无待定位目标', () => {
    const store = useCategoryLocateStore()
    expect(store.pendingLocate).toBeNull()
    expect(store.consumePendingLocate()).toBeNull()
  })

  it('写入后消费返回目标，且读后即清（第二次消费为 null）', () => {
    const store = useCategoryLocateStore()
    store.setPendingLocate({ categoryFirstId: 'f1', categorySecondId: 's1' })
    expect(store.consumePendingLocate()).toEqual({ categoryFirstId: 'f1', categorySecondId: 's1' })
    expect(store.consumePendingLocate()).toBeNull()
  })

  it('再次写入覆盖上一次（连续跳转以最后一次为准）', () => {
    const store = useCategoryLocateStore()
    store.setPendingLocate({ categoryFirstId: 'f1', categorySecondId: '' })
    store.setPendingLocate({ categoryFirstId: 'f2', categorySecondId: '' })
    expect(store.consumePendingLocate()?.categoryFirstId).toBe('f2')
  })

  it('写入做快照拷贝，调用方复用对象不影响已存目标', () => {
    const store = useCategoryLocateStore()
    const target = { categoryFirstId: 'f1', categorySecondId: '' }
    store.setPendingLocate(target)
    target.categoryFirstId = 'f2'
    expect(store.consumePendingLocate()?.categoryFirstId).toBe('f1')
  })
})
