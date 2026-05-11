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
    params,
  })
}

export function getActivityById(id: string) {
  return alovaInstance.Get<GroupBuyActivity>(`/promotion/app/groupbuy/activity/${id}`)
}
