<script setup lang="ts">
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
    class="fixed bottom-0 left-0 right-0 flex justify-between border-t border-t-[rgba(255,255,255,0.33)] bg-white p-20rpx pb-[max(env(safe-area-inset-bottom),16rpx)]"
  >
    <view class="flex text-center text-20rpx">
      <view class="ml-20rpx mr-30rpx" @click="toHome">
        <wd-icon name="home" size="40rpx" />
        <view class="text">
          首页
        </view>
      </view>
      <button
        class="kf-btn mx-30rpx"
        :open-type="useNativeContact ? 'contact' : undefined"
        @click="customerService"
      >
        <wd-icon name="service" size="40rpx" />
        <view class="text">
          客服
        </view>
      </button>
      <view class="mx-30rpx" @click="toCart">
        <wd-badge :model-value="shoppingCartCount">
          <wd-icon name="cart" size="40rpx" />
          <view class="text">
            购物车
          </view>
        </wd-badge>
      </view>
    </view>
    <view>
      <view class="box-border w-full flex items-center justify-between">
        <view
          class="h-68rpx w-200rpx rounded-l-[38rpx] text-center text-28rpx text-white font-500 leading-[68rpx] bg-secondary!"
          @click="openSkuPopup(2)"
        >
          加入购物车
        </view>
        <view
          class="h-68rpx w-200rpx rounded-r-[38rpx] text-center text-28rpx text-white font-500 leading-[68rpx] bg-primary!"
          @click="openSkuPopup(3)"
        >
          立即购买
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
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
