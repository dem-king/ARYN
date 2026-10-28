/**
 * 秒杀「已抢进度」的统一口径。
 *
 * 同一份数据在三个地方渲染：C 端会场页、C 端首页秒杀楼层、管理端装修预览。
 * 三处曾各自维护一套阈值（20% 比例 / 5 件绝对值 / 0% 归零），导致同一个商品
 * 在会场显示「已抢 0% · 仅剩 1 件」、在楼层显示「限量 25 件」。这里收敛为一处，
 * 两端只决定用短文案还是长文案（受卡片宽度限制），判定规则完全一致。
 *
 * 规则（按优先级）：
 * 1. 库存未知（-1/null/缺失）→ null，调用方整块隐藏；
 * 2. 剩余 ≤ 0 → 「已抢光」；
 * 3. 已抢取整为 0% → 「限量 N 件」（开场还没卖出去，说"仅剩"是假紧迫）；
 * 4. 剩余 ≤5 件或 ≤20% → 「仅剩 N 件」；
 * 5. 其余 → 短文案「已抢 N%」/ 长文案「已抢 N% · 仅剩 N 件」。
 *
 * 文案在调用时翻译：会场页、首页楼层、管理端预览共用同一份口径，切语言即时生效。
 */
import { translate } from '@/locales'

/** 低于该剩余量不再输出百分比（绝对值） */
export const LOW_STOCK_THRESHOLD = 5
/** 低于该剩余比例不再输出百分比 */
export const LOW_STOCK_RATIO = 0.2

export interface SeckillProgress {
  /** 已抢百分比（0–100，四舍五入） */
  pct: number
  /** 是否已售罄 */
  soldOut: boolean
  /** 是否进入紧迫区间（仅剩 N 件） */
  low: boolean
  /** 短文案：首页楼层等窄卡片用（「已抢 90%」） */
  shortText: string
  /** 长文案：会场页宽卡片用（「已抢 90% · 仅剩 2 件」） */
  fullText: string
}

/**
 * 库存数字归一化：后端用 -1 / null 表示「库存未知」，统一折成 NaN。
 * 不能直接用 Number(null) —— 它等于 0，会把「未知」误判成售罄。
 */
export function normalizeStock(value: unknown) {
  if (value === null || value === undefined || value === '')
    return Number.NaN
  const num = Number(value)
  return Number.isFinite(num) ? num : Number.NaN
}

export function resolveSeckillProgress(stockValue: unknown, remainingValue: unknown): SeckillProgress | null {
  const stock = normalizeStock(stockValue)
  const remaining = normalizeStock(remainingValue)
  if (!Number.isFinite(stock) || !Number.isFinite(remaining))
    return null
  if (remaining < 0 || stock <= 0)
    return null

  const sold = Math.min(stock, Math.max(0, stock - remaining))
  const pct = Math.round((sold / stock) * 100)

  if (remaining <= 0) {
    const soldOut = translate('promotion.seckill.soldOut')
    return { low: true, pct: 100, shortText: soldOut, soldOut: true, fullText: soldOut }
  }

  if (pct <= 0) {
    const text = translate('promotion.common.limitedStock', { count: stock })
    return { low: false, pct, shortText: text, soldOut: false, fullText: text }
  }

  const low = remaining <= LOW_STOCK_THRESHOLD || remaining <= stock * LOW_STOCK_RATIO
  if (low) {
    const text = translate('labels.onlyLeft', { count: remaining })
    return { low: true, pct, shortText: text, soldOut: false, fullText: text }
  }

  return {
    low: false,
    pct,
    shortText: translate('promotion.seckill.progress.grabbed', { pct }),
    soldOut: false,
    fullText: translate('promotion.seckill.progress.grabbedFull', { count: remaining, pct }),
  }
}