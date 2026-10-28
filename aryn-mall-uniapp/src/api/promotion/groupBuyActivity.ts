import { alovaInstance } from '@/api/core/instance'

export interface GroupBuyActivity {
  id?: string
  activityName?: string
  spuId?: string
  skuId?: string
  originalPrice?: number
  groupPrice?: number
  groupNum?: number
  limitNum?: number
  virtualNum?: number
  /** 拼团中的团数（后端实时统计，不参与「已拼」展示） */
  joinCount?: number
  /** 真实已拼件数：已付款参团成员数（后端实时统计） */
  realSoldCount?: number
  /** 展示用「已拼件数」= virtualNum（运营虚增）+ realSoldCount，由后端合成下发 */
  soldCount?: number
  activityStatus?: string
  startedAt?: string
  endedAt?: string
  groupExpireHours?: number
}

export interface GroupBuyActivityPageResponse {
  records: GroupBuyActivity[]
  total: number
}

export function getActivityPage(params: object) {
  return alovaInstance.Get<GroupBuyActivityPageResponse>('/promotion/app/groupbuy/activity/page', {
    headers: { skipToken: true },
    params,
  })
}

export function getActivityById(id: string) {
  return alovaInstance.Get<GroupBuyActivity>(`/promotion/app/groupbuy/activity/${id}`, {
    headers: { skipToken: true },
  })
}
