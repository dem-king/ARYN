<script setup lang="ts">
import { useI18n } from 'vue-i18n'

interface Address {
  id: string
  detailAddress: string
  recipientName: string
  telephone: string
  provinceName: string
  cityName: string
  areaName: string
}

interface Props {
  selectedAddress: Address | null
}

defineProps<Props>()

const emit = defineEmits<{
  toAddress: []
}>()

const { t } = useI18n()

function toAddress() {
  emit('toAddress')
}
</script>

<template>
  <view class="address-card" @click="toAddress">
    <view class="address-card__icon">
      <wd-icon
        name="location"
        size="20px"
        color="var(--wot-color-theme-primary, #ff2e4d)"
      />
    </view>
    <view v-if="selectedAddress" class="address-card__body">
      <view class="address-card__line1">
        <text class="address-card__name">
          {{ selectedAddress.recipientName }}
        </text>
        <text class="address-card__phone">
          {{ selectedAddress.telephone }}
        </text>
      </view>
      <text class="address-card__detail">
        {{ selectedAddress.provinceName }} {{ selectedAddress.cityName }}
        {{ selectedAddress.areaName }} {{ selectedAddress.detailAddress }}
      </text>
    </view>
    <view v-else class="address-card__body">
      <text class="address-card__empty">
        {{ t('checkout.address.select') }}
      </text>
    </view>
    <text class="i-carbon:chevron-right address-card__arrow" />
  </view>
</template>

<style scoped lang="scss">
/*
 * 收货地址卡：主流结算页样式——左侧浅主题底圆形图标、
 * 姓名/电话一行加粗、详细地址灰色两行截断，整卡可点。
 */
.address-card {
  display: flex;
  align-items: center;
  margin: 20rpx 20rpx 0;
  padding: 28rpx 24rpx;
  border-radius: 20rpx;
  background: var(--app-card);
}

.address-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  background: var(--wot-color-theme-primary-soft, rgba(255, 34, 55, 0.09));
}

.address-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  margin: 0 20rpx;
}

.address-card__line1 {
  display: flex;
  align-items: baseline;
}

.address-card__name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--app-text);
  line-height: 42rpx;
}

.address-card__phone {
  margin-left: 16rpx;
  font-size: 26rpx;
  color: var(--app-text-3);
  line-height: 36rpx;
}

.address-card__detail {
  margin-top: 6rpx;
  overflow: hidden;
  font-size: 26rpx;
  color: var(--app-text-2);
  line-height: 38rpx;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.address-card__empty {
  font-size: 28rpx;
  color: var(--app-text-3);
  line-height: 40rpx;
}

.address-card__arrow {
  flex-shrink: 0;
  font-size: 28rpx;
  color: var(--app-text-3);
}
</style>
