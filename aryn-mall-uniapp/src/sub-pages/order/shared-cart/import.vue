<script setup lang="ts">
import type { ReplenishDirectImport, ReplenishDirectImportConfirmResult } from '@/api/order/replenishImport'
import type { SharedCartImport, SharedCartImportRow } from '@/api/order/sharedCart'
import { onHide, onLoad, onPageScroll, onShow, onUnload } from '@dcloudio/uni-app'
import { computed, ref, watch } from 'vue'
import { useI18n } from '@/sub-pages/locales'
import { getDirectImport, getLatestDirectImport, resumeDirectImport } from '@/api/order/replenishImport'
import { getSharedCartImport, listSharedCartImports } from '@/api/order/sharedCart'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useRequireLogin } from '@/composables/useRequireLogin'
import { useAuthStore } from '@/store/authStore'
import { useGoodsStore } from '@/store/goodsStore'
import { useDirectImportOrderStore } from '@/store/directImportOrderStore'

/** 旧报告只读；新文字/Excel导入与人工处置统一在采购工作台。 */
definePage({ name: 'shared-cart-import', style: { navigationStyle: 'custom', navigationBarTitleText: '导入补给清单' } })
const { t } = useI18n()
const { ensureLoggedIn } = useRequireLogin()
const { handlePageScroll } = useFrostedNavbar()
const auth = useAuthStore()
const goodsStore = useGoodsStore()
const directImportOrderStore = useDirectImportOrderStore()
const cartId = ref('')
const directMode = ref(false)
const handoffId = ref('')
const importId = ref('')
const report = ref<ReplenishDirectImport | SharedCartImport | null>(null)
const history = ref<SharedCartImport[]>([])
const loading = ref(false)
const confirming = ref(false)
const error = ref('')
const rows = computed<SharedCartImportRow[]>(() => report.value?.rows ?? [])
const notFilledCount = computed(() => report.value?.notFilledRows ?? rows.value.filter(row => row.resultType === 'NOT_FILLED').length)
const canResumeCheckout = computed(() => directMode.value && report.value?.status === '2' && directImportOrderStore.matches(handoffId.value))
let visible = false
let initialized = false
let epoch = 0
const resultLabels = computed<Record<string, string>>(() => ({
  OK: t('sharedCart.result.matched'), UNMATCHED: t('sharedCart.result.unmatched'),
  SPEC_CHANGED: t('sharedCart.result.specChanged'), OVER_STOCK: t('sharedCart.result.overStock'),
  INVALID_QTY: t('sharedCart.result.invalidQty'), OFF_SHELF: t('sharedCart.result.offShelf'),
  NOT_FILLED: t('sharedCart.result.notFilled'),
}))

function invalidate() {
  epoch++
  report.value = null
  history.value = []
  loading.value = false
  confirming.value = false
}
function isCurrent(ticket: number, token: string) {
  return visible && ticket === epoch && auth.isLoggedIn && token === auth.getToken
}
function openWorkbench() {
  if (directMode.value && !directImportOrderStore.matches(handoffId.value)) {
    uni.redirectTo({ url: '/sub-pages/order/shared-cart/import-entry' })
    return
  }
  uni.redirectTo({ url: `/sub-pages/order/procurement/index?cartId=${encodeURIComponent(cartId.value)}&handoff=${encodeURIComponent(handoffId.value)}` })
}

