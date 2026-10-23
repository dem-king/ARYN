import type {
  DiscountActivityItem,
  DiscountProps,
  GoodsRankingProps,
  GoodsTabConfig,
  GoodsWaterfallProps,
  LimitedActivityProps,
  NewbieFloorItem,
  RetailBaseProps,
  SeckillProps,
  SeckillSessionItem,
} from './retail-types'

import { getById, getByIds, getPage as getGoodsPage } from '@/api/product/spu'
import {
  getCurrentNewbieFloor,
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
  normalizeRetailNewbieFloor,
  normalizeRetailSeckillSessions,
  normalizeRetailShop,
  restoreConfiguredOrder,
} from './retail-normalizers'
import { createGoodsSortParams, createRetailSortParams } from './retail-query'

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
    ...createGoodsSortParams(props.dataSource.sort || 'sales', true),
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
    ...createGoodsSortParams(props.dataSource.sort || 'sales', true),
    size,
  })
  const list = normalizeRetailGoods(response)
  const total = Number(response?.total ?? list.length)
  return { list, total }
}

/**
 * 多 Tab 商品流取数：与商品分组保持同一套排序/归一化口径。
 *
 * manual 一次性取回并保持运营配置顺序，按展示上限截断；
 * automatic 按 current/size 分页，滚动加载只影响当前 Tab。
 */
export async function loadGoodsTabPage(
  tab: GoodsTabConfig,
  current: number,
  size: number,
) {
  if (tab.dataSource.mode === 'manual') {
    const ids = tab.dataSource.targetIds || []
    if (ids.length === 0)
      return { list: [], manual: true, total: 0 }
    const items = restoreConfiguredOrder(
      normalizeRetailGoods(await getByIds(ids)),
      ids,
    ).slice(0, Math.max(1, size))
    return { list: items, manual: true, total: items.length }
  }
  const response: any = await getGoodsPage({
    categorySecondId: tab.dataSource.categoryId || undefined,
    current,
    ...createGoodsSortParams(tab.dataSource.sort || 'sales', true),
    size,
  })
  const list = normalizeRetailGoods(response)
  return {
    list,
    manual: false,
    total: Number(response?.total ?? list.length),
  }
}

export async function loadGoodsRanking(props: GoodsRankingProps) {
  const response = await getGoodsPage({
    current: 1,
    ...createGoodsSortParams(props.dataSource.metric || 'sales'),
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
  // 进行中优先、未开始次之；已结束的场次不进楼层，避免运营看到过期数据。
  // 注意不能直接按 status 升序：0=未开始会排在 1=进行中前面，
  // count=1 时楼层会取到未开始的场次，运营看到的是「即将开始」而不是正在抢。
  const rank = (status: number) => {
    if (status === 1)
      return 0
    if (status === 0)
      return 1
    return 2
  }
  const ranked = sessions
    .filter(session => session.status !== 2)
    .sort((a, b) => rank(a.status) - rank(b.status) || a.status - b.status)
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

/**
 * 新人专享楼层取数。
 *
 * 数据源固定读「当前进行中的新人价活动」，不做手动选活动（运营只需建活动即生效）；
 * 无进行中活动时返回空数组，走组件空态策略。响应不含用户维度数据（免登可访问），
 * 因此不需要按 userId 键缓存。
 */
export async function loadNewbieFloor(): Promise<NewbieFloorItem[]> {
  return normalizeRetailNewbieFloor(await getCurrentNewbieFloor())
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
      ...createGoodsSortParams(props.dataSource.sort || 'sales', true),
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
    ...createGoodsSortParams(props.dataSource.sort || 'sales', true),
    size,
  })
  const list = normalizeRetailGoods(response)
  const total = Number(response?.total ?? list.length)
  return { list, total }
}

/**
 * 优惠券组合取数。
 *
 * · automatic：领取列表第一页前 N 张。
 * · manual：按 targetIds 逐张取（byIds 接口），不拉第一页再过滤——
 *   被选中的券排在列表第一页之外时，过滤式实现会让整个楼层漏渲染。
 */
export async function loadCouponCombo(
  props: import('./retail-types').CouponComboProps,
) {
  const { getPage: getCouponPage, getByIds: getCouponsByIds } = await import('@/api/promotion/couponInfo')
  const { normalizeCoupons } = await import('./retail-normalizers')
  return withDataSourceCache(dataSourceKey('coupon-combo', props), props.dataSource.cacheTtl ?? 0, async () => {
    if (props.dataSource.mode === 'manual') {
      const ids = props.dataSource.targetIds || []
      if (!ids.length)
        return []
      const coupons = normalizeCoupons(await getCouponsByIds(ids))
      return restoreConfiguredOrder(coupons, ids).slice(0, props.count)
    }
    const response = await getCouponPage({ current: 1, size: Math.max(props.count, 20) })
    return normalizeCoupons(response).slice(0, props.count)
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
      ...createGoodsSortParams('sales', true),
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
