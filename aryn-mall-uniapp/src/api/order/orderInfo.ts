import { alovaInstance } from '@/api/core/instance'

export { getMallDeliveryProgress as getOrderMallDeliveryProgress } from './mallDelivery'
// 获取订单列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/mall-order/app/orderinfo/page', {
    params,
  })
}
// 获取数量
export function getCount(params: object) {
  return alovaInstance.Get<any>('/mall-order/app/orderinfo/count', {
    params,
  })
}
// 通过id查询
export function getById(id: string) {
  return alovaInstance.Get<any>(`/mall-order/app/orderinfo/${id}`)
}
// 通过订单号查询
export function getByOrderNo(orderNo: string) {
  return alovaInstance.Get<any>(
    `/mall-order/app/orderinfo/getByOrderNo/${orderNo}`,
  )
}

// 取消订单
export function orderCancel(id: string) {
  return alovaInstance.Get<any>(`/mall-order/app/orderinfo/cancel/${id}`)
}
// 删除订单
export function orderDel(id: string) {
  return alovaInstance.Get<any>(`/mall-order/app/orderinfo/del/${id}`)
}
// 确认收货
export function orderReceiver(id: string) {
  return alovaInstance.Get<any>(`/mall-order/app/orderinfo/receiver/${id}`)
}
// 创建订单
export function orderCreate(data: object) {
  return alovaInstance.Post<any>('/mall-order/app/orderinfo/create', data)
}
// 结算订单
export function orderSettlement(data: object) {
  return alovaInstance.Post<any>('/mall-order/app/orderinfo/settlement', data)
}
// 预支付
export function orderPrepay(data: object) {
  return alovaInstance.Post<any>('/mall-order/app/orderinfo/prepay', data)
}
// 订单评价
export function orderAppraise(id: string, data: object) {
  return alovaInstance.Post<any>(
    `/mall-order/app/orderinfo/appraise/${id}`,
    data,
  )
}
