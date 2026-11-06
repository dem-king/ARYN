import { translate } from '@/locales'
import {
  getSalesBindSourceKey,
  getSalesOrderStatusKey,
} from './presentation-core'

export {
  formatMoney,
  getSalesOrderStatusClass,
  hasMoreRecords,
} from './presentation-core'

export function getSalesOrderStatusText(status: string) {
  return translate(getSalesOrderStatusKey(status))
}

export function getSalesBindSourceText(source?: string | null) {
  return translate(getSalesBindSourceKey(source))
}
