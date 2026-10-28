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
  /** 商品子标题；null/缺失 = 未配置，与名称相同时由展示层隐藏 */
  subTitle?: string | null
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

// ============ 会员价活动货盘（会员专区 Tab「按活动自动」数据源） ============

/**
 * C 端「会员价活动商品」查询结果（会员专区 Tab 的自动货盘）。
 *
 * `spuIds` 是活动配置顺序（后端已按 SKU 配置顺序回填 SPU 并去重保序），
 * C 端只按该顺序渲染，不做二次排序——活动里怎么配置，专区就怎么展示。
 */
export interface AppMemberActivityGoodsVO {
  activityId: string
  activityName: string
  endTime: string
  /** 活动配置的 SPU ID 列表（活动配置顺序） */
  spuIds: string[]
}

/**
 * 查询会员价活动里的商品（会员专区 Tab「按会员价活动自动」数据源）
 * GET /promotion/app/member/activity-goods?activityId=
 *
 * - `activityId` 空/缺失：后端自动取当前进行中的会员价活动（type=10，优先级最高者）；
 *   有值：按指定活动返回（允许运营提前装修，未开始的活动货盘也能预览）；
 * - **免登可访问**：货盘只是商品 ID 列表，不含用户数据与价格，会员价资格由
 *   `/app/member/prices` 另行判定；故按公共请求处理（不携带登录态也无需携带）；
 * - 无活动 / 无商品 → `data:null`，C 端按空态渲染，不报错；
 * - 双模式路径改写由请求封装统一处理，这里只写微服务域首段。
 */
export function getMemberActivityGoods(activityId?: string) {
  const id = String(activityId ?? '').trim()
  return alovaInstance.Get<AppMemberActivityGoodsVO | null>(
    '/promotion/app/member/activity-goods',
    {
      // activityId 为空时不下发该参数：后端据此走「自动取当前进行中活动」
      ...(id ? { params: { activityId: id } } : {}),
      headers: { skipToken: true },
    },
  )
}

// ============ 会员价（F3，会员专享价） ============

/**
 * C 端会员价商品项（只包含请求且被会员价活动覆盖的 SKU/SPU）。
 *
 * 与新人价 SKU 项同构：商详/购物车/列表已掌握商品名称/图片，这里只补价格与等级元数据。
 * 按 spuIds 查询时同一 SPU 可能出现多条（不同规格都配了会员价），由前端按规格价挑选。
 */
export interface AppMemberSkuPriceVO {
  skuId: string
  spuId: string
  /** 会员专享价 */
  memberPrice: number
  /** 划线基准价 = 该 SKU 当前售价（列表按它与卡片展示价匹配规格） */
  originalPrice: number
  /** 活动配置的等级门槛（member_level.id） */
  minLevelId: string
  /** 等级门槛名称，用于「会员专享 ¥x」引导文案 */
  minLevelName: string
}

/**
 * C 端指定 SKU/SPU 的会员价查询结果。
 *
 * `eligible` 三态（后端只从登录态取用户等级，不接受请求参数传入）：
 * - `true`  —— 当前用户等级达标，可享会员价；
 * - `false` —— 已登录但等级不达标，**不展示会员价**，保持原价并给引导文案；
 * - `null`  —— 未登录/未知，保持原价展示，`items` 仍下发活动配置价供引导文案使用。
 */
export interface AppMemberPriceVO {
  items: AppMemberSkuPriceVO[]
  /** 当前用户等级 ID；未登录为 null */
  userLevelId: string | null
  /** 当前用户等级名称；未登录为 null */
  userLevelName: string | null
  eligible: boolean | null
  /**
   * 当前用户的会员折扣率权益（全场身份折扣）：0&lt;rate≤1，1=无折扣，null=未登录。
   *
   * **仅供展示取优**：达标价 = min(会员价, round2(当前售价 × rate))，与结算
   * 「会员价与会员折扣率取优，不叠加」同口径；成交仍以下单/结算接口为准。
   */
  userDiscountRate: number | null
}

/**
 * 批量查询指定 SKU 的会员价（商详 / 购物车等已聚焦商品的页面用）
 * GET /promotion/app/member/prices?skuIds=1,2
 *
 * **可选登录**：接口在免登白名单里，但与新人价同样**不传 `skipToken`** ——
 * 带上登录态时后端才判定当前用户等级是否达标；未登录时中间件不带 token，
 * 请求等价免登，后端返回 `eligible=null` + 活动配置价（供引导文案）。
 *
 * 双模式路径改写由请求封装统一处理，这里只写微服务域首段。
 */
export function getMemberSkuPrices(skuIds: string[]) {
  const ids = skuIds.filter(Boolean)
  if (!ids.length) {
    return Promise.resolve<AppMemberPriceVO | null>(null)
  }
  return alovaInstance.Get<AppMemberPriceVO | null>(
    '/promotion/app/member/prices',
    {
      params: { skuIds: ids.join(',') },
    },
  )
}

/**
 * 批量查询指定 **SPU** 的会员价（会员专区商品流列表用）
 * GET /promotion/app/member/prices?spuIds=1,2
 *
 * 列表接口（`/product/app/goodsspu/page`）只下发 SPU、没有 SKU 明细，无法按 skuIds
 * 取价；后端因此支持可选 `spuIds`。同一 SPU 可能返回多条 item（不同规格都配了会员价），
 * 由前端按「originalPrice 与卡片展示价一致」挑选对应规格（见 `resolveListMemberPrice`），
 * 后端不做挑选。
 *
 * **可选登录**：与 `getMemberSkuPrices` 同一契约——请求带登录态（未登录时等价免登），
 * 由后端判定等级资格；未登录返回 `eligible=null`，items 仍带活动价供「会员专享 ¥x」引导。
 * 空入参不发请求；接口失败由调用方静默降级（列表按卡片原价渲染）。
 */
export function getMemberSpuPrices(spuIds: string[]) {
  const ids = spuIds.filter(Boolean)
  if (!ids.length) {
    return Promise.resolve<AppMemberPriceVO | null>(null)
  }
  return alovaInstance.Get<AppMemberPriceVO | null>(
    '/promotion/app/member/prices',
    {
      params: { spuIds: ids.join(',') },
    },
  )
}
