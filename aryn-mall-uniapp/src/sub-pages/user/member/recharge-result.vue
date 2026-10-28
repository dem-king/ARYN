<script setup lang="ts">
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getRechargeOrder } from '@/sub-pages/api/user/recharge'

definePage({
  name: 'member-recharge-result',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '充值结果',
  },
})

/** 渠道明确表示该笔不会成功：可引导用户重新充值，而不是让他继续等 */
const CLOSED_TRADE_STATES = ['CLOSED', 'REVOKED', 'PAYERROR', 'REFUND']

type Phase = 'closed' | 'fail' | 'paid' | 'pending'

const { t } = useI18n()
const loading = ref(true)
const orderNo = ref('')
const order = ref<any>({})
const channelTradeState = ref('')
const phase = ref<Phase>('pending')
const checking = ref(false)
const userStore = useUserStore()

let timer: ReturnType<typeof setTimeout> | null = null
let disposed = false
/** 已发起的自动核对次数，用于退避；确认中时不必高频打扰渠道 */
let autoCheckCount = 0

const phaseTitle = computed(() => {
  switch (phase.value) {
    case 'paid': {
      return t('accountMember.rechargeResult.paidTitle')
    }
    case 'closed': {
      return t('accountMember.rechargeResult.closedTitle')
    }
    case 'fail': {
      return t('accountMember.rechargeResult.failTitle')
    }
    default: {
      return t('accountMember.rechargeResult.pendingTitle')
    }
  }
})

const phaseDesc = computed(() => {
  switch (phase.value) {
    case 'paid': {
      return t('accountMember.rechargeResult.paidDesc')
    }
    case 'closed': {
      return t('accountMember.rechargeResult.closedDesc')
    }
    case 'fail': {
      return t('accountMember.rechargeResult.failDesc')
    }
    default: {
      return t('accountMember.rechargeResult.pendingDesc')
    }
  }
})

onLoad((options) => {
  orderNo.value = options?.orderNo || ''
  void checkOrder()
})

// 从后台切回或重新进入本页时再核对一次：支付结果不该只在首次进入时确认
onShow(() => {
  if (!loading.value) {
    void checkOrder()
  }
})

onUnload(() => {
  disposed = true
  clearTimer()
})

