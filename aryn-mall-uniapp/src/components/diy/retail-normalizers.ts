export interface RetailGoodsItem {
  id: string
  imageUrl: string
  name: string
  price: number
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
  return toText(record.picUrl ?? record.imageUrl ?? record.logoUrl)
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
  value: string
}

/** 优惠券数据归一化：兼容 couponType/amount/discount/threshold 的存量字段 */
export function normalizeCoupons(value: unknown): RetailCouponItem[] {
  const records = Array.isArray(value)
    ? value
    : (isRecordLike(value) && Array.isArray((value as any).records) ? (value as any).records : [])
  if (!Array.isArray(records))
    return []
  return records
    .map((item: any) => {
      if (!item || typeof item !== 'object')
        return null
      const id = String(item.id ?? '')
      if (!id)
        return null
      const title = String(item.couponName ?? item.title ?? '优惠券')
      const couponType = String(item.couponType ?? '1')
      const value = couponType === '2' ? `${Number(item.discount ?? 0)}折` : `¥${Number(item.amount ?? 0)}`
      const threshold = Number(item.threshold ?? 0)
      return {
        id,
        thresholdText: threshold > 0 ? `满${threshold}可用` : '无门槛',
        title,
        value,
      }
    })
    .filter((item: any): item is RetailCouponItem => !!item)
}

function isRecordLike(value: unknown): boolean {
  return typeof value === 'object' && value !== null
}
