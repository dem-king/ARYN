import { alovaInstance } from '@/api/core/instance'

// 余额变动记录
export function getBalanceRecordPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/balancerecord/user/page', { params })
}
