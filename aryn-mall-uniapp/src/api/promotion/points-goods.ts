import { alovaInstance } from '@/api/core/instance'

export interface PointsGoods {
  id?: string
  name?: string
  cover?: string
  type?: string
  targetId?: string
  pointsPrice?: number
  stock?: number
  limitPerUser?: number
  startTime?: string
  endTime?: string
  status?: string
}

export interface PointsGoodsListResponse {
  records: PointsGoods[]
  total: number
}

/** 获取积分商品列表 */
export function getPointsGoodsList(params: object) {
  return alovaInstance.Get<PointsGoodsListResponse>('/promotion/app/points-goods/list', {
    params,
  })
}

/** 获取积分商品详情 */
export function getPointsGoodsById(id: string) {
  return alovaInstance.Get<PointsGoods>(`/promotion/app/points-goods/${id}`)
}

/** 兑换积分商品 */
export function exchangePointsGoods(id: string) {
  return alovaInstance.Post(`/promotion/app/points-goods/exchange/${id}`)
}