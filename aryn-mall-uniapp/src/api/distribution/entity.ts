import { alovaInstance } from '@/api/core/instance'

export const DISTRIBUTION_CENTER_PATH = '/sub-pages/user/distribution/index'

export const DISTRIBUTION_PERMISSION_POINTS = {
  ENTITY_PAGE: 'distribution:center:page',
} as const

export type DistributionPermissionPoint = typeof DISTRIBUTION_PERMISSION_POINTS[keyof typeof DISTRIBUTION_PERMISSION_POINTS]

/**
 * 分销中心概览
 */
export interface DistributionCenterSummary {
  userId?: string
  inviteUserCount: number
  totalCommission: number
  availableCommission: number
  frozenCommission: number
  withdrawnCommission: number
}

/**
 * 佣金流水记录
 */
export interface DistributionCommissionRecord {
  id: string
  bizOrderId?: string
  flowType: string
  amount: number
  balanceAfter: number
  createTime: string
  remark?: string
}

/**
 * 佣金流水分页查询参数
 */
export interface DistributionCommissionRecordPageQuery {
  current?: number
  size?: number
}

/**
 * 提现申请参数
 */
export interface DistributionWithdrawApplyPayload {
  amount: number
  accountType?: string
  accountNo: string
  accountName: string
  remark?: string
}

/**
 * 提现进度记录
 */
export interface DistributionWithdrawProgress {
  id: string
  withdrawNo?: string
  amount: number
  status: string
  rejectReason?: string
  createTime: string
  updateTime?: string
}

/**
 * 提现进度分页查询参数
 */
export interface DistributionWithdrawProgressPageQuery {
  current?: number
  size?: number
}

/**
 * 通用分页结果
 */
export interface DistributionPageResult<T = any> {
  records: T[]
  total: number
  current: number
  size: number
}

/**
 * 分享参数绑定（与后端DistributionUserRegisterDTO对齐）
 */
export interface DistributionShareBindPayload {
  inviterUserId?: string
  nickname?: string
  avatar?: string
}

/**
 * 分销中心概览
 */
export function getDistributionCenterSummary() {
  return alovaInstance.Get<DistributionCenterSummary>('/promotion/app/distribution/center')
}

/**
 * 佣金记录分页
 */
export function getDistributionCommissionRecordPage(params: DistributionCommissionRecordPageQuery = {}) {
  return alovaInstance.Get<DistributionPageResult<DistributionCommissionRecord>>('/promotion/app/distribution/commission/page', {
    params,
  })
}

/**
 * 提现申请
 */
export function submitDistributionWithdrawApply(data: DistributionWithdrawApplyPayload) {
  return alovaInstance.Post<boolean>('/promotion/app/distribution/withdraw/apply', data)
}

/**
 * 提现进度分页
 */
export function getDistributionWithdrawProgressPage(params: DistributionWithdrawProgressPageQuery = {}) {
  return alovaInstance.Get<DistributionPageResult<DistributionWithdrawProgress>>('/promotion/app/distribution/withdraw/page', {
    params,
  })
}

/**
 * 分享参数绑定
 */
export function bindDistributionShareParams(data: DistributionShareBindPayload) {
  return alovaInstance.Post<boolean>('/promotion/app/distribution/share/bind', data)
}

/**
 * 注册分销用户
 */
export interface DistributionRegisterPayload {
  inviterUserId?: string
  nickname?: string
  avatar?: string
}

export function registerDistributionUser(data: DistributionRegisterPayload) {
  return alovaInstance.Post<boolean>('/promotion/app/distribution/register', data)
}

/**
 * 获取分销配置
 */
export interface DistributionConfigInfo {
  id: string
  configName: string
  commissionRate: number
  commissionRateLevel2?: number
  minWithdrawAmount: number
  settleCycleDays?: number
  status: string
}

export function getDistributionConfig() {
  return alovaInstance.Get<DistributionConfigInfo>('/promotion/app/distribution/config')
}
