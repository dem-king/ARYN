import { alovaInstance } from '@/api/core/instance'
// 通过id查询
export function getByOrderId(orderId: string) {
  return alovaInstance.Get<any>(`/mall-order/app/order/${orderId}/delivery-progress`)
}
