/**
 * 单楼层最多保留的商品条数：无限滚动会一直往 DOM 里追加卡片，
 * 超过上限后按「没有更多」收口，避免长列表把图片和加购按钮无限堆积。
 */
export const DIY_PAGED_ITEM_LIMIT = 200

/**
 * 合并分页结果：按 id 去掉跨页重复项，并截断到上限。
 *
 * 销量/价格并列很多时，即使排序带了 id 兜底，翻页期间数据变化仍可能让
 * 同一商品出现在相邻两页；z-paging 只负责追加，不会自行去重。
 * 返回值直接交给 z-paging 的 completeByNoMore，reachedLimit 表示已到上限。
 */
export function mergePagedItems<T extends { id?: string }>(
  existing: readonly T[],
  incoming: readonly T[],
  limit = DIY_PAGED_ITEM_LIMIT,
) {
  const existingIds = new Set(
    existing.map(item => item.id).filter((id): id is string => Boolean(id)),
  )
  const fresh = incoming.filter(item => item.id && !existingIds.has(item.id))
  const remaining = Math.max(0, limit - existing.length)
  const items = fresh.slice(0, remaining)
  return {
    items,
    reachedLimit: existing.length + items.length >= limit,
  }
}
