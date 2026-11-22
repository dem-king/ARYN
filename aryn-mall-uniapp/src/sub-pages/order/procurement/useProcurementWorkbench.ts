import type { ProcurementBatch, ProcurementCandidate, ProcurementContext, ProcurementDraft, ProcurementRow, ReviewCommand } from '@/api/order/procurement-draft'
import { computed, ref } from 'vue'
import { useI18n } from '@/sub-pages/locales'
import { cancelProcurementBatch, confirmProcurementBatch, createProcurementDraft, getProcurementBatch, getProcurementCheckout, getProcurementDraft, getProcurementRows, listProcurementBatches, listProcurementDrafts, retryProcurementBatch, reviewProcurementRow, reviewReadyProcurementRows, searchProcurementSkus, submitProcurementText, submitProcurementHistory } from '@/api/order/procurement-draft'
import { getSharedCart } from '@/api/order/sharedCart'
import { useAuthStore } from '@/store/authStore'
import { useUserStore } from '@/store/userStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { useDirectImportOrderStore } from '@/store/directImportOrderStore'
import { useGoodsStore } from '@/store/goodsStore'
import { useProcurementStore } from '@/store/procurementStore'
import { uploadProcurementExcel } from '@/sub-pages/api/order/procurement-upload'
import { pendingProcurementRows, procurementScope } from '@/utils/procurement-session'

