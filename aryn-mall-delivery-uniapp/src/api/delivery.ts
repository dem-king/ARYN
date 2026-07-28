import alovaInstance from './core/instance'

export type DeliveryTaskStatus =
  | 'ASSIGNED'
  | 'CLOSED'
  | 'DELIVERED'
  | 'DELIVERING'
  | 'EXCEPTION'
  | 'PICKING'
  | 'RETURN_PENDING'
  | 'WAITING_ASSIGNMENT'

export interface DeliveryTaskItem {
  checked: string
  id: string
  orderItemId: string
}

export interface DeliveryEvidence {
  evidenceType: string
  id: string
  materialId: string
}

export interface DeliveryTask {
  attemptNo?: number
  createTime?: string
  evidence?: DeliveryEvidence[]
  exceptionCode?: string
  exceptionSummary?: string
  id: string
  items?: DeliveryTaskItem[]
  latitude?: number
  longitude?: number
  orderId?: string
  orderNo?: string
  recipientAddress?: string
  recipientArea?: string
  recipientCity?: string
  recipientName?: string
  recipientPhone?: string
  recipientProvince?: string
  status?: DeliveryTaskStatus
  taskNo?: string
  updateTime?: string
  version?: number
}

export interface DeliveryTaskPage {
  current: number
  records: DeliveryTask[]
  size: number
  total: number
}

export interface DeliveryMutationRequest {
  requestId: string
  version: number
}

export function getMyTasks(params: { current?: number, size?: number, status?: string } = {}) {
  return alovaInstance.Get<DeliveryTaskPage>('/mall-order/delivery/staff/tasks', { params })
}

export function getMyTask(id: string) {
  return alovaInstance.Get<DeliveryTask>(`/mall-order/delivery/staff/tasks/${id}`)
}

export function startPicking(id: string, data: DeliveryMutationRequest) {
  return alovaInstance.Post<boolean>(`/mall-order/delivery/staff/tasks/${id}/picking/start`, data)
}

export function checkTaskItem(id: string, itemId: string, data: DeliveryMutationRequest & { checked: boolean }) {
  return alovaInstance.Put<boolean>(`/mall-order/delivery/staff/tasks/${id}/items/${itemId}/checked`, data)
}

export function pickupTask(id: string, data: DeliveryMutationRequest) {
  return alovaInstance.Post<boolean>(`/mall-order/delivery/staff/tasks/${id}/pickup`, data)
}

export function completeTask(id: string, data: DeliveryMutationRequest & { materialIds: string[] }) {
  return alovaInstance.Post<boolean>(`/mall-order/delivery/staff/tasks/${id}/delivered`, data)
}

export function reportTaskException(id: string, data: DeliveryMutationRequest & {
  description: string
  materialIds?: string[]
  reasonCode: string
}) {
  return alovaInstance.Post<boolean>(`/mall-order/delivery/staff/tasks/${id}/exception`, data)
}
