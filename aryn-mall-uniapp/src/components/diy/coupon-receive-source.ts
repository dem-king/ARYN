export interface CouponReceiveSourceConfig {
  mode?: string
  targetIds?: string[]
}

export interface CouponReceiveSource {
  ids: string[]
  mode: 'automatic' | 'manual'
}

/**
 * 装修「优惠券」组件取数口径。
 *
 * 旧配置没有 dataSource，按自动领取列表处理；手选 ID 按选择顺序去重后
 * 截断到「最多显示」，渲染侧不拉第一页再过滤（被选中的券可能不在第一页）。
 */
export function resolveCouponReceiveSource(
  dataSource: CouponReceiveSourceConfig | null | undefined,
  showNum: number,
): CouponReceiveSource {
  const limit = Math.min(10, Math.max(1, Math.floor(Number(showNum)) || 1))
  const seen = new Set<string>()
  const ids: string[] = []
  for (const value of dataSource?.targetIds ?? []) {
    const id = String(value ?? '').trim()
    if (!id || seen.has(id))
      continue
    seen.add(id)
    ids.push(id)
    if (ids.length >= limit)
      break
  }
  return {
    ids,
    mode: dataSource?.mode === 'manual' ? 'manual' : 'automatic',
  }
}

/** byIds 不保证顺序，按运营配置顺序回填；查不到的券（过期/删除）直接丢弃 */
export function orderCouponsByIds<T extends { id?: string }>(
  rows: T[],
  ids: string[],
): T[] {
  const byId = new Map<string, T>()
  for (const row of rows) {
    const id = String(row?.id ?? '')
    if (id)
      byId.set(id, row)
  }
  const ordered: T[] = []
  for (const id of ids) {
    const row = byId.get(id)
    if (row)
      ordered.push(row)
  }
  return ordered
}
