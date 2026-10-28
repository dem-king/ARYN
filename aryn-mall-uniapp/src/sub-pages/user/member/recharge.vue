<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createRechargeOrder, getRechargeConfigList, rechargePrepay } from '@/sub-pages/api/user/recharge'
import { prepay } from '@/sub-pages/utils/pay'

definePage({
  name: 'member-recharge',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '充值',
  },
})

interface RechargeConfig {
  id: string
  rechargeAmount: number
  giftAmount: number
  giftPoint: number
  status: string
}

const { t } = useI18n()
const userStore = useUserStore()
const globalLoading = useGlobalLoading()
const router = useRouter()

const configList = ref<RechargeConfig[]>([])
const selectedId = ref<string>('')
const paying = ref(false)

const selectedConfig = computed(() => configList.value.find(item => item.id === selectedId.value) || null)

const arrivalText = computed(() => {
  const config = selectedConfig.value
  if (!config)
    return ''
  const total = Number(config.rechargeAmount) + Number(config.giftAmount || 0)
  const gifts: string[] = []
  if (config.giftAmount > 0)
    gifts.push(t('accountMember.recharge.giftAmountShort', { amount: config.giftAmount }))
  if (config.giftPoint > 0)
    gifts.push(t('accountMember.recharge.giftPointsShort', { points: config.giftPoint }))
  return gifts.length
    ? t('accountMember.recharge.arrivalWithGift', { total, gifts: gifts.join(' ') })
    : t('accountMember.recharge.arrival', { total })
})

onLoad(() => {
  loadRechargeConfig()
})

onShow(() => {
  userStore.refreshPointsInfo()
})

async function loadRechargeConfig() {
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getRechargeConfigList()
    configList.value = response || []
    if (configList.value.length && !selectedId.value)
      selectedId.value = configList.value[0].id
  }
  catch (error) {
    console.error('加载充值配置失败:', error)
  }
  finally {
    globalLoading.close()
  }
}

function selectPlan(config: RechargeConfig) {
  selectedId.value = config.id
}

function toBalanceRecord() {
  router.push({ name: 'member-balance-record' })
}

async function handlePay() {
  const config = selectedConfig.value
  if (!config || paying.value)
    return
  paying.value = true
  globalLoading.loading(t('accountMember.recharge.creatingOrder'))
  try {
    const order = await createRechargeOrder(config.id)
    const orderNo = order?.orderNo
    if (!orderNo)
      throw new Error(t('accountMember.recharge.createOrderFailed'))
    // 创建订单后立即发起支付，避免只建单不入账
    const response = await rechargePrepay(buildPrepayParams(orderNo))
    await prepay(response.payParams, Number(config.rechargeAmount), `/sub-pages/user/member/recharge-result?orderNo=${orderNo}`, '1')
  }
  catch (error) {
    // 错误提示由请求层全局拦截器统一弹出
    console.error('充值下单失败:', error)
  }
  finally {
    paying.value = false
    globalLoading.close()
  }
}

function buildPrepayParams(orderNo: string) {
  const params = {
    orderNo,
    paymentType: '1',
    tradeType: 'WX_JSAPI_PAY',
    returnUrl: '',
    quitUrl: '',
  }
  // #ifdef MP-WEIXIN
  params.tradeType = 'WX_JSAPI_PAY'
  // #endif
  // #ifdef H5
  params.tradeType = 'WX_H5_PAY'
  params.returnUrl = `${window.location.origin}/sub-pages/user/member/recharge-result?orderNo=${orderNo}`
  params.quitUrl = `${window.location.origin}/sub-pages/user/member/recharge`
  // #endif
  // #ifdef APP-PLUS
  params.tradeType = 'WX_APP_PAY'
  // #endif
  return params
}
</script>

<template>
  <hr-navbar :title="t('accountMember.recharge.title')" />
  <view class="page-body">
    <!-- 当前余额 -->
    <view class="balance-card">
      <view class="balance-card__circle balance-card__circle--1" />
      <view class="balance-card__circle balance-card__circle--2" />
      <view class="balance-card__content">
        <view class="balance-card__label">
          {{ t('accountMember.recharge.currentBalance') }}
        </view>
        <view class="balance-card__value">
          {{ userStore.getBalance }}
        </view>
      </view>
      <view class="balance-card__entry" @click="toBalanceRecord">
        {{ t('accountMember.recharge.balanceRecords') }}
      </view>
    </view>

    <!-- 充值配置列表 -->
    <view class="section">
      <view class="section__title">
        {{ t('accountMember.recharge.choosePlan') }}
      </view>
      <view v-if="configList.length" class="plan-grid">
        <view
          v-for="config in configList"
          :key="config.id"
          class="plan-tile"
          :class="{ 'plan-tile--active': selectedId === config.id }"
          @click="selectPlan(config)"
        >
          <view v-if="selectedId === config.id" class="plan-tile__check">
            <text class="i-carbon:checkmark plan-tile__check-icon" />
          </view>
          <view class="plan-tile__price">
            <text class="plan-tile__symbol">
              ¥
            </text>
            <text class="plan-tile__amount">
              {{ config.rechargeAmount }}
            </text>
          </view>
          <view v-if="config.giftAmount > 0 || config.giftPoint > 0" class="plan-tile__tags">
            <text v-if="config.giftAmount > 0" class="plan-tag">
              {{ t('accountMember.recharge.giftAmountTag', { amount: config.giftAmount }) }}
            </text>
            <text v-if="config.giftPoint > 0" class="plan-tag">
              {{ t('accountMember.recharge.giftPointsTag', { points: config.giftPoint }) }}
            </text>
          </view>
        </view>
      </view>
      <view v-else class="plan-empty">
        <text class="i-carbon:wallet plan-empty__icon" />
        <text class="plan-empty__text">
          {{ t('accountMember.recharge.noPlans') }}
        </text>
      </view>
      <view v-if="configList.length" class="section__tip">
        {{ t('accountMember.recharge.planTip') }}
      </view>
    </view>
  </view>

  <!-- 底部支付栏 -->
  <view v-if="configList.length" class="pay-bar">
    <view class="pay-bar__info">
      <view v-if="selectedConfig" class="pay-bar__amount">
        <text class="pay-bar__symbol">
          ¥
        </text>
        <text class="pay-bar__num">
          {{ selectedConfig.rechargeAmount }}
        </text>
      </view>
      <view v-else class="pay-bar__placeholder">
        {{ t('accountMember.recharge.selectPlan') }}
      </view>
      <view v-if="selectedConfig && arrivalText" class="pay-bar__arrival">
        {{ arrivalText }}
      </view>
    </view>
    <view
      class="pay-bar__btn"
      :class="{ 'pay-bar__btn--disabled': !selectedConfig || paying }"
      @click="handlePay"
    >
      {{ paying ? t('accountMember.recharge.paying') : t('accountMember.recharge.payNow') }}
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page-body {
  padding-bottom: calc(180rpx + env(safe-area-inset-bottom));
}

