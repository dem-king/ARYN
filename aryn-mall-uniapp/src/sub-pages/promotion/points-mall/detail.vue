<script setup lang="ts">
import type { PointsGoods } from '@/api/promotion/points-goods'
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getPointsGoodsById, exchangePointsGoods } from '@/api/promotion/points-goods'
import { getPointsInfo } from '@/api/user/points'

definePage({
  name: 'points-mall-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '积分商品详情',
  },
})

const state = reactive<{
  goods: PointsGoods | null
  pointsBalance: number
  loading: boolean
}>({
  goods: null,
  pointsBalance: 0,
  loading: false,
})

const showExchangeConfirm = ref(false)
const exchanging = ref(false)
const globalLoading = useGlobalLoading()

onLoad(async (options) => {
  if (options?.id) {
    await loadGoods(options.id)
    await loadPointsBalance()
  }
})

async function loadGoods(id: string) {
  globalLoading.loading('加载中...')
  try {
    const response = await getPointsGoodsById(id)
    state.goods = response
  }
  finally {
    globalLoading.close()
  }
}

async function loadPointsBalance() {
  try {
    const info = await getPointsInfo()
    state.pointsBalance = info.point || 0
  }
  catch {
    state.pointsBalance = 0
  }
}

function handleExchangeClick() {
  if (!state.goods) return
  if (state.goods.stock <= 0) {
    uni.showToast({ title: '库存不足', icon: 'none' })
    return
  }
  if (state.pointsBalance < (state.goods.pointsPrice || 0)) {
    uni.showToast({ title: '积分不足', icon: 'none' })
    return
  }
  showExchangeConfirm.value = true
}

async function confirmExchange() {
  if (!state.goods?.id) return
  exchanging.value = true
  try {
    await exchangePointsGoods(state.goods.id)
    uni.showToast({ title: '兑换成功', icon: 'success' })
    showExchangeConfirm.value = false
    await loadGoods(state.goods.id)
    await loadPointsBalance()
  }
  catch {
    uni.showToast({ title: '兑换失败', icon: 'none' })
  }
  finally {
    exchanging.value = false
  }
}

function cancelExchange() {
  showExchangeConfirm.value = false
}

function getTypeLabel(type?: string) {
  if (type === 'goods') return '实物商品'
  if (type === 'coupon') return '优惠券'
  if (type === 'gift') return '赠品'
  return ''
}
</script>

<template>
  <view v-if="state.goods" class="detail-page">
    <hr-navbar title="积分商品详情" />

    <!-- 商品封面 -->
    <view class="cover-section">
      <image
        v-if="state.goods.cover"
        class="cover-img"
        :src="state.goods.cover"
        mode="aspectFill"
      />
      <view v-else class="cover-img cover-placeholder">
        <text class="placeholder-text">
          {{ getTypeLabel(state.goods.type) }}
        </text>
      </view>
    </view>

    <!-- 商品信息 -->
    <view class="info-section">
      <view class="goods-name">
        {{ state.goods.name }}
      </view>
      <view class="price-row">
        <text class="price-value">
          {{ state.goods.pointsPrice }}
        </text>
        <text class="price-unit">
          积分
        </text>
      </view>
    </view>

    <!-- 详细信息 -->
    <view class="detail-section">
      <view class="detail-item">
        <text class="detail-label">
          商品类型
        </text>
        <text class="detail-value">
          {{ getTypeLabel(state.goods.type) }}
        </text>
      </view>
      <view class="detail-item">
        <text class="detail-label">
          库存
        </text>
        <text class="detail-value">
          {{ state.goods.stock }}
        </text>
      </view>
      <view class="detail-item">
        <text class="detail-label">
          限购
        </text>
        <text class="detail-value">
          {{ state.goods.limitPerUser && state.goods.limitPerUser > 0 ? `每人限购${state.goods.limitPerUser}件` : '不限购' }}
        </text>
      </view>
      <view v-if="state.goods.startTime" class="detail-item">
        <text class="detail-label">
          活动时间
        </text>
        <text class="detail-value">
          {{ state.goods.startTime }} ~ {{ state.goods.endTime }}
        </text>
      </view>
    </view>

    <!-- 积分余额 -->
    <view class="balance-section">
      <text class="balance-label">
        当前积分
      </text>
      <text class="balance-value">
        {{ state.pointsBalance }}
      </text>
    </view>

    <!-- 底部兑换按钮 -->
    <view class="bottom-bar">
      <view class="exchange-btn" @tap="handleExchangeClick">
        立即兑换
      </view>
    </view>

    <!-- 兑换确认弹窗 -->
    <view v-if="showExchangeConfirm" class="modal-mask" @tap="cancelExchange">
      <view class="modal-content" @tap.stop>
        <view class="modal-title">
          确认兑换
        </view>
        <view class="modal-body">
          <text>确认使用 </text>
          <text class="modal-points">
            {{ state.goods.pointsPrice }}
          </text>
          <text> 积分兑换「{{ state.goods.name }}」？</text>
        </view>
        <view class="modal-footer">
          <view class="modal-btn modal-cancel" @tap="cancelExchange">
            取消
          </view>
          <view class="modal-btn modal-confirm" @tap="confirmExchange">
            确认兑换
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 120rpx;
}

