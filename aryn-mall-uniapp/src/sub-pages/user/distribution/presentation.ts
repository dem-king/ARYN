import { translate } from '@/locales'
import {
  getCommissionFlowKey,
  getWithdrawStatusKey,
} from './presentation-core'

export type { CommissionFlowType } from './presentation-core'
export {
  formatMoney,
  formatSignedCommission,
  getWithdrawStatusClass,
} from './presentation-core'

export function getCommissionFlowText(flowType: string) {
  return translate(getCommissionFlowKey(flowType))
}

export function getWithdrawStatusText(status: string) {
  return translate(getWithdrawStatusKey(status))
}
