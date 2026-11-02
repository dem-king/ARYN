import { alovaInstance } from '@/api/core/instance'

// 查询评论列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/product/app/goodsappraise/page', {
    params,
  })
}
// 查询用户评论列表
// 支持订单参数：`getUserPage({ current, size, orderId })` 只查该订单下的评价，
// 评价页用它判断哪些订单项已评（子集提交/跳过场景）。
export function getUserPage(params: object) {
  return alovaInstance.Get<any>('/product/app/goodsappraise/user-page', {
    params,
  })
}
// 查询数量
export function getCount(params: object) {
  return alovaInstance.Get<any>('/product/app/goodsappraise/count', {
    params,
  })
}