export function useProcurementWorkbench() {
  const { t } = useI18n()
  const auth = useAuthStore()
  const user = useUserStore()
  const ship = useShipContextStore()
  const handoff = useDirectImportOrderStore()
  const cache = useProcurementStore()
  const context = ref<ProcurementContext>({ purchaseScene: '1' })
  const draft = ref<ProcurementDraft | null>(null)
  const batch = ref<ProcurementBatch | null>(null)
  const batches = ref<ProcurementBatch[]>([])
  const drafts = ref<ProcurementDraft[]>([])
  const rows = ref<ProcurementRow[]>([])
  const busy = ref(false)
  const error = ref('')
  const notice = ref('')
  const cartId = ref('')
  const explicitNew = ref(false)
  const replacement = ref('')
  const duplicate = ref(false)
  const onlyPending = ref(true)
  const searchResults = ref<Record<string, ProcurementCandidate[]>>({})
  const session = computed(() => cache.session)
  const pending = computed(() => pendingProcurementRows(rows.value))
  const exactRows = computed(() => rows.value.filter((row) => {
    const value = session.value.edits[row.id]
    const unchanged = !value || (!value.reason && value.skuId === row.selectedSkuId && value.salesQuantity === (row.confirmedSalesQuantity?.toString() || '') && value.ownerName === (row.rawOwner || ''))
    return row.parseStatus === 'PARSED' && row.reviewStatus === 'REQUIRED' && row.matchStatus === 'MATCHED' && row.unitStatus === 'EXACT' && row.quantityStatus === 'VALID' && unchanged
  }))
  const processing = computed(() => !!batch.value && ['QUEUED', 'RUNNING'].includes(batch.value.status))
  const ready = computed(() => batch.value?.status === 'READY' && pending.value === 0)
  const replaceOptions = computed(() => batches.value.filter(item => item.status === 'CONFIRMED' && item.id !== batch.value?.id))
  const groups = computed(() => {
    const grouped = new Map<string, ProcurementRow[]>()
    for (const row of rows.value) {
      if (onlyPending.value && ['CONFIRMED', 'REJECTED'].includes(row.reviewStatus))
        continue
      const owner = row.rawOwner || t('procurement.unassigned')
      grouped.set(owner, [...(grouped.get(owner) || []), row])
    }
    return [...grouped].map(([owner, items]) => ({ owner, items }))
  })
  let initialized = false
  let epoch = 0
  let timer: ReturnType<typeof setTimeout> | undefined
  let options: Record<string, string> = {}
  let visible = true
  function stop() { if (timer) clearTimeout(timer); timer = undefined }
  function valid(ticket: number) { return ticket === epoch && !!cache.scope && auth.isLoggedIn }
  function clearView() {
    initialized = false; stop(); epoch++; draft.value = null; batch.value = null; rows.value = []; batches.value = []
    drafts.value = []; searchResults.value = {}; error.value = ''; notice.value = ''; busy.value = false
    explicitNew.value = false; replacement.value = ''; duplicate.value = false
  }
  function showError(cause: unknown) {
    error.value = cause instanceof Error ? cause.message : t('common.requestFailed')
  }
  async function initialize(query: Record<string, string> = options) {
    options = query
    clearView()
    if (!auth.isLoggedIn) { cache.clear(); return }
    const ticket = epoch
    try {
      if (!user.getUserId) await user.fetchUserInfo({ onlyUser: true })
      if (ticket !== epoch) return
      cartId.value = query.cartId || ''
      let initial: ProcurementContext
      if (cartId.value) {
        const cart = await getSharedCart(cartId.value)
        initial = { purchaseScene: cart.cartType === '2' ? '3' : '2', buyerOrgId: cart.buyerOrgId, vesselId: cart.vesselId, vesselCallId: cart.vesselCallId }
      }
      else if (query.handoff && handoff.matches(query.handoff)) {
        initial = { purchaseScene: handoff.scene, buyerOrgId: handoff.buyerOrgId, vesselId: handoff.vesselId, vesselCallId: handoff.vesselCallId }
      }
      else {
        initial = { purchaseScene: ship.purchaseScene, vesselId: ship.vesselId, vesselCallId: ship.vesselCallId }
      }
      if (query.scene && !cartId.value && !query.draftId) {
        initial = { ...initial, purchaseScene: query.scene, buyerOrgId: query.buyerOrgId || undefined }
        if (query.scene === '3') { initial.vesselId = undefined; initial.vesselCallId = undefined }
      }
      if (query.draftId) {
        const restored = await getProcurementDraft(query.draftId)
        initial = { purchaseScene: restored.purchaseScene, buyerOrgId: restored.buyerOrgId, vesselId: restored.vesselId, vesselCallId: restored.vesselCallId }
      }
      if (ticket !== epoch) return
      if (!user.getUserId) throw new Error(t('common.loginExpired'))
      context.value = initial
      cache.bind(procurementScope(import.meta.env.VITE_TENANT_ID, user.getUserId, initial) + (cartId.value ? `:${cartId.value}` : ''))
      if (query.draftId) cache.session.draftId = query.draftId
      cache.save()
      initialized = true
      const available = await listProcurementDrafts()
      if (!valid(ticket)) return
      drafts.value = available.filter(item => procurementScope('tenant', 'user', item) === procurementScope('tenant', 'user', initial))
      if (session.value.draftId) await loadDraftState(session.value.draftId, ticket)
      if (valid(ticket) && ['FREQUENT', 'REORDER', 'HISTORICAL'].includes(query.sourceType)) await submitHistory()
    }
    catch (cause) { if (ticket === epoch) showError(cause) }
  }
  async function loadDraftState(id: string, ticket: number) {
    const restored = await getProcurementDraft(id)
    if (!valid(ticket)) return
    draft.value = restored; cache.session.draftId = id; cache.save()
    await refreshState(ticket)
  }
  async function refreshState(ticket: number) {
    if (!draft.value) return
    const id = draft.value.id
    const [current, sources] = await Promise.all([getProcurementDraft(id), listProcurementBatches(id)])
    if (!valid(ticket) || draft.value?.id !== id) return
    draft.value = current; batches.value = sources
    // 上传响应丢失后从权威请求号恢复，不要求临时文件仍存在。
    for (const action of ['text', 'excel', 'history']) {
      const request = cache.session.pending[action]
      const recovered = request && sources.find(source => source.clientRequestId === request.id)
      if (recovered) { cache.session.batchId = recovered.id; cache.complete(action) }
    }
    const confirmation = cache.session.pending.confirm
    if (confirmation && sources.some(source => source.confirmationJson && source.id === JSON.parse(confirmation.payload).batch)) cache.complete('confirm')
    const selected = cache.session.batchId || sources[0]?.id
    if (selected) await selectBatchState(selected, ticket)
  }
  async function selectBatchState(id: string, ticket: number) {
    if (!draft.value) return
    stop()
    const draftId = draft.value.id
    cache.session.batchId = id; cache.save()
    const current = await getProcurementBatch(draftId, id)
    const all: ProcurementRow[] = []
    let after: string | undefined
    for (let page = 0; page < 21; page++) {
      const next = await getProcurementRows(draftId, id, after)
      if (!valid(ticket) || session.value.batchId !== id) return
      all.push(...next)
      if (next.length < 100) break
      const last = next.at(-1)?.sourcePosition
      if (!last || last === after) throw new Error(t('procurement.coverageError'))
      after = last
    }
    if (!valid(ticket) || session.value.batchId !== id) return
    batch.value = current; rows.value = all
    if (['QUEUED', 'RUNNING'].includes(current.status) && visible)
      timer = setTimeout(() => { void selectBatchState(id, ticket).catch(cause => { if (valid(ticket)) showError(cause) }) }, 2500)
  }
  async function operate(work: (ticket: number) => Promise<void>) {
    if (busy.value || !cache.scope || !initialized) return
    const ticket = epoch; busy.value = true; error.value = ''
    try { await work(ticket) }
    catch (cause) { if (valid(ticket)) showError(cause) }
    finally { if (ticket === epoch) busy.value = false }
  }
  function loadDraft(id: string) { return operate(ticket => loadDraftState(id, ticket)) }
  function refresh() { return operate(ticket => refreshState(ticket)) }
  function selectBatch(id: string) { return operate(ticket => selectBatchState(id, ticket)) }
  async function ensureDraft(ticket: number) {
    if (draft.value) return draft.value
    const input = cache.envelope('create', context.value, { ...context.value })
    const created = await createProcurementDraft(input)
    if (!valid(ticket)) return null
    cache.complete('create'); if (options.handoff && handoff.matches(options.handoff)) handoff.setProcurementDraftId(created.id); draft.value = created; cache.session.draftId = created.id; cache.save()
    return created
  }
  function submitText() {
    return operate(async (ticket) => {
      const text = session.value.text
      if (!text.trim()) throw new Error(t('procurement.textRequired'))
      const current = await ensureDraft(ticket)
      if (!current) return
      const input = cache.envelope('text', { text, explicitNew: explicitNew.value }, { text, explicitNew: explicitNew.value, expectedRevision: current.revision })
      const result = await submitProcurementText(current.id, input)
      if (!valid(ticket)) return
      cache.complete('text'); cache.session.batchId = result.batchId; cache.save()
      duplicate.value = result.decision === 'DUPLICATE'; explicitNew.value = false
      notice.value = duplicate.value ? t('procurement.duplicate') : t('procurement.created')
      await refreshState(ticket)
    })
  }
  function submitHistory() {
    return operate(async (ticket) => {
      const current = await ensureDraft(ticket)
      if (!current) return
      const intent = { sourceType: options.sourceType as 'FREQUENT' | 'REORDER' | 'HISTORICAL', sourceId: options.sourceId, skuId: options.skuId }
      const input = cache.envelope('history', intent, { ...intent, expectedRevision: current.revision, explicitNew: false })
      const result = await submitProcurementHistory(current.id, input)
      if (!valid(ticket)) return
      cache.complete('history'); cache.session.batchId = result.batchId; cache.save()
      notice.value = t('procurement.historyNeedsReview'); await refreshState(ticket)
    })
  }
  function upload(path: string, name: string, size: number) {
    return operate(async (ticket) => {
      const current = await ensureDraft(ticket)
      if (!current) return
      const input = cache.envelope('excel', { name, size, explicitNew: explicitNew.value }, { expectedRevision: current.revision, explicitNew: explicitNew.value })
      const result = await uploadProcurementExcel(current.id, path, input)
      if (!valid(ticket)) return
      cache.complete('excel'); cache.session.batchId = result.batchId; cache.save()
      duplicate.value = result.decision === 'DUPLICATE'; explicitNew.value = false
      notice.value = duplicate.value ? t('procurement.duplicate') : t('procurement.created')
      await refreshState(ticket)
    })
  }
  function edit(row: ProcurementRow) {
    return cache.session.edits[row.id] ||= { skuId: row.selectedSkuId, salesQuantity: row.confirmedSalesQuantity?.toString() || '', reason: '', ownerName: row.rawOwner || '' }
  }
  function review(row: ProcurementRow, action: ReviewCommand['action'], acceptRounding = false) {
    return operate(async (ticket) => {
      if (!draft.value || !batch.value) return
      const value = edit(row)
      const result = await reviewProcurementRow(draft.value.id, batch.value.id, row.id, {
        expectedRevision: row.revision, action, selectedSkuId: value.skuId,
        salesQuantity: value.salesQuantity ? Number(value.salesQuantity) : undefined,
        acceptRounding, reason: value.reason || '', ownerName: value.ownerName,
      })
      if (!valid(ticket)) return
      if (result.decision === 'CONFLICT') notice.value = t('procurement.conflict')
      else { delete cache.session.edits[row.id]; notice.value = t('procurement.saved') }
      cache.save(); await refreshState(ticket)
    })
  }
  async function search(row: ProcurementRow, keyword: string) {
    const ticket = epoch
    try {
      const found = await searchProcurementSkus(keyword, context.value.purchaseScene)
      if (valid(ticket)) searchResults.value[row.id] = (found.records || []).map(item => ({ ...item, spec: item.specsInfo }))
    }
    catch (cause) { if (valid(ticket)) showError(cause) }
  }
  function reviewReady(reason: string) {
    return operate(async (ticket) => {
      if (!draft.value || !batch.value || !exactRows.value.length) return
      const result = await reviewReadyProcurementRows(draft.value.id, batch.value.id, {
        rows: exactRows.value.slice(0, 100).map(row => ({ rowId: row.id, expectedRevision: row.revision })), reason,
      })
      if (!valid(ticket)) return
      notice.value = t('procurement.bulkResult', { applied: result.applied.length, problems: result.problems.length })
      if (result.problems.length) error.value = result.problems.map(item => item.message).filter((message, index, all) => all.indexOf(message) === index).join('；')
      await refreshState(ticket)
    })
  }
  function confirm() {
    return operate(async (ticket) => {
      if (!draft.value || !batch.value || !ready.value) return
      const old = replaceOptions.value.find(item => item.id === replacement.value)
      if (replacement.value && !old) throw new Error(t('procurement.replaceUnavailable'))
      const target = { targetType: cartId.value ? 'SHARED_CART' as const : 'DIRECT' as const, targetId: cartId.value || undefined }
      const intent = { batch: batch.value.id, replacement: old?.id, ...target }
      const input = cache.envelope('confirm', intent, {
        expectedRevision: batch.value.revision, mode: old ? 'REPLACE' as const : 'ADD' as const,
        replaceBatchId: old?.id, replaceBatchRevision: old?.revision, ...target,
      })
      await confirmProcurementBatch(draft.value.id, batch.value.id, input)
      if (!valid(ticket)) return
      cache.complete('confirm'); replacement.value = ''; notice.value = t('procurement.confirmed')
      await refreshState(ticket)
    })
  }
  function changeJob(action: 'retry' | 'cancel') {
    return operate(async (ticket) => {
      if (!draft.value || !batch.value) return
      await (action === 'retry' ? retryProcurementBatch : cancelProcurementBatch)(draft.value.id, batch.value.id, batch.value.revision)
      if (valid(ticket)) await refreshState(ticket)
    })
  }
  function checkout() {
    return operate(async (ticket) => {
      if (!draft.value) return
      if (cartId.value) { uni.navigateTo({ url: `/sub-pages/order/shared-cart/detail?id=${encodeURIComponent(cartId.value)}` }); return }
      const current = await getProcurementCheckout(draft.value.id)
      if (!valid(ticket)) return
      if (!current.items.length) throw new Error(t('procurement.emptyCheckout'))
      useGoodsStore().setGoodsList(current.items.map(item => ({ skuId: item.skuId, spuId: item.spuId, quantity: item.quantity })))
      handoff.begin({ scene: context.value.purchaseScene, buyerOrgId: context.value.buyerOrgId, vesselId: context.value.vesselId, vesselCallId: context.value.vesselCallId, vesselName: ship.vesselName, portName: ship.portName, berth: ship.berth })
      handoff.setProcurementDraftId(draft.value.id)
      uni.navigateTo({ url: `/sub-pages/order/order-confirm/index?createWay=2&handoff=${encodeURIComponent(handoff.handoffId)}` })
    })
  }
  function newDraft() {
    if (!initialized || busy.value) return
    const savedDrafts = drafts.value
    clearView(); initialized = true; drafts.value = savedDrafts
    options = { ...options, draftId: '', sourceType: '', sourceId: '', skuId: '' }
    cache.session.draftId = ''; cache.session.batchId = ''; cache.session.edits = {}; cache.session.pending = {}; cache.save()
  }
  function setVisible(value: boolean) {
    visible = value; stop()
    if (value && draft.value) void refresh()
  }
  function dispose() { visible = false; clearView() }
  return { auth, user, context, cache, draft, drafts, batch, batches, rows, busy, error, notice, cartId, explicitNew, replacement, duplicate, onlyPending, searchResults, session, pending, exactRows, processing, ready, replaceOptions, groups, initialize, loadDraft, refresh, selectBatch, submitText, submitHistory, upload, edit, review, reviewReady, search, confirm, changeJob, checkout, newDraft, setVisible, dispose }
}