onLoad((options) => {
  cartId.value = options?.cartId ?? ''
  directMode.value = options?.mode === 'direct'
  handoffId.value = options?.handoff ?? ''
  importId.value = options?.importId ?? ''
  const query = `legacy=1&cartId=${encodeURIComponent(cartId.value)}&mode=${directMode.value ? 'direct' : 'cart'}&handoff=${encodeURIComponent(handoffId.value)}&importId=${encodeURIComponent(importId.value)}`
  const redirectPath = `/sub-pages/order/shared-cart/import?${query}`
  if (!ensureLoggedIn(redirectPath))
    return
  if (!options?.legacy && !(directMode.value && directImportOrderStore.importId)) {
    openWorkbench()
    return
  }
  visible = true
  initialized = true
})
onShow(() => {
  visible = true
  if (initialized && auth.isLoggedIn)
    void restoreDirectReport()
})
onHide(() => { visible = false; invalidate() })
onUnload(() => { visible = false; invalidate() })
onPageScroll(handlePageScroll)
watch(() => auth.getToken, () => { invalidate(); initialized = false }, { flush: 'sync' })
watch(() => directImportOrderStore.handoffId, invalidate, { flush: 'sync' })

/** 服务器历史报告为唯一来源；失败或离页后不显示旧数据。 */
async function restoreDirectReport() {
  const ticket = ++epoch
  const token = auth.getToken
  report.value = null
  error.value = ''
  loading.value = true
  try {
    if (directMode.value) {
      const id = importId.value || directImportOrderStore.importId
      const restored = await (id ? getDirectImport(id) : getLatestDirectImport())
      if (!isCurrent(ticket, token))
        return
      report.value = restored
    }
    else {
      const list = await listSharedCartImports(cartId.value)
      if (!isCurrent(ticket, token))
        return
      history.value = list ?? []
      const id = importId.value || history.value[0]?.importId
      if (id) {
        const restored = await getSharedCartImport(cartId.value, id)
        if (isCurrent(ticket, token))
          report.value = restored
      }
    }
  }
  catch (cause) {
    if (isCurrent(ticket, token))
      error.value = cause instanceof Error ? cause.message : t('sharedCart.importPage.loadFailed')
  }
  finally {
    if (isCurrent(ticket, token))
      loading.value = false
  }
}
function selectReport(id: string) {
  importId.value = id
  void restoreDirectReport()
}

/** 已确认直下单仅恢复服务器复核清单；结算继续校验当前价格、库存与配送资格。 */
async function handleResumeCheckout() {
  if (!canResumeCheckout.value || !report.value || confirming.value)
    return
  const id = report.value.importId
  const ticket = ++epoch
  const token = auth.getToken
  confirming.value = true
  const stillCurrent = () => isCurrent(ticket, token) && directImportOrderStore.matches(handoffId.value)
  try {
    const result: ReplenishDirectImportConfirmResult = await resumeDirectImport(id)
    if (!stillCurrent())
      return
    if (!result.checkoutReady || !result.items?.length) {
      uni.showModal({ title: t('sharedCart.importPage.confirmBlockedTitle'), content: result.message || t('sharedCart.importPage.nothingToOrder'), showCancel: false })
      return
    }
    const proceed = () => {
      if (!stillCurrent())
        return
      directImportOrderStore.setImportId(id)
      goodsStore.setGoodsList(result.items.map(item => ({
        spuId: item.spuId, skuId: item.skuId, quantity: item.quantity,
        spuName: item.spuName, specsInfo: item.specsInfo, picUrl: item.picUrl,
      })))
      uni.redirectTo({ url: `/sub-pages/order/order-confirm/index?createWay=2&handoff=${encodeURIComponent(handoffId.value)}` })
    }
    if (Number(result.skippedRows) > 0) {
      const skipped = result.rows.filter(row => row.resolvedAction === 'SKIP').slice(0, 3)
        .map(row => `${t('sharedCart.importPage.rowNo', { row: row.rowNo })}：${row.resultMessage || ''}`).join('\n')
      uni.showModal({
        title: t('sharedCart.importPage.confirmSkippedTitle'), content: `${result.message || ''}\n${skipped}`.trim(),
        confirmText: t('sharedCart.importPage.continueCheckout'),
        success: ({ confirm }) => { if (confirm) proceed() },
      })
    }
    else {
      proceed()
    }
  }
  catch (cause) {
    if (stillCurrent())
      error.value = cause instanceof Error ? cause.message : t('sharedCart.importPage.loadFailed')
  }
  finally {
    if (stillCurrent())
      confirming.value = false
  }
}
</script>

