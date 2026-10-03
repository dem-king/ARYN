import { alovaInstance } from '@/api/core/instance'

// 获取等级权益
export function getLevelBenefits(levelId: string) {
  return alovaInstance.Get<any>('/mall-user/app/member/level-benefits', { params: { levelId } })
}

// 获取等级列表
export function getMemberLevelList() {
  return alovaInstance.Get<any>('/mall-user/app/member/levels')
}
