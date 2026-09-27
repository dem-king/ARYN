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
 * 获取当前登录用户的会员等级与标签信息。
 * 用于装修区块条件渲染（memberLevel / userTag 规则）。
 */
export function getMemberCurrentInfo() {
  return alovaInstance.Get<MemberCurrentInfo>('/mall-user/app/member/current-info')
}
