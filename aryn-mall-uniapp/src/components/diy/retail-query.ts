/** 折扣力度优先排序：装修器检漏 Tab 数据源 sort 取值 */
export const BARGAIN_SORT = 'bargain'

/**
 * 折扣率阈值缺省值。
 *
 * 与后端校验/查询缺省保持一致：`sort=bargain` 时前端未配置也按 1.15 下发，
 * 让「自动检漏」在运营没有调整阈值时也有稳定的入池门槛（原价 ≥ 售价 × 1.15）。
 */
export const DEFAULT_BARGAIN_RATE = 1.15

/**
 * 折扣率阈值归一化：仅接受 (1.00, 9.99] 区间的有限数值，其余一律回落缺省 1.15。
 *
 * 越界值（如 1、10、0 或脏字符串）若原样下发，后端会报错让整个 Tab 空掉；
 * 前端在这里静默回落缺省阈值，保证存量配置/脏数据下商品流仍可用。
 * 管理端阈值步长为 0.01，这里按两位小数收敛浮点误差。
 */
export function normalizeBargainRate(value: unknown): number {
  const rate = Number(value)
  if (!Number.isFinite(rate) || rate <= 1 || rate > 9.99)
    return DEFAULT_BARGAIN_RATE
  return Math.round(rate * 100) / 100
}

export function createRetailSortParams(
  sort: string,
  priceAscending = false,
  tieBreaker = 'id',
  bargainRate?: unknown,
) {
  // 折扣力度优先：阈值过滤与固定排序全部由后端完成（忽略 asc/desc），
  // 前端只下发阈值绑定值，不拼列名（无注入面），也不与 asc/desc 混发。
  if (sort === BARGAIN_SORT)
    return { bargainRate: normalizeBargainRate(bargainRate) }
  if (sort === 'sales_price' && priceAscending)
    return { asc: tieBreaker ? `sales_price,${tieBreaker}` : 'sales_price' }
  const fields: Record<string, string> = {
    create_time: 'create_time',
    newest: 'create_time',
    price: 'sales_price',
    sales: 'sales_volume',
    sales_price: 'sales_price',
  }
  const primary = fields[sort] || 'sales_volume'
  return { desc: tieBreaker ? `${primary},${tieBreaker}` : primary }
}

/**
 * 商品分页排序：销量/时间/价格大量并列时，OFFSET 分页需要唯一列兜底，
 * 否则滚动加载可能重复或漏掉商品。
 * selectApiPage 关联了分类与品牌表，id 必须带 goods_spu 前缀，避免列名歧义。
 *
 * @param bargainRate `sort='bargain'` 时的折扣率阈值；其它排序忽略
 */
export function createGoodsSortParams(
  sort: string,
  priceAscending = false,
  bargainRate?: unknown,
) {
  return createRetailSortParams(sort, priceAscending, 'goods_spu.id', bargainRate)
}
