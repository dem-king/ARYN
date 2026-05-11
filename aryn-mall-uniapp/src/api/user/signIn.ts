import { alovaInstance } from '@/api/core/instance'

export interface SignInResult {
  consecutiveDay: number
  rewardPoint: number
}

export interface SignInConfig {
  id: string
  consecutiveDay: number
  rewardPoint: number
  sortOrder: number
  status: string
}

// 用户签到
export function signIn() {
  return alovaInstance.Post<SignInResult>('/mall-user/app/signin')
}

// 签到配置列表（用于展示签到奖励规则）
export function getSignInConfigPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/signinconfig/page', {
    params,
  })
}

// 签到记录分页
export function getSignInRecordPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/signinrecord/page', {
    params,
  })
}
