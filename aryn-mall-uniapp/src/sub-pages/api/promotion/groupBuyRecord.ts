import { alovaInstance } from '@/api/core/instance'

export interface GroupBuyRecord {
  id?: string
  activityId?: string
  spuId?: string
  skuId?: string
  groupPrice?: number
  groupNum?: number
  currentNum?: number
  leaderUserId?: string
  /**
   * 团长昵称（后端 record 列表装配下发；后端未合入/查无用户时为空，
   * 前端回落「拼友」，避免详情页再显示裸 userId）。
   */
  leaderNickname?: string
  /** 团长头像 URL（装配下发；缺失或加载失败走灰圆首字兜底） */
  leaderAvatar?: string
  /** 本团成员（团长置首、isLeader=1；缺失时按 currentNum 渲染进度位置） */
  memberList?: GroupBuyMemberVO[]
  groupStatus?: string
  expireAt?: string
  successAt?: string
  isJoined?: boolean
  /** 当前用户参团状态：0待付款,1已付款,2已取消 */
  myMemberStatus?: string
  /** 当前用户已创建的订单ID（待付款时用于继续支付） */
  myOrderId?: string
}

/**
 * 拼团成员出参（后端 `GroupBuyMemberVO`）。
 *
 * 注意与实体类型 {@link GroupBuyMember} 区分：这里只承载展示所需的公开字段，
 * 是 record 列表/详情的内嵌出参，不带 orderId/租户/审计等后台字段。
 */
export interface GroupBuyMemberVO {
  userId?: string
  nickname?: string
  avatarUrl?: string
  /** 是否团长：'1' 是 */
  isLeader?: string
  /** 参团状态：0待付款,1已付款,2已取消 */
  memberStatus?: string
}

/** 拼团成员实体（沿用既有类型，保留给后台/记录页使用） */
export interface GroupBuyMember {
  id?: string
  recordId?: string
  activityId?: string
  userId?: string
  orderId?: string
  memberStatus?: string
  isLeader?: string
}

export interface GroupBuyRecordPageResponse {
  records: GroupBuyRecord[]
  total: number
}

export function openGroup(data: { activityId: string }) {
  return alovaInstance.Post<GroupBuyRecord>('/promotion/app/groupbuy/open', data)
}

export function joinGroup(data: { activityId: string, recordId: string }) {
  return alovaInstance.Post<GroupBuyRecord>('/promotion/app/groupbuy/join', data)
}

export function getRecordPage(params: object) {
  return alovaInstance.Get<GroupBuyRecordPageResponse>('/promotion/app/groupbuy/record/page', {
    params,
  })
}
