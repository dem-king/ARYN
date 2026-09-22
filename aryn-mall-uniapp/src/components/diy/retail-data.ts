import type {
  GoodsGroupProps,
  GoodsRankingProps,
  GoodsWaterfallProps,
  LimitedActivityProps,
} from './retail-types'

import { getByIds, getPage as getGoodsPage } from '@/api/product/spu'
import {
  getActivityById,
  getActivityPage,
} from '@/api/promotion/groupBuyActivity'
import { getCurrentShop } from '@/api/upms/tenant'

import {
  mergeActivityGoods,
  normalizeRetailActivities,
  normalizeRetailGoods,
  normalizeRetailShop,
  restoreConfiguredOrder,
} from './retail-normalizers'
import { createRetailSortParams } from './retail-query'

export async function loadGoodsGroup(props: GoodsGroupProps) {
  if (props.dataSource.mode === 'manual') {
    const ids = props.dataSource.targetIds || []
    const items = normalizeRetailGoods(await getByIds(ids))
    return restoreConfiguredOrder(items, ids).slice(0, props.count)
  }
  const response = await getGoodsPage({
    categorySecondId: props.dataSource.categoryId || undefined,
    current: 1,
    ...createRetailSortParams(props.dataSource.sort || 'sales', true),
    size: props.count,
  })
  return normalizeRetailGoods(response).slice(0, props.count)
}

/**
 * 商品分组「自动规则」模式分页加载：供首页底部无限滚动（上拉加载更多）使用。
 * 手选（manual）模式不走此函数，仍由 loadGoodsGroup 一次性返回。
 */
export async function loadGoodsGroupPage(
  props: GoodsGroupProps,
  current: number,
  size: number,
) {
  const response: any = await getGoodsPage({
    categorySecondId: props.dataSource.categoryId || undefined,
    current,
    ...createRetailSortParams(props.dataSource.sort || 'sales', true),
    size,
  })
  const list = normalizeRetailGoods(response)
  const total = Number(response?.total ?? list.length)
  return { list, total }
}

export async function loadGoodsRanking(props: GoodsRankingProps) {
  const response = await getGoodsPage({
    current: 1,
    ...createRetailSortParams(props.dataSource.metric || 'sales'),
    size: props.count,
  })
  return normalizeRetailGoods(response).slice(0, props.count)
}

export async function loadLimitedActivities(props: LimitedActivityProps) {
  async function withGoods(value: unknown) {
    const activities = normalizeRetailActivities(value)
    const spuIds = [...new Set(activities.map(item => item.spuId).filter(Boolean))]
    if (!spuIds.length)
      return activities
    const goods = normalizeRetailGoods(await getByIds(spuIds))
    return mergeActivityGoods(activities, goods)
  }

  if (props.dataSource.mode === 'manual') {
    const ids = props.dataSource.targetIds || []
    const responses = await Promise.all(ids.map(id => getActivityById(id)))
    return restoreConfiguredOrder(
      await withGoods(responses),
      ids,
    ).slice(0, props.count)
  }
  const response = await getActivityPage({
    activityStatus: '1',
    current: 1,
    ...createRetailSortParams('create_time'),
    size: props.count,
  })
  return (await withGoods(response)).slice(0, props.count)
}

export async function loadCurrentShop() {
  return normalizeRetailShop(await getCurrentShop())
}

// ---------- 数据源协议：TTL 缓存与扩展组件加载器 ----------

const dataSourceCache = new Map<string, { expiresAt: number, value: unknown }>()

/** 按 key 缓存数据源结果；ttl<=0 时直读。加载失败由调用方的 fallback 策略兜底。 */
export async function withDataSourceCache<T>(
  key: string,
  ttlSeconds: number,
  loader: () => Promise<T>,
): Promise<T> {
  const ttl = Number(ttlSeconds ?? 0)
  if (ttl <= 0)
    return loader()
  const cached = dataSourceCache.get(key)
  const now = Date.now()
  if (cached && cached.expiresAt > now)
    return cached.value as T
  const value = await loader()
  dataSourceCache.set(key, { expiresAt: now + ttl * 1000, value })
  return value
}

export function clearDataSourceCache() {
  dataSourceCache.clear()
}

function dataSourceKey(componentId: string, props: { dataSource?: unknown }) {
  return `${componentId}:${JSON.stringify(props.dataSource ?? {})}`
}

export async function loadGoodsWaterfall(
  props: import('./retail-types').GoodsWaterfallProps,
) {
  return withDataSourceCache(dataSourceKey('goods-waterfall', props), props.dataSource.cacheTtl ?? 0, async () => {
    if (props.dataSource.mode === 'manual') {
      const ids = props.dataSource.targetIds || []
      const items = normalizeRetailGoods(await getByIds(ids))
      return restoreConfiguredOrder(items, ids).slice(0, props.count)
    }
    const response = await getGoodsPage({
      categorySecondId: props.dataSource.categoryId || undefined,
      current: 1,
      ...createRetailSortParams(props.dataSource.sort || 'sales', true),
      size: props.count,
    })
    return normalizeRetailGoods(response).slice(0, props.count)
  })
}

/**
 * 商品瀑布流「自动数据源」分页加载：供首页无限滚动（上拉加载更多）使用。
 * 与一次性的 loadGoodsWaterfall 不同，这里按 current/size 取指定一页并回传 total，
 * 由调用方（z-paging）负责追加与判断是否到底。手选（manual）模式不走此函数。
 */
export async function loadGoodsWaterfallPage(
  props: GoodsWaterfallProps,
  current: number,
  size: number,
) {
  const response: any = await getGoodsPage({
    categorySecondId: props.dataSource.categoryId || undefined,
    current,
    ...createRetailSortParams(props.dataSource.sort || 'sales', true),
    size,
  })
  const list = normalizeRetailGoods(response)
  const total = Number(response?.total ?? list.length)
  return { list, total }
}

export async function loadCouponCombo(
  props: import('./retail-types').CouponComboProps,
) {
  const { getPage: getCouponPage } = await import('@/api/promotion/couponInfo')
  const { normalizeCoupons } = await import('./retail-normalizers')
  return withDataSourceCache(dataSourceKey('coupon-combo', props), props.dataSource.cacheTtl ?? 0, async () => {
    const response = await getCouponPage({ current: 1, size: Math.max(props.count, 20) })
    const coupons = normalizeCoupons(response)
    if (props.dataSource.mode === 'manual' && (props.dataSource.targetIds || []).length > 0) {
      const ids = props.dataSource.targetIds || []
      const filtered = coupons.filter(coupon => ids.includes(coupon.id))
      return restoreConfiguredOrder(filtered, ids).slice(0, props.count)
    }
    return coupons.slice(0, props.count)
  })
}
