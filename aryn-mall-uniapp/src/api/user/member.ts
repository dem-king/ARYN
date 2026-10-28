import { alovaInstance } from '@/api/core/instance'

/** 用户标签项 */
export interface MemberTag {
  tagId: string
  tagName: string
}

/** 当前登录用户的会员信息（等级 + 标签） */
export interface MemberCurrentInfo {
  /** 会员等级 ID */
  levelId: string
  /** 会员等级名称 */
  levelName: string
  /** 用户标签列表 */
  tags: MemberTag[]
}

/**
 * 当前登录会员的有效权益（后端 `MemberBenefitsVO`）。
 *
 * 装修「会员权益条」只消费折扣率一项（0.9 → 9 折），其余字段保留给后续
 * 权益透出场景；后端未配置权益时 discountRate 回落 1（无折扣）。
 */
export interface MemberBenefits {
  levelId: string
  levelName: string
  /** 最优商品折扣率，范围 (0,1]；1 表示无折扣 */
  discountRate: number
  /** 是否免运费 */
  freeShipping: boolean
  /** 最优积分倍率，不小于 1 */
  pointsMultiplier: number
  /** 等级专属积分抵扣上限（百分数，如 30 表示 30%）；null=未配置，回落全局配置 */
  deductMaxRatio?: number | null
  /** 等级专属券模板 ID 列表 */
  exclusiveCouponTemplateIds?: string[]
}

/**
 * 获取当前登录用户的会员等级与标签信息。
 * 用于装修区块条件渲染（memberLevel / userTag 规则）。
 */
export function getMemberCurrentInfo() {
  return alovaInstance.Get<MemberCurrentInfo>('/mall-user/app/member/current-info')
}

/**
 * 获取当前登录会员的有效权益（等级折扣率 / 免邮 / 积分倍率等）。
 *
 * 与 `current-info` 一样是 C 端登录态接口（只消费 `/app/member/*`，不碰管理端），
 * 调用方必须已登录；未登录/接口失败时调用方按「不渲染权益透出」降级。
 */
export function getMemberBenefits() {
  return alovaInstance.Get<MemberBenefits | null>('/mall-user/app/member/benefits')
}
