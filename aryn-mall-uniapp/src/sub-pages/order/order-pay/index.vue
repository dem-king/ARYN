<script setup lang="ts">
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getByOrderNo, orderPrepay } from '@/api/order/orderInfo'
import { useGlobalLoading } from '@/composables/useGlobalLoading'
import { prepay } from '@/sub-pages/utils/pay'
import { DEFAULT_PAY_TIMEOUT_MINUTES } from '@/sub-pages/utils/pay-timeout'

definePage({
  name: 'order-pay',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '收银台',
  },
})
type PaymentType = '1' | '2' | '3' // 1: 微信, 2: 支付宝, 3: 余额

type TradeType = 'WX_JSAPI_PAY' | 'WX_H5_PAY' | 'WX_APP_PAY' | 'ALI_JSAPI_PAY' | 'ALI_H5_PAY' | 'ALI_APP_PAY'

interface PrepayParams {
  orderNo: string
  paymentType: PaymentType
  tradeType: TradeType
  returnUrl?: string
  quitUrl?: string
}

const loading = ref(true)
let initialPaymentType: PaymentType = '1'
// 支付宝小程序只展示支付宝选项，默认值必须与展示保持一致
// #ifdef MP-ALIPAY
initialPaymentType = '2'
// #endif
const paymentType = ref<PaymentType>(initialPaymentType)
const paying = ref(false)
let disposed = false
const { t } = useI18n()
const globalLoading = useGlobalLoading()
const state = reactive<{ order: any }>({
  order: {},
})
const paymentPrice = ref(0)
const payOrderNo = ref('')

const amountText = computed(() => {
  const val = Number(paymentPrice.value || 0)
  return val.toFixed(2)
})
// 与 pay-timeout 工具同口径：整小时按小时展示，缺省回落默认分钟数
const payTimeoutText = computed(() => {
  const value = Number(state.order?.payTimeoutMinutes)
  const minutes = Number.isFinite(value) && value > 0 ? value : DEFAULT_PAY_TIMEOUT_MINUTES
  return minutes >= 60 && minutes % 60 === 0
    ? t('order.time.hours', { hours: minutes / 60 })
    : t('order.time.minutes', { minutes })
})
onLoad((options) => {
  getOrder(options?.orderNo)
})

onUnload(() => {
  disposed = true
})

function getOrder(orderNo: string) {
  getByOrderNo(orderNo).then((response) => {
    loading.value = false
    state.order = response
    paymentPrice.value = response.paymentPrice
    payOrderNo.value = response.orderNo
  })
}
async function onPrepay() {
  // 防止连点或支付未结束时重复唤起收银台
  if (paying.value || !payOrderNo.value) return
  paying.value = true
  globalLoading.loading(t('common.loading'))
  // 预支付
  const prepayParams = buildPrepayParams(payOrderNo.value, paymentType.value)
  try {
    const response = await orderPrepay(prepayParams)
    if (disposed) return
    const orderNo = response.orderNo
    let payParams = response.payParams
    // #ifdef H5
    if (paymentType.value === '1') {
      payParams
        = `${payParams
        }&redirect_url=${encodeURIComponent(`${window.location.origin}/sub-pages/order/pay-result/index?orderNo=${orderNo}`)}`
    }
    // #endif
    await prepay(payParams, paymentPrice.value, `/sub-pages/order/pay-result/index?orderNo=${orderNo}`, paymentType.value)
  }
  catch (error) {
    // 请求层已统一提示业务错误；这里兜住租户校验等本地异常，避免未处理拒绝
    console.error('发起支付失败:', error)
  }
  finally {
    paying.value = false
    globalLoading.close()
  }
}
function buildPrepayParams(orderNo: string, paymentType: PaymentType): PrepayParams {
  const params: PrepayParams = {
    orderNo,
    paymentType,
    tradeType: 'WX_JSAPI_PAY', // 默认，后续覆盖
  }

  switch (paymentType) {
    case '1': // 微信支付
      // #ifdef MP
      params.tradeType = 'WX_JSAPI_PAY'
      // #endif
      // #ifdef H5
      params.tradeType = 'WX_H5_PAY'
      // #endif
      // #ifdef APP-PLUS
      params.tradeType = 'WX_APP_PAY'
      // #endif
      break

    case '2': // 支付宝支付
      // #ifdef MP
      params.tradeType = 'ALI_JSAPI_PAY'
      // #endif
      // #ifdef H5
      params.tradeType = 'ALI_H5_PAY'
      params.returnUrl = `${window.location.origin}/sub-pages/order/pay-result/index?orderNo=${orderNo}`
      params.quitUrl = `${window.location.origin}/sub-pages/order/order-pay/index?orderNo=${orderNo}`
      // #endif
      // #ifdef APP-PLUS
      params.tradeType = 'ALI_APP_PAY'
      // #endif
      break
    default:
      throw new Error('Unsupported payment type')
  }

  return params
}
</script>

