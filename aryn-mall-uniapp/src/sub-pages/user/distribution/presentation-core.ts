export type CommissionFlowType = 'EXPENSE' | 'INCOME'

/** 券包可兑换状态 */
export type CouponPackExchangeState = 'available' | 'sold-out' | 'off-shelf'

export function formatMoney(value?: number | null | string) {
  const amount = Number(value ?? 0)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}

export function formatSignedCommission(value: number, flowType: string) {
  const amount = Math.abs(Number(value || 0))
  return `${flowType === 'EXPENSE' ? '-' : '+'}${formatMoney(amount)}`
}

export function getCommissionFlowKey(flowType: string) {
  if (flowType === 'INCOME') return 'accountSettings.distribution.flow.income'
  if (flowType === 'EXPENSE') return 'accountSettings.distribution.flow.expense'
  return 'accountSettings.distribution.flow.unknown'
}

export function getWithdrawStatusKey(status: string) {
  const labelKeys: Record<string, string> = {
    '0': 'accountSettings.distribution.status.pending',
    '1': 'accountSettings.distribution.status.approved',
    '2': 'accountSettings.distribution.status.rejected',
  }
  return labelKeys[status] ?? 'accountSettings.distribution.status.unknown'
}

export function getWithdrawStatusClass(status: string) {
  if (status === '1') return 'status-success'
  if (status === '2') return 'status-rejected'
  return 'status-pending'
}

/**
 * 券包是否可兑换：
 * - `status === '1'`（下架）优先置灰；
 * - 库存为数字且 <= 0 视为售罄；`null` / `undefined` 表示不限库存。
 *
 * 后端列表接口只会上架上架券包，这里仍做兜底：库存扣完到运营下架之间，
 * 列表可能短暂返回 stock=0 的券包，按钮必须置灰而不是让人点出业务错误。
 */
export function resolveCouponPackState(pack: {
  status?: string | null
  stock?: number | null
}): CouponPackExchangeState {
  if (pack.status === '1')
    return 'off-shelf'
  if (typeof pack.stock === 'number' && pack.stock <= 0)
    return 'sold-out'
  return 'available'
}

/**
 * 可兑换佣金是否足够。
 *
 * 按「分」比较而不是直接比浮点：佣金和消耗金额都是两位小数，
 * `0.1 + 0.2` 这类误差会让余额刚好相等时被误判为不足。
 */
export function hasEnoughCommission(
  available: number | null | undefined,
  required: number | null | undefined,
) {
  const availableFen = Math.floor(Number(available ?? 0) * 100)
  const requiredFen = Math.ceil(Number(required ?? 0) * 100)
  return availableFen >= requiredFen
}

/** 券兑换记录状态：0 发放中、1 已发放、2 发放失败 */
export function getCouponExchangeStatusKey(status: string) {
  const labelKeys: Record<string, string> = {
    '0': 'accountSettings.distribution.exchangeStatus.processing',
    '1': 'accountSettings.distribution.exchangeStatus.granted',
    '2': 'accountSettings.distribution.exchangeStatus.failed',
  }
  return labelKeys[status] ?? 'accountSettings.distribution.exchangeStatus.unknown'
}

export function getCouponExchangeStatusClass(status: string) {
  if (status === '1') return 'status-success'
  if (status === '2') return 'status-rejected'
  return 'status-pending'
}
