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

// 签到奖励规则（C 端只读启用中的配置）
export function getSignInConfigPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/app/signin/configs', {
    params,
  })
}

// 我的签到记录（C 端按登录态取本人记录）
export function getSignInRecordPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/app/signin/records', {
    params,
  })
}