<template>
  <view class="history-page">
    <hr-navbar :title="t('procurement.legacyTitle')" />
    <view class="history-content">
      <view class="history-notice">{{ t('procurement.legacyReadOnly') }}</view>
      <view class="history-actions">
        <button size="mini" @tap="openWorkbench">{{ t('procurement.eyebrow') }}</button>
        <button size="mini" :disabled="loading || confirming" @tap="restoreDirectReport">{{ t('procurement.refresh') }}</button>
      </view>
      <view v-if="loading" class="history-empty">{{ t('common.loading') }}</view>
      <view v-else-if="error" class="history-error">{{ error }}</view>
      <view v-else-if="!report" class="history-empty">{{ t('procurement.noLegacyReport') }}</view>
      <template v-if="report">
        <view class="history-heading">{{ report.fileName || t('procurement.legacyTitle') }}</view>
        <view class="history-meta">{{ report.createTime }} · {{ t('sharedCart.importPage.totalRows', { count: rows.length }) }}</view>
        <view v-if="notFilledCount" class="history-meta">{{ t('sharedCart.importPage.notFilledHint', { count: notFilledCount }) }}</view>
        <view v-for="row in rows" :key="row.rowNo" class="history-row">
          <view class="history-meta">{{ t('sharedCart.importPage.rowNo', { row: row.rowNo }) }} · {{ row.personName || t('procurement.unassigned') }}</view>
          <view class="history-heading">{{ row.rawName || row.rawCode || t('procurement.unknownName') }}</view>
          <view>{{ row.rawQuantity ?? '—' }} {{ row.rawUnit || '' }} · {{ row.rawSpec || '' }}</view>
          <view class="history-meta">{{ row.matchedName || row.resolvedSkuId || '' }} {{ row.matchedSpec || '' }}</view>
          <view class="history-meta">{{ resultLabels[row.resultType] || row.resultType }} · {{ row.resultMessage || '' }}</view>
          <view v-if="row.resolvedAction" class="history-meta">{{ row.resolvedAction }} · {{ row.resolvedQuantity ?? '—' }}</view>
          <view v-if="row.rawRemark" class="history-evidence">{{ row.rawRemark }}</view>
        </view>
        <button v-if="canResumeCheckout" :loading="confirming" :disabled="confirming" class="history-resume" @tap="handleResumeCheckout">{{ t('sharedCart.importPage.resumeCheckout') }}</button>
      </template>
      <view v-if="history.length > 1" class="history-list">
        <view class="history-heading">{{ t('procurement.legacyRecords') }}</view>
        <button v-for="item in history" :key="item.importId" size="mini" @tap="selectReport(item.importId)">{{ item.fileName || item.importId }} · {{ item.createTime }}</button>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.history-page { min-height: 100vh; background: #f4f7f6; color: #223d37; }
.history-content { padding: 28rpx; }
.history-notice { padding: 28rpx; border-left: 6rpx solid #248875; background: #e5f2ee; line-height: 1.7; font-size: 26rpx; }
.history-actions { display: flex; gap: 20rpx; margin: 24rpx 0; }
.history-actions button { flex: 1; margin: 0; }
.history-heading { font-size: 29rpx; font-weight: 600; margin: 12rpx 0; }
.history-meta { font-size: 24rpx; color: #64776f; margin: 10rpx 0; }
.history-row { padding: 24rpx; margin: 20rpx 0; background: #fff; border-radius: 16rpx; }
.history-evidence { white-space: pre-wrap; font-size: 24rpx; padding: 16rpx; background: #f6f8f7; }
.history-error { color: #ac4935; padding: 24rpx 0; }
.history-empty { padding: 60rpx 0; text-align: center; color: #64776f; }
.history-resume { margin-top: 24rpx; color: #fff; background: #237d6b; }
.history-list button { display: block; margin: 16rpx 0; }
</style>
