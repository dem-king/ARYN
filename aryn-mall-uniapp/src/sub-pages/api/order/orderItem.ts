import { alovaInstance } from '@/api/core/instance'

// 通过id查询
export function getById(id: string) {
  return alovaInstance.Get<any>(`/mall-order/app/orderitem/${id}`)
}
