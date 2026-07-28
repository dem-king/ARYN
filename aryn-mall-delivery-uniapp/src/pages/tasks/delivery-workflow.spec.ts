import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { DeliveryApiError } from '@/api/core/instance'
import { canPickup, useDeliveryStore, validateDeliveryEvidence } from '@/store/delivery'
import { handleStaffSocketEvent } from '@/composables/useStaffWebSocket'
import { requestDeliverySubscription } from '@/composables/useWechatSubscription'

const completeTask = vi.fn()

vi.mock('@/api/delivery', () => ({
  completeTask: (...args: unknown[]) => completeTask(...args),
}))

beforeEach(() => {
  completeTask.mockReset()
  setActivePinia(createPinia())
})

describe('配送员履约工作流', () => {
  it('未全部勾选时不能取货', () => {
    expect(canPickup([{ checked: '1' }, { checked: '0' }])).toBe(false)
    expect(canPickup([{ checked: '1' }, { checked: '1' }])).toBe(true)
  })

  it('送达凭证限制为 1 至 6 张', () => {
    expect(validateDeliveryEvidence([])).toBe(false)
    expect(validateDeliveryEvidence(['1'])).toBe(true)
    expect(validateDeliveryEvidence(['1', '2', '3', '4', '5', '6'])).toBe(true)
    expect(validateDeliveryEvidence(['1', '2', '3', '4', '5', '6', '7'])).toBe(false)
  })

  it('任务提交失败时保留素材草稿并复用 requestId', async () => {
    completeTask.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(true)
    const store = useDeliveryStore()
    store.saveEvidenceDraft('task-1', ['material-1'])

    await expect(store.submitDelivered('task-1', 3)).rejects.toThrow('network')
    const requestId = store.evidenceDrafts['task-1']?.requestId
    expect(store.evidenceDrafts['task-1']?.materialIds).toEqual(['material-1'])

    await store.submitDelivered('task-1', 3)
    expect(completeTask.mock.calls[1]?.[1]).toMatchObject({ requestId })
    expect(store.evidenceDrafts['task-1']).toBeUndefined()
  })

  it('WebSocket 事件只触发 HTTP 补拉', async () => {
    const refresh = vi.fn().mockResolvedValue(undefined)
    await handleStaffSocketEvent('{"task":{"recipientPhone":"should-not-merge"}}', refresh)
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('改派后的无权或冲突响应清理隐私详情', () => {
    const store = useDeliveryStore()
    store.currentTask = { id: 'task-1', recipientPhone: '13800000000' }
    expect(store.handleMutationFailure(new DeliveryApiError('forbidden', 403))).toBe(true)
    expect(store.currentTask).toBeNull()
  })

  it('微信拒绝订阅不阻塞任务操作', async () => {
    const requestSubscribeMessage = vi.fn().mockRejectedValue(new Error('deny'))
    vi.stubGlobal('uni', { requestSubscribeMessage })
    await expect(requestDeliverySubscription(['template-1'])).resolves.toBe(false)
  })
})
