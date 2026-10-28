export type CommissionFlowType = 'EXPENSE' | 'INCOME'

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
