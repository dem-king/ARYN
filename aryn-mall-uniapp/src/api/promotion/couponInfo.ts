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
 *
 * 该接口对游客开放（无需登录即可领取），但仍要带上登录态：
 * 服务端据此返回 userReceiveCount，C 端才能把「已领取」状态在刷新后恢复出来。
 * 未登录/登录态失效时服务端按游客处理，不会因此报错。
 */
export function getPage(params: object) {
  return alovaInstance.Get<CouponInfoPageResponse>('/promotion/app/couponinfo/page', {
    params,
  })
}
