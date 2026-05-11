import { alovaInstance } from '@/api/core/instance'

// 分页列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/mall-order/app/shopping-cart/page', {
    params,
  })
}

// 购物车添加
export function addShoppingCart(data: object) {
  return alovaInstance.Post<any>('/mall-order/app/shopping-cart', data)
}

// 购物车编辑
export function editShoppingCart(data: object) {
  return alovaInstance.Put<any>('/mall-order/app/shopping-cart', data)
}

// 购物车删除
export function delShoppingCart(data: object) {
  return alovaInstance.Post<any>('/mall-order/app/shopping-cart/del', data)
}

// 购物车数量查询
export function getCount(params: object) {
  return alovaInstance.Get<any>('/mall-order/app/shopping-cart/count', {
    params,
  })
}
