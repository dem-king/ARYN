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
  /** 本场「N 人在抢」的去重人数（场次内 SKU 仍在购物车里的人数；拿不到时为空） */
  cartUserCount?: number | null
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
  /** 限量库存：null/缺失 = 不限量 */
  stock?: number | null
  /** 剩余可售 = 限量 − 已占用；stock 为空时为 null */
  remainingStock?: number | null
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
  /** 活动级限量：仅全场型，null/缺失 = 不限量 */
  stock?: number | null
  /** 活动级剩余可售（全场型） */
  remainingStock?: number | null
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
 * 批量查询指定 SKU 当前命中的最优限时折扣（商详 / 购物车 / 加购确认弹层用）。
 *
 * 免登可访问（与折扣会场列表同一白名单口径）：折扣价与会场展示同源，
 * 未登录也能看到活动价；真正的成交价仍以下单/结算接口为准。
 * 单个 SKU 的 `/goods/{skuId}` 带登录校验，供「商品最优折扣」单点场景使用；
 * 页面一次要标几十行时走本批量接口，避免 N+1 请求。
 */
export function getGoodsDiscountPrices(skuIds: string[]) {
  const ids = skuIds.filter(Boolean)
  if (!ids.length) {
    return Promise.resolve<AppDiscountVO[]>([])
  }
  return alovaInstance.Get<AppDiscountVO[]>(
    '/promotion/app/discount/prices',
    {
      headers: { skipToken: true },
      params: { skuIds: ids.join(',') },
    },
  )
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

// ============ 新人专享 ============

/** C端新人专享商品项 */
export interface AppNewbieGoodsVO {
  spuId: string
  skuId: string
  goodsName: string
  goodsImage: string
  /** 新人专享价 */
  newbiePrice: number
  /** 划线原价（当前售价） */
  originalPrice: number
}

/** C端新人专享楼层 VO */
export interface AppNewbieFloorVO {
  activityId: string
  activityName: string
  /** 注册时效（天），null 表示不限 */
  registerWithinDays: number | null
  /** 每人每 SKU 限购件数（固定 1） */
  limitPerUser: number
  endTime: string
  goodsList: AppNewbieGoodsVO[]
}

/**
 * 获取当前进行中的新人专享活动（含新人价商品）
 * GET /promotion/app/newbie/current
 *
 * 免登可访问：楼层对所有人展示新人价做营销吸引，资格在下单链路硬校验。
 */
export function getCurrentNewbieFloor() {
  return alovaInstance.Get<AppNewbieFloorVO | null>(
    '/promotion/app/newbie/current',
    {
      headers: { skipToken: true },
    },
  )
}

/**
 * C 端指定 SKU 的新人价商品项（只包含请求且被活动覆盖的 SKU）。
 *
 * 与楼层商品项的口径差异：楼层项带名称/图片（楼层要自己渲染卡片），
 * 商详/购物车已经掌握了商品信息，这里只补价格与活动元数据。
 */
export interface AppNewbieSkuPriceVO {
  skuId: string
  spuId: string
  /** 新人专享价 */
  newbiePrice: number
  /** 划线原价（当前售价） */
  originalPrice: number
}

/**
 * C 端指定 SKU 的新人价查询结果。
 *
 * `eligible` 三态语义（后端只从登录态取用户，不接受请求参数传入）：
 * - `true`  —— 当前用户可享新人价；
 * - `false` —— 已登录但确定不符合资格，此时**不要展示新人价**，按原价并给出 `ineligibleReason` 说明；
 * - `null`  —— 未登录/未知，按可享新人价展示（资格在下单链路硬校验）。
 */
export interface AppNewbiePriceVO {
  activityId: string
  activityName: string
  /** 注册时效（天），null 表示不限 */
  registerWithinDays: number | null
  /** 每人每 SKU 限购件数（固定 1） */
  limitPerUser: number
  endTime: string
  eligible: boolean | null
  /** 不满足资格的原因：ALREADY_PAID/REGISTER_TIMEOUT/PARAM_INVALID/ERROR */
  ineligibleReason?: string | null
  items: AppNewbieSkuPriceVO[]
}

/**
 * 批量查询指定 SKU 的新人价（商详 / 购物车等已聚焦商品的页面用）
 * GET /promotion/app/newbie/prices?skuIds=1,2
 *
 * **可选登录**：接口本身在免登白名单里（未登录也能看到活动价），但这里**故意不传
 * `skipToken`** —— 带上登录态时后端才会判定当前用户资格，页面据此在用户确定不符合
 * 资格时直接按原价展示并说明原因，消除「页面标新人价、结算按原价」的价差观感。
 *
 * 未登录时中间件不带 token，请求等价免登，不会 401（接口已双端登记白名单）。
 */
export function getNewbieSkuPrices(skuIds: string[]) {
  const ids = skuIds.filter(Boolean)
  if (!ids.length) {
    return Promise.resolve<AppNewbiePriceVO | null>(null)
  }
  return alovaInstance.Get<AppNewbiePriceVO | null>(
    '/promotion/app/newbie/prices',
    {
      params: { skuIds: ids.join(',') },
    },
  )
}
