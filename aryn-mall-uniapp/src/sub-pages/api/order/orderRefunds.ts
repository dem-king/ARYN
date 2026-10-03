import { alovaInstance } from '@/api/core/instance'

// 通过id查询
export function getById(id: string) {
  return alovaInstance.Get<any>(`/mall-order/app/orderrefund/${id}`)
}
// 获取退单列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/mall-order/app/orderrefund/page', {
    params,
  })
}
// 申请退款
export function addObj(data: object) {
  return alovaInstance.Post<any>('/mall-order/app/orderrefund', data)
}
