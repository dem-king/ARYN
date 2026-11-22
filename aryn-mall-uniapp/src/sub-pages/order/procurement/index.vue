<script setup lang="ts">
import { onHide, onLoad, onPageScroll, onShow, onUnload } from '@dcloudio/uni-app'
import { watch } from 'vue'
import { useI18n } from '@/sub-pages/locales'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useRequireLogin } from '@/composables/useRequireLogin'
import { useShipContextStore } from '@/store/shipContextStore'
import ProcurementRow from './components/ProcurementRow.vue'
import { useProcurementWorkbench } from './useProcurementWorkbench'

definePage({ name: 'procurement-workbench', style: { navigationStyle: 'custom', navigationBarTitleText: '采购清单' } })
const { t } = useI18n()
const { handlePageScroll } = useFrostedNavbar()
const { ensureLoggedIn } = useRequireLogin()
const work = useProcurementWorkbench()
const { auth, user, cache, draft, drafts, batch, batches, busy, error, notice, cartId, explicitNew, replacement, duplicate, onlyPending, searchResults, session, pending, exactRows, processing, ready, replaceOptions, groups } = work
const ship = useShipContextStore()
let loaded = false
onLoad((query) => {
  loaded = true
  if (ensureLoggedIn()) void work.initialize(query as Record<string, string>)
})
onShow(() => { if (loaded) work.setVisible(true) })
onHide(() => work.setVisible(false))
onUnload(work.dispose)
onPageScroll(handlePageScroll)
watch(() => [auth.getToken, user.getUserId, ship.purchaseScene, ship.vesselId, ship.vesselCallId], (next, before) => {
  if (!loaded || (!before[1] && next[1] && next[0] === before[0])) return
  cache.clear()
  void work.initialize()
}, { flush: 'sync' })
function pickFile() {
  // #ifdef MP-WEIXIN
  uni.chooseMessageFile({ count: 1, type: 'file', extension: ['xls', 'xlsx'], success: ({ tempFiles }) => {
    const file = tempFiles?.[0]
    if (file?.path) void work.upload(file.path, file.name, file.size)
  } })
  // #endif
  // #ifdef H5
  uni.chooseFile({ count: 1, extension: ['.xls', '.xlsx'], success: (result) => {
    const files = Array.isArray(result.tempFiles) ? result.tempFiles : [result.tempFiles]
    const paths = Array.isArray(result.tempFilePaths) ? result.tempFilePaths : [result.tempFilePaths]
    const file = files[0] as { name?: string, path?: string, size?: number } | undefined
    const path = file?.path || paths[0]
    if (path) void work.upload(path, file?.name || '', file?.size || 0)
  } })
  // #endif
  // #ifdef APP-PLUS
  uni.showToast({ title: t('procurement.fileUnsupported'), icon: 'none' })
  // #endif
}
function confirm() {
  const selected = replaceOptions.value.find(item => item.id === replacement.value)
  uni.showModal({ title: t('procurement.confirmTitle'), content: selected ? t('procurement.replaceConfirm', { source: selected.inputName || selected.id.slice(-8) }) : t('procurement.addConfirm'),
    success: (result) => { if (result.confirm) void work.confirm() },
  })
}
function chooseBatch(event: { detail: { value: string | number } }) {
  const selected = batches.value[Number(event.detail.value)]
  if (selected) void work.selectBatch(selected.id).catch(() => { error.value = t('common.loadFailed') })
}
function chooseReplacement(event: { detail: { value: string | number } }) {
  replacement.value = replaceOptions.value[Number(event.detail.value) - 1]?.id || ''
}
function reviewExact() {
  uni.showModal({ title: t('procurement.bulkTitle'), content: t('procurement.bulkHint'), editable: true, placeholderText: t('procurement.reason'),
    success: (result) => { if (result.confirm) void work.reviewReady(result.content || '') },
  })
}
</script>

