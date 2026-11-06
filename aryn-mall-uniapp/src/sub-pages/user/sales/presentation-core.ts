/**
 * 销售工作台纯展示逻辑（不依赖 uni / pinia / i18n 运行时，便于单测）。
 */

export function formatMoney(value?: number | null | string) {
  const amount = Number(value ?? 0)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}

/** 业绩单状态：0 待确认、1 可发放、2 已发放、3 已全额退款 */
export function getSalesOrderStatusKey(status: string) {
  const labelKeys: Record<string, string> = {
    '0': 'sales.workbench.status.pending',
    '1': 'sales.workbench.status.payable',
    '2': 'sales.workbench.status.paid',
    '3': 'sales.workbench.status.refunded',
  }
  return labelKeys[status] ?? 'sales.workbench.status.unknown'
}

export function getSalesOrderStatusClass(status: string) {
  if (status === '2')
    return 'status-success'
  if (status === '3')
    return 'status-rejected'
  return 'status-pending'
}

/** 客户绑定来源：SHARE / QRCODE / ADMIN / TRANSFER / CLAIM */
export function getSalesBindSourceKey(source?: string | null) {
  const labelKeys: Record<string, string> = {
    SHARE: 'sales.workbench.bindSource.share',
    QRCODE: 'sales.workbench.bindSource.qrcode',
    ADMIN: 'sales.workbench.bindSource.admin',
    TRANSFER: 'sales.workbench.bindSource.transfer',
    CLAIM: 'sales.workbench.bindSource.claim',
  }
  return (source && labelKeys[source]) || 'sales.workbench.bindSource.unknown'
}

/**
 * 列表分页是否还有下一页。
 *
 * z-paging 之外的自绘「加载更多」按 `已加载数量 < total` 判断；
 * total 缺失时按「本页返回条数 >= pageSize」兜底。
 */
export function hasMoreRecords(
  loaded: number,
  total: number | null | undefined,
  pageSize: number,
) {
  if (typeof total === 'number' && total >= 0)
    return loaded < total
  return loaded > 0 && loaded % pageSize === 0
}
