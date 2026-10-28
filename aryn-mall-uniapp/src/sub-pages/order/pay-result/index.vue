<script setup lang="ts">
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getByOrderNo as getOrderInfoByNo } from '@/api/order/orderInfo'
import { useDecorationInfiniteScroll } from '@/composables/useDecorationInfiniteScroll'
import { getOrder as getOrderByNo } from '@/sub-pages/api/pay/index'
import { useDict } from '@/sub-pages/utils/dict'

definePage({
  name: 'pay-result',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '支付结果',
  },
})
const { pay_type } = useDict('pay_type')
const { t } = useI18n()
// 底部推荐商品流的页面滚动广播：滚动接近底部时自动加载下一页
useDecorationInfiniteScroll()

const PAY_TYPE_LABEL_KEYS: Record<string, string> = {
  0: 'order.payType.free',
  1: 'order.payType.wechat',
  2: 'order.payType.alipay',
  3: 'order.payType.cod',
}
const payTypeOptions = computed(() => (pay_type.value || []).map((item: any) => {
  const key = PAY_TYPE_LABEL_KEYS[item.value]
  return key ? { ...item, label: t(key) } : item
}))

const POLL_INTERVAL = 1500
const MAX_POLL = 8

const loading = ref(true)
const checking = ref(false)
const orderNo = ref('')
const state = reactive<{ order: any, payType: string }>({
  order: {},
  payType: '',
})
/** 支付结果必须等待服务端确认，不能仅凭客户端回调展示成功 */
const isPaid = computed(() => String(state.order?.payStatus ?? '') === '1')
/** 推荐上下文：支付结果页只拿到支付单，订单商品需另查一次 */
const recommendItems = ref<any[]>([])
const payRecommendSpuIds = computed(() => {
  const ids = recommendItems.value
    .map((item) => String(item?.spuId ?? '').trim())
    .filter(Boolean)
  return [...new Set(ids)]
})
const payRecommendSourceIds = computed(() => payRecommendSpuIds.value.slice(0, 20))
const payRecommendExcludeIds = computed(() => payRecommendSpuIds.value.slice(0, 50))
const router = useRouter()

let pollTimer: ReturnType<typeof setTimeout> | null = null
let pollCount = 0
let disposed = false
let loaded = false

onLoad((options) => {
  orderNo.value = options?.orderNo || ''
  void refresh()
  loadRecommendContext(orderNo.value)
})

onShow(() => {
  // 从其他页面返回时补一次查询，避免长时间停在「确认中」
  if (loaded && !isPaid.value) {
    void refresh()
  }
})

onUnload(() => {
  disposed = true
  clearPoll()
})

async function refresh() {
  if (!orderNo.value || checking.value) {
    return
  }
  checking.value = true
  try {
    await getOrder(orderNo.value)
    loaded = true
    if (isPaid.value) {
      clearPoll()
    }
    else {
      schedulePoll()
    }
  }
  finally {
    checking.value = false
  }
}

async function getOrder(no: string) {
  try {
    const response = await getOrderByNo(no)
    state.order = response || {}
    if (state.order) {
      try {
        state.payType = JSON.parse(state.order.extra || '{}')?.payType || ''
      }
      catch {
        state.payType = ''
      }
    }
  }
  catch (error) {
    // 网络异常保留当前结论，交给下一轮轮询或手动刷新
    console.error('查询支付结果失败:', error)
  }
  finally {
    loading.value = false
  }
}

function schedulePoll() {
  if (disposed || pollTimer || pollCount >= MAX_POLL || isPaid.value) {
    return
  }
  pollTimer = setTimeout(() => {
    pollTimer = null
    pollCount++
    void refresh()
  }, POLL_INTERVAL)
}

function clearPoll() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function manualRefresh() {
  pollCount = 0
  clearPoll()
  void refresh()
}

/** 订单商品只用于推荐上下文；失败时退化为浏览足迹/全店推荐，不影响支付结果主流程 */
async function loadRecommendContext(no?: string) {
  if (!no) {
    return
  }
  try {
    const order = await getOrderInfoByNo(no)
    recommendItems.value = order?.orderItemList || []
  }
  catch {
    recommendItems.value = []
  }
}

// 返回首页
function goHome() {
  router.replaceAll({ name: 'home' })
}
// 跳转订单
function goOrder() {
  router.push({ name: 'order-list' })
}
</script>

