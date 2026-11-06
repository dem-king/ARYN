import { alovaInstance } from '@/api/core/instance'

export const DISTRIBUTION_CENTER_PATH = '/sub-pages/user/distribution/index'
export const DISTRIBUTION_COUPON_EXCHANGE_PATH = '/sub-pages/user/distribution/coupon-exchange'
export const DISTRIBUTION_COUPON_EXCHANGE_RECORD_PATH = '/sub-pages/user/distribution/coupon-exchange-record'

export const DISTRIBUTION_PERMISSION_POINTS = {
  ENTITY_PAGE: 'distribution:center:page',
} as const

export type DistributionPermissionPoint =
  (typeof DISTRIBUTION_PERMISSION_POINTS)[keyof typeof DISTRIBUTION_PERMISSION_POINTS]

/**
 * 分销中心概览
 */
export interface DistributionCenterSummary {
  userId?: string
  inviteUserCount: number
  totalCommission: number
  availableCommission: number
  pendingCommission: number
  frozenCommission: number
  withdrawnCommission: number
  commissionDebt: number
  pendingWithdrawCount: number
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
  accountNo?: string
  payoutNo?: string
  payoutTime?: string
  payoutBy?: string
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
  /**
   * 绑定来源：SHARE 分享链接、QRCODE 推广码（后端据此写 sales_customer_bind.bind_source）
   */
  bindSource?: DistributionBindSource
}

/**
 * 客户绑定来源（与后端 sales_customer_bind.bind_source 对齐）
 */
export type DistributionBindSource =
  | 'SHARE'
  | 'QRCODE'
  | 'ADMIN'
  | 'TRANSFER'
  | 'CLAIM'

/**
 * 券包关联的优惠券模板
 */
export interface DistributionCouponPackItem {
  couponInfoId: string
  name?: string
  faceAmount?: number
  quantity?: number
}

/**
 * 可兑换券包（分销佣金兑换优惠券）
 *
 * 后端接口：GET /promotion/app/distribution/coupon/pack/list（仅返回上架券包）
 */
export interface DistributionCouponPack {
  id: string
  packName: string
  faceAmount: number
  thresholdAmount?: number
  /** 有效期（天） */
  validDays?: number
  /** 兑换消耗佣金 */
  requiredCommission: number
  /** 库存，null 表示不限 */
  stock?: number | null
  /** 0 上架 1 下架 */
  status?: string
  sort?: number
  remark?: string
  couponInfos?: DistributionCouponPackItem[]
}

/**
 * 券包兑换参数
 */
export interface DistributionCouponExchangePayload {
  packId: string
}

/**
 * 券兑换记录
 *
 * 后端接口：GET /promotion/app/distribution/coupon/exchange/page
 */
export interface DistributionCouponExchangeRecord {
  id: string
  userId?: string
  nickname?: string
  packId?: string
  packName?: string
  faceAmount?: number
  /** 消耗佣金 */
  commissionAmount?: number
  couponUserIds?: string
  /** 0 发放中 1 已发放 2 发放失败 */
  status: string
  exchangeTime?: string
  createTime?: string
  remark?: string
}

/**
 * 券兑换记录分页查询参数
 */
export interface DistributionCouponExchangeRecordPageQuery {
  current?: number
  size?: number
}

/**
 * 分销中心概览
 */
export function getDistributionCenterSummary() {
  return alovaInstance.Get<DistributionCenterSummary>(
    '/promotion/app/distribution/center',
  )
}

/**
 * 佣金记录分页
 */
export function getDistributionCommissionRecordPage(
  params: DistributionCommissionRecordPageQuery = {},
) {
  return alovaInstance.Get<
    DistributionPageResult<DistributionCommissionRecord>
  >('/promotion/app/distribution/commission/page', {
    params,
  })
}

/**
 * 提现申请（已下线）
 *
 * 2026-11 销售业绩/提成改造：普通分销员佣金不再支持提现，改为兑换优惠券
 * （见 docs/50-需求文档/2026-11-05-销售业绩归属与提成）。后端
 * `POST /promotion/app/distribution/withdraw/apply` 会返回明确的业务错误，
 * 因此这里不再提供调用入口；`DistributionWithdrawApplyPayload` 类型暂时保留，
 * 避免历史代码/测试大范围改动。新入口统一走 {@link exchangeDistributionCouponPack}。
 */

/**
 * 提现进度分页（保留只读：历史提现单据查询）
 */
export function getDistributionWithdrawProgressPage(
  params: DistributionWithdrawProgressPageQuery = {},
) {
  return alovaInstance.Get<
    DistributionPageResult<DistributionWithdrawProgress>
  >('/promotion/app/distribution/withdraw/page', {
    params,
  })
}

/**
 * 可兑换券包列表（上架券包）
 *
 * 佣金兑换优惠券的券源：分销员用 availableCommission 兑换大额优惠券。
 */
export function getDistributionCouponPackList() {
  return alovaInstance.Get<DistributionCouponPack[]>(
    '/promotion/app/distribution/coupon/pack/list',
  )
}

/**
 * 兑换券包
 *
 * 单事务：校验上架/库存/佣金余额 → 扣 available_commission → 发券。
 * 余额不足、券发放失败都会整体回滚，调用方只需处理业务错误提示。
 */
export function exchangeDistributionCouponPack(
  data: DistributionCouponExchangePayload,
) {
  return alovaInstance.Post<boolean>(
    '/promotion/app/distribution/coupon/exchange',
    data,
  )
}

/**
 * 我的券兑换记录分页
 */
export function getDistributionCouponExchangeRecordPage(
  params: DistributionCouponExchangeRecordPageQuery = {},
) {
  return alovaInstance.Get<
    DistributionPageResult<DistributionCouponExchangeRecord>
  >('/promotion/app/distribution/coupon/exchange/page', {
    params,
  })
}

/**
 * 分享参数绑定
 */
export function bindDistributionShareParams(
  data: DistributionShareBindPayload,
) {
  return alovaInstance.Post<boolean>(
    '/promotion/app/distribution/share/bind',
    data,
  )
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
  return alovaInstance.Post<boolean>(
    '/promotion/app/distribution/register',
    data,
  )
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
  return alovaInstance.Get<DistributionConfigInfo>(
    '/promotion/app/distribution/config',
  )
}