<template>
  <view class="procurement-page">
    <hr-navbar :title="t('procurement.title')" />
    <view class="workbench">
      <view class="masthead">
        <text class="eyebrow">{{ t('procurement.eyebrow') }}</text>
        <text class="headline">{{ t('procurement.heading') }}</text>
        <text class="intro">{{ t('procurement.intro') }}</text>
      </view>
      <view v-if="error" class="notice notice--error"><text>{{ error }}</text><text class="link" @tap="work.refresh()">{{ t('common.retry') }}</text></view>
      <view v-if="notice" class="notice">{{ notice }}</view>
      <view v-if="drafts.length && !draft" class="paper">
        <view class="section-head"><text>{{ t('procurement.savedDrafts') }}</text><text>{{ drafts.length }}</text></view>
        <view v-for="item in drafts" :key="item.id" class="draft-link" @tap="work.loadDraft(item.id)">
          <text>{{ item.title || item.id.slice(-8) }}</text><text>{{ item.updateTime || '' }} →</text>
        </view>
      </view>
      <view class="paper input-paper">
        <view class="section-head"><text>{{ t('procurement.addSource') }}</text><text class="section-no">01</text></view>
        <textarea v-model="session.text" :placeholder="t('procurement.textPlaceholder')" :maxlength="200000" class="source-input" @input="cache.save()" />
        <view class="input-actions">
          <button class="secondary" :disabled="busy" @tap="pickFile">{{ t('procurement.excel') }}</button>
          <button class="primary" :disabled="busy || !session.text.trim()" @tap="work.submitText()">{{ busy ? t('common.loading') : t('procurement.parse') }}</button>
        </view>
        <text class="footnote">{{ t('procurement.inputLimits') }}</text>
        <view v-if="duplicate" class="duplicate-actions">
          <text class="link" @tap="duplicate = false">{{ t('procurement.continueBatch') }}</text>
          <text class="link" @tap="explicitNew = true; duplicate = false">{{ t('procurement.asNew') }}</text>
          <text v-if="batch?.status === 'CONFIRMED'" class="link" @tap="replacement = batch.id; explicitNew = true; duplicate = false">{{ t('procurement.replaceThis') }}</text>
        </view>
        <text v-if="explicitNew" class="footnote">{{ t('procurement.newIntent') }}</text>
      </view>
      <view v-if="draft" class="paper report-paper">
        <view class="section-head"><text>{{ t('procurement.review') }}</text><text class="section-no">02</text></view>
        <view class="batch-picker">
          <picker v-if="batches.length" :range="batches.map((item, index) => `${index + 1}. ${item.inputName || t('procurement.textSource')} · ${t(`procurement.state.${item.status}`)}`)" @change="chooseBatch">
            <view class="batch-name">{{ batch?.inputName || t('procurement.textSource') }} <text class="i-carbon:chevron-down" /></view>
          </picker>
          <text class="link" @tap="work.refresh()">{{ t('procurement.refresh') }}</text>
        </view>
        <view v-if="batch" class="progress-strip">
          <view><text class="progress-number">{{ batch.completedRows || 0 }}</text><text>/ {{ batch.totalRows || '—' }}</text><text>{{ t('procurement.processed') }}</text></view>
          <text class="state-tag">{{ t(`procurement.state.${batch.status}`) }}</text>
        </view>
        <view v-if="processing" class="job-tools"><text>{{ t('procurement.background') }}</text><text class="link" @tap="work.changeJob('cancel')">{{ t('procurement.cancelJob') }}</text></view>
        <view v-if="batch && ['FAILED', 'CANCELLED'].includes(batch.status)" class="job-tools"><text>{{ batch.errorCode || t('procurement.stopped') }}</text><button v-if="batch.status === 'FAILED'" size="mini" :disabled="busy" @tap="work.changeJob('retry')">{{ t('procurement.retryJob') }}</button></view>
        <view class="filter-bar"><text>{{ t('procurement.pendingCount', { count: pending }) }}</text><text class="link" @tap="onlyPending = !onlyPending">{{ onlyPending ? t('procurement.showAll') : t('procurement.onlyPending') }}</text></view>
        <button v-if="exactRows.length && batch && ['READY', 'REVIEW_REQUIRED'].includes(batch.status)" class="secondary full" :disabled="busy" @tap="reviewExact">{{ t('procurement.bulkTitle') }}</button>
        <view v-if="!groups.length" class="empty-report">{{ processing ? t('procurement.waiting') : t('procurement.noPending') }}</view>
        <view v-for="group in groups" :key="group.owner" class="owner-group">
          <view class="owner-head"><text class="i-carbon:user" /><text>{{ group.owner }}</text><text class="owner-count">{{ group.items.length }}</text></view>
          <ProcurementRow v-for="row in group.items" :key="row.id" :row="row" :edit="work.edit(row)" :results="searchResults[row.id] || []" :disabled="busy" :readonly="!batch || !['READY', 'REVIEW_REQUIRED'].includes(batch.status)" @change="cache.save()" @search="keyword => work.search(row, keyword)" @save="(action, rounding) => work.review(row, action, rounding)" />
        </view>
      </view>
      <view v-if="draft && batch" class="paper confirm-paper">
        <view class="section-head"><text>{{ t('procurement.confirmSource') }}</text><text class="section-no">03</text></view>
        <picker v-if="ready" :range="[t('procurement.addMode'), ...replaceOptions.map(item => t('procurement.replaceMode', { source: item.inputName || item.id.slice(-8) }))]" @change="chooseReplacement">
          <view class="confirm-mode">{{ replacement ? t('procurement.replaceMode', { source: replaceOptions.find(item => item.id === replacement)?.inputName || replacement.slice(-8) }) : t('procurement.addMode') }} <text class="i-carbon:chevron-down" /></view>
        </picker>
        <text class="footnote">{{ t('procurement.confirmHint') }}</text>
        <button v-if="ready" class="primary full" :disabled="busy" @tap="confirm">{{ t('procurement.confirmSource') }}</button>
        <button v-if="batches.some(item => ['CONFIRMED', 'SUPERSEDED'].includes(item.status))" class="primary full" :disabled="busy" @tap="work.checkout()">{{ cartId ? t('procurement.openCart') : t('procurement.checkout') }}</button>
        <text class="link new-draft" @tap="work.newDraft()">{{ t('procurement.newDraft') }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.procurement-page { min-height: 100vh; color: var(--app-text); background: var(--app-bg); padding-bottom: calc(60rpx + env(safe-area-inset-bottom)); }
.workbench { padding: 32rpx 28rpx; }
.masthead { padding: 12rpx 8rpx 38rpx; display: flex; flex-direction: column; gap: 14rpx; }
.eyebrow { font-size: 20rpx; letter-spacing: 4rpx; color: #a37b42; }
.headline { font-size: 44rpx; font-weight: 650; letter-spacing: 1rpx; }
.intro { font-size: 24rpx; line-height: 1.8; color: var(--app-text-secondary); max-width: 590rpx; }
.paper { background: var(--app-card); border: 1rpx solid var(--app-border); border-radius: 14rpx; margin-bottom: 24rpx; padding: 30rpx; }
.section-head { display: flex; justify-content: space-between; align-items: baseline; font-weight: 600; font-size: 28rpx; margin-bottom: 24rpx; }
.section-no { font-size: 22rpx; color: #b99b6c; font-weight: 400; }
.source-input { box-sizing: border-box; width: 100%; height: 250rpx; padding: 20rpx; background: var(--app-card-2); font-size: 26rpx; line-height: 1.7; border-radius: 8rpx; }
.input-actions { display: flex; gap: 20rpx; margin: 24rpx 0 18rpx; }
button { font-size: 26rpx; line-height: 82rpx; margin: 0; border-radius: 8rpx; }
button::after { border: none; }
.primary { background: #8b6635; color: #fff; flex: 1; }
.secondary { background: var(--app-card-2); color: var(--app-text); flex: 1; }
.full { width: 100%; margin-top: 24rpx; }
.footnote { font-size: 22rpx; color: var(--app-text-secondary); line-height: 1.8; display: block; }
.notice { padding: 24rpx; background: var(--app-card); border-left: 4rpx solid #a37b42; font-size: 24rpx; margin-bottom: 24rpx; line-height: 1.7; display: flex; gap: 20rpx; justify-content: space-between; }
.notice--error { border-color: #bd5141; }
.link { color: #a37b42; font-size: 24rpx; }
.batch-picker, .progress-strip, .progress-strip > view, .filter-bar, .job-tools, .owner-head, .draft-link, .confirm-mode { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.batch-name { font-size: 26rpx; display: flex; gap: 12rpx; align-items: center; }
.progress-strip { background: var(--app-card-2); padding: 24rpx 18rpx; margin: 24rpx 0; border-radius: 8rpx; font-size: 22rpx; color: var(--app-text-secondary); }
.progress-number { font-size: 44rpx; color: var(--app-text); font-variant-numeric: tabular-nums; }
.state-tag { color: #a37b42; }
.job-tools { font-size: 22rpx; color: var(--app-text-secondary); line-height: 1.8; margin-bottom: 24rpx; }
.filter-bar { font-size: 24rpx; padding-bottom: 24rpx; border-bottom: 1rpx solid var(--app-border); }
.owner-head { justify-content: flex-start; font-size: 24rpx; padding: 30rpx 0 4rpx; color: var(--app-text-secondary); }
.owner-count { margin-left: auto; font-variant-numeric: tabular-nums; }
.empty-report { padding: 44rpx 0; font-size: 24rpx; text-align: center; color: var(--app-text-secondary); }
.duplicate-actions { display: flex; flex-wrap: wrap; gap: 24rpx; margin-top: 24rpx; }
.confirm-mode { padding: 24rpx 18rpx; border: 1rpx solid var(--app-border); font-size: 24rpx; margin-bottom: 18rpx; }
.new-draft { display: block; text-align: center; padding-top: 28rpx; }
.draft-link { padding: 24rpx 0; border-bottom: 1rpx solid var(--app-border); font-size: 24rpx; }
</style>
