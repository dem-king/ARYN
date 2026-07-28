import { alovaInstance } from '@/api/core/instance'

export interface MallDeliveryAvailability {
  available: boolean
  matchedScopeLevel?: string
  reason: string
}

export interface MallDeliveryEvidence {
  evidenceType: string
  id: string
  sortNo: number
}

export interface MallDeliveryProgress {
  assigneeName?: string
  closedAt?: string
  createTime?: string
  deliveredAt?: string
  evidences?: MallDeliveryEvidence[]
  id: string
  orderId: string
  pickedUpAt?: string
  pickingStartedAt?: string
  returnPendingAt?: string
  returnedAt?: string
  status: string
  updateTime?: string
}

export function getMallDeliveryAvailability(addressId: string) {
  return alovaInstance.Get<MallDeliveryAvailability>(
    '/mall-order/delivery/app/availability',
    {
      params: { addressId },
    },
  )
}

export function getMallDeliveryProgress(orderId: string) {
  return alovaInstance.Get<MallDeliveryProgress>(
    `/mall-order/delivery/app/tasks/order/${orderId}`,
  )
}

export function getMallDeliveryEvidenceAccess(
  orderId: string,
  evidenceId: string,
) {
  return alovaInstance.Get<{ accessUrl: string, expiresAt: string }>(
    `/mall-order/delivery/app/tasks/order/${orderId}/evidences/${evidenceId}/access`,
  )
}

export function buildDeliveryMethods(availability: MallDeliveryAvailability) {
  return [
    { value: '1', name: '普通快递' },
    { value: '2', name: '上门自提' },
    {
      value: '3',
      name: '商城配送',
      ...(!availability.available
        ? { disabled: true, reason: availability.reason }
        : {}),
    },
  ]
}

export function deliveryWayLabel(deliveryWay?: string) {
  if (deliveryWay === '1')
    return '普通快递'
  if (deliveryWay === '2')
    return '上门自提'
  if (deliveryWay === '3')
    return '商城配送'
  return '无需配送'
}

export function customerDeliveryTimeline(progress: Record<string, any>) {
  const timeline = [
    { title: '订单已进入商城配送流程', time: progress.createTime || '' },
  ]
  if (
    ['ASSIGNED', 'PICKING', 'DELIVERING', 'DELIVERED'].includes(progress.status)
  ) {
    timeline.push({
      title: '配送任务已派发',
      time: progress.assignedAt || progress.updateTime || '',
    })
  }
  if (progress.pickingStartedAt) {
    timeline.push({ title: '配送员正在配货', time: progress.pickingStartedAt })
  }
  if (progress.pickedUpAt) {
    timeline.push({
      title: '配送员已取货，正在送往收货地址',
      time: progress.pickedUpAt,
    })
  }
  if (progress.deliveredAt) {
    timeline.push({
      title: '商品已送达，请确认收货',
      time: progress.deliveredAt,
    })
  }
  if (progress.status === 'EXCEPTION') {
    timeline.push({
      title: '配送遇到异常，商家正在处理',
      time: progress.updateTime || '',
    })
  }
  if (progress.status === 'RETURN_PENDING') {
    timeline.push({
      title: '订单正在处理商品退回',
      time: progress.returnPendingAt || progress.updateTime || '',
    })
  }
  if (progress.returnedAt || progress.status === 'CLOSED') {
    timeline.push({
      title: '本次商城配送已关闭',
      time: progress.returnedAt || progress.closedAt || '',
    })
  }
  return timeline
}
