/**
 * 新人价拆行明细的 C 端展示收敛（纯函数，便于单测）。
 *
 * 背景：新人价放宽为「首件享新人价、超出件按替换前生效价」后，后端结算/订单把同一
 * SKU 拆成「优惠行（activityType=newbie）+ 超出价行」两行落库——门槛累计、优惠
 * 分摊、按行退款的金额都在两行上各自成立。管理端按两行展示便于对账；C 端分两种口径：
 * - 确认页（无逐行操作）：mergeNewbieSplitRows 合并回一行，用「首件享新人价」提示解释价差；
 * - 订单详情/列表/评价（退款/评价按行 id 提交）：annotateNewbieSplitRows 保留逐行，
 *   仅给拆行组打「首件/超出件」标注，不让用户以为重复加购，也不孤儿化任何一行的售后入口。
 *
 * 合并/标注条件（保守，避免误伤其它多行场景）：
 * - 仅「同 SKU + 同归属人」的行（共享购物车按成员拆行后跨成员不能并）；
 * - 组内必须同时出现新人价行与非新人价行（真正的拆行结果）；纯原价多行（跨靠港
 *   滞留行）与纯优惠单行（数量未超限）保持原样；
 * - 赠品行（giftFlag=1）永不参与：0 元明细必须独立展示。
 *
 * 合并后的行：数量/成交金额/划线金额逐项累加，标签取新人价行，并打上
 * `newbieSplitHint` 标记供模板渲染提示文案。
 */

/** 触发展示合并的新人价活动类型（与后端 StackActivityTypeEnum.NEWBIE 的 code 一致） */
export const NEWBIE_ACTIVITY_TYPE = 'newbie'

/** 参与合并判定的最小字段（结构化兼容结算/订单接口的明细行） */
export interface MergeableOrderItem {
  skuId?: string | null
  buyQuantity?: number | null
  totalPrice?: number | null
  paymentPrice?: number | null
  originalTotalPrice?: number | null
  activityDiscountPrice?: number | null
  contributorUserId?: string | null
  giftFlag?: string | null
  activityType?: string | null
}

/** 展示收敛在原行上附加的标记：模板据此渲染「首件/超出件」提示 */
export interface NewbieSplitMerged {
  newbieSplitHint?: boolean
  newbieRemainderHint?: boolean
}

function toCount(value: unknown): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}

function toAmount(value: unknown): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}

/** 金额累加后收敛两位小数，吃掉 1.9 + 11.8 = 13.700000000000001 这类浮点噪声 */
function round2(value: number): number {
  return Math.round(value * 100) / 100
}

function isGiftRow(item: MergeableOrderItem): boolean {
  return String(item.giftFlag ?? '') === '1'
}

function isNewbieRow(item: MergeableOrderItem): boolean {
  return String(item.activityType ?? '') === NEWBIE_ACTIVITY_TYPE
}

/** 分组键：同 SKU 且同归属人才可能是一次拆行的产物 */
function mergeKey(item: MergeableOrderItem): string | null {
  if (isGiftRow(item))
    return null
  const skuId = String(item.skuId ?? '').trim()
  if (!skuId)
    return null
  return `${skuId}|${String(item.contributorUserId ?? '')}`
}

/** 合格拆行组：组内同时有新人价行与非新人价行；返回 键→组内下标 */
function qualifiedSplitGroups(list: MergeableOrderItem[]): Map<string, number[]> {
  const groupIndexes = new Map<string, number[]>()
  const qualified = new Map<string, number[]>()
  for (let index = 0; index < list.length; index++) {
    const key = mergeKey(list[index])
    if (key === null)
      continue
    const group = groupIndexes.get(key)
    if (group) {
      group.push(index)
      const hasNewbie = group.some(i => isNewbieRow(list[i]))
      const hasOriginal = group.some(i => !isNewbieRow(list[i]))
      if (hasNewbie && hasOriginal)
        qualified.set(key, group)
    }
    else {
      groupIndexes.set(key, [index])
    }
  }
  return qualified
}

/**
 * 合并新人价拆行明细（确认页口径）。非新人价订单（无合格拆行组）原样返回，行为零变化。
 */
export function mergeNewbieSplitRows<T extends MergeableOrderItem>(items: T[] | null | undefined): T[] {
  const list = Array.isArray(items) ? items : []
  if (!list.length)
    return list

  const qualified = qualifiedSplitGroups(list)
  if (!qualified.size)
    return list

  const emittedKeys = new Set<string>()
  const result: T[] = []
  for (const item of list) {
    const key = mergeKey(item)
    const group = key === null ? undefined : qualified.get(key)
    if (!group) {
      result.push(item)
      continue
    }
    if (emittedKeys.has(key!)) {
      continue
    }
    emittedKeys.add(key!)
    // 以新人价行为展示基底（标签/图片/规格都来自它），数量与金额逐项累加
    const newbieBase = group.map(i => list[i]).find(row => isNewbieRow(row)) ?? item
    const merged = { ...newbieBase } as T & NewbieSplitMerged
    merged.buyQuantity = group.reduce((sum, i) => sum + toCount(list[i].buyQuantity), 0)
    merged.totalPrice = round2(group.reduce((sum, i) => sum + toAmount(list[i].totalPrice), 0))
    merged.paymentPrice = round2(group.reduce((sum, i) => sum + toAmount(list[i].paymentPrice), 0))
    merged.originalTotalPrice = round2(group.reduce((sum, i) => sum + toAmount(list[i].originalTotalPrice), 0))
    merged.activityDiscountPrice = round2(group.reduce((sum, i) => sum + toAmount(list[i].activityDiscountPrice), 0))
    merged.newbieSplitHint = true
    result.push(merged)
  }
  return result
}

/**
 * 标注新人价拆行明细（订单详情/列表/评价口径）：不合并行，保留逐行售后入口，
 * 仅给拆行组内的新人价行打 `newbieSplitHint`、超出价行打 `newbieRemainderHint`。
 * 非拆行订单原样返回（同一数组引用），行为零变化。
 */
export function annotateNewbieSplitRows<T extends MergeableOrderItem>(items: T[] | null | undefined): T[] {
  const list = Array.isArray(items) ? items : []
  if (!list.length)
    return list

  const qualified = qualifiedSplitGroups(list)
  if (!qualified.size)
    return list

  return list.map((item) => {
    const key = mergeKey(item)
    if (key === null || !qualified.has(key))
      return item
    return isNewbieRow(item)
      ? { ...item, newbieSplitHint: true } as T
      : { ...item, newbieRemainderHint: true } as T
  })
}