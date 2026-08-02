/**
 * 系统版权相关API配置
 * 定义系统版权获取的API接口
 */

import { alovaInstance } from '@/api/core/instance'

export interface CouponInfo {
  id?: string
  couponName?: string
  couponType?: string
  amount?: number
  discount?: number
  threshold?: number
  remainNum?: number
  receiveCount?: number
  receiveStartedAt?: string
  receiveEndedAt?: string
  useRange?: string
  useDescription?: string
  status?: string
  userReceiveCount?: number
}

export interface CouponInfoPageResponse {
  records: CouponInfo[]
  total: number
}
/**
 * 查询优惠券列表
 */
export function getPage(params: object) {
  return alovaInstance.Get<CouponInfoPageResponse>('/promotion/app/couponinfo/page', {
    params,
    headers: { skipToken: true },
  })
}
