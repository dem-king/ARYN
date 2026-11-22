import type { ProcurementContext } from '@/api/order/procurement-draft'

export function procurementScope(tenant: string, user: string, context: ProcurementContext) {
  if (!tenant || !user)
    return ''
  return JSON.stringify([tenant, user, context.purchaseScene, context.buyerOrgId || '', context.vesselId || '', context.vesselCallId || ''])
}
export function procurementRequestId() {
  return `p-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`
}
export interface ProcurementSession {
  draftId: string
  batchId: string
  text: string
  pending: Record<string, { id: string, payload: string, command?: Record<string, unknown> }>
  edits: Record<string, { skuId?: string, salesQuantity?: string, reason?: string, ownerName?: string }>
}
export function emptyProcurementSession(): ProcurementSession {
  return { draftId: '', batchId: '', text: '', pending: {}, edits: {} }
}
/** 直到服务器确认成功才释放，响应丢失/离页重开仍复用原请求号。 */
export function stableProcurementRequest(session: ProcurementSession, action: string, payload: unknown) {
  const serialized = JSON.stringify(payload)
  const existing = session.pending[action]
  if (existing?.payload === serialized)
    return existing.id
  const id = procurementRequestId()
  session.pending[action] = { id, payload: serialized }
  return id
}
export function pendingProcurementRows(rows: Array<{ reviewStatus: string }>) {
  return rows.filter(row => !['CONFIRMED', 'REJECTED'].includes(row.reviewStatus)).length
}

/** 幂等重试保留最初的revision；不能在响应丢失后换新revision再造一份采购。 */
export function procurementEnvelope<T extends Record<string, unknown>>(session: ProcurementSession, action: string, intent: unknown, command: T) {
  const id = stableProcurementRequest(session, action, intent)
  const pending = session.pending[action]
  if (!pending.command)
    pending.command = command
  return { ...pending.command, clientRequestId: id, requestId: id } as T & { clientRequestId: string, requestId: string }
}
export function procurementCandidates(value?: string) {
  try {
    const parsed: unknown = JSON.parse(value || '[]')
    return Array.isArray(parsed) ? parsed.filter(item => item && typeof item.skuId === 'string') : []
  }
  catch { return [] }
}
