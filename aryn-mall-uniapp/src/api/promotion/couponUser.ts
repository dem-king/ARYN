import { alovaInstance } from '@/api/core/instance'

// 查询优惠券
export function getPage(params: object) {
  return alovaInstance.Get<any>('/promotion/app/couponuser/page', {
    params,
  })
}
// 领取优惠券
export function addObj(data: object) {
  return alovaInstance.Post<any>('/promotion/app/couponuser', data)
}

// 查询用户可用优惠券数量
export function getCount() {
  return alovaInstance.Get<any>('/promotion/app/couponuser/count')
}
