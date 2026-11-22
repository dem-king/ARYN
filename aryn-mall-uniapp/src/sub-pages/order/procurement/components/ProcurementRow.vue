<script setup lang="ts">
import type { ProcurementCandidate, ProcurementRow, ReviewCommand } from '@/api/order/procurement-draft'
import { computed, ref } from 'vue'
import { useI18n } from '@/sub-pages/locales'
import { procurementCandidates } from '@/utils/procurement-session'
const props = defineProps<{
  row: ProcurementRow
  edit: { skuId?: string, salesQuantity?: string, reason?: string, ownerName?: string }
  results: ProcurementCandidate[]
  disabled: boolean
  readonly: boolean
}>()
const emit = defineEmits<{
  save: [action: ReviewCommand['action'], rounding: boolean]
  search: [keyword: string]
  change: []
}>()
const { t } = useI18n()
const keyword = ref('')
const rounding = ref(false)
const expanded = ref(false)
const candidates = computed<ProcurementCandidate[]>(() => props.results.length ? props.results : procurementCandidates(props.row.candidatesJson))
const evidence = computed(() => {
  try { return JSON.parse(props.row.decisionJson || '{}') }
  catch { return {} }
})
const fact = computed(() => evidence.value.latestReviewFacts || evidence.value)
const matchedName = computed(() => candidates.value.find(item => item.skuId === (props.edit.skuId || props.row.selectedSkuId))?.name || evidence.value.matchedName || props.row.rawName)
function status(key: string) { return t(`procurement.state.${key}`) }
</script>

<template>
  <view class="source-row" :class="{ 'source-row--issue': !['CONFIRMED', 'REJECTED'].includes(row.reviewStatus) }">
    <view class="row-head" @tap="expanded = !expanded">
      <text class="line-no">{{ row.sourceSheet ? `${row.sourceSheet} · ` : '' }}{{ row.sourceRow }}</text>
      <text class="row-name">{{ matchedName || t('procurement.unknownName') }}</text>
      <text class="state-tag">{{ status(row.reviewStatus) }}</text>
    </view>
    <view class="quantity-line">
      <text>{{ row.rawQuantity ?? '—' }} {{ row.rawUnit || t('procurement.unknownUnit') }}</text>
      <text class="arrow">→</text>
      <text class="sales-qty">{{ row.confirmedSalesQuantity ?? row.suggestedSalesQuantity ?? '—' }} {{ fact.measurement?.salesUnit || t('procurement.salesUnits') }}</text>
    </view>
    <view class="status-line">
      <text>{{ status(row.matchStatus) }}</text><text>{{ status(row.unitStatus) }}</text><text>{{ status(row.quantityStatus) }}</text>
    </view>
    <view v-if="row.excessBaseQuantity != null && Number(row.excessBaseQuantity) > 0" class="rounding-notice">
      {{ t('procurement.excess', { quantity: row.excessBaseQuantity, unit: fact.measurement?.baseUnit || '' }) }}
    </view>
    <view v-if="expanded" class="evidence">
      <text class="evidence-title">{{ t('procurement.source') }} · {{ row.sourcePosition }}</text>
      <text selectable class="raw-text">{{ row.rawText }}</text>
      <text>{{ t('procurement.evidence', { parser: evidence.parserSource || '—', version: evidence.configVersion || '—' }) }}</text>
      <text v-if="evidence.parseWarning || evidence.fallbackWarning">{{ t('procurement.sourceWarning') }}</text>
      <text v-if="fact.measurement?.baseQtyPerSalesUnit">{{ t('procurement.conversion', { sales: fact.measurement.salesUnit, quantity: fact.measurement.baseQtyPerSalesUnit, base: fact.measurement.baseUnit }) }}</text>
      <text v-if="fact.measurement">{{ t('procurement.threshold', { min: fact.measurement.minOrderQty, step: fact.measurement.stepQty }) }}</text>
    </view>
    <view v-if="!readonly && (expanded || row.reviewStatus === 'REQUIRED')" class="review-editor">
      <view class="field-row">
        <input v-model="edit.ownerName" :placeholder="t('procurement.owner')" :maxlength="120" @input="emit('change')">
        <button size="mini" :disabled="disabled" @tap="emit('save', 'SET_OWNER', false)">{{ t('procurement.saveOwner') }}</button>
      </view>
      <view class="field-row">
        <input v-model="keyword" :placeholder="t('procurement.searchPlaceholder')" @confirm="emit('search', keyword)">
        <button size="mini" :disabled="disabled || !keyword.trim()" @tap="emit('search', keyword)">{{ t('procurement.search') }}</button>
      </view>
      <view v-for="candidate in candidates" :key="candidate.skuId" class="candidate" :class="{ selected: edit.skuId === candidate.skuId }" @tap="edit.skuId = candidate.skuId; emit('change')">
        <text>{{ candidate.name }} {{ candidate.spec || '' }}</text>
        <text class="candidate-stock">{{ t('procurement.stock', { count: candidate.stock ?? '—' }) }}</text>
      </view>
      <view class="field-row">
        <input v-model="edit.salesQuantity" type="number" :placeholder="t('procurement.salesQuantity')" @input="emit('change')">
        <text class="sales-unit">{{ fact.measurement?.salesUnit || t('procurement.salesUnits') }}</text>
      </view>
      <input v-model="edit.reason" class="reason-input" :placeholder="t('procurement.reason')" :maxlength="500" @input="emit('change')">
      <view v-if="row.suggestedSalesQuantity" class="rounding-toggle" @tap="rounding = !rounding">
        <text :class="rounding ? 'i-carbon:checkbox-checked' : 'i-carbon:checkbox'" />
        <text>{{ t('procurement.acceptRounding') }}</text>
      </view>
      <view class="review-actions">
        <button size="mini" :disabled="disabled || !edit.reason" @tap="emit('save', 'REJECT', false)">{{ t('procurement.reject') }}</button>
        <button size="mini" :disabled="disabled || !edit.skuId || !edit.reason" @tap="emit('save', 'SELECT_SKU', false)">{{ t('procurement.selectSku') }}</button>
        <button v-if="row.suggestedSalesQuantity" size="mini" :disabled="disabled || !edit.skuId || !edit.reason" @tap="emit('save', 'ACCEPT_SUGGESTION', rounding)">{{ t('procurement.acceptSuggestion') }}</button>
        <button class="primary-mini" size="mini" :disabled="disabled || !edit.skuId || !edit.salesQuantity || !edit.reason" @tap="emit('save', 'CONFIRM_SALES', false)">{{ t('procurement.saveQuantity') }}</button>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.source-row { padding: 28rpx 0; border-bottom: 1rpx solid var(--app-border); }
