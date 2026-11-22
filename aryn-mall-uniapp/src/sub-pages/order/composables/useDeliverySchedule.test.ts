import { effectScope, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getCheckoutDeliverySchedule, getOrderDeliverySchedule } from '@/api/order/delivery-group'
import { useDeliverySchedule } from './useDeliverySchedule'

vi.mock('@/api/order/delivery-group', () => ({ getCheckoutDeliverySchedule: vi.fn(), getOrderDeliverySchedule: vi.fn() }))
const flush = async () => { await nextTick(); await Promise.resolve(); await nextTick() }
describe('本人配送排程', () => {
  beforeEach(() => vi.clearAllMocks())
  it('切靠港后迟到结果不能覆盖新排程，切岸上时清空', async () => {
    let resolveOld!: (value: any) => void
    vi.mocked(getCheckoutDeliverySchedule).mockImplementationOnce(() => new Promise(resolve => resolveOld = resolve) as any)
      .mockResolvedValueOnce({ vesselCallId: 'new', needsScheduling: false } as any)
    const call = ref('old')
    const scope = effectScope()
    const state = scope.run(() => useDeliverySchedule(() => call.value ? { vesselId: 'ship', vesselCallId: call.value } : null))!
    call.value = 'new'; await flush()
    expect(state.schedule.value?.vesselCallId).toBe('new')
    resolveOld({ vesselCallId: 'old', needsScheduling: false }); await flush()
    expect(state.schedule.value?.vesselCallId).toBe('new')
    call.value = ''; await flush(); expect(state.schedule.value).toBeUndefined(); scope.stop()
  })
  it('离页迟到失败不污染状态，刷新失败不回显旧窗口', async () => {
    let rejectLate!: (reason: Error) => void
    vi.mocked(getOrderDeliverySchedule).mockResolvedValueOnce({ orderId: 'own', needsScheduling: false } as any)
      .mockImplementationOnce(() => new Promise((_resolve, reject) => rejectLate = reject) as any)
    const scope = effectScope()
    const state = scope.run(() => useDeliverySchedule(() => ({ orderId: 'own' })))!
    await flush(); expect(state.schedule.value?.orderId).toBe('own')
    const refresh = state.refresh(); expect(state.schedule.value).toBeUndefined()
    scope.stop(); rejectLate(new Error('late')); await refresh; expect(state.failed.value).toBe(false)
  })
})
