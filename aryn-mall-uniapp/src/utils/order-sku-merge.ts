/**
 * 结算/下单请求按 SKU 合并重复行。
 *
 * 背景：购物车按靠港（vessel_call_id）分组，同一 SKU 可能合法地存在多行 ——
 * 一行是「此前靠港计划（已结束）」的滞留行，另一行是「当前无靠港」组
 * （vessel_call_id 为空）。购物车页不能直接合并展示：每行的靠港归属是
 * 「防串船」校验与分组信息的一部分，合并会丢失「这一件属于哪条靠港」。
 *
 * 但结算与下单接口要求一个 SKU 至多一行：原样提交重复 SKU 会返回
 * 「订单商品不能包含重复SKU」，确认页空白、金额 ¥0.00。因此在提交边界
 * （order-confirm 构造 skuReqList 时）只合并数量，购物车页保留原始行。
 *
 * 合并规则：
 * - 按 skuId 归并，数量相加（`Number(quantity) || 0` 兜住字符串/缺失）；
 * - 展示字段取首次出现的行，结果顺序与首次出现一致；
 * - skuId 为空白的行没有可归并的身份，原样保留、不参与合并；
 * - 返回浅拷贝的新数组与新行对象，绝不修改入参（调用方还要拿原始列表做校验）。
 */
export function mergeDuplicateSkuItems<T extends { skuId?: any, quantity?: any }>(
  items: T[] = [],
): T[] {
  const merged: T[] = []
  // skuId（String 归一后的非空值）→ 首次出现行在结果数组里的下标
  const indexBySkuId = new Map<string, number>()
  for (const item of items) {
    const skuId = String(item.skuId ?? '').trim()
    if (!skuId) {
      merged.push({ ...item })
      continue
    }
    const index = indexBySkuId.get(skuId)
    if (index === undefined) {
      indexBySkuId.set(skuId, merged.length)
      merged.push({ ...item })
      continue
    }
    const target = merged[index]
    target.quantity = (Number(target.quantity) || 0) + (Number(item.quantity) || 0)
  }
  return merged
}
