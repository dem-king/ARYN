<script setup lang="ts">
import type { GroupBuyActivity } from '@/api/promotion/groupBuyActivity'
import type { GroupBuyRecord } from '@/api/promotion/groupBuyRecord'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getById as getSpuById } from '@/api/product/spu'
import { getActivityById } from '@/api/promotion/groupBuyActivity'
import { getRecordPage, joinGroup, openGroup } from '@/api/promotion/groupBuyRecord'

definePage({
  name: 'group-buy-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '拼团详情',
  },
})

const state = reactive<{
  activity: GroupBuyActivity | null
  recordList: GroupBuyRecord[]
  loading: boolean
}>({
  activity: null,
  recordList: [],
  loading: false,
})

const spuInfo = ref<{ spuName: string, picUrl: string } | null>(null)

const globalLoading = useGlobalLoading()
const { countdownDisplay, startCountdown } = useCountdown(null)

onLoad(async (options) => {
  if (options?.id) {
    await loadActivity(options.id)
  }
})

onShareAppMessage(() => {
  const activity = state.activity
  return {
    title: `${activity?.activityName || '拼团优惠'} — ${activity?.groupPrice ? `¥${activity.groupPrice}` : ''}开团`,
    path: `/sub-pages/promotion/group-buy/group-buy-detail/index?id=${activity?.id}`,
  }
})

async function loadActivity(id: string) {
  globalLoading.loading('加载中...')
  try {
    const response = await getActivityById(id)
    state.activity = response
    if (response.endedAt) {
      startCountdown(response.endedAt)
    }
    await loadRecords(id)
    await loadSpuInfo(response.spuId)
  }
  finally {
    globalLoading.close()
  }
}

async function loadSpuInfo(spuId?: string) {
  if (!spuId)
    return
  try {
    const spu = await getSpuById(spuId)
    spuInfo.value = {
      spuName: spu.spuName || spu.goodsName || '',
      picUrl: spu.picUrl || (spu.sliderPicUrls?.[0]) || '',
    }
  }
  catch {
    spuInfo.value = null
  }
}

async function loadRecords(activityId: string) {
  try {
    const response = await getRecordPage({
      current: 1,
      size: 20,
      activityId,
    })
    state.recordList = response.records
  }
  catch {
    state.recordList = []
  }
}

async function handleOpenGroup() {
  if (!state.activity?.id)
    return
  globalLoading.loading('开团中...')
  try {
    const record = await openGroup({ activityId: state.activity.id })
    uni.showToast({ title: '开团成功，请尽快支付', icon: 'success' })
    await loadRecords(state.activity.id)
    navigateToOrder(record.orderId)
  }
  catch {
    uni.showToast({ title: '开团失败', icon: 'none' })
  }
  finally {
    globalLoading.close()
  }
}

async function handleJoinGroup(recordId: string) {
  if (!state.activity?.id)
    return
  globalLoading.loading('参团中...')
  try {
    const record = await joinGroup({ activityId: state.activity.id, recordId })
    uni.showToast({ title: '参团成功，请尽快支付', icon: 'success' })
    await loadRecords(state.activity.id)
    navigateToOrder(record.orderId)
  }
  catch {
    uni.showToast({ title: '参团失败', icon: 'none' })
  }
  finally {
    globalLoading.close()
  }
}

function navigateToOrder(orderId?: string) {
  if (orderId) {
    uni.navigateTo({
      url: `/sub-pages/order/order-confirm/index?orderId=${orderId}`,
    })
  }
  else {
    uni.showToast({ title: '订单创建中，请稍后在订单列表查看', icon: 'none' })
  }
}

function getGroupStatusLabel(status?: string) {
  if (status === '0')
    return '拼团中'
  if (status === '1')
    return '拼团成功'
  if (status === '2')
    return '拼团失败'
  return ''
}

function getGroupStatusClass(status?: string) {
  if (status === '0')
    return 'status-pending'
  if (status === '1')
    return 'status-success'
  if (status === '2')
    return 'status-fail'
  return ''
}
</script>

