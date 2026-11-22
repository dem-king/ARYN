import { alovaInstance } from '@/api/core/instance'

export const PROCUREMENT_BASE = '/mall-order/app/procurement-drafts'
const segment = (value: string) => encodeURIComponent(value)
export interface ProcurementContext {
  purchaseScene: string
  buyerOrgId?: string
  vesselId?: string
  vesselCallId?: string
}
export interface ProcurementDraft extends ProcurementContext {
  id: string
  ownerUserId: string
  title?: string
  revision: number
  status: string
  updateTime?: string
}
export interface ProcurementBatch {
  id: string
  draftId: string
  clientRequestId: string
  status: string
  revision: number
  inputType: string
  inputName?: string
  inputText?: string
  parserSource?: string
  completedRows: number
  totalRows: number
  errorCode?: string
  confirmationJson?: string
  replacedByBatchId?: string
}
export interface ProcurementCandidate {
  skuId: string
  spuId?: string
  name: string
  spec?: string
  stock?: number
}
export interface ProcurementRow {
  id: string
  batchId: string
  sourcePosition: string
  sourceSheet?: string
  sourceRow: number
  rawText: string
  rawName?: string
  rawQuantity?: number | string | null
  rawUnit?: string
  rawOwner?: string
  parseStatus: string
  matchStatus: string
  unitStatus: string
  quantityStatus: string
  reviewStatus: string
  selectedSkuId?: string
  suggestedSalesQuantity?: number | null
  confirmedSalesQuantity?: number | null
  excessBaseQuantity?: number | string | null
  measurementRevision?: number
  candidatesJson?: string
  decisionJson?: string
  revision: number
}
export interface BatchSubmission {
  decision: 'CREATED' | 'REPLAY' | 'DUPLICATE'
  batchId: string
  draftRevision: number
}
export interface ReviewCommand {
  expectedRevision: number
  action: 'SELECT_SKU' | 'CONFIRM_SALES' | 'ACCEPT_SUGGESTION' | 'REJECT' | 'SET_OWNER'
  selectedSkuId?: string
  salesQuantity?: number
  acceptRounding: boolean
  reason: string
  ownerName?: string
}
export interface ConfirmCommand {
  requestId: string
  expectedRevision: number
  mode: 'ADD' | 'REPLACE'
  replaceBatchId?: string
  replaceBatchRevision?: number
  targetType: 'DIRECT' | 'SHARED_CART'
  targetId?: string
}
export interface ProcurementConfirmation {
  batchId: string
  targetType: string
  targetId: string
  status: string
  batchRevision: number
  items: Array<{ skuId: string, spuId?: string, quantity: number, sourceRowIds: string[] }>
}
const batchPath = (draft: string, batch: string) => `${PROCUREMENT_BASE}/${segment(draft)}/batches/${segment(batch)}`
export const listProcurementDrafts = () => alovaInstance.Get<ProcurementDraft[]>(PROCUREMENT_BASE, { cacheFor: 0 })
export const createProcurementDraft = (data: ProcurementContext & { clientRequestId: string, title?: string }) =>
  alovaInstance.Post<ProcurementDraft>(PROCUREMENT_BASE, data)
export const getProcurementDraft = (draft: string) => alovaInstance.Get<ProcurementDraft>(`${PROCUREMENT_BASE}/${segment(draft)}`, { cacheFor: 0 })
export const listProcurementBatches = (draft: string) => alovaInstance.Get<ProcurementBatch[]>(`${PROCUREMENT_BASE}/${segment(draft)}/batches`, { cacheFor: 0 })
export const getProcurementBatch = (draft: string, batch: string) => alovaInstance.Get<ProcurementBatch>(batchPath(draft, batch), { cacheFor: 0 })
export const getProcurementRows = (draft: string, batch: string, after?: string) =>
  alovaInstance.Get<ProcurementRow[]>(`${batchPath(draft, batch)}/rows`, { params: { after }, cacheFor: 0 })
export const submitProcurementText = (draft: string, data: { clientRequestId: string, expectedRevision: number, text: string, explicitNew: boolean }) =>
  alovaInstance.Post<BatchSubmission>(`${PROCUREMENT_BASE}/${segment(draft)}/text`, data)
export const reviewProcurementRow = (draft: string, batch: string, row: string, data: ReviewCommand) =>
  alovaInstance.Put<{ decision: 'APPLIED' | 'CONFLICT', row: ProcurementRow, batchRevision: number }>(`${batchPath(draft, batch)}/rows/${segment(row)}`, data)
export const reviewReadyProcurementRows = (draft: string, batch: string, data: { rows: Array<{ rowId: string, expectedRevision: number }>, reason: string }) =>
  alovaInstance.Post<{ applied: Array<{ row: ProcurementRow }>, problems: Array<{ rowId: string, code: string, message: string, revision?: number }> }>(`${batchPath(draft, batch)}/review-ready`, data)
export const cancelProcurementBatch = (draft: string, batch: string, expectedRevision: number) =>
  alovaInstance.Post<ProcurementBatch>(`${batchPath(draft, batch)}/cancel`, { expectedRevision })
export const retryProcurementBatch = (draft: string, batch: string, expectedRevision: number) =>
  alovaInstance.Post<ProcurementBatch>(`${batchPath(draft, batch)}/retry`, { expectedRevision })
export const confirmProcurementBatch = (draft: string, batch: string, data: ConfirmCommand) =>
  alovaInstance.Post<ProcurementConfirmation>(`${batchPath(draft, batch)}/confirm`, data)
export const searchProcurementSkus = (keyword: string, scene: string) =>
  alovaInstance.Get<{ records: Array<{ skuId: string, spuId?: string, name: string, specsInfo?: string, stock?: number }> }>('/product/app/goodsspu/search', {
    params: { keyword, scene, current: 1, size: 20 }, cacheFor: 0,
  })

export const getProcurementCheckout = (draft: string) => alovaInstance.Get<ProcurementConfirmation>(`${PROCUREMENT_BASE}/${segment(draft)}/items`, { cacheFor: 0 })

export interface ProcurementHistoryCommand {
  clientRequestId: string
  expectedRevision: number
  sourceType: 'FREQUENT' | 'REORDER' | 'HISTORICAL'
  sourceId?: string
  skuId?: string
  salesQuantity?: number
  explicitNew: boolean
}
export const submitProcurementHistory = (draft: string, input: ProcurementHistoryCommand) => alovaInstance.Post<BatchSubmission>(`${PROCUREMENT_BASE}/${segment(draft)}/history`, input)