function clearTimer() {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

/**
 * 核对支付结果。
 *
 * 后端每次都会在本地仍是待支付时向支付渠道主动查单，因此这里的「成功」由渠道
 * 说了算，而不是由我们轮询了多少次决定；轮询只用于拿准时机，不承担判定职责。
 */
async function checkOrder(manual = false) {
  if (checking.value || disposed) {
    return
  }
  checking.value = true
  clearTimer()
  try {
    const response = await getRechargeOrder(orderNo.value)
    order.value = response || {}
    channelTradeState.value = response?.channelTradeState || ''
    resolvePhase()
    if (phase.value === 'pending') {
      scheduleNext()
    }
    else {
      userStore.refreshPointsInfo()
    }
  }
  catch (error) {
    // 单次查询失败不算结论：保留确认中文案，让用户可手动重试
    console.error('核对充值订单失败:', error)
    if (manual) {
      phase.value = 'fail'
    }
    else {
      scheduleNext()
    }
  }
  finally {
    checking.value = false
    loading.value = false
  }
}

function resolvePhase() {
  if (order.value?.payStatus === '1') {
    phase.value = 'paid'
    return
  }
  if (order.value?.payStatus === '2') {
    phase.value = 'closed'
    return
  }
  if (CLOSED_TRADE_STATES.includes(channelTradeState.value)) {
    phase.value = 'closed'
    return
  }
  phase.value = 'pending'
}

/**
 * 自动核对的节奏：默认退避重试，总计约 20 秒。
 *
 * 超过这个节奏仍不确定时不再自动轮询（避免用户长时间停留在本页空耗），
 * 改为提示「会自动到账」并提供手动重新查询；返回本页时仍会再核对一次。
 *
 * 特例：渠道已确认 SUCCESS 而本地仍未入账时，说明入账消息在途，此时值得
 * 继续以 1 秒间隔追几次，让用户尽快看到结果——不要在这个节骨眼停下。
 */
function scheduleNext() {
  const channelConfirmed = channelTradeState.value === 'SUCCESS'
  const maxTry = channelConfirmed ? 15 : 6
  if (disposed || autoCheckCount >= maxTry) {
    return
  }
  const delays = channelConfirmed ? [] : [1000, 1500, 2000, 3000, 4000, 5000]
  const delay = channelConfirmed ? 1000 : delays[Math.min(autoCheckCount, delays.length - 1)]
  autoCheckCount++
  timer = setTimeout(() => void checkOrder(), delay)
}

function recheck() {
  autoCheckCount = 0
  phase.value = 'pending'
  void checkOrder(true)
}

function goBack() {
  // 支付成功经 reLaunch 进入本页，页面栈为空时 navigateBack 无效
  if (getCurrentPages().length > 1) {
    uni.navigateBack()
  }
  else {
    goMemberCenter()
  }
}

function goMemberCenter() {
  uni.reLaunch({ url: '/sub-pages/user/member/index' })
}

function goRecharge() {
  uni.redirectTo({ url: '/sub-pages/user/member/recharge' })
}
</script>

<template>
  <hr-navbar :title="t('accountMember.rechargeResult.title')" />
  <view v-if="!loading" class="result-container">
    <view class="text-center py-10">
      <view class="text-20px font-bold">
        {{ phaseTitle }}
      </view>
      <view class="text-13px text-gray-400 mt-2 px-8">
        {{ phaseDesc }}
      </view>
    </view>
    <view class="m-2 rounded-xl bg-white p-4">
      <view class="flex items-center justify-between py-1">
        <text class="text-13px text-gray-500">
          {{ t('accountMember.rechargeResult.orderNo') }}
        </text>
        <text class="text-13px">{{ order?.orderNo || orderNo }}</text>
      </view>
      <view class="flex items-center justify-between py-1">
        <text class="text-13px text-gray-500">
          {{ t('accountMember.rechargeResult.amount') }}
        </text>
        <text class="text-13px">￥{{ order?.rechargeAmount }}</text>
      </view>
      <view v-if="order?.giftAmount > 0" class="flex items-center justify-between py-1">
        <text class="text-13px text-gray-500">
          {{ t('accountMember.rechargeResult.giftAmount') }}
        </text>
        <text class="text-13px">￥{{ order?.giftAmount }}</text>
      </view>
      <view v-if="order?.giftPoint > 0" class="flex items-center justify-between py-1">
        <text class="text-13px text-gray-500">
          {{ t('accountMember.rechargeResult.giftPoints') }}
        </text>
        <text class="text-13px">{{ order?.giftPoint }}</text>
      </view>
    </view>
    <view class="m-2 flex items-center justify-center gap-3">
      <wd-button v-if="phase === 'closed'" type="primary" @click="goRecharge">
        {{ t('accountMember.rechargeResult.rechargeAgain') }}
      </wd-button>
      <template v-else-if="phase === 'paid'">
        <wd-button plain @click="goBack">
          {{ t('accountMember.rechargeResult.back') }}
        </wd-button>
        <wd-button type="primary" @click="goMemberCenter">
          {{ t('accountMember.rechargeResult.memberCenter') }}
        </wd-button>
      </template>
      <template v-else>
        <wd-button plain @click="goBack">
          {{ t('accountMember.rechargeResult.back') }}
        </wd-button>
        <wd-button :loading="checking" type="primary" @click="recheck">
          {{ t('accountMember.rechargeResult.recheck') }}
        </wd-button>
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.result-container {
  min-height: calc(100vh - var(--window-top));
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf9 100%);
}
</style>
