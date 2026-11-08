<script setup lang="ts">
import type { PayCapability } from '@/sub-pages/api/pay/index'
import type { PayPrepayResult } from '@/sub-pages/utils/pay'
import { onLoad, onPageScroll, onUnload } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getByOrderNo, orderPrepay } from '@/api/order/orderInfo'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useGlobalLoading } from '@/composables/useGlobalLoading'
import { getCapabilities } from '@/sub-pages/api/pay/index'
import { detectClientPlatform, filterCapabilitiesForClientPlatform, prepay } from '@/sub-pages/utils/pay'
import { DEFAULT_PAY_TIMEOUT_MINUTES } from '@/sub-pages/utils/pay-timeout'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'order-pay',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '收银台',
  },
})
type PaymentType = '1' | '2' // 1: 微信, 2: 支付宝

const loading = ref(true)
const paying = ref(false)
const capabilityLoading = ref(true)
const allCapabilities = ref<PayCapability[]>([])
let disposed = false
const { t } = useI18n()
const globalLoading = useGlobalLoading()
const state = reactive<{ order: any }>({
  order: {},
})
const paymentPrice = ref(0)
const payOrderNo = ref('')
const paymentType = ref<PaymentType>('1')
const isWechatBrowser = ref(false)
// #ifdef H5
isWechatBrowser.value = /micromessenger/i.test(navigator.userAgent)
// #endif

const availableCapabilities = computed(() => allCapabilities.value.filter((item) => item.available))
const availableChannels = computed(() => {
  const channels = new Set(availableCapabilities.value.map((item) => item.channel))
  return [...channels]
})
const selectedCapability = computed(() =>
  availableCapabilities.value.find((item) => item.channel === paymentType.value),
)
const selectedTradeType = computed(() => selectedCapability.value?.tradeType || '')
const noCapabilityReason = computed(() => {
  const reasons = allCapabilities.value
    .filter((item) => !item.available && item.reason)
    .map((item) => item.reason as string)
  return reasons[0] || t('labels.payUnavailable')
})
const amountText = computed(() => {
  const val = Number(paymentPrice.value || 0)
  return val.toFixed(2)
})
const payTimeoutText = computed(() => {
  const value = Number(state.order?.payTimeoutMinutes)
  const minutes = Number.isFinite(value) && value > 0 ? value : DEFAULT_PAY_TIMEOUT_MINUTES
  return minutes >= 60 && minutes % 60 === 0
    ? t('order.time.hours', { hours: minutes / 60 })
    : t('order.time.minutes', { minutes })
})

onLoad((options) => {
  void loadCapabilities()
  getOrder(options?.orderNo)
})

onUnload(() => {
  disposed = true
})

function detectTerminalType() {
  let terminal = '2'
  // #ifdef MP-WEIXIN
  terminal = '0'
  // #endif
  // #ifdef MP-ALIPAY
  terminal = '0'
  // #endif
  // #ifdef APP-PLUS
  terminal = '1'
  // #endif
  return terminal
}

async function loadCapabilities() {
  capabilityLoading.value = true
  try {
    const platform = detectClientPlatform()
    allCapabilities.value = filterCapabilitiesForClientPlatform(
      (await getCapabilities(detectTerminalType(), platform)) || [],
      platform,
    )
    if (isWechatBrowser.value) {
      allCapabilities.value = allCapabilities.value.map(item => item.tradeType === 'WX_H5_PAY'
        ? { ...item, available: false, reason: '微信内网页暂不支持公众号 JSAPI，请使用小程序或外部浏览器' }
        : item)
    }
    let preferred: PaymentType = '1'
    // #ifdef MP-ALIPAY
    preferred = '2'
    // #endif
    if (!availableCapabilities.value.some((item) => item.channel === preferred)) {
      preferred = (availableCapabilities.value[0]?.channel as PaymentType) || '1'
    }
    paymentType.value = preferred
  }
  catch (error) {
    console.error('加载支付能力失败:', error)
    allCapabilities.value = []
  }
  finally {
    capabilityLoading.value = false
  }
}

function getOrder(orderNo: string) {
  getByOrderNo(orderNo).then((response) => {
    loading.value = false
    state.order = response
    paymentPrice.value = response.paymentPrice
    payOrderNo.value = response.orderNo
  })
}

function resultUrl(orderNo: string) {
  return `/sub-pages/order/pay-result/index?orderNo=${orderNo}`
}

function payReturnUrl(orderNo: string) {
  // #ifdef H5
  return `${window.location.origin}${resultUrl(orderNo)}`
  // #endif
  return ''
}

function payQuitUrl(orderNo: string) {
  // #ifdef H5
  return `${window.location.origin}/sub-pages/order/order-pay/index?orderNo=${orderNo}`
  // #endif
  return ''
}

async function onPrepay() {
  if (paying.value || !payOrderNo.value) return
  if (!selectedTradeType.value) {
    uni.showModal({
      title: t('labels.notice'),
      content: noCapabilityReason.value,
      showCancel: false,
    })
    return
  }
  paying.value = true
  globalLoading.loading(t('common.loading'))
  const orderNo = payOrderNo.value
  try {
    const response = await orderPrepay({
      orderNo,
      paymentType: paymentType.value,
      tradeType: selectedTradeType.value,
      returnUrl: payReturnUrl(orderNo),
      quitUrl: payQuitUrl(orderNo),
    })
    if (disposed) return
    await prepay(response.payParams as PayPrepayResult, paymentPrice.value, resultUrl(orderNo))
  }
  catch (error) {
    console.error('发起支付失败:', error)
  }
  finally {
    paying.value = false
    globalLoading.close()
  }
}

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
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
      <view v-if="capabilityLoading" class="cashier-tip">
        {{ t('common.loading') }}
      </view>
      <wd-radio-group v-else-if="availableChannels.length" v-model="paymentType" shape="dot" size="large" cell>
        <template v-for="(channel, index) in availableChannels" :key="channel">
          <wd-radio :value="channel">
            <view style="display: flex; align-items: center">
              <image
                :src="channel === '1' ? '/static/pay/wxpay.svg' : '/static/pay/alipay.svg'"
                mode="scaleToFill"
                class="h-22px w-22px"
              />
              <text class="pl-1 text-xs">
                {{ channel === '1' ? t('order.payType.wechat') : t('order.payType.alipay') }}
              </text>
            </view>
          </wd-radio>
          <wd-divider v-if="index < availableChannels.length - 1" color="#e0e0e0" custom-class="m-0!" />
        </template>
      </wd-radio-group>
      <view v-else class="cashier-tip">
        {{ isWechatBrowser ? '微信内网页暂不支持公众号 JSAPI，请使用小程序或外部浏览器' : noCapabilityReason }}
      </view>
    </view>
    <view class="fixed bottom-0 left-0 right-0 border-t border-t-[rgba(255,255,255,0.33)] bg-white p-20rpx pb-[max(env(safe-area-inset-bottom),16rpx)]">
      <wd-button type="primary" block :loading="paying" :disabled="!selectedTradeType" @click="onPrepay">
        {{ t('order.pay.confirmPayment') }}
      </wd-button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.cashier-container {
  background: var(--app-card);
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
  color: var(--app-text-3);
  font-size: 26rpx;
}
.cashier-card {
  margin-top: 20rpx;
  background: var(--app-card);
  border-radius: 16rpx;
  box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.06);
  padding: 16rpx 20rpx;
}
.card-title {
  font-size: 26rpx;
  color: var(--app-text);
  font-weight: 600;
  padding: 8rpx 0 16rpx;
}
</style>
