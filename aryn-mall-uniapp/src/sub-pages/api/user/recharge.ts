import { alovaInstance } from '@/api/core/instance'

// 获取充值配置列表（C 端只回启用中的方案）
export function getRechargeConfigList() {
  return alovaInstance.Get<any>('/mall-user/app/recharge/config/list')
}

// 创建充值订单
export function createRechargeOrder(rechargeConfigId: string) {
  return alovaInstance.Post<any>('/mall-user/app/recharge/order', {}, {
    params: { rechargeConfigId },
  })
}

// 发起充值支付（复用订单支付链路）
export function rechargePrepay(data: {
  orderNo: string
  paymentType: string
  tradeType: string
  returnUrl?: string
  quitUrl?: string
}) {
  return alovaInstance.Post<any>('/mall-user/app/recharge/prepay', data)
}

// 查询充值订单（支付结果轮询用）
export function getRechargeOrder(orderNo: string) {
  return alovaInstance.Get<any>(`/mall-user/app/recharge/order/${orderNo}`)
}

// 我的充值订单
export function getRechargeOrderPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/app/recharge/order/page', { params })
}
