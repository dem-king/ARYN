import { translate } from '@/locales'
import {
  getCommissionFlowKey,
  getCouponExchangeStatusKey,
  getWithdrawStatusKey,
} from './presentation-core'

export type { CommissionFlowType, CouponPackExchangeState } from './presentation-core'
export {
  formatMoney,
  formatSignedCommission,
  getCouponExchangeStatusClass,
  getWithdrawStatusClass,
  hasEnoughCommission,
  resolveCouponPackState,
} from './presentation-core'

export function getCommissionFlowText(flowType: string) {
  return translate(getCommissionFlowKey(flowType))
}

export function getWithdrawStatusText(status: string) {
  return translate(getWithdrawStatusKey(status))
}

export function getCouponExchangeStatusText(status: string) {
  return translate(getCouponExchangeStatusKey(status))
}
