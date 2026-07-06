import { alovaInstance } from '@/api/core/instance'

export interface PointsInfo {
  point: number
  balance: number
  levelName: string
  growthValue: number
}

// 获取用户积分信息（积分余额、储值余额、等级名称、成长值）
export function getPointsInfo() {
  return alovaInstance.Get<PointsInfo>('/mall-user/app/points/info')
}

// 用户积分记录分页
export function getPointsRecordPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/pointsrecord/user/page', {
    params,
  })
}
