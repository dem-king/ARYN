<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
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

const userStore = useUserStore()
const globalLoading = useGlobalLoading()

const configList = ref<RechargeConfig[]>([])
const selectedId = ref<string>('')

onLoad(() => {
  loadRechargeConfig()
})

onShow(() => {
  userStore.refreshPointsInfo()
})

async function loadRechargeConfig() {
  globalLoading.loading('加载中...')
  try {
    const response = await getRechargeConfigList()
    configList.value = response || []
  }
  catch (error) {
    console.error('加载充值配置失败:', error)
  }
  finally {
    globalLoading.close()
  }
}

async function handleRecharge(config: RechargeConfig) {
  if (selectedId.value)
    return
  selectedId.value = config.id
  globalLoading.loading('创建订单中...')
  try {
    const order = await createRechargeOrder(config.id)
    const orderNo = order?.orderNo
    if (!orderNo) {
      throw new Error('创建充值订单失败')
    }
    // 创建订单后立即发起支付，避免只建单不入账
    const response = await rechargePrepay(buildPrepayParams(orderNo))
    prepay(response.payParams, Number(config.rechargeAmount), `/sub-pages/user/member/recharge-result?orderNo=${orderNo}`, '1')
  }
  catch (error) {
    // 错误提示由请求层全局拦截器统一弹出
    console.error('充值下单失败:', error)
  }
  finally {
    selectedId.value = ''
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
  <hr-navbar title="充值" />
  <!-- 当前余额 -->
  <view class="balance-card">
    <view class="card-bg" />
    <view class="card-content">
      <view class="text-center">
        <view class="text-14px text-white opacity-80">当前储值余额</view>
        <view class="text-36px font-bold text-white mt-1">{{ userStore.getBalance }}</view>
      </view>
    </view>
  </view>

  <!-- 充值配置列表 -->
  <view class="m-2">
    <view class="text-14px font-bold mb-2 px-2">选择充值方案</view>
    <view v-for="config in configList" :key="config.id" class="config-card" @click="handleRecharge(config)">
      <view class="config-main">
        <view class="config-amount">
          <text class="text-24px font-bold color-primary">{{ config.rechargeAmount }}</text>
          <text class="text-13px ml-1">元</text>
        </view>
        <view v-if="config.giftAmount > 0" class="config-gift">
          <text class="gift-tag">赠{{ config.giftAmount }}元</text>
        </view>
        <view v-if="config.giftPoint > 0" class="config-gift">
          <text class="gift-tag">赠{{ config.giftPoint }}积分</text>
        </view>
      </view>
      <wd-button
        type="primary"
        size="small"
        :loading="selectedId === config.id"
        :disabled="selectedId !== '' && selectedId !== config.id"
        custom-class="recharge-btn"
      >
        立即充值
      </wd-button>
    </view>
    <view v-if="configList.length === 0" class="text-center text-gray-400 py-10">
      暂无充值方案
    </view>
  </view>
</template>

<style lang="scss" scoped>
.balance-card {
  position: relative;
  margin: 20rpx;
  border-radius: 20rpx;
  overflow: hidden;

  .card-bg {
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  .card-content {
    position: relative;
    padding: 40rpx 30rpx;
    z-index: 1;
  }
}

.config-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx 30rpx;
  margin-bottom: 16rpx;
  display: flex;
  flex-direction: column;
  position: relative;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.config-main {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.config-amount {
  display: flex;
  align-items: baseline;
}

.config-gift {
  .gift-tag {
    display: inline-block;
    padding: 2rpx 12rpx;
    border-radius: 8rpx;
    background: rgba(102, 126, 234, 0.1);
    color: #667eea;
    font-size: 22rpx;
  }
}

:deep(.recharge-btn) {
  position: absolute;
  right: 30rpx;
  top: 50%;
  transform: translateY(-50%);
}

.color-primary {
  color: var(--wot-color-theme);
}
</style>
