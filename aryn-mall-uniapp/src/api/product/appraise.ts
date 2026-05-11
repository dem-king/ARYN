import { alovaInstance } from '@/api/core/instance'

// 查询评论列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/product/app/goodsappraise/page', {
    params,
  })
}
// 查询用户评论列表
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
