<script setup lang="ts">
import type { GoodsParamRow } from '@/utils/goods-params'
import type { GoodsProfile } from '@/utils/goods-profile'
import { computed } from 'vue'
import { useI18n } from '@/sub-pages/locales'
import { OVERLAY_Z_INDEX } from '@/utils/overlay'

interface Props {
  modelValue: boolean
  /** 基础参数与深层档案在同一个弹层分组展示，主页面不再铺一张长参数表。 */
  rows?: GoodsParamRow[]
  profile?: GoodsProfile | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const props = withDefaults(defineProps<Props>(), {
  rows: () => [],
  profile: null,
})
const emit = defineEmits<Emits>()
const { t } = useI18n()

const show = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})
const hasNutrition = computed(() => Boolean(props.profile?.nutrition?.length))
const hasContent = computed(() => props.rows.length > 0 || Boolean(props.profile))
const headerSummary = computed(() => props.profile
  ? t('product.profile.summary')
  : t('product.params.title'))

function rowLabel(row: GoodsParamRow) {
  return row.label ?? t(`product.params.${row.key}`)
}

function rowValue(row: GoodsParamRow) {
  return row.valueKey ? t(row.valueKey, row.args ?? {}) : row.value
}
</script>

<template>
  <view
    v-if="show && hasContent"
    class="profile-overlay"
    :style="{ zIndex: OVERLAY_Z_INDEX.sheet }"
    @click="show = false"
    @touchmove.stop.prevent
  >
    <view class="profile-sheet" @click.stop>
      <view class="profile-sheet__head">
        <view class="profile-sheet__heading">
          <text class="profile-sheet__title">
            {{ t('product.profile.title') }}
          </text>
          <text class="profile-sheet__meta">
            {{ headerSummary }}
          </text>
        </view>
        <view class="profile-sheet__close" role="button" :aria-label="t('product.profile.close')" @click="show = false">
          <wd-icon name="close" :size="18" color="var(--app-text-3)" />
        </view>
      </view>

      <scroll-view scroll-y class="profile-sheet__scroll">
        <view class="profile-sheet__body">
          <view v-if="rows.length" class="profile-block">
            <text class="profile-block__label">
              {{ t('product.params.title') }}
            </text>
            <view class="profile-params">
              <view v-for="row in rows" :key="row.key" class="profile-row">
                <text class="profile-row__label">{{ rowLabel(row) }}</text>
                <text class="profile-row__value">{{ rowValue(row) }}</text>
              </view>
            </view>
          </view>

          <view v-if="profile?.ingredients" class="profile-block">
            <text class="profile-block__label">
              {{ t('product.profile.ingredients') }}
            </text>
            <text class="profile-block__text">
              {{ profile.ingredients }}
            </text>
          </view>

          <view v-if="hasNutrition" class="profile-block">
            <view class="profile-block__title-row">
              <text class="profile-block__label">
                {{ t('product.profile.nutrition') }}
              </text>
              <text class="profile-block__hint">
                {{ t('product.profile.nutritionPer') }}
              </text>
            </view>
            <view class="nutrition-table">
              <view class="nutrition-row nutrition-row--head">
                <text class="nutrition-row__name">
                  {{ t('product.profile.nutritionName') }}
                </text>
                <text class="nutrition-row__amount">
                  {{ t('product.profile.nutritionAmount') }}
                </text>
                <text class="nutrition-row__nrv">
                  {{ t('product.profile.nutritionNrv') }}
                </text>
              </view>
              <view
                v-for="(item, index) in profile?.nutrition"
                :key="`${item.name}-${index}`"
                class="nutrition-row"
              >
                <text class="nutrition-row__name">
                  {{ item.name }}
                </text>
                <text class="nutrition-row__amount">
                  {{ `${item.amount || ''}${item.unit || ''}` }}
                </text>
                <text class="nutrition-row__nrv">
                  {{ item.nrv || '—' }}
                </text>
              </view>
            </view>
          </view>

          <view v-if="profile?.allergens || profile?.standard" class="profile-block">
            <view v-if="profile?.allergens" class="profile-row">
              <text class="profile-row__label">
                {{ t('product.profile.allergens') }}
              </text>
              <text class="profile-row__value">
                {{ profile.allergens }}
              </text>
            </view>
            <view v-if="profile?.standard" class="profile-row">
              <text class="profile-row__label">
                {{ t('product.profile.standard') }}
              </text>
              <text class="profile-row__value">
                {{ profile.standard }}
              </text>
            </view>
          </view>

          <view v-if="profile?.certifications?.length" class="profile-block">
            <text class="profile-block__label">
              {{ t('product.profile.certifications') }}
            </text>
            <view class="profile-tags">
              <text
                v-for="item in profile?.certifications"
                :key="item"
                class="profile-tag"
              >
                {{ item }}
              </text>
            </view>
          </view>
        </view>
      </scroll-view>

      <view class="profile-sheet__foot">
        {{ t('product.profile.footnote') }}
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.profile-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgb(17 17 17 / 45%);
}

