import { alovaInstance } from '@/api/core/instance'
// 通过支付单号查询订单
export function getOrder(orderNo: string) {
  return alovaInstance.Get<any>(`/pay/app/order/${orderNo}`)
}
