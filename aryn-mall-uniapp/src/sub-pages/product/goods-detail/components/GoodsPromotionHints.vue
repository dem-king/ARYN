<script setup lang="ts">
import type { GoodsPromotionHints } from '@/api/promotion/goods-hints'
import { computed, ref } from 'vue'
import { useI18n } from '@/sub-pages/locales'
import { displayFixedDiscountName, formatHintAmount, groupGiftOffers, resolveGiftProgress } from '@/utils/goods-hints'
import { OVERLAY_Z_INDEX } from '@/utils/overlay'

interface TierView {
  key: string
  threshold: string
  benefit: string
}

interface VolumeView {
  id: string
  name: string
  tiers: TierView[]
}

const props = withDefaults(defineProps<{
  hints: GoodsPromotionHints
  /** 当前选择数量：只用于本地估算差额，不参与结算判定 */
  quantity?: number
  /** 当前 SKU 售价（商品基价）：与满赠门槛「优惠前金额」同口径 */
  unitPrice?: number
  /** 当前 SKU 销售单位（箱/盒/件），用于差额估算 */
  unit?: string
}>(), {
  quantity: 0,
  unitPrice: 0,
  unit: '',
})

const { t } = useI18n()
const detailVisible = ref(false)
const giftGroups = computed(() => groupGiftOffers(props.hints.gifts))

const volumeGroups = computed<VolumeView[]>(() => (props.hints.volumes ?? [])
  .map((volume) => {
    const activityName = displayFixedDiscountName(volume?.name)
    const name = activityName === '海员整船满减'
      ? t('product.orderOffers.wholeVesselDiscount')
      : activityName
    const tiers = (volume?.tiers ?? [])
      .map<TierView | null>((tier) => {
        const thresholds = [
          tier?.minAmount == null ? '' : t('product.orderOffers.amount', { amount: formatHintAmount(tier.minAmount) }),
          tier?.minQty == null ? '' : t('product.orderOffers.quantity', { count: Math.floor(Number(tier.minQty)) }),
        ].filter(Boolean)
        const discount = Number(tier?.discountAmount)
        if (!thresholds.length || !Number.isFinite(discount) || discount <= 0)
          return null
        return {
          key: `${volume?.activityId ?? ''}:${thresholds.join('-')}`,
          threshold: thresholds.join(t('product.orderOffers.and')),
          benefit: t('product.orderOffers.discount', { amount: formatHintAmount(discount) }),
        }
      })
      .filter((tier): tier is TierView => tier !== null)
    return tiers.length ? { id: String(volume?.activityId ?? ''), name, tiers } : null
  })
  .filter((group): group is VolumeView => group !== null))

const giftTierCount = computed(() =>
  giftGroups.value.reduce((count, group) => count + group.rows.length, 0))

/** 主页面只露一个可扫读利益点，完整活动名、全部档位和结算脚注进弹层。 */
const primarySummary = computed(() => {
  const volume = volumeGroups.value[0]
  const firstTier = volume?.tiers[0]
  if (volume && firstTier)
    return `${volume.name} · ${firstTier.threshold} ${firstTier.benefit}`
  const gift = giftGroups.value[0]
  if (gift) {
    const prefix = gift.name || t('product.orderOffers.title')
    return `${prefix} · ${t('product.orderOffers.tierCount', { count: giftTierCount.value })}`
  }
  return ''
})

const quantityText = computed(() => Math.max(0, Math.floor(Number(props.quantity) || 0)))
const unitText = computed(() => (props.unit || '').trim() || t('product.ladderFallbackUnit'))
const estimatedAmount = computed(() => {
  const price = Number(props.unitPrice)
  if (!Number.isFinite(price) || price <= 0 || quantityText.value <= 0)
    return 0
  return Math.round(quantityText.value * price * 100) / 100
})

