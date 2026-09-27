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
  /** 场次结束时间（商详页倒计时用） */
  sessionEndTime?: string
  /** 场次名称 */
  sessionName?: string
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

/**
 * C端折扣活动 VO（会场按活动分组）
 *
 * 与「商品最优折扣」接口区分：本类型描述一个活动及其商品明细，
 * 由后端 `getActiveActivityPage` 分页返回。
 */
export interface AppDiscountActivityVO {
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
  /** 倒计时(秒) */
  countdown: number
  /** 参与折扣的商品；全场活动为空 */
  goodsList: AppDiscountGoodsVO[]
}

/** 折扣活动分页响应 */
export interface AppDiscountPageResponse {
  records: AppDiscountActivityVO[]
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
 * C端「商品当前最优折扣」VO（扁平：一个 SKU 一条）
 *
 * 对应后端 `AppDiscountVO`，用于商品详情页展示该 SKU 命中的最低折扣；
 * 与按活动分组的 `AppDiscountActivityVO` 是两套结构，不可混用。
 */
export interface AppDiscountVO {
  activityId: string
  activityName: string
  /** 折扣类型: 1打折 2减价 3固定价 */
  discountType: number
  discountValue: number
  spuId: string
  skuId: string
  goodsName: string
  goodsImage: string
  originalPrice: number
  discountPrice: number
}

/**
 * 获取商品当前最优折扣信息
 * GET /promotion/app/discount/goods/{skuId}
 */
export function getGoodsDiscountInfo(skuId: string) {
  return alovaInstance.Get<AppDiscountVO>(
    `/promotion/app/discount/goods/${skuId}`,
    {
      headers: { skipToken: true },
    },
  )
}