.balance-card {
  position: relative;
  margin: 20rpx;
  border-radius: 24rpx;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

  &__circle {
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.12);
  }

  &__circle--1 {
    width: 300rpx;
    height: 300rpx;
    top: -140rpx;
    right: -60rpx;
  }

  &__circle--2 {
    width: 200rpx;
    height: 200rpx;
    bottom: -110rpx;
    left: 60rpx;
    background: rgba(255, 255, 255, 0.08);
  }

  &__content {
    position: relative;
    z-index: 1;
    padding: 44rpx 36rpx 48rpx;
  }

  &__label {
    font-size: 24rpx;
    color: rgba(255, 255, 255, 0.8);
    letter-spacing: 2rpx;
  }

  &__value {
    margin-top: 12rpx;
    font-size: 64rpx;
    font-weight: 700;
    line-height: 1.1;
    color: #fff;
  }

  &__entry {
    position: absolute;
    top: 32rpx;
    right: 28rpx;
    z-index: 1;
    padding: 8rpx 24rpx;
    border-radius: 999rpx;
    background: rgba(255, 255, 255, 0.2);
    color: #fff;
    font-size: 22rpx;
  }
}

.section {
  margin: 8rpx 20rpx 0;

  &__title {
    padding: 0 4rpx 20rpx;
    font-size: 30rpx;
    font-weight: 600;
    color: #303133;
  }

  &__tip {
    margin-top: 20rpx;
    padding: 0 4rpx;
    font-size: 22rpx;
    line-height: 1.6;
    color: #909399;
  }
}

.plan-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}

.plan-tile {
  position: relative;
  box-sizing: border-box;
  width: calc((100% - 40rpx) / 3);
  padding: 28rpx 12rpx 24rpx;
  overflow: hidden;
  border: 3rpx solid transparent;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  transition: border-color 0.2s;

  &--active {
    border-color: var(--wot-color-theme-primary, #ff2237);
  }

  &__check {
    position: absolute;
    top: 0;
    right: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 44rpx;
    height: 44rpx;
    border-radius: 0 12rpx 0 16rpx;
    background: var(--wot-color-theme-primary, #ff2237);
  }

  &__check-icon {
    font-size: 24rpx;
    color: #fff;
  }

  &__price {
    display: flex;
    align-items: baseline;
    justify-content: center;
    color: var(--wot-color-theme-primary, #ff2237);
  }

  &__symbol {
    font-size: 24rpx;
    font-weight: 600;
  }

  &__amount {
    font-size: 40rpx;
    font-weight: 700;
    line-height: 1.2;
  }

  &__tags {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6rpx;
    margin-top: 12rpx;
  }
}

.plan-tag {
  display: inline-block;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  background: #fff0eb;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 20rpx;
  line-height: 1.5;
  white-space: nowrap;
}

.plan-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 0 120rpx;

  &__icon {
    font-size: 88rpx;
    color: #c0c4cc;
  }

  &__text {
    margin-top: 16rpx;
    font-size: 26rpx;
    color: #909399;
  }
}

.pay-bar {
  position: fixed;
  bottom: var(--window-bottom, 0px);
  left: 0;
  z-index: 10;
  display: flex;
  box-sizing: border-box;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.04);

  &__amount {
    display: flex;
    align-items: baseline;
    color: #303133;
  }

  &__symbol {
    font-size: 24rpx;
    font-weight: 600;
  }

  &__num {
    font-size: 44rpx;
    font-weight: 700;
    line-height: 1;
  }

  &__placeholder {
    font-size: 28rpx;
    color: #909399;
  }

  &__arrival {
    margin-top: 6rpx;
    font-size: 20rpx;
    color: #909399;
  }

  &__btn {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    min-width: 240rpx;
    height: 80rpx;
    margin-left: 24rpx;
    padding: 0 48rpx;
    border-radius: 999rpx;
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff7a45), var(--wot-color-theme-primary, #ff4d4f));
    color: #fff;
    font-size: 30rpx;
    font-weight: 600;

    &--disabled {
      opacity: 0.5;
    }
  }
}
</style>
