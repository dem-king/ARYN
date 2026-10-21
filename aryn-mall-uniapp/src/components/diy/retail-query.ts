export function createRetailSortParams(
  sort: string,
  priceAscending = false,
  tieBreaker = 'id',
) {
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
 */
export function createGoodsSortParams(sort: string, priceAscending = false) {
  return createRetailSortParams(sort, priceAscending, 'goods_spu.id')
}
