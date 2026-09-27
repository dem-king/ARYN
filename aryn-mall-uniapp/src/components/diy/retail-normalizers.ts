import type {
  DiscountActivityItem,
  DiscountGoodsItem,
  SeckillGoodsItem,
  SeckillSessionItem,
} from './retail-types'

export interface RetailGoodsItem {
  id: string
  imageUrl: string
  name: string
  price: number
  /**
   * 商品原价（划线价），来源 `goods_spu.original_price`。
   *
   * 0 表示商家未填原价（存量商品普遍如此），渲染侧必须据此隐藏划线，
   * 否则会划出一个「￥0」。判断口径与商详页一致：仅当原价 > 现价时才显示。
   */
  originalPrice: number
  sales: number
  stock: number
  /**
   * 规格摘要（如「950ml/瓶」）。
   *
   * 商品**列表**接口只返回 SPU 字段，没有 SKU 明细，因此大多数情况下这里
   * 为空；仅当按 ID 批量取回（含 goodsSkus）时才可能补上。
   * 卡片渲染必须允许它缺失，不能编造规格。
   */
  specsInfo?: string
  /**
   * 是否免配送费。
   *
   * 来源 `goods_spu.freight_type === '0'`（包邮）。注意语义是「商品级包邮」，
   * 与"满额免运费"这类订单级规则不是一回事，文案上不写「包邮」以免与
   * 运费模板规则混淆。
   */
  freeShipping?: boolean
}

export interface RetailActivityItem {
  activityPrice: number
  endTime: string
  id: string
  imageUrl: string
  name: string
  originalPrice: number
  spuId: string
  status: 'active' | 'ended' | 'pending'
}

export interface RetailShopInfo {
  address: string
  id: string
  logoUrl: string
  name: string
  phone: string
  siteUrl: string
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object'
    ? value as Record<string, unknown>
    : {}
}

function extractItems(value: unknown): unknown[] {
  if (Array.isArray(value))
    return value
  const records = asRecord(value).records
  return Array.isArray(records) ? records : []
}

function toNumber(value: unknown): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function toText(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/**
 * 取规格摘要。
 *
 * 列表接口不返回 goodsSkus，所以优先用后端可能补的 `specsInfo` 字段；
 * 其次从 goodsSkus[0].specsArr 拼。单规格商品的值为「默认」，视为无规格。
 */
function firstSpecs(record: Record<string, unknown>): string {
  const direct = toText(record.specsInfo ?? record.packageSpec ?? '')
  if (direct && direct !== '默认')
    return direct

  const skus = Array.isArray(record.goodsSkus) ? record.goodsSkus : []
  const first = asRecord(skus[0])
  const specsArr = Array.isArray(first.specsArr) ? first.specsArr : []
  const joined = specsArr
    .map(item => toText(asRecord(item).specsValueName))
    .filter(value => value && value !== '默认')
    .join('；')
  return joined
}

function firstImage(record: Record<string, unknown>): string {
  if (Array.isArray(record.spuUrls))
    return toText(record.spuUrls[0])
  // goodsImage 是秒杀/折扣 VO 的图片字段名，与商品模块的 picUrl 不同名
  return toText(record.picUrl ?? record.imageUrl ?? record.goodsImage ?? record.logoUrl)
}

export function normalizeRetailGoods(value: unknown): RetailGoodsItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item)
      const specsInfo = firstSpecs(record)
      return {
        id: toText(record.id),
        imageUrl: firstImage(record),
        name: toText(record.name ?? record.spuName),
        price: toNumber(record.salesPrice ?? record.price),
        originalPrice: toNumber(record.originalPrice),
        sales: toNumber(record.salesVolume),
        stock: toNumber(record.stock),
        specsInfo: specsInfo || undefined,
        freeShipping: toText(record.freightType) === '0' ? true : undefined,
      }
    })
    .filter(item => Boolean(item.id))
}

export function normalizeRetailActivities(value: unknown): RetailActivityItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item)
      const rawStatus = toText(record.activityStatus ?? record.status)
      const status = rawStatus === '1' || rawStatus === 'active'
        ? 'active'
        : rawStatus === '2' || rawStatus === 'ended'
          ? 'ended'
          : 'pending'
      return {
        activityPrice: toNumber(record.groupPrice ?? record.activityPrice),
        endTime: toText(record.endedAt ?? record.endTime),
        id: toText(record.id),
        imageUrl: firstImage(record),
        name: toText(record.activityName ?? record.name),
        originalPrice: toNumber(record.originalPrice),
        spuId: toText(record.spuId),
        status,
      } as RetailActivityItem
    })
    .filter(item => Boolean(item.id))
}

/** 折扣商品项归一化 */
function normalizeDiscountGoods(value: unknown): DiscountGoodsItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item)
      return {
        discountPrice: toNumber(record.discountPrice),
        goodsImage: firstImage(record),
        goodsName: toText(record.goodsName),
        originalPrice: toNumber(record.originalPrice),
        skuId: toText(record.skuId),
        spuId: toText(record.spuId),
      } as DiscountGoodsItem
    })
    .filter(item => Boolean(item.skuId))
}

/**
 * 折扣活动归一化。
 *
 * 后端 `/app/discount/activities` 返回分页对象（records/total），
 * extractItems 已兼容；`goodsList` 仅指定商品(scope=2)活动才有，
 * 全场活动为空数组，渲染侧对其展示「全场参与」文案。
 */
