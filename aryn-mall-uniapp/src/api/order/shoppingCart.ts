import { alovaInstance } from '@/api/core/instance'
import { useShipContextStore } from '@/store/shipContextStore'

// 分页列表
export function getPage(params: object) {
  return alovaInstance.Get<any>('/mall-order/app/shopping-cart/page', {
    params,
  })
}

// 购物车添加（自动携带当前船舶/靠港/场景归属，防串船分组键）
export function addShoppingCart(data: Record<string, any>) {
  const shipContextStore = useShipContextStore()
  return alovaInstance.Post<any>('/mall-order/app/shopping-cart', {
    ...data,
    vesselId: shipContextStore.vesselId || undefined,
    vesselCallId: shipContextStore.vesselCallId || undefined,
    purchaseScene: shipContextStore.purchaseScene || undefined,
  })
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
