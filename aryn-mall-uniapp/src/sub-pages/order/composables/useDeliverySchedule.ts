import type { DeliverySchedule } from '@/api/order/delivery-group'
import { onScopeDispose, ref, watch } from 'vue'
import { getCheckoutDeliverySchedule, getOrderDeliverySchedule } from '@/api/order/delivery-group'

export type ScheduleQuery = { orderId: string } | { vesselId: string, vesselCallId: string } | null
/** 按当前订单/靠港读取公开排程；切换主体或离页后拒收旧响应。 */
export function useDeliverySchedule(query: () => ScheduleQuery) {
  const schedule = ref<DeliverySchedule>()
  const loading = ref(false)
  const failed = ref(false)
  const fetchedAt = ref(0)
  let epoch = 0
  let disposed = false
  async function refresh() {
    const ticket = ++epoch
    const input = query()
    schedule.value = undefined
    failed.value = false
    loading.value = !!input
    if (!input || disposed)
      return
    try {
      const response = await ('orderId' in input ? getOrderDeliverySchedule(input.orderId) : getCheckoutDeliverySchedule(input.vesselId, input.vesselCallId))
      if (ticket !== epoch || disposed)
        return
      schedule.value = response
      fetchedAt.value = Date.now()
    }
    catch {
      if (ticket === epoch && !disposed)
        failed.value = true
    }
    finally {
      if (ticket === epoch && !disposed)
        loading.value = false
    }
  }
  watch(() => JSON.stringify(query()), refresh, { immediate: true })
  onScopeDispose(() => { disposed = true; epoch++ })
  return { schedule, loading, failed, fetchedAt, refresh }
}
