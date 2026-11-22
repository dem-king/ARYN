import { alovaInstance } from '@/api/core/instance'

export interface DeliverySchedule {
  orderId?: string
  groupId?: string
  vesselId?: string
  vesselCallId?: string
  status?: string
  windowStart?: string
  windowEnd?: string
  cutoff?: string
  needsScheduling: boolean
}
export const getOrderDeliverySchedule = (orderId: string) => alovaInstance.Get<DeliverySchedule>(
  `/mall-order/app/delivery-groups/orders/${encodeURIComponent(orderId)}`, { cacheFor: 0 },
)
export const getCheckoutDeliverySchedule = (vesselId: string, vesselCallId: string) => alovaInstance.Get<DeliverySchedule>(
  '/mall-order/app/delivery-groups/checkout', { params: { vesselId, vesselCallId }, cacheFor: 0 },
)
