import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({
  api: {
    listProcurementDrafts: vi.fn(), getProcurementDraft: vi.fn(), listProcurementBatches: vi.fn(), getProcurementBatch: vi.fn(), getProcurementRows: vi.fn(),
    submitProcurementHistory: vi.fn(), createProcurementDraft: vi.fn(), submitProcurementText: vi.fn(), reviewProcurementRow: vi.fn(), reviewReadyProcurementRows: vi.fn(), searchProcurementSkus: vi.fn(),
    confirmProcurementBatch: vi.fn(), getProcurementCheckout: vi.fn(), cancelProcurementBatch: vi.fn(), retryProcurementBatch: vi.fn(),
  },
  upload: vi.fn(), auth: { isLoggedIn: true, getToken: 'token' }, user: { getUserId: 'actor', fetchUserInfo: vi.fn() },
  ship: { purchaseScene: '1', vesselId: '', vesselCallId: '' },
}))
vi.mock('@/api/order/procurement-draft', () => mocks.api)
vi.mock('@/api/order/sharedCart', () => ({ getSharedCart: vi.fn() }))
vi.mock('@/store/authStore', () => ({ useAuthStore: () => mocks.auth }))
vi.mock('@/store/userStore', () => ({ useUserStore: () => mocks.user }))
vi.mock('@/store/shipContextStore', () => ({ useShipContextStore: () => mocks.ship }))
vi.mock('@/store/directImportOrderStore', () => ({ useDirectImportOrderStore: () => ({ matches: () => false }) }))
vi.mock('@/store/goodsStore', () => ({ useGoodsStore: () => ({ setGoodsList: vi.fn() }) }))
vi.mock('@/sub-pages/api/order/procurement-upload', () => ({ uploadProcurementExcel: mocks.upload }))
vi.mock('@/sub-pages/locales', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
import { useProcurementWorkbench } from './useProcurementWorkbench'
const current = { id: 'draft', purchaseScene: '1', revision: 1, ownerUserId: 'actor', status: 'OPEN' }
const source = { id: 'batch', draftId: 'draft', status: 'READY', revision: 1, completedRows: 1, totalRows: 1 }
const row = { id: 'row', batchId: 'batch', sourceRow: 1, sourcePosition: '0001', rawText: '苹果2件', revision: 0, reviewStatus: 'REQUIRED', selectedSkuId: 'sku' } as any
const storage = new Map()
beforeEach(() => {
  vi.clearAllMocks(); setActivePinia(createPinia()); storage.clear()
  vi.stubEnv('VITE_TENANT_ID', 'tenant')
  vi.stubGlobal('uni', { getStorageSync: (key: string) => storage.get(key), setStorageSync: (key: string, value: unknown) => storage.set(key, value), removeStorageSync: (key: string) => storage.delete(key) })
  mocks.api.listProcurementDrafts.mockResolvedValue([]); mocks.api.createProcurementDraft.mockResolvedValue(current)
  mocks.api.getProcurementDraft.mockResolvedValue(current); mocks.api.listProcurementBatches.mockResolvedValue([source])
  mocks.api.getProcurementBatch.mockResolvedValue(source); mocks.api.getProcurementRows.mockResolvedValue([row])
})
describe('采购工作台权威状态', () => {
  it('弱网响应丢失再次提交时复用原请求，成功后以服务器批次恢复', async () => {
    const work = useProcurementWorkbench(); await work.initialize(); work.session.value.text = '苹果2件'
    mocks.api.submitProcurementText.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ decision: 'REPLAY', batchId: 'batch' })
    await work.submitText(); expect(work.error.value).toBe('offline')
    await work.submitText()
    const first = mocks.api.submitProcurementText.mock.calls[0][1]
    expect(mocks.api.submitProcurementText.mock.calls[1][1]).toEqual(first)
    expect(work.batch.value?.id).toBe('batch'); expect(work.cache.session.pending.text).toBeUndefined()
    work.dispose()
  })
  it('服务端行冲突后刷新当前revision，保留人工未同步编辑', async () => {
    const work = useProcurementWorkbench(); await work.initialize({ draftId: 'draft' })
    work.edit(row).salesQuantity = '9'; work.edit(row).reason = 'reason'; work.cache.save()
    mocks.api.reviewProcurementRow.mockResolvedValue({ decision: 'CONFLICT', row: { ...row, revision: 2 }, batchRevision: 3 })
    mocks.api.getProcurementRows.mockResolvedValue([{ ...row, revision: 2 }])
    await work.review(row, 'CONFIRM_SALES')
    expect(work.rows.value[0].revision).toBe(2)
    expect(work.session.value.edits.row.salesQuantity).toBe('9')
    expect(work.notice.value).toBe('procurement.conflict'); work.dispose()
  })
  it('离页或身份切换后丢弃迟到创建响应', async () => {
    const work = useProcurementWorkbench(); await work.initialize(); work.session.value.text = '苹果2件'
    let resolve: (value: unknown) => void = () => {}
    mocks.api.submitProcurementText.mockReturnValue(new Promise(done => { resolve = done }))
    const pending = work.submitText()
    await vi.waitFor(() => expect(mocks.api.submitProcurementText).toHaveBeenCalled())
    work.dispose(); work.cache.clear(); resolve({ decision: 'CREATED', batchId: 'old-batch' }); await pending
    expect(work.batch.value).toBeNull(); expect(work.cache.session.batchId).toBe('')
  })
  it('Excel上传收到重复来源后打开原批次且不直接确认', async () => {
    const work = useProcurementWorkbench(); await work.initialize()
    mocks.upload.mockResolvedValue({ decision: 'DUPLICATE', batchId: 'batch' })
    await work.upload('/file.xlsx', 'file.xlsx', 123)
    expect(work.duplicate.value).toBe(true); expect(work.batch.value?.id).toBe('batch')
    expect(mocks.api.confirmProcurementBatch).not.toHaveBeenCalled(); work.dispose()
  })
  it('另开采购后仍可提交，清理上份替换和重复标志并保留服务器草稿入口', async () => {
    mocks.api.listProcurementDrafts.mockResolvedValue([current])
    const work = useProcurementWorkbench(); await work.initialize({ draftId: 'draft' })
    work.replacement.value = 'old'; work.duplicate.value = true; work.explicitNew.value = true
    work.newDraft(); work.session.value.text = '本次苹果3件'
    mocks.api.createProcurementDraft.mockResolvedValue({ ...current, id: 'new-draft' })
    mocks.api.submitProcurementText.mockResolvedValue({ decision: 'CREATED', batchId: 'batch' })
    await work.submitText()
    expect(mocks.api.submitProcurementText.mock.calls[0][0]).toBe('new-draft')
    expect(mocks.api.submitProcurementText.mock.calls[0][1].explicitNew).toBe(false)
    expect(work.replacement.value).toBe(''); expect(work.duplicate.value).toBe(false)
    expect(work.drafts.value).toHaveLength(1); work.dispose()
  })
  it('刷新和重开失败均提示错误，恢复网络后可重新读取', async () => {
    const work = useProcurementWorkbench(); await work.initialize({ draftId: 'draft' })
    mocks.api.getProcurementDraft.mockRejectedValueOnce(new Error('refresh offline'))
    await expect(work.refresh()).resolves.toBeUndefined()
    expect(work.error.value).toBe('refresh offline')
    mocks.api.getProcurementDraft.mockRejectedValueOnce(new Error('load offline'))
    await expect(work.loadDraft('draft')).resolves.toBeUndefined()
    expect(work.error.value).toBe('load offline')
    await work.refresh(); expect(work.error.value).toBe(''); expect(work.batch.value?.id).toBe('batch')
    work.dispose()
  })
  it('历史来源自动进入同一批次管道，响应丢失后保留原请求重试', async () => {
    const work = useProcurementWorkbench()
    mocks.api.submitProcurementHistory.mockRejectedValueOnce(new Error('lost')).mockResolvedValueOnce({ decision: 'REPLAY', batchId: 'batch' })
    await work.initialize({ scene: '1', sourceType: 'REORDER', sourceId: 'order' })
    expect(work.error.value).toBe('lost')
    const first = mocks.api.submitProcurementHistory.mock.calls[0][1]
    await work.submitHistory()
    expect(mocks.api.submitProcurementHistory.mock.calls[1][1]).toEqual(first)
    expect(first.sourceId).toBe('order'); expect(first.explicitNew).toBe(false)
    expect(work.batch.value?.id).toBe('batch'); expect(work.cache.session.pending.history).toBeUndefined()
    expect(mocks.api.confirmProcurementBatch).not.toHaveBeenCalled(); work.dispose()
  })
  it('刷新离页后的迟到失败不会污染下一次会话', async () => {
    const work = useProcurementWorkbench(); await work.initialize({ draftId: 'draft' })
    let reject: (cause: unknown) => void = () => {}
    mocks.api.getProcurementDraft.mockReturnValueOnce(new Promise((_, fail) => { reject = fail }))
    const pending = work.refresh(); work.dispose(); reject(new Error('old error')); await pending
    expect(work.error.value).toBe(''); expect(work.draft.value).toBeNull()
  })
  it('批量核对保留人工未保存编辑并展示部分失败，不自动确认来源', async () => {
    const exact = { ...row, parseStatus: 'PARSED', matchStatus: 'MATCHED', unitStatus: 'EXACT', quantityStatus: 'VALID' }
    mocks.api.getProcurementRows.mockResolvedValue([exact, { ...exact, id: 'edited' }, { ...exact, id: 'approx', unitStatus: 'APPROX' }])
    const work = useProcurementWorkbench(); await work.initialize({ draftId: 'draft' })
    work.rows.value.forEach(item => work.edit(item))
    work.edit(work.rows.value[1]).salesQuantity = '8'
    mocks.api.reviewReadyProcurementRows.mockResolvedValue({ applied: [], problems: [{ rowId: 'row', code: 'REVIEW_REQUIRED', message: '库存不足' }] })
    await work.reviewReady('逐项已核对')
    expect(mocks.api.reviewReadyProcurementRows.mock.calls[0][2]).toEqual({ rows: [{ rowId: 'row', expectedRevision: 0 }], reason: '逐项已核对' })
    expect(work.error.value).toBe('库存不足'); expect(work.session.value.edits.edited.salesQuantity).toBe('8')
    expect(mocks.api.confirmProcurementBatch).not.toHaveBeenCalled(); work.dispose()
  })
})
