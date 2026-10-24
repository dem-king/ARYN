import { alovaInstance } from '@/api/core/instance'

export interface PointsInfo {
  point: number
  balance: number
  levelName: string
  /** 30 天内即将过期积分（按剩余可用口径汇总），null/缺失=无即将过期积分 */
  expiringPoints?: number | null
  /** 最近一次过期时间（30 天窗口内最早到期批次，yyyy-MM-dd HH:mm:ss），null=永久/无 */
  nearestExpireTime?: string | null
}

/** C 端积分记录（后端 AppPointsRecordVO） */
export interface PointsRecord {
  id: string
  // 后端 AppPointsRecordVO.changeType 是字符串：1-获取；2-消耗
  changeType: string
  changePoint: number
  balanceAfter: number
  triggerScene: string
  remark: string
  createTime: string
  /** 该批次积分到期时间（仅获取类流水下发，yyyy-MM-dd HH:mm:ss），null=永不过期 */
  expireTime?: string | null
}

/** 积分记录分页结果（后端 IPage 结构，仅声明页面用到的字段） */
export interface PointsRecordPage {
  records: PointsRecord[]
  total?: number
}

// 获取用户积分信息（积分余额、储值余额、等级名称、即将过期汇总）
export function getPointsInfo() {
  return alovaInstance.Get<PointsInfo>('/mall-user/app/points/info')
}

// 我的积分记录（C 端按登录态取本人记录）
export function getPointsRecordPage(params: object) {
  return alovaInstance.Get<PointsRecordPage>('/mall-user/app/points/records', {
    params,
  })
}