.profile-sheet {
  display: flex;
  width: 100%;
  flex-direction: column;
  max-height: 76vh;
  overflow: hidden;
  border-radius: 24rpx 24rpx 0 0;
  padding: 28rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));
  background: var(--app-card);

  &__head {
    display: flex;
    flex: none;
    align-items: flex-start;
    justify-content: space-between;
    gap: 20rpx;
    padding-bottom: 20rpx;
  }

  &__heading {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 4rpx;
  }

  &__title {
    color: var(--app-text);
    font-size: 32rpx;
    font-weight: 700;
    line-height: 1.35;
  }

  &__meta {
    color: var(--app-text-3);
    font-size: 22rpx;
    line-height: 1.4;
  }

  &__close {
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

  &__scroll {
    min-height: 0;
    max-height: 60vh;
  }

  &__body {
    padding-right: 4rpx;
  }

  &__foot {
    flex: none;
    padding-top: 16rpx;
    color: var(--app-text-3);
    font-size: 22rpx;
    line-height: 1.4;
  }
}

.profile-block {
  padding: 18rpx 0;
  border-top: 1rpx solid var(--app-border);

  &:first-child {
    border-top: 0;
  }

  &__label {
    display: block;
    color: var(--app-text-3);
    font-size: 24rpx;
    line-height: 1.5;
  }

  &__text {
    display: block;
    margin-top: 8rpx;
    color: var(--app-text);
    font-size: 26rpx;
    line-height: 1.65;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  &__title-row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16rpx;
  }

  &__hint {
    flex: none;
    color: var(--app-text-3);
    font-size: 22rpx;
  }
}

.nutrition-table {
  margin-top: 12rpx;
  border: 1rpx solid var(--app-border);
  border-radius: 16rpx;
  overflow: hidden;
}

.nutrition-row {
  display: flex;
  align-items: center;
  padding: 12rpx 16rpx;
  border-top: 1rpx solid var(--app-border);

  &--head {
    border-top: 0;
    background: var(--app-card-2);
    color: var(--app-text-3);
    font-size: 22rpx;
  }

  &__name {
    flex: 1;
    min-width: 0;
    color: var(--app-text);
    font-size: 24rpx;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }

  &__amount,
  &__nrv {
    flex: none;
    text-align: right;
    color: var(--app-text);
    font-size: 24rpx;
    line-height: 1.5;
  }

  &__amount {
    width: 32%;
  }

  &__nrv {
    width: 18%;
    color: var(--app-text-3);
  }
}

.nutrition-row--head .nutrition-row__name,
.nutrition-row--head .nutrition-row__amount,
.nutrition-row--head .nutrition-row__nrv {
  color: var(--app-text-3);
  font-size: 22rpx;
}

.profile-params {
  margin-top: 8rpx;
}

.profile-row {
  display: flex;
  align-items: baseline;
  gap: 20rpx;
  padding: 10rpx 0;

  &__label {
    width: 26%;
    flex: none;
    color: var(--app-text-3);
    font-size: 24rpx;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }

  &__value {
    flex: 1;
    min-width: 0;
    color: var(--app-text);
    font-size: 24rpx;
    line-height: 1.5;
    overflow-wrap: anywhere;
    white-space: pre-wrap;
  }
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 10rpx;
}

.profile-tag {
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  background: var(--app-card-2);
  color: var(--app-text-2);
  font-size: 22rpx;
  line-height: 1.5;
}
</style>
