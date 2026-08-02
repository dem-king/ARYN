/**
 * 限时秒杀 + 限时折扣 C端 API
 * 对应后端 AppSeckillController / AppDiscountController
 */
import { alovaInstance } from '@/api/core/instance'

// ============ 限时秒杀 ============

/** C端秒杀商品 VO */
export interface AppSeckillGoodsVO {
  spuId: string
  skuId: string
  goodsName: string
  goodsImage: string
  originalPrice: number
  seckillPrice: number
  seckillStock: number
  soldCount: number
  limitPerUser: number
  remainingStock: number
}

/** C端秒杀场次 VO */
export interface AppSeckillVO {
  sessionId: string
  sessionName: string
  startTime: string
  endTime: string
  /** 状态: 0未开始 1进行中 2已结束 */
  status: number
  /** 倒计时(秒) */
  countdown: number
  goodsList: AppSeckillGoodsVO[]
}

/**
 * 获取进行中的秒杀场次列表
 * GET /promotion/app/seckill/sessions
 */
export function getSeckillSessions() {
  return alovaInstance.Get<AppSeckillVO[]>('/promotion/app/seckill/sessions', {
    headers: { skipToken: true },
  })
}

/**
 * 获取指定场次的商品列表
 * GET /promotion/app/seckill/sessions/{sessionId}/goods
 */
export function getSessionGoods(sessionId: string) {
  return alovaInstance.Get<AppSeckillVO>(
    `/promotion/app/seckill/sessions/${sessionId}/goods`,
    {
      headers: { skipToken: true },
    },
  )
}

/**
 * 获取商品当前的秒杀信息
 * GET /promotion/app/seckill/goods/{skuId}
 */
export function getGoodsSeckillInfo(skuId: string) {
  return alovaInstance.Get<AppSeckillGoodsVO>(
    `/promotion/app/seckill/goods/${skuId}`,
    {
      headers: { skipToken: true },
    },
  )
}

// ============ 限时折扣 ============

/** C端折扣商品 VO */
export interface AppDiscountGoodsVO {
  spuId: string
  skuId: string
  goodsName: string
  goodsImage: string
  originalPrice: number
  discountPrice: number
  /** 折扣类型: 1打折 2减价 3固定价 */
  discountType: number
  discountValue: number
}

/** C端折扣活动 VO */
export interface AppDiscountVO {
  activityId: string
  activityName: string
  startTime: string
  endTime: string
  /** 折扣类型: 1打折 2减价 3固定价 */
  discountType: number
  discountValue: number
  /** 适用范围: 1全场 2指定商品 */
  scope: number
  /** 状态: 0未开始 1进行中 2已结束 */
  status: number
  countdown: number
  goodsList: AppDiscountGoodsVO[]
}

/** 折扣活动分页响应 */
export interface AppDiscountPageResponse {
  records: AppDiscountVO[]
  total: number
}

/**
 * 获取进行中的折扣活动列表（含商品）
 * GET /promotion/app/discount/activities
 */
export function getDiscountActivities(params?: { current?: number, size?: number }) {
  return alovaInstance.Get<AppDiscountPageResponse>(
    '/promotion/app/discount/activities',
    {
      headers: { skipToken: true },
      params,
    },
  )
}

/**
 * 获取指定折扣活动的商品列表
 * GET /promotion/app/discount/activities/{activityId}/goods
 */
export function getDiscountGoods(activityId: string) {
  return alovaInstance.Get<AppDiscountVO>(
    `/promotion/app/discount/activities/${activityId}/goods`,
    {
      headers: { skipToken: true },
    },
  )
}

/**
 * 获取商品当前最优折扣信息
 * GET /promotion/app/discount/goods/{skuId}
 */
export function getGoodsDiscountInfo(skuId: string) {
  return alovaInstance.Get<AppDiscountGoodsVO>(
    `/promotion/app/discount/goods/${skuId}`,
    {
      headers: { skipToken: true },
    },
  )
}