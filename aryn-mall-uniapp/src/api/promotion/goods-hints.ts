import { alovaInstance } from '@/api/core/instance'

export interface GoodsPromotionHints {
  gifts: Array<{
    activityId: string
    name: string
    thresholdAmount: number
    gifts: Array<{ name: string, quantity: number }>
  }>
  volumes: Array<{
    activityId: string
    name: string
    tiers: Array<{ minAmount: number | null, minQty: number | null, discountAmount: number }>
  }>
}

export function getGoodsPromotionHints(
  spuId: string,
  params: { skuId?: string, purchaseScene: string, vesselCallId?: string },
) {
  return alovaInstance.Get<GoodsPromotionHints>(`/promotion/app/goods-hints/${encodeURIComponent(spuId)}`, {
    params,
    cacheFor: 0,
  })
}
