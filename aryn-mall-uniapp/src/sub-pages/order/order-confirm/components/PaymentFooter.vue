<script setup lang="ts">
interface Props {
  paymentPrice: number
}

defineProps<Props>()

const emit = defineEmits<{
  toPay: []
}>()

function toPay() {
  emit('toPay')
}
</script>

<template>
  <view class="fixed bottom-0 left-0 right-0 bg-white p-20rpx dark:bg-[var(--wot-dark-background)]">
    <view class="flex flex-row items-center justify-between">
      <view class="left">
        <wd-text size="18px" :text="paymentPrice" color="red" mode="price" prefix="￥" />
      </view>
      <view class="right">
        <wd-button type="error" size="medium" @click="toPay">
          立即支付
        </wd-button>
      </view>
    </view>
  </view>
</template>
