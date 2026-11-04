import { alovaInstance } from '@/api/core/instance'

/** 签到结果（后端 SignInResultVO） */
export interface SignInResult {
  consecutiveDay: number
  rewardPoint: number
}

/** 签到奖励规则（后端 AppSignInConfigVO） */
export interface SignInConfig {
  id: string
  consecutiveDay: number
  rewardPoint: number
  sortOrder: number
  status: string
}

/** 签到奖励规则分页（后端 IPage，仅声明页面消费的字段） */
export interface AppSignInConfigPage {
  records: SignInConfig[]
  total?: number
}

/** C 端签到记录（后端 AppSignInRecordVO） */
export interface AppSignInRecord {
  id: string
  /** 签到日期 yyyy-MM-dd */
  signDate: string
  /** 连续签到天数 */
  consecutiveDay: number
  /** 获得积分 */
  rewardPoint: number
  createTime: string
}

/** 签到记录分页（后端 IPage，仅声明页面消费的字段） */
export interface AppSignInRecordPage {
  records: AppSignInRecord[]
  total?: number
}

// 用户签到
export function signIn() {
  return alovaInstance.Post<SignInResult>('/mall-user/app/signin')
}

// 签到奖励规则（C 端只读启用中的配置）
export function getSignInConfigPage(params: object) {
  return alovaInstance.Get<AppSignInConfigPage>('/mall-user/app/signin/configs', {
    params,
  })
}

// 我的签到记录（C 端按登录态取本人记录）
export function getSignInRecordPage(params: object) {
  return alovaInstance.Get<AppSignInRecordPage>('/mall-user/app/signin/records', {
    params,
  })
}

/**
 * 今日签到记录：复用记录接口，把范围收窄到「当天」且只取 1 条。
 *
 * `sign_in_record` 对 (user_id, sign_date) 有唯一约束，当天最多一条；
 * 返回 null 表示今天还没签到。
 */
export async function getTodaySignInRecord(date: string): Promise<AppSignInRecord | null> {
  const response = await getSignInRecordPage({
    current: 1,
    size: 1,
    beginDate: date,
    endDate: date,
  })
  return response?.records?.[0] ?? null
}