<template>
  <hr-navbar :title="t('order.pay.title')" />
  <view v-if="!loading" class="cashier-container">
    <view class="cashier-header">
      <view class="cashier-amount">
        <text class="amount-symbol">
          ￥
        </text>
        <text class="amount-value">
          {{ amountText }}
        </text>
      </view>
      <view class="cashier-tip">
        {{ t('order.pay.timeout', { time: payTimeoutText }) }}
      </view>
    </view>

    <view class="cashier-card">
      <view class="card-title">
        {{ t('order.pay.selectPayment') }}
      </view>
      <wd-radio-group v-model="paymentType" shape="dot" size="large" cell>
        <!-- #ifndef MP-ALIPAY -->
        <wd-radio value="1">
          <view style="display: flex; align-items: center">
            <image
              src="/static/pay/wxpay.svg"
              mode="scaleToFill"
              class="h-22px w-22px"
            />
            <text class="pl-1 text-xs">
              {{ t('order.payType.wechat') }}
            </text>
          </view>
        </wd-radio>
        <wd-divider color="#e0e0e0" custom-class="m-0!" />
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <wd-radio value="2">
          <view style="display: flex; align-items: center">
            <image
              src="/static/pay/alipay.svg"
              mode="scaleToFill"
              class="h-22px w-22px"
            />
            <text class="pl-1 text-xs">
              {{ t('order.payType.alipay') }}
            </text>
          </view>
        </wd-radio>
        <wd-divider color="#e0e0e0" custom-class="m-0!" />
        <!-- #endif -->
        <wd-divider color="#e0e0e0" custom-class="m-0!" />
      </wd-radio-group>
    </view>
    <view class="fixed bottom-0 left-0 right-0 border-t border-t-[rgba(255,255,255,0.33)] bg-white p-20rpx pb-[max(env(safe-area-inset-bottom),16rpx)]">
      <wd-button type="primary" block :loading="paying" @click="onPrepay">
        {{ t('order.pay.confirmPayment') }}
      </wd-button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.cashier-container {
  background: #fff;
  box-sizing: border-box;
  padding: 20rpx 20rpx 60rpx;
}
.cashier-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 0 30rpx;
}
.cashier-amount {
  display: flex;
  align-items: baseline;
  color: #ff4d4f;
}
.amount-symbol {
  font-size: 34rpx;
  margin-right: 8rpx;
}
.amount-value {
  font-size: 56rpx;
  font-weight: 700;
}
.cashier-tip {
  margin-top: 20rpx;
  color: #999;
  font-size: 26rpx;
}
.order-no {
  margin-top: 8rpx;
  color: #666;
  font-size: 22rpx;
}
.cashier-card {
  margin-top: 20rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.06);
  padding: 16rpx 20rpx;
}
.card-title {
  font-size: 26rpx;
  color: #333;
  font-weight: 600;
  padding: 8rpx 0 16rpx;
}
.balance-tip {
  margin-top: 12rpx;
  color: #ff8a00;
  font-size: 22rpx;
}
</style>
