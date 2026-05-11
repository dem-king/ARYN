<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'

definePage({
  name: 'member-center',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '会员中心',
  },
})

const router = useRouter()
const userStore = useUserStore()

onShow(() => {
  userStore.refreshPointsInfo()
})

function toSignIn() {
  router.push({ name: 'member-sign-in' })
}

function toPointsRecord() {
  router.push({ name: 'member-points-record' })
}

function toRecharge() {
  router.push({ name: 'member-recharge' })
}

function toBalanceRecord() {
  router.push({ name: 'member-balance-record' })
}

function toLevelBenefit() {
  router.push({ name: 'member-level-benefit' })
}
</script>

<template>
  <hr-navbar title="会员中心" />
  <!-- 会员信息卡片 -->
  <view class="member-card">
    <view class="card-bg" />
    <view class="card-content">
      <view class="flex items-center">
        <image
          :src="userStore.getUserAvatar || '/static/default-avatar.png'"
          class="h-80rpx w-80rpx rounded-full border-2 border-white"
          mode="aspectFill"
        />
        <view class="ml-20rpx">
          <view class="text-16px font-bold text-white">
            {{ userStore.getUserNickname || '未设置昵称' }}
          </view>
          <view v-if="userStore.getLevelName" class="mt-1">
            <text class="level-tag">{{ userStore.getLevelName }}</text>
            <text class="level-benefit-link" @click.stop="toLevelBenefit">查看权益</text>
          </view>
        </view>
      </view>
      <view class="mt-4 flex justify-around">
        <view class="text-center" @click="toPointsRecord">
          <view class="text-24px font-bold text-white">{{ userStore.getPoint }}</view>
          <view class="text-12px text-white opacity-80">积分</view>
        </view>
        <view class="divider" />
        <view class="text-center">
          <view class="text-24px font-bold text-white">{{ userStore.getBalance }}</view>
          <view class="text-12px text-white opacity-80">储值余额</view>
          <view class="mt-1 flex items-center justify-center gap-2">
            <text class="balance-action" @click.stop="toRecharge">充值</text>
            <text class="balance-action" @click.stop="toBalanceRecord">明细</text>
          </view>
        </view>
      </view>
    </view>
  </view>

  <!-- 功能入口 -->
  <view class="m-2 rounded-xl bg-white p-2">
    <wd-cell-group border>
      <wd-cell is-link @click="toSignIn">
        <template #title>
          <view class="flex items-center">
            <text class="i-carbon:calendar text-xl color-primary" />
            <text class="ml-10rpx">每日签到</text>
          </view>
        </template>
        <template #value>
          <text class="text-12px text-gray-400">签到领积分</text>
        </template>
      </wd-cell>
      <wd-cell is-link @click="toPointsRecord">
        <template #title>
          <view class="flex items-center">
            <text class="i-carbon:chart-line-data text-xl color-primary" />
            <text class="ml-10rpx">积分记录</text>
          </view>
        </template>
        <template #value>
          <text class="text-12px text-gray-400">查看积分变动</text>
        </template>
      </wd-cell>
    </wd-cell-group>
  </view>
</template>

<style lang="scss" scoped>
.member-card {
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

  .level-tag {
    display: inline-block;
    padding: 2rpx 16rpx;
    border-radius: 20rpx;
    background: rgba(255, 255, 255, 0.25);
    font-size: 22rpx;
    color: #fff;
  }

  .level-benefit-link {
    display: inline-block;
    margin-left: 12rpx;
    padding: 2rpx 12rpx;
    border-radius: 20rpx;
    background: rgba(255, 255, 255, 0.35);
    font-size: 20rpx;
    color: #fff;
  }

  .balance-action {
    display: inline-block;
    padding: 2rpx 16rpx;
    border-radius: 20rpx;
    background: rgba(255, 255, 255, 0.25);
    font-size: 22rpx;
    color: #fff;
  }

  .divider {
    width: 1px;
    height: 60rpx;
    background: rgba(255, 255, 255, 0.3);
    align-self: center;
  }
}

.color-primary {
  color: var(--wot-color-theme);
}
</style>
