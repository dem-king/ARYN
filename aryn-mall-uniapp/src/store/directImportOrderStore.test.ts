import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DIRECT_IMPORT_HANDOFF_TTL_MS, useDirectImportOrderStore } from './directImportOrderStore'

describe('directImportOrderStore handoff lifecycle', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useRealTimers()
  })

  it('bind one handoff id and only match that exact checkout', () => {
    const store = useDirectImportOrderStore()
    store.begin({ scene: '2', remark: '甲板部', vesselId: 'v1', vesselCallId: 'c1' })

    expect(store.active).toBe(true)
    expect(store.handoffId).toBeTruthy()
    expect(store.matches(store.handoffId)).toBe(true)
    // 普通下单没有 handoff 参数 / 旧的 handoff 都不能消费本次交接
    expect(store.matches('')).toBe(false)
    expect(store.matches('another-checkout')).toBe(false)
  })

  it('expires after TTL so stale context cannot hijack a later ordinary order', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-07T10:00:00Z'))
    const store = useDirectImportOrderStore()
    store.begin({ scene: '3', userAddressId: 'addr-1' })
    const handoffId = store.handoffId

    vi.setSystemTime(new Date(Date.now() + DIRECT_IMPORT_HANDOFF_TTL_MS + 1))

    expect(store.isExpired).toBe(true)
    expect(store.matches(handoffId)).toBe(false)
  })

  it('keeps a user-cleared remark instead of refilling the wizard value', () => {
    const store = useDirectImportOrderStore()
    store.begin({ scene: '3', remark: '向导备注' })

    store.setRemark('')

    expect(store.remark).toBe('')
    expect(store.remarkEdited).toBe(true)
  })

  it('clear removes vessel context, address and remark', () => {
    const store = useDirectImportOrderStore()
    store.begin({ scene: '2', vesselId: 'v1', vesselCallId: 'c1', remark: 'x' })
    store.clear()

    expect(store.active).toBe(false)
    expect(store.handoffId).toBe('')
    expect(store.vesselId).toBe('')
    expect(store.vesselCallId).toBe('')
    expect(store.remark).toBe('')
  })
})
