import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useProcurementStore } from './procurementStore'
import { emptyProcurementSession, procurementEnvelope, procurementScope } from '@/utils/procurement-session'
const context = { purchaseScene: '3', buyerOrgId: 'org-1' }
const storage = new Map<string, unknown>()
beforeEach(() => {
  storage.clear(); setActivePinia(createPinia())
  vi.stubGlobal('uni', {
    getStorageSync: (key: string) => structuredClone(storage.get(key)),
    setStorageSync: (key: string, value: unknown) => storage.set(key, JSON.parse(JSON.stringify(value))),
    removeStorageSync: (key: string) => storage.delete(key),
  })
})
describe('采购弱网和身份恢复', () => {
  it('响应丢失和服务端revision变化后保留原请求与命令，显式新意图才换号', () => {
    const session = emptyProcurementSession()
    const first = procurementEnvelope(session, 'text', { text: '苹果2件', explicitNew: false }, { expectedRevision: 3, text: '苹果2件' })
    const restored = JSON.parse(JSON.stringify(session))
    const retry = procurementEnvelope(restored, 'text', { text: '苹果2件', explicitNew: false }, { expectedRevision: 5, text: '苹果2件' })
    expect(retry.clientRequestId).toBe(first.clientRequestId)
    expect(retry.expectedRevision).toBe(3)
    expect(procurementEnvelope(restored, 'text', { text: '苹果2件', explicitNew: true }, { expectedRevision: 5 }).clientRequestId).not.toBe(first.clientRequestId)
  })
  it('冷启动可恢复当前主体未同步编辑，切换主体删除旧缓存', () => {
    const scope = procurementScope('tenant', 'actor', context)
    let store = useProcurementStore(); store.bind(scope)
    store.session.draftId = 'draft'; store.session.edits.row = { salesQuantity: '3', reason: 'changed' }; store.save()
    setActivePinia(createPinia()); store = useProcurementStore(); store.bind(scope)
    expect(store.session.draftId).toBe('draft')
    expect(store.session.edits.row.salesQuantity).toBe('3')
    store.bind(procurementScope('tenant', 'actor', { ...context, buyerOrgId: 'org-2' }))
    expect(store.session.draftId).toBe('')
    expect(storage.has(`procurement:${scope}`)).toBe(false)
  })
  it('冷启动后未打开工作台就退出，仍删除上次原文与处置缓存', () => {
    const scope = procurementScope('tenant', 'actor', context)
    let store = useProcurementStore(); store.bind(scope); store.session.text = 'private'; store.save()
    setActivePinia(createPinia()); store = useProcurementStore(); store.clear()
    expect(storage.has(`procurement:${scope}`)).toBe(false)
  })
  it('租户、用户、场景、企业、船与靠港各自隔离', () => {
    const full = { ...context, vesselId: 'v', vesselCallId: 'c' }
    const base = procurementScope('tenant', 'actor', full)
    for (const scope of [procurementScope('other', 'actor', full), procurementScope('tenant', 'other', full), ...Object.keys(full).map(key => procurementScope('tenant', 'actor', { ...full, [key]: 'other' }))]) expect(scope).not.toBe(base)
  })
})
