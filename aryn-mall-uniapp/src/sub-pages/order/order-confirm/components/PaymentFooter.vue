<script setup lang="ts">
interface Props {
  paymentPrice: number
  /** 支付方式：''=在线支付；'3'=货到付款（仅商城配送/内部配送可选） */
  paymentWay?: string
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
        <!-- 货到付款下单后直接进订单详情，不拉起收银台，按钮语义是提交订单而非支付 -->
        <wd-button type="error" size="medium" @click="toPay">
          {{ paymentWay === '3' ? '提交订单' : '立即支付' }}
        </wd-button>
      </view>
    </view>
  </view>
</template>
