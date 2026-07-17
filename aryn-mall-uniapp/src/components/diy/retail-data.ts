import type {
  GoodsGroupProps,
  GoodsRankingProps,
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