/** 满赠进度仍在弹层内按当前 SKU 本地估算，不把估算结果冒充订单结算结果。 */
const giftProgress = computed(() => {
  const view = resolveGiftProgress(estimatedAmount.value, giftGroups.value[0]?.rows ?? [])
  if (!view)
    return null
  const estimateText = t('product.orderOffers.estimate', {
    count: quantityText.value,
    unit: unitText.value,
    amount: formatHintAmount(estimatedAmount.value),
  })
  if (view.mode === 'reached') {
    return { estimateText, mode: 'reached' as const, percent: 0, gapText: t('product.orderOffers.reached') }
  }
  if (view.mode === 'compact') {
    return {
      estimateText,
      mode: 'compact' as const,
      percent: 0,
      gapText: t('product.orderOffers.gapCompact', { amount: formatHintAmount(view.threshold) }),
    }
  }
  return {
    estimateText,
    mode: 'progress' as const,
    percent: view.percent,
    gapText: t(view.firstTier ? 'product.orderOffers.gapFirst' : 'product.orderOffers.gapNext', {
      amount: formatHintAmount(view.gap),
    }),
  }
})
</script>

<template>
  <view
    v-if="volumeGroups.length || giftGroups.length"
    class="offer-entry"
    hover-class="offer-entry--pressed"
    role="button"
    :aria-label="t('product.orderOffers.title')"
    @click="detailVisible = true"
  >
    <view class="offer-entry__icon-wrap">
      <text class="offer-entry__icon i-carbon:gift" />
    </view>
    <view class="offer-entry__content">
      <text class="offer-entry__title">{{ t('product.orderOffers.title') }}</text>
      <text class="offer-entry__summary">{{ primarySummary }}</text>
    </view>
    <wd-icon name="arrow-right" size="16px" color="var(--app-text-3)" />
  </view>

  <wd-popup
    v-model="detailVisible"
    position="bottom"
    :safe-area-inset-bottom="true"
    :z-index="OVERLAY_Z_INDEX.sheet"
    custom-style="border-radius: 28rpx 28rpx 0 0; overflow: hidden;"
  >
    <view class="offer-sheet">
      <view class="offer-sheet__head">
        <view class="offer-sheet__heading">
          <text class="offer-sheet__title">{{ t('product.orderOffers.title') }}</text>
          <text class="offer-sheet__subtitle">{{ primarySummary }}</text>
        </view>
        <view class="offer-sheet__close" role="button" @click="detailVisible = false">
          <wd-icon name="close" size="18px" color="var(--app-text-3)" />
        </view>
      </view>

      <scroll-view scroll-y class="offer-sheet__scroll">
        <view class="offer-sheet__body">
          <view v-if="giftProgress" class="offer-progress">
            <view class="offer-progress__head">
              <text class="offer-progress__estimate">{{ giftProgress.estimateText }}</text>
              <text class="offer-progress__gap">{{ giftProgress.gapText }}</text>
            </view>
            <view v-if="giftProgress.mode === 'progress'" class="offer-progress__track">
              <view class="offer-progress__fill" :style="{ width: `${giftProgress.percent}%` }" />
            </view>
          </view>

          <view v-for="group in giftGroups" :key="group.key" class="offer-group">
            <view v-if="group.name" class="offer-group__title">{{ group.name }}</view>
            <view v-for="row in group.rows" :key="row.activityId || row.thresholdAmount" class="offer-row">
              <text class="offer-row__threshold">
                {{ t('product.orderOffers.amount', { amount: formatHintAmount(row.thresholdAmount) }) }}
              </text>
              <view class="offer-row__body">
                <text v-for="(gift, index) in row.gifts" :key="`${gift.name}-${index}`">
                  <text v-if="index > 0"> · </text>{{ gift.name }}<text class="offer-qty">×{{ gift.quantity }}</text>
                </text>
              </view>
            </view>
          </view>

          <view v-for="group in volumeGroups" :key="group.id" class="offer-group">
            <view v-if="group.name" class="offer-group__title">{{ group.name }}</view>
            <view v-for="tier in group.tiers" :key="tier.key" class="offer-row">
              <text class="offer-row__threshold">{{ tier.threshold }}</text>
              <view class="offer-row__body offer-row__body--benefit">{{ tier.benefit }}</view>
            </view>
          </view>

          <view v-if="giftGroups.length" class="offer-footnote">
            {{ t('product.orderOffers.giftCheckout') }}
          </view>
          <view v-if="volumeGroups.length" class="offer-footnote">
            {{ t('product.orderOffers.fixedDiscountCheckout') }}
          </view>
        </view>
      </scroll-view>
    </view>
  </wd-popup>
