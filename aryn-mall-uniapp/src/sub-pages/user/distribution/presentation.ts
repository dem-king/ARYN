export type CommissionFlowType = 'EXPENSE' | 'INCOME'

export function formatMoney(value?: number | null | string) {
  const amount = Number(value ?? 0)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}

export function formatSignedCommission(value: number, flowType: string) {
  const amount = Math.abs(Number(value || 0))
  return `${flowType === 'EXPENSE' ? '-' : '+'}${formatMoney(amount)}`
}

export function getCommissionFlowText(flowType: string) {
  if (flowType === 'INCOME') return '收入'
  if (flowType === 'EXPENSE') return '支出'
  return '未知'
}

export function getWithdrawStatusText(status: string) {
  const labels: Record<string, string> = {
    '0': '审核中',
    '1': '已通过',
    '2': '已驳回',
  }
  return labels[status] || '未知'
}

export function getWithdrawStatusClass(status: string) {
  if (status === '1') return 'status-success'
  if (status === '2') return 'status-rejected'
  return 'status-pending'
}