export function normalizeRetailDiscounts(value: unknown): DiscountActivityItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item)
      return {
        activityId: toText(record.activityId ?? record.id),
        activityName: toText(record.activityName),
        countdown: toNumber(record.countdown),
        discountType: toNumber(record.discountType),
        discountValue: toNumber(record.discountValue),
        endTime: toText(record.endTime),
        goodsList: normalizeDiscountGoods(record.goodsList),
        scope: toNumber(record.scope),
        startTime: toText(record.startTime),
        status: toNumber(record.status),
      } as DiscountActivityItem
    })
    .filter(item => Boolean(item.activityId))
}

/** 秒杀商品项归一化：兼容后端 seckillPrice / remainingStock 字段与缺失值 */
function normalizeSeckillGoods(value: unknown): SeckillGoodsItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item)
      const remaining = record.remainingStock
      return {
        goodsImage: firstImage(record),
        goodsName: toText(record.goodsName),
        limitPerUser: toNumber(record.limitPerUser),
        originalPrice: toNumber(record.originalPrice),
        seckillPrice: toNumber(record.seckillPrice),
        skuId: toText(record.skuId),
        soldCount: toNumber(record.soldCount),
        spuId: toText(record.spuId),
        // 后端未下发剩余库存时置 -1，渲染侧据此隐藏进度条而不是显示 0%
        remainingStock: remaining === null || remaining === undefined ? -1 : toNumber(remaining),
        seckillStock: toNumber(record.seckillStock),
      } as SeckillGoodsItem
    })
    .filter(item => Boolean(item.skuId))
}

/**
 * 秒杀场次归一化。
 *
 * `/app/seckill/sessions` 返回的是**裸数组**（非分页对象），
 * 这里用 extractItems 同时兼容数组与 `{records: []}` 两种形态。
 */
export function normalizeRetailSeckillSessions(value: unknown): SeckillSessionItem[] {
  return extractItems(value)
    .map((item) => {
      const record = asRecord(item)
      return {
        countdown: toNumber(record.countdown),
        endTime: toText(record.endTime),
        goodsList: normalizeSeckillGoods(record.goodsList),
        sessionId: toText(record.sessionId ?? record.id),
        sessionName: toText(record.sessionName),
        startTime: toText(record.startTime),
        status: toNumber(record.status),
      } as SeckillSessionItem
    })
    .filter(item => Boolean(item.sessionId))
}

export function normalizeRetailShop(value: unknown): RetailShopInfo[] {
  const record = asRecord(value)
  if (!record.id && !record.name)
    return []
  return [{
    address: toText(record.address),
    id: toText(record.id),
    logoUrl: toText(record.logoUrl),
    name: toText(record.name),
    phone: toText(record.phone),
    siteUrl: toText(record.siteUrl),
  }]
}

export function mergeActivityGoods(
  activities: RetailActivityItem[],
  goods: RetailGoodsItem[],
): RetailActivityItem[] {
  const goodsById = new Map(goods.map(item => [item.id, item]))
  return activities.map((activity) => {
    const goodsItem = goodsById.get(activity.spuId)
    if (!goodsItem)
      return activity
    return {
      ...activity,
      imageUrl: activity.imageUrl || goodsItem.imageUrl,
      name: activity.name || goodsItem.name,
      originalPrice: activity.originalPrice || goodsItem.price,
    }
  })
}

export function restoreConfiguredOrder<T extends { id: string }>(
  items: T[],
  ids: string[],
): T[] {
  const order = new Map(ids.map((id, index) => [id, index]))
  return [...items].sort((left, right) =>
    (order.get(left.id) ?? Number.MAX_SAFE_INTEGER)
    - (order.get(right.id) ?? Number.MAX_SAFE_INTEGER))
}

export interface RetailCouponItem {
  id: string
  thresholdText: string
  title: string
  /** 券类型标签：满减券 / 折扣券 */
  typeLabel: string
  value: string
  /**
   * 当前登录用户对该券的领取次数；游客请求时为 null/undefined。
   * 领券组件的「已领取」标记依赖它，刷新后才能由服务端还原。
   */
  userReceiveCount?: number | null
}

/** 优惠券数据归一化：兼容 couponType/amount/discount/threshold 的存量字段 */
export function normalizeCoupons(value: unknown): RetailCouponItem[] {
  const records = Array.isArray(value)
    ? value
    : (isRecordLike(value) && Array.isArray((value as any).records) ? (value as any).records : [])
  if (!Array.isArray(records))
    return []
  return records
    .map((item: any): RetailCouponItem | null => {
      if (!item || typeof item !== 'object')
        return null
      const id = String(item.id ?? '')
      if (!id)
        return null
      const title = String(item.couponName ?? item.title ?? '优惠券')
      const couponType = String(item.couponType ?? '1')
      const value = couponType === '2' ? `${Number(item.discount ?? 0)}折` : `¥${Number(item.amount ?? 0)}`
      const threshold = Number(item.threshold ?? 0)
      // 保留服务端下发的领取次数，否则领券组件刷新后无法还原「已领取」
      const userReceiveCount = item.userReceiveCount == null ? null : Number(item.userReceiveCount)
      return {
        id,
        thresholdText: threshold > 0 ? `满${threshold}可用` : '无门槛',
        title,
        typeLabel: couponType === '2' ? '折扣券' : '满减券',
        userReceiveCount,
        value,
      }
    })
    .filter((item): item is RetailCouponItem => item !== null)
}

function isRecordLike(value: unknown): boolean {
  return typeof value === 'object' && value !== null
}
