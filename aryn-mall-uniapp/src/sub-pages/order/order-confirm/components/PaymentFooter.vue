<script setup lang="ts">
import { useI18n } from 'vue-i18n'

interface Props {
  paymentPrice: number
  /** 支付方式：''=在线支付；'3'=货到付款（仅商城配送/内部配送可选） */
  paymentWay?: string
  /** 已优惠合计（活动+会员+券+积分口径由父组件汇总），0/缺省不展示 */
  totalDiscount?: number
}

withDefaults(defineProps<Props>(), {
  paymentWay: '',
  totalDiscount: 0,
})

const emit = defineEmits<{
  toPay: []
}>()

const { t } = useI18n()

function toPay() {
  emit('toPay')
}
</script>

<template>
  <view class="pay-footer dark:bg-[var(--wot-dark-background)]">
    <view class="pay-footer__left">
      <view class="pay-footer__total">
        <text class="pay-footer__total-label">
          {{ t('checkout.footer.total') }}
        </text>
        <wd-text
          :text="paymentPrice"
          size="40rpx"
          mode="price"
          prefix="￥"
          color="var(--wot-color-theme-primary, #ff2e4d)"
        />
      </view>
      <text v-if="totalDiscount > 0" class="pay-footer__saving">
        {{ t('checkout.footer.saved', { amount: totalDiscount }) }}
      </text>
    </view>
    <!-- 货到付款下单后直接进订单详情，不拉起收银台，按钮语义是提交订单而非支付 -->
    <wd-button
      type="error"
      custom-style="height: 80rpx; padding: 0 52rpx; border-radius: 999rpx; font-size: 30rpx; font-weight: 600; border: none; background: var(--wot-color-theme-primary, #ff2e4d);"
      @click="toPay"
    >
      {{ paymentWay === '3' ? t('checkout.payment.placeOrder') : t('checkout.payment.payNow') }}
    </wd-button>
  </view>
</template>

<style lang="scss" scoped>
/*
 * 底部结算栏：合计+主题色大价格在左（副行显示已优惠），
 * 胶囊主按钮在右；高度同步到宿主页的 footer 占位。
 */
.pay-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--app-card);
  box-shadow: 0 -4rpx 20rpx rgba(29, 33, 41, 0.04);
}

.pay-footer__left {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.pay-footer__total {
  display: flex;
  align-items: baseline;
  font-weight: 600;
}

.pay-footer__total-label {
  margin-right: 8rpx;
  font-size: 26rpx;
  color: var(--app-text);
}

.pay-footer__saving {
  margin-top: 2rpx;
  font-size: 22rpx;
  color: #ff8f1f;
}
</style>
