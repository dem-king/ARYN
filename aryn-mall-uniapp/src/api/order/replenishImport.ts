import type { SharedCartImportActionPayload, SharedCartImportRow } from '@/api/order/sharedCart'
/**
 * 清单直下单导入 API（不挂共享购物车）。
 *
 * 后端契约（AppReplenishImportController，C 端网关域 mall-order）：
 * - 上传解析走文件流（`sharedCartImport.ts` 的 previewDirectImport）；
 * - 报告取回 / 确认是普通 JSON 接口，挂本文件；
 * - 防篡改契约与补给单导入一致：确认只回传「行号 → 处置动作」，
 *   行内容一律以服务端解析行为准；
 * - 确认的终点是**已复核的 SKU 清单**（同 SKU 已合并），前端写进
 *   goodsStore 后进结算页下单（createWay=2），不经过任何购物车。
 */
import { alovaInstance } from '@/api/core/instance'

/** 直下单导入任务状态：1待确认 2已确认 3已取消 */
export const DIRECT_IMPORT_STATUS_PENDING = '1'
export const DIRECT_IMPORT_STATUS_CONFIRMED = '2'

/** 结果类型（与补给单导入报告同口径） */
export const DIRECT_IMPORT_RESULT = {
  ok: 'OK',
  unmatched: 'UNMATCHED',
  specChanged: 'SPEC_CHANGED',
  overStock: 'OVER_STOCK',
  invalidQty: 'INVALID_QTY',
  offShelf: 'OFF_SHELF',
  notFilled: 'NOT_FILLED',
} as const

/**
 * 报告行与车版 SharedCartImportRow 同构（无接龙的 personName/aiPicked 与已下线的
 * 单位列）；刻意保持结构兼容，报告页对两种报告共用同一套渲染与处置函数。
 */
export type ReplenishDirectImportRow = Omit<
  SharedCartImportRow,
  'personName' | 'aiPickedSkuId' | 'rawUnit' | 'matchedUnit'
>

export interface ReplenishDirectImport {
  importId: string
  fileName?: string
  /** 1待确认 2已确认 3已取消 */
  status: string
  totalRows: number
  matchedRows: number
  unmatchedRows: number
  specChangedRows: number
  overStockRows: number
  invalidRows: number
  offShelfRows: number
  notFilledRows: number
  confirmedRows?: number
  skippedRows?: number
  createTime?: string
  confirmedTime?: string
  rows: ReplenishDirectImportRow[]
}

/** 确认产出的已复核明细（同 SKU 已合并；价格仅展示，实价以结算为准） */
export interface ReplenishDirectImportItem {
  spuId?: string
  skuId: string
  quantity: number
  spuName?: string
  specsInfo?: string
  picUrl?: string
  salesPrice?: number | null
}

/**
 * 确认回执。
 *
 * `checkoutReady=false` 表示本次没有可下单商品（全未处置、全部下架、
 * 合并后超库存等），任务仍是待确认，报告页必须留在原页继续处置；
 * `skippedRows`/`rows` 用于提示哪些行未纳入订单，不能静默跳结算。
 */
export interface ReplenishDirectImportConfirmResult {
  importId: string
  /** 1待确认 2已确认 */
  status: string
  checkoutReady: boolean
  items: ReplenishDirectImportItem[]
  rows: ReplenishDirectImportRow[]
  confirmedRows?: number
  skippedRows?: number
  message?: string
}

const BASE = '/mall-order/app/replenish-import'

/** 取回导入报告（稍后处理） */
export function getDirectImport(importId: string) {
  return alovaInstance.Get<ReplenishDirectImport>(`${BASE}/imports/${importId}`)
}

/** 最近一次可继续的导入（待确认或已确认）；杀进程/重启后恢复用，无则 null */
export function getLatestDirectImport() {
  return alovaInstance.Get<ReplenishDirectImport | null>(`${BASE}/latest`)
}

/** 恢复已确认导入的复核清单（只读）；杀进程后重新进入结算 */
export function resumeDirectImport(importId: string) {
  return alovaInstance.Get<ReplenishDirectImportConfirmResult>(`${BASE}/imports/${importId}/items`)
}

/**
 * 确认导入，返回已复核的 SKU 清单（幂等）。
 *
 * 客户端只回传「行号 → 处置动作」，与补给单导入同一契约；
 * 复用 SharedCartImportActionPayload（rowNo/action/skuId/quantity）。
 */
export function confirmDirectImport(importId: string, rows: SharedCartImportActionPayload[]) {
  return alovaInstance.Post<ReplenishDirectImportConfirmResult>(`${BASE}/imports/${importId}/confirm`, { rows })
}
