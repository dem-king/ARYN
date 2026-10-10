import type {
  DiscountActivityItem,
  DiscountProps,
  GoodsRankingProps,
  GoodsWaterfallProps,
  LimitedActivityProps,
  RetailBaseProps,
  SeckillProps,
  SeckillSessionItem,
} from './retail-types'

import { getById, getByIds, getPage as getGoodsPage } from '@/api/product/spu'
import {
  getDiscountActivities,
  getSeckillSessions,
  getSessionGoods,
} from '@/api/promotion'
import {
  getActivityById,
  getActivityPage,
} from '@/api/promotion/groupBuyActivity'
import { getCurrentShop } from '@/api/upms/tenant'

import {
  mergeActivityGoods,
  normalizeRetailActivities,
  normalizeRetailDiscounts,
  normalizeRetailGoods,
  normalizeRetailSeckillSessions,
  normalizeRetailShop,
  restoreConfiguredOrder,
} from './retail-normalizers'
import { createRetailSortParams } from './retail-query'

/**
 * 商品取数：只读取数据源与条数，故参数用基础契约，
 * 商品分组（含 columns）与商品横滑等组件都能直接复用。
 */
export async function loadGoodsGroup(props: RetailBaseProps) {
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
  props: RetailBaseProps,
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

/**
 * 秒杀会场取数。
 *
 * 数据源语义与其它零售组件不同：秒杀的活动结构是「场次」，
 * `targetIds` 存的是场次 ID（不是活动 ID）——一场秒杀就是一个可独立投放的楼层。
 *
 * · automatic：读全部进行中场次
 * · manual：按 targetIds 逐场次取数（`/sessions/{id}/goods` 只回单场且带商品）
 */
export async function loadSeckillSessions(props: SeckillProps): Promise<SeckillSessionItem[]> {
  if (props.dataSource.mode === 'manual') {
    const ids = props.dataSource.targetIds || []
    if (ids.length === 0)
      return []
    const sessions = await Promise.all(ids.map(id => getSessionGoods(id)))
    return normalizeRetailSeckillSessions(sessions).slice(0, props.count)
  }
  const sessions = normalizeRetailSeckillSessions(await getSeckillSessions())
  // 进行中优先、未开始次之；已结束的场次不进楼层，避免运营看到过期数据
  const ranked = sessions
    .filter(session => session.status !== 2)
    .sort((a, b) => a.status - b.status)
  return ranked.slice(0, props.count)
}

/**
 * 折扣会场取数。
 *
 * 手动模式与自动模式的差别同其它零售组件：manual 按 `targetIds`(活动 ID) 取；
 * 自动模式读进行中活动，一页取够配置条数即可（组件只展示前 count 个）。
 */
export async function loadDiscounts(props: DiscountProps): Promise<DiscountActivityItem[]> {
  const response = await getDiscountActivities({
    current: 1,
    size: Math.max(props.count, 10),
  })
  const activities = normalizeRetailDiscounts(response)
  if (props.dataSource.mode === 'manual') {
    const ids = props.dataSource.targetIds || []
    if (ids.length === 0)
      return []
    const picked = activities.filter(activity => ids.includes(activity.activityId))
    // 折扣活动的键是 activityId（非 id），不能直接用 restoreConfiguredOrder
    const order = new Map(ids.map((id, index) => [id, index]))
    return [...picked]
      .sort((left, right) =>
        (order.get(left.activityId) ?? Number.MAX_SAFE_INTEGER)
        - (order.get(right.activityId) ?? Number.MAX_SAFE_INTEGER))
      .slice(0, props.count)
  }
  return activities.slice(0, props.count)
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

/**
 * 商品推荐（看了又看）取数。
 *
 * · automatic：当前商品同分类销量 Top N——分类解析自 goodsId 的
 *   categorySecondId（运营固定分类时走 dataSource.categoryId）；
 *   goodsId 缺失或分类解析失败（预览/非商详上下文）回落全站销量榜。
 *   自动结果排除当前商品本身，不足 count 时按顺序追加手选商品补位。
 * · manual：手选商品 ID（发布校验保证非空）。
 *
 * 缓存 key 必须包含 goodsId：同一组件配置在不同商品详情页结果不同。
 */
export async function loadGoodsRecommend(
  props: import('./retail-types').GoodsRecommendProps,
  goodsId?: string,
) {
  const count = Math.max(1, Number(props.count) || 6)
  const selfId = goodsId || ''
  const cacheKey = `goods-recommend:${selfId}:${JSON.stringify(props.dataSource ?? {})}`
  return withDataSourceCache(cacheKey, props.dataSource.cacheTtl ?? 0, async () => {
    if (props.dataSource.mode === 'manual') {
      const ids = props.dataSource.targetIds || []
      const items = normalizeRetailGoods(await getByIds(ids))
      return restoreConfiguredOrder(items, ids).slice(0, count)
    }
    let categorySecondId = props.dataSource.categoryId || ''
    if (!categorySecondId && selfId) {
      try {
        const spu: any = await getById(selfId)
        categorySecondId = String(spu?.categorySecondId ?? '')
      }
      catch {
        // 分类解析失败不阻断渲染：回落全站销量榜
      }
    }
    // 多取一件用于排除当前商品后仍能凑满 count
    const response = await getGoodsPage({
      categorySecondId: categorySecondId || undefined,
      current: 1,
      ...createRetailSortParams('sales', true),
      size: count + (selfId ? 1 : 0),
    })
    const items = normalizeRetailGoods(response)
      .filter(item => !selfId || item.id !== selfId)
    const manualIds = (props.dataSource.targetIds || [])
      .filter(id => id && id !== selfId && !items.some(item => item.id === id))
    if (manualIds.length > 0 && items.length < count) {
      const extra = normalizeRetailGoods(await getByIds(manualIds))
      items.push(...restoreConfiguredOrder(extra, manualIds))
    }
    return items.slice(0, count)
  })
}