</template>

<style scoped>
.offer-entry {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 68px;
  padding: 14px 16px;
  background: var(--app-card);
  transition: background 120ms ease, opacity 120ms ease;
}

.offer-entry--pressed {
  background: var(--app-card-2);
  opacity: 0.82;
}

.offer-entry__icon-wrap {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: #fff0e8;
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #e6452e));
}

.offer-entry__icon {
  width: 19px;
  height: 19px;
  font-size: 19px;
}

.offer-entry__content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.offer-entry__title {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.35;
}

.offer-entry__summary {
  overflow: hidden;
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #e6452e));
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.offer-sheet {
  display: flex;
  flex-direction: column;
  max-height: 76vh;
  padding: 28rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
  background: var(--app-card);
}

.offer-sheet__head {
  display: flex;
  flex: none;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  padding-bottom: 20rpx;
}

.offer-sheet__heading {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 6rpx;
}

.offer-sheet__title {
  color: var(--app-text);
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.35;
}

.offer-sheet__subtitle {
  overflow: hidden;
  color: var(--app-text-3);
  font-size: 22rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.offer-sheet__close {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  height: 48rpx;
  margin-top: -4rpx;
  border-radius: 50%;
  background: var(--app-card-2);
}

.offer-sheet__scroll {
  min-height: 0;
  max-height: 62vh;
}

.offer-sheet__body {
  padding: 4rpx 4rpx 16rpx 0;
}

.offer-progress {
  margin-bottom: 16rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: var(--app-card-2);
}

.offer-progress__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12rpx;
  font-size: 24rpx;
  line-height: 1.5;
}

.offer-progress__estimate {
  min-width: 0;
  color: var(--app-text-2);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.offer-progress__gap {
  flex: none;
  max-width: 56%;
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #e6452e));
  text-align: right;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.offer-progress__track {
  height: 8rpx;
  margin-top: 12rpx;
  overflow: hidden;
  border-radius: 999rpx;
  background: var(--app-border);
}

.offer-progress__fill {
  height: 100%;
  border-radius: 999rpx;
  background: var(--theme-color-primary, var(--wot-color-theme-primary, #e6452e));
}

.offer-group {
  padding: 20rpx 0;
  border-top: 1rpx solid var(--app-border);
}

.offer-group:first-child {
  border-top: 0;
}

.offer-group__title {
  display: block;
  margin-bottom: 8rpx;
  color: var(--app-text);
  font-size: 27rpx;
  font-weight: 600;
  line-height: 1.4;
}

.offer-row {
  display: flex;
  align-items: baseline;
  gap: 20rpx;
  padding: 10rpx 0;
}

.offer-row__threshold {
  width: 42%;
  flex: none;
  color: var(--app-text-2);
  font-size: 24rpx;
  line-height: 1.5;
  font-variant-numeric: tabular-nums;
}

.offer-row__body {
  min-width: 0;
  flex: 1;
  color: var(--app-text);
  font-size: 26rpx;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.offer-row__body--benefit {
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #e6452e));
  font-variant-numeric: tabular-nums;
}

.offer-qty {
  margin-left: 8rpx;
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #e6452e));
  font-weight: 600;
}

.offer-footnote {
  margin-top: 12rpx;
  color: var(--app-text-3);
  font-size: 22rpx;
  line-height: 1.55;
}

.wot-theme-dark .offer-entry__icon-wrap {
  background: #382823;
}
</style>