.row-head { display: flex; gap: 14rpx; align-items: baseline; }
.line-no { color: var(--app-text-tertiary); font-size: 20rpx; flex-shrink: 0; }
.row-name { font-size: 28rpx; font-weight: 600; flex: 1; }
.state-tag { font-size: 20rpx; color: #237b5a; }
.source-row--issue .state-tag { color: #b06c1b; }
.quantity-line { display: flex; align-items: baseline; gap: 20rpx; margin: 16rpx 0 10rpx; font-size: 26rpx; font-variant-numeric: tabular-nums; }
.arrow, .status-line, .candidate-stock, .sales-unit { color: var(--app-text-secondary); font-size: 22rpx; }
.sales-qty { font-weight: 650; }
.status-line { display: flex; flex-wrap: wrap; gap: 12rpx; }
.rounding-notice { color: #b06c1b; font-size: 22rpx; margin-top: 12rpx; }
.evidence { background: var(--app-card-2); margin-top: 18rpx; padding: 20rpx; display: flex; flex-direction: column; gap: 12rpx; font-size: 22rpx; color: var(--app-text-secondary); border-left: 3rpx solid #c79c57; }
.evidence-title { font-size: 20rpx; }
.raw-text { white-space: pre-wrap; color: var(--app-text); }
.field-row { display: flex; align-items: center; gap: 14rpx; margin-top: 18rpx; }
.field-row input, .reason-input { flex: 1; min-width: 0; height: 70rpx; padding: 0 16rpx; background: var(--app-card-2); border-radius: 8rpx; font-size: 24rpx; }
.reason-input { margin-top: 18rpx; }
.candidate { border: 1rpx solid var(--app-border); padding: 18rpx; margin-top: 12rpx; display: flex; justify-content: space-between; gap: 16rpx; font-size: 24rpx; border-radius: 8rpx; }
.candidate.selected { border-color: var(--wot-color-theme, #c29452); background: var(--app-card-2); }
.rounding-toggle { display: flex; gap: 12rpx; align-items: flex-start; margin: 22rpx 0; font-size: 22rpx; color: var(--app-text-secondary); }
.review-actions { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 18rpx; }
button { margin: 0; font-size: 22rpx; color: var(--app-text); background: var(--app-card-2); }
.primary-mini { color: #fff; background: #8b6635; }
</style>
