<script setup lang="ts">
import type { BusinessCardType } from '@/utils/message'
import { businessCardRoute } from '@/utils/message'

const props = defineProps<{
  payload: Record<string, any>
  type: BusinessCardType
}>()

const labels: Record<BusinessCardType, string> = {
  NOTICE_CARD: '通知',
  ORDER_CARD: '订单',
  PRODUCT_CARD: '商品',
  REFUND_CARD: '退款',
}

function openCard() {
  const url = businessCardRoute(props.type, props.payload)
  if (url)
    uni.navigateTo({ url })
}
</script>

<template>
  <view class="business-card" @click="openCard">
    <view class="business-card__type">
      {{ labels[type] }} CARD
    </view>
    <view class="business-card__body">
      <image v-if="payload.image" :src="payload.image" mode="aspectFill" />
      <view class="business-card__copy">
        <text class="business-card__title">
          {{
            payload.title || payload.name || `${labels[type]}详情`
          }}
        </text>
        <text class="business-card__summary">
          {{
            payload.summary
              || payload.statusText
              || payload.amount
              || "点击查看最新业务状态"
          }}
        </text>
      </view>
      <wd-icon name="arrow-right" size="30rpx" color="#7b928d" />
    </view>
  </view>
</template>

<style scoped lang="scss">
.business-card {
  width: 470rpx;
  overflow: hidden;
  border: 1rpx solid #d8e8e4;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 12rpx 30rpx rgb(28 74 67 / 8%);
  &__type {
    padding: 12rpx 20rpx;
    color: #0f766e;
    font-size: 18rpx;
    font-weight: 700;
    letter-spacing: 0.12em;
    background: #e7f5f1;
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
    background: #eef4f2;
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
    color: #173f4f;
    font-size: 26rpx;
    font-weight: 700;
  }
  &__summary {
    margin-top: 8rpx;
    color: #82958f;
    font-size: 21rpx;
  }
}
</style>
