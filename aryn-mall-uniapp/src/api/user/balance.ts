import { alovaInstance } from '@/api/core/instance'

// 我的余额变动记录（C 端按登录态取本人记录）
export function getBalanceRecordPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/app/balance/records', { params })
}
