import { alovaInstance } from '@/api/core/instance'

/** 会员等级信息 */
export interface MemberLevelInfo {
  id: string
  levelName: string
  levelIcon: string
  growthValue: number
  isPaid: string
  price: number
  duration: number
  exclusiveDiscount: number
  birthdayGiftPoints: number
}

/** 用户会员信息 */
export interface UserMemberInfo {
  levelId: string
  levelName: string
  levelIcon: string
  currentGrowthValue: number
  nextLevelGrowthValue: number
  isPaidMember: boolean
  paidExpireTime: string
}

/** 成长值记录 */
export interface GrowthLogItem {
  id: string
  growthValue: number
  sourceType: string
  sourceDesc: string
  createTime: string
}

// 获取会员等级列表（C端）
export function getAppMemberLevels() {
  return alovaInstance.Get<MemberLevelInfo[]>('/mall-user/app/user/member/levels')
}

// 开通/续费付费会员
export function createPaidOrder(data: { levelId: string }) {
  return alovaInstance.Post<any>('/mall-user/app/user/member/paid-order', data)
}

// 获取成长值记录
export function getGrowthLogPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/app/user/member/growth-log', { params })
}