.cover-section {
  .cover-img {
    width: 100%;
    height: 560rpx;
    display: block;

    &.cover-placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f0f0f0;

      .placeholder-text {
        font-size: 32rpx;
        color: #ccc;
      }
    }
  }
}

.info-section {
  background: linear-gradient(135deg, #667eea, #764ba2);
  padding: 32rpx;
  color: #fff;

  .goods-name {
    font-size: 32rpx;
    font-weight: 700;
    margin-bottom: 16rpx;
    line-height: 1.4;
  }

  .price-row {
    display: flex;
    align-items: baseline;
    gap: 8rpx;

    .price-value {
      font-size: 56rpx;
      font-weight: 700;
    }

    .price-unit {
      font-size: 26rpx;
      opacity: 0.9;
    }
  }
}

.detail-section {
  margin: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;

  .detail-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16rpx 0;
    border-bottom: 1rpx solid #f0f0f0;

    &:last-child {
      border-bottom: none;
    }

    .detail-label {
      font-size: 26rpx;
      color: #999;
    }

    .detail-value {
      font-size: 26rpx;
      color: #333;
    }
  }
}

.balance-section {
  margin: 0 24rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .balance-label {
    font-size: 26rpx;
    color: #999;
  }

  .balance-value {
    font-size: 32rpx;
    font-weight: 700;
    color: #764ba2;
  }
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16rpx 32rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
  display: flex;
  justify-content: center;

  .exchange-btn {
    width: 100%;
    text-align: center;
    background: linear-gradient(135deg, #667eea, #764ba2);
    color: #fff;
    font-size: 32rpx;
    font-weight: 600;
    padding: 20rpx 0;
    border-radius: 48rpx;
  }
}

.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

.modal-content {
  width: 600rpx;
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;

  .modal-title {
    font-size: 32rpx;
    font-weight: 600;
    text-align: center;
    padding: 32rpx 0 16rpx;
  }

  .modal-body {
    padding: 16rpx 32rpx 32rpx;
    font-size: 28rpx;
    color: #666;
    text-align: center;
    line-height: 1.6;

    .modal-points {
      color: #764ba2;
      font-weight: 700;
    }
  }

  .modal-footer {
    display: flex;
    border-top: 1rpx solid #f0f0f0;

    .modal-btn {
      flex: 1;
      text-align: center;
      padding: 24rpx 0;
      font-size: 30rpx;
    }

    .modal-cancel {
      color: #999;
    }

    .modal-confirm {
      color: #764ba2;
      font-weight: 600;
    }
  }
}
</style>