<template>
  <hr-navbar :title="t('order.result.title')" />
  <view v-if="!loading" class="pay-result-container">
    <view class="pay-result-bg-elements">
      <view class="bg-circle bg-circle-1" />
      <view class="bg-circle bg-circle-2" />
      <view class="bg-circle bg-circle-3" />
    </view>
    <view class="pay-result-header animated bounceInDown flex flex-col items-center justify-center p-30rpx">
      <wd-icon
        :color="isPaid ? '#19be6b' : '#ff9900'"
        :name="isPaid ? 'check-circle' : 'info-circle'"
        size="100rpx"
        :class="isPaid ? 'success-icon animated pulse infinite' : ''"
      />
      <text class="animated fadeInUp py-1 text-xl font-bold">
        {{ isPaid ? t('order.result.success') : t('order.result.confirming') }}
      </text>
      <text v-if="!isPaid" class="mt-2 px-6 text-center text-xs text-gray-400">
        {{ t('order.result.confirmingDesc') }}
      </text>
    </view>
    <view class="pay-result-content animated fadeInUp m-50rpx mx-80rpx delay-03s">
      <view class="item flex items-center justify-between pb-10rpx">
        <text class="text-xs">
          {{ t('order.result.paymentNo') }}
        </text>
        <wd-text size="26rpx" color="inherit" :text="state.order?.outTradeNo" />
      </view>
      <view class="item flex items-center justify-between pb-10rpx">
        <text class="text-xs">
          {{ t('order.result.paymentMethod') }}
        </text>
        <dict-tag :options="payTypeOptions" :value="state.payType" />
      </view>
      <view class="item flex items-center justify-between pb-10rpx">
        <text class="text-xs">
          {{ t('order.result.amount') }}
        </text>
        <wd-text size="16px" color="red" :text="state.order?.amount" mode="price" prefix="￥" />
      </view>
    </view>
    <view class="pay-result-foot animated fadeInUp mt-40rpx flex items-center justify-center delay-05s">
      <view v-if="!isPaid" class="p-2">
        <wd-button plain :loading="checking" custom-class="action-btn hover-scale" @click="manualRefresh">
          {{ t('order.result.refresh') }}
        </wd-button>
      </view>
      <view class="p-2">
        <wd-button plain custom-class="action-btn hover-scale" @click="goHome">
          {{ t('order.result.backHome') }}
        </wd-button>
      </view>
      <view class="p-2">
        <wd-button type="primary" custom-class="action-btn hx-ml10 hover-scale" @click="goOrder">
          {{ t('order.result.viewOrder') }}
        </wd-button>
      </view>
    </view>
    <WaterfallGoods
      :source-spu-ids="payRecommendSourceIds"
      :exclude-spu-ids="payRecommendExcludeIds"
    />
  </view>
</template>

<style lang="scss" scoped>
.pay-result-container {
  position: relative;
  min-height: calc(100vh - var(--window-top));
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf9 100%);
  overflow: hidden;
}

.pay-result-bg-elements {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--theme-color-primary, #4D7FFF) 0%, var(--theme-color-secondary, #7BA7FF) 100%);
  opacity: 0.1;
  filter: blur(20rpx);
}

.bg-circle-1 {
  top: -50rpx;
  right: -50rpx;
  width: 300rpx;
  height: 300rpx;
  animation: float 6s ease-in-out infinite;
}

.bg-circle-2 {
  top: 300rpx;
  left: -80rpx;
  width: 200rpx;
  height: 200rpx;
  animation: float 8s ease-in-out infinite 1s;
}

.bg-circle-3 {
  bottom: 100rpx;
  right: 50rpx;
  width: 150rpx;
  height: 150rpx;
  animation: float 7s ease-in-out infinite 0.5s;
}

.success-icon {
  animation-duration: 2s;
}

.action-btn {
  transition: all 0.3s ease;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.1);

  &:active {
    transform: scale(0.95);
  }
}

.hover-scale {
  transition: transform 0.3s ease;

  &:hover {
    transform: scale(1.05);
  }

  &:active {
    transform: scale(0.95);
  }
}

/* 自定义动画类 */
.animated {
  animation-duration: 1s;
  animation-fill-mode: both;
}

.bounceInDown {
  animation-name: bounceInDown;
}

.fadeInUp {
  animation-name: fadeInUp;
}

.pulse {
  animation-name: pulse;
  animation-timing-function: ease-in-out;
}

.infinite {
  animation-iteration-count: infinite;
}

.delay-03s {
  animation-delay: 0.3s;
}

.delay-05s {
  animation-delay: 0.5s;
}

@keyframes bounceInDown {
  from,
  60%,
  75%,
  90%,
  to {
    animation-timing-function: cubic-bezier(0.215, 0.61, 0.355, 1);
  }

  0% {
    opacity: 0;
    transform: translate3d(0, -3000rpx, 0);
  }

  60% {
    opacity: 1;
    transform: translate3d(0, 25rpx, 0);
  }

  75% {
    transform: translate3d(0, -10rpx, 0);
  }

  90% {
    transform: translate3d(0, 5rpx, 0);
  }

  to {
    transform: translate3d(0, 0, 0);
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translate3d(0, 100rpx, 0);
  }

  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes pulse {
  from {
    transform: scale3d(1, 1, 1);
  }

  50% {
    transform: scale3d(1.05, 1.05, 1.05);
  }

  to {
    transform: scale3d(1, 1, 1);
  }
}

@keyframes float {
  0% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(20rpx, -20rpx);
  }
  100% {
    transform: translate(0, 0);
  }
}
</style>
