<script setup lang="ts">
import type { GroupBuyActivity } from '@/api/promotion/groupBuyActivity'

const props = defineProps<{
  activity: GroupBuyActivity
}>()

const emit = defineEmits<{
  (e: 'click', activity: GroupBuyActivity): void
}>()

function handleClick() {
  emit('click', props.activity)
}
</script>

<template>
  <view class="group-buy-card" @tap="handleClick">
    <view class="card-header">
      <view class="activity-name">
        {{ activity.activityName }}
      </view>
      <view class="group-num">
        {{ activity.groupNum }}人团
      </view>
    </view>
    <view class="card-body">
      <view class="price-row">
        <view class="group-price">
          <text class="price-symbol">
            ¥
          </text>
          <text class="price-value">
            {{ activity.groupPrice }}
          </text>
        </view>
        <view class="original-price">
          ¥{{ activity.originalPrice }}
        </view>
      </view>
      <view v-if="activity.limitNum && activity.limitNum > 0" class="limit-info">
        限购{{ activity.limitNum }}件
      </view>
    </view>
    <view class="card-footer">
      <view class="status-tag" :class="`status-${activity.activityStatus}`">
        {{ activity.activityStatus === '0' ? '即将开始' : activity.activityStatus === '1' ? '进行中' : '已结束' }}
      </view>
      <view v-if="activity.activityStatus === '1'" class="action-btn">
        去拼团
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.group-buy-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16rpx;

    .activity-name {
      font-size: 28rpx;
      font-weight: 600;
      color: #333;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .group-num {
      background: linear-gradient(135deg, #ff6b35, #ff4500);
      color: #fff;
      font-size: 22rpx;
      padding: 4rpx 16rpx;
      border-radius: 20rpx;
      margin-left: 16rpx;
      white-space: nowrap;
    }
  }

  .card-body {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    margin-bottom: 16rpx;

    .price-row {
      display: flex;
      align-items: baseline;
      gap: 12rpx;

      .group-price {
        color: #ff4500;

        .price-symbol {
          font-size: 24rpx;
        }

        .price-value {
          font-size: 40rpx;
          font-weight: 700;
        }
      }

      .original-price {
        color: #999;
        font-size: 24rpx;
        text-decoration: line-through;
      }
    }

    .limit-info {
      color: #999;
      font-size: 22rpx;
    }
  }

  .card-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .status-tag {
      font-size: 22rpx;
      padding: 4rpx 12rpx;
      border-radius: 4rpx;

      &.status-0 {
        color: #999;
        background: #f5f5f5;
      }

      &.status-1 {
        color: #ff4500;
        background: #fff2f0;
      }

      &.status-2 {
        color: #999;
        background: #f5f5f5;
      }
    }

    .action-btn {
      background: linear-gradient(135deg, #ff6b35, #ff4500);
      color: #fff;
      font-size: 24rpx;
      padding: 12rpx 32rpx;
      border-radius: 32rpx;
    }
  }
}
</style>
