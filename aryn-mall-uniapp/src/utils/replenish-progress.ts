import type { ReplenishSummaryProgress, SharedCartItem, SharedCartSummary } from '@/api/order/sharedCart'

/**
 * 补给单进度展示口径（纯函数，与后端 ReplenishProgressCalculator 一一对应）。
 *
 * 后端已经算好 `progress`（按项数、未排计划的行不计入百分比、无计划时
 * `progressPercent = null`）。前端只做三件事：翻译成文案、算进度条宽度、
 * 决定明细预览行该说「还差 N」还是「已采满」。
 *
 * 硬性底线（踩过就会造假数据）：
 * - 未排计划的行不参与百分比，也不能拿 `requestedQuantity` 当计划量；
 * - 无计划时百分比为 null，展示「尚未排计划」而不是 0% —— 0% 会被读成
 *   「有计划但一项没采」；
 * - 百分比只信服务端字段，前端不做二次除法。
 */

/**
 * 单行进度视图（详情页每行「目标 N · 已采 M」）。
 *
 * `planned` 为 null 表示未设计划 —— 此时不显示任何还差/百分比，
 * 只提示「未排计划」，因为需求量不是计划量。
 */
export interface ReplenishRowView {
  planned: number | null
  fulfilled: number
  remaining: number | null
  completed: boolean
  /** `申请 9 · 目标 4 · 已采 4` / `申请 9 · 未排计划` */
  text: string
}

export interface ReplenishProgressView {
  /** 是否已排计划：只有排过计划才谈百分比、才画进度条 */
  hasPlan: boolean
  /** 进度条宽度 0~100；无计划时为 0（配合 hasPlan 一起用） */
  percent: number
  /** 进度文案，如 `已采 8 项 · 还差 4 项`；无数据时为空串 */
  progressText: string
  /** 明细预览整行文案（含「清单还差：」前缀）；无明细时为空串 */
  previewLineText: string
}

const EMPTY_VIEW: ReplenishProgressView = {
  hasPlan: false,
  percent: 0,
  progressText: '',
  previewLineText: '',
}

function clampPercent(percent?: number | null): number {
  if (typeof percent !== 'number' || Number.isNaN(percent))
    return 0
  return Math.min(Math.max(percent, 0), 100)
}

/** `已采 8 项 · 还差 4 项 · 2 项未排计划` */
function buildProgressText(
  progress: ReplenishSummaryProgress | null | undefined,
  hasPlan: boolean,
): string {
  if (!progress)
    return ''
  if (!hasPlan) {
    return progress.totalItems > 0 ? `尚未排计划 · ${progress.totalItems} 项待安排` : ''
  }
  const parts = [`已采 ${progress.fulfilledItems} 项`, `还差 ${progress.remainingItems} 项`]
  if (progress.unplannedItems > 0)
    parts.push(`${progress.unplannedItems} 项未排计划`)
  return parts.join(' · ')
}

/**
 * 明细预览：已排计划的行优先说还差多少（这才是用户要补的），
 * 采满显示「已采满」，未排计划的行才回落需求量。
 * 商品名拿不到时回落 SKU ID，避免出现空白行。
 */
function buildPreviewText(summary: SharedCartSummary): string {
  if (summary.previewItems.length === 0)
    return ''
  const text = summary.previewItems
    .map((item) => {
      const name = item.spuName || item.skuId
      if (item.plannedQuantity == null)
        return `${name} ${item.quantity}`
      const remaining = item.remainingQuantity
      if (typeof remaining === 'number' && remaining > 0)
        return `${name} 还差 ${remaining}`
      return `${name} 已采满`
    })
    .join(' · ')
  return summary.previewTruncated ? `${text} …` : text
}

export function buildReplenishProgressView(
  summary?: null | SharedCartSummary,
): ReplenishProgressView {
  if (!summary)
    return { ...EMPTY_VIEW }

  const progress = summary.progress
  const hasPlan = (progress?.plannedItems ?? 0) > 0
  const previewText = buildPreviewText(summary)

  return {
    hasPlan,
    percent: hasPlan ? clampPercent(progress?.progressPercent) : 0,
    progressText: buildProgressText(progress, hasPlan),
    // 有计划且还差东西时才加「清单还差：」前缀（与原型一致）
    previewLineText:
      previewText && (progress?.remainingItems ?? 0) > 0
        ? `清单还差：${previewText}`
        : previewText,
  }
}

/**
 * 单行进度：与后端 {@code ReplenishProgressCalculator.ofRow} 同口径。
 *
 * 未设计划的行 `remaining`/`completed` 一律为 null，**不回落需求量**。
 */
export function buildReplenishRowView(
  item: Pick<SharedCartItem, 'requestedQuantity' | 'plannedQuantity' | 'fulfilledQuantity'>,
): ReplenishRowView {
  const planned = item.plannedQuantity ?? null
  // 存量行可能为 null 或负数，按 0 展示（与后端口径一致）
  const fulfilled = Math.max(item.fulfilledQuantity ?? 0, 0)
  // 申请量始终展示：确认人排计划时要对照成员报了多少才决定采多少
  const requested = item.requestedQuantity ?? 0

  if (planned == null) {
    return {
      planned: null,
      fulfilled,
      remaining: null,
      completed: false,
      text: `申请 ${requested} · 未排计划`,
    }
  }

  const plannedSafe = Math.max(planned, 0)
  // 超采不显示负数
  const remaining = Math.max(plannedSafe - fulfilled, 0)
  return {
    planned: plannedSafe,
    fulfilled,
    remaining,
    completed: fulfilled >= plannedSafe,
    text: `申请 ${requested} · 目标 ${plannedSafe} · 已采 ${fulfilled}`,
  }
}

/**
 * 整单汇总：与后端 {@code ReplenishProgressCalculator.summarize} 同口径，
 * 按**项数**而非数量算（不同商品单位不同，数量相加没有意义）。
 *
 * 之所以在前端再算一遍：详情接口返回的是原始明细行，没有聚合视图；
 * 卡片走的是一次性聚合接口。两处口径必须一致，由单测与契约测试同时守。
 */
export function summarizeReplenishItems(
  items: SharedCartItem[],
): ReplenishSummaryProgress {
  const summary: ReplenishSummaryProgress = {
    totalItems: items.length,
    plannedItems: 0,
    fulfilledItems: 0,
    remainingItems: 0,
    unplannedItems: 0,
    progressPercent: null,
    totalAmount: 0,
  }
  if (items.length === 0)
    return summary

  for (const item of items) {
    const row = buildReplenishRowView(item)
    if (row.planned == null) {
      summary.unplannedItems++
      continue
    }
    summary.plannedItems++
    if (row.completed)
      summary.fulfilledItems++
  }

  summary.remainingItems = summary.plannedItems - summary.fulfilledItems
  // 无任何计划时百分比为 null（显示「—」），而不是 0%
  summary.progressPercent = summary.plannedItems === 0
    ? null
    : Math.round((summary.fulfilledItems * 100) / summary.plannedItems)
  return summary
}
