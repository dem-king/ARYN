<script setup lang="ts">
import type { BusinessCardType } from '@/utils/message'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { businessCardRoute } from '@/utils/message'

const props = defineProps<{
  payload: Record<string, any>
  type: BusinessCardType
}>()

const { t } = useI18n()

const labelKeys: Record<BusinessCardType, string> = {
  NOTICE_CARD: 'notice',
  ORDER_CARD: 'order',
  PRODUCT_CARD: 'product',
  REFUND_CARD: 'refund',
}

const typeLabel = computed(() =>
  t(`message.businessCard.type.${labelKeys[props.type]}`),
)
const detailLabel = computed(() =>
  t(`message.businessCard.detail.${labelKeys[props.type]}`),
)

function openCard() {
  const url = businessCardRoute(props.type, props.payload)
  if (url)
    uni.navigateTo({ url })
}
</script>

<template>
  <view class="business-card" @click="openCard">
    <view class="business-card__type">
      {{ typeLabel }}
    </view>
    <view class="business-card__body">
      <image v-if="payload.image" :src="payload.image" mode="aspectFill" />
      <view class="business-card__copy">
        <text class="business-card__title">
          {{
            payload.title || payload.name || detailLabel
          }}
        </text>
        <text class="business-card__summary">
          {{
            payload.summary
              || payload.statusText
              || payload.amount
              || t('message.businessCard.tapForStatus')
          }}
        </text>
      </view>
      <wd-icon name="arrow-right" size="30rpx" color="#b6bfbc" />
    </view>
  </view>
</template>

<style scoped lang="scss">
.business-card {
  width: 470rpx;
  overflow: hidden;
  border: 1rpx solid #eceff0;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 12rpx 30rpx rgb(17 27 38 / 6%);
  &__type {
    padding: 12rpx 20rpx;
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
    font-size: 18rpx;
    font-weight: 700;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    background: #f2f4f4;
  }
  &__body {
    display: flex;
    align-items: center;
    gap: 16rpx;
    padding: 20rpx;
  }
  image {
    width: 86rpx;
    height: 86rpx;
    flex: none;
    border-radius: 14rpx;
    background: #f2f4f4;
  }
  &__copy {
    min-width: 0;
    flex: 1;
  }
  &__title,
  &__summary {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &__title {
    color: #1f2a2e;
    font-size: 26rpx;
    font-weight: 700;
  }
  &__summary {
    margin-top: 8rpx;
    color: #7b8783;
    font-size: 21rpx;
  }
}
</style>
