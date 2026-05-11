import { alovaInstance } from '@/api/core/instance'

// 获取充值配置列表
export function getRechargeConfigList(params: object) {
  return alovaInstance.Get<any>('/mall-user/rechargeconfig/page', { params })
}

// 创建充值订单
export function createRechargeOrder(data: object) {
  return alovaInstance.Post<any>('/mall-user/app/recharge/order', data)
}

// 我的充值订单
export function getRechargeOrderPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/app/recharge/order/page', { params })
}
