import { alovaInstance } from '@/api/core/instance'

export interface PayCapability {
  tradeType: string
  channel: string
  terminalType?: string
  invokeType: string
  available: boolean
  reason?: string
}

// 通过支付单号查询订单
export function getOrder(orderNo: string) {
  return alovaInstance.Get<any>(`/pay/app/order/${orderNo}`)
}

// 当前租户指定终端/客户端平台可用支付能力
export function getCapabilities(terminalType?: string, platformType?: string) {
  const params: Record<string, string> = {}
  if (terminalType) params.terminalType = terminalType
  if (platformType) params.platformType = platformType
  return alovaInstance.Get<PayCapability[]>('/pay/pay/capability', { params })
}
