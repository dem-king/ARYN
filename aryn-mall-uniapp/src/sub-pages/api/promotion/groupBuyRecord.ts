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
  groupStatus?: string
  expireAt?: string
  successAt?: string
  isJoined?: boolean
  /** 当前用户参团状态：0待付款,1已付款,2已取消 */
  myMemberStatus?: string
  /** 当前用户已创建的订单ID（待付款时用于继续支付） */
  myOrderId?: string
}

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
