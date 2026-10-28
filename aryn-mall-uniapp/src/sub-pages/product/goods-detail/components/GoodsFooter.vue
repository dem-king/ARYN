<script setup lang="ts">
import { useI18n } from 'vue-i18n'

interface Props {
  shoppingCartCount: number
}

interface Emits {
  (e: 'toHome'): void
  (e: 'toCart'): void
  (e: 'customerService'): void
  (e: 'openSkuPopup', value: number): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()
const { t } = useI18n()
const useNativeContact
  = import.meta.env.VITE_CUSTOMER_SERVICE_MODE === 'NATIVE'

function toHome() {
  emit('toHome')
}

function toCart() {
  emit('toCart')
}

function openSkuPopup(value: number) {
  emit('openSkuPopup', value)
}

function customerService() {
  if (!useNativeContact)
    emit('customerService')
}
</script>

<template>
  <view
    class="goods-footer fixed bottom-0 left-0 right-0 flex justify-between bg-white p-20rpx pb-[max(env(safe-area-inset-bottom),16rpx)] dark:bg-[var(--wot-dark-background2)]"
  >
    <view class="flex flex-shrink-0 text-center text-20rpx">
      <view class="ml-20rpx mr-30rpx" @click="toHome">
        <wd-icon name="home" size="40rpx" />
        <view class="text">
          {{ t('product.home') }}
        </view>
      </view>
      <button
        class="kf-btn mx-30rpx"
        :open-type="useNativeContact ? 'contact' : undefined"
        @click="customerService"
      >
        <wd-icon name="service" size="40rpx" />
        <view class="text">
          {{ t('product.support') }}
        </view>
      </button>
      <view class="mx-30rpx" @click="toCart">
        <wd-badge :model-value="shoppingCartCount">
          <wd-icon name="cart" size="40rpx" />
          <view class="text">
            {{ t('product.cart') }}
          </view>
        </wd-badge>
      </view>
    </view>
    <view class="ml-20rpx flex-1">
      <view
        class="goods-footer__action"
        hover-class="goods-footer__action--pressed"
        @click="openSkuPopup(2)"
      >
        {{ t('product.addToCart') }}
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.goods-footer {
  border-top: 1rpx solid #f0f1f3;
  box-shadow: 0 -6rpx 24rpx rgb(22 34 51 / 6%);
}

.goods-footer__action {
  height: 76rpx;
  border-radius: 999rpx;
  background: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  color: #fff;
  font-size: 28rpx;
  font-weight: 500;
  line-height: 76rpx;
  text-align: center;
  transition: transform 120ms ease, opacity 120ms ease;

  &--pressed {
    opacity: 0.88;
    transform: scale(0.99);
  }
}

.kf-btn {
  background: none;
  border: none;
  line-height: normal;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 20rpx;
  color: inherit;

  &::after {
    border: none;
  }
}
</style>
