import { alovaInstance } from '@/api/core/instance'

export interface PointsInfo {
  point: number
  balance: number
  levelName: string
}

// 获取用户积分信息（积分余额、储值余额、等级名称）
export function getPointsInfo() {
  return alovaInstance.Get<PointsInfo>('/mall-user/app/points/info')
}

// 我的积分记录（C 端按登录态取本人记录）
export function getPointsRecordPage(params: object) {
  return alovaInstance.Get<any>('/mall-user/app/points/records', {
    params,
  })
}