<template>
  <view v-if="state.activity" class="detail-page">
    <hr-navbar title="拼团详情" />

    <view v-if="spuInfo" class="spu-info">
      <image v-if="spuInfo.picUrl" class="spu-img" :src="spuInfo.picUrl" mode="aspectFill" />
      <view class="spu-name">
        {{ spuInfo.spuName }}
      </view>
    </view>

    <view class="activity-info">
      <view class="activity-name">
        {{ state.activity.activityName }}
      </view>
      <view class="price-row">
        <view class="group-price">
          <text class="symbol">
            ¥
          </text>
          <text class="value">
            {{ state.activity.groupPrice }}
          </text>
        </view>
        <view class="original-price">
          ¥{{ state.activity.originalPrice }}
        </view>
      </view>
      <view class="meta-row">
        <text class="meta-item">
          {{ state.activity.groupNum }}人成团
        </text>
        <text v-if="state.activity.limitNum && state.activity.limitNum > 0" class="meta-item">
          限购{{ state.activity.limitNum }}件
        </text>
        <text class="meta-item">
          拼团时效{{ state.activity.groupExpireHours || 24 }}小时
        </text>
      </view>
      <view v-if="state.activity.activityStatus === '1'" class="countdown-row">
        <text class="countdown-label">
          距活动结束
        </text>
        <text class="countdown-value">
          {{ countdownDisplay }}
        </text>
      </view>
    </view>

    <view class="record-section">
      <view class="section-title">
        进行中的拼团
      </view>
      <view v-if="state.recordList.length === 0" class="empty-tip">
        暂无拼团记录
      </view>
      <view v-for="record in state.recordList" :key="record.id" class="record-item">
        <view class="record-info">
          <view class="record-leader">
            团长: {{ record.leaderUserId }}
          </view>
          <view class="record-progress">
            {{ record.currentNum }}/{{ record.groupNum }}人
          </view>
        </view>
        <view class="record-status" :class="[getGroupStatusClass(record.groupStatus)]">
          {{ getGroupStatusLabel(record.groupStatus) }}
        </view>
        <view v-if="record.groupStatus === '0' && !record.isJoined" class="record-action">
          <view class="join-btn" @tap="handleJoinGroup(record.id!)">
            参与拼团
          </view>
        </view>
      </view>
    </view>

    <view v-if="state.activity.activityStatus === '1'" class="bottom-bar">
      <view class="open-group-btn" @tap="handleOpenGroup">
        ¥{{ state.activity.groupPrice }} 开团
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

.spu-info {
  display: flex;
  align-items: center;
  background: #fff;
  padding: 24rpx 32rpx;
  margin-bottom: 2rpx;

  .spu-img {
    width: 120rpx;
    height: 120rpx;
    border-radius: 12rpx;
    margin-right: 24rpx;
    flex-shrink: 0;
  }

  .spu-name {
    font-size: 28rpx;
    color: #333;
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}

.activity-info {
  background: linear-gradient(135deg, #ff6b35, #ff4500);
  padding: 32rpx;
  color: #fff;

  .activity-name {
    font-size: 32rpx;
    font-weight: 700;
    margin-bottom: 16rpx;
  }

  .price-row {
    display: flex;
    align-items: baseline;
    gap: 12rpx;
    margin-bottom: 16rpx;

    .group-price {
      .symbol { font-size: 28rpx; }
      .value { font-size: 52rpx; font-weight: 700; }
    }

    .original-price {
      font-size: 26rpx;
      text-decoration: line-through;
      opacity: 0.8;
    }
  }

  .meta-row {
    display: flex;
    gap: 24rpx;
    font-size: 24rpx;
    opacity: 0.9;
  }

  .countdown-row {
    margin-top: 16rpx;
    font-size: 26rpx;
    display: flex;
    gap: 12rpx;

    .countdown-value {
      font-weight: 700;
    }
  }
}

.record-section {
  margin: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;

  .section-title {
    font-size: 28rpx;
    font-weight: 600;
    margin-bottom: 16rpx;
  }

  .empty-tip {
    text-align: center;
    color: #999;
    font-size: 26rpx;
    padding: 32rpx 0;
  }
}

.record-item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f0f0f0;

  &:last-child { border-bottom: none; }

  .record-info {
    flex: 1;

    .record-leader {
      font-size: 26rpx;
      color: #333;
    }

    .record-progress {
      font-size: 22rpx;
      color: #999;
      margin-top: 4rpx;
    }
  }

  .record-status {
    font-size: 24rpx;
    padding: 4rpx 12rpx;
    border-radius: 4rpx;
    margin-right: 16rpx;

    &.status-pending { color: #ff8c00; background: #fff7e6; }
    &.status-success { color: #52c41a; background: #f6ffed; }
    &.status-fail { color: #999; background: #f5f5f5; }
  }

  .join-btn {
    background: linear-gradient(135deg, #ff6b35, #ff4500);
    color: #fff;
    font-size: 24rpx;
    padding: 12rpx 24rpx;
    border-radius: 24rpx;
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

  .open-group-btn {
    width: 100%;
    text-align: center;
    background: linear-gradient(135deg, #ff6b35, #ff4500);
    color: #fff;
    font-size: 32rpx;
    font-weight: 600;
    padding: 20rpx 0;
    border-radius: 48rpx;
  }
}
</style>
