<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getPage } from '@/api/promotion/couponInfo'
import { addObj } from '@/api/promotion/couponUser'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const showStyle = computed(() => props.showData.showStyle || '1')
const showNum = computed(() => props.showData.showNum || 3)
const showReceiveBtn = computed(() => props.showData.showReceiveBtn !== false)
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))

const couponList = ref<any[]>([])

async function fetchCoupons() {
  try {
    const res = await getPage({
      current: 1,
      size: showNum.value,
    })
    couponList.value = res.records || []
  }
  catch {
    couponList.value = []
  }
}

async function handleReceive(couponId: string) {
  try {
    await addObj({ couponId })
    uni.showToast({ title: '领取成功', icon: 'success' })
    await fetchCoupons()
  }
  catch {
    uni.showToast({ title: '领取失败', icon: 'none' })
  }
}

function getCouponValue(item: any) {
  if (item.couponType === '1') {
    return `¥${item.amount}`
  }
  if (item.couponType === '2') {
    return `${item.discount}折`
  }
  return ''
}

function getThresholdText(item: any) {
  if (item.threshold && item.threshold > 0) {
    return `满${item.threshold}可用`
  }
  return '无门槛'
}

onMounted(() => {
  fetchCoupons()
})
</script>

<template>
  <view class="coupon-receive" :style="dynamicStyles">
    <!--
      布局与后台设计器预览保持一致：
      showStyle=1：横向一排小卡片，上红下白（list-style）
      showStyle=2：纵向大卡片，左红右白（card-style）
    -->
    <view class="coupon-list" :class="{ 'list-style': showStyle === '1', 'card-style': showStyle === '2' }">
      <view
        v-for="(item, index) in couponList"
        :key="index"
        class="coupon-item"
      >
        <view class="coupon-amount">
          <text class="coupon-symbol">¥</text>
          <text class="coupon-value">{{ item.couponType === '2' ? `${item.discount}折` : item.amount }}</text>
        </view>
        <view class="coupon-info">
          <view class="coupon-name">{{ item.couponName }}</view>
          <view class="coupon-condition">{{ getThresholdText(item) }}</view>
          <view v-if="showReceiveBtn" class="coupon-btn" @click="handleReceive(item.id)">
            领取
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.coupon-receive {
  padding: 12px;

  .coupon-list {
    &.list-style {
      display: flex;
      gap: 16rpx;
      overflow-x: auto;

      &::-webkit-scrollbar {
        display: none;
      }

      .coupon-item {
        flex: 1;
        min-width: 0;
        flex-direction: column;
        align-items: center;

        .coupon-amount {
          width: 100%;
          height: 100rpx;
          border-radius: 12rpx 12rpx 0 0;
        }

        .coupon-info {
          width: 100%;
          padding: 12rpx 16rpx;
          border-radius: 0 0 12rpx 12rpx;
        }
      }
    }

    &.card-style {
      display: flex;
      flex-direction: column;
      gap: 16rpx;

      .coupon-item {
        flex-direction: row;
        height: 140rpx;

        .coupon-amount {
          width: 160rpx;
          height: 100%;
          border-radius: 12rpx 0 0 12rpx;
        }

        .coupon-info {
          flex: 1;
          padding: 16rpx 24rpx;
          border-radius: 0 12rpx 12rpx 0;
        }
      }
    }

    .coupon-item {
      display: flex;
      overflow: hidden;
      background: #fff;
      border-radius: 12rpx;
      box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.08);

      .coupon-amount {
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
        background: linear-gradient(135deg, #f44, #ff6b6b);

        .coupon-symbol {
          font-size: 24rpx;
        }

        .coupon-value {
          font-size: 44rpx;
          font-weight: bold;
        }
      }

      .coupon-info {
        display: flex;
        flex-direction: column;
        justify-content: center;
        background: #fff;

        .coupon-name {
          font-size: 26rpx;
          font-weight: 500;
          color: #333;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .coupon-condition {
          margin-top: 4rpx;
          font-size: 22rpx;
          color: #999;
        }

        .coupon-btn {
          margin-top: 8rpx;
          align-self: flex-start;
          padding: 4rpx 20rpx;
          font-size: 24rpx;
          color: #f44;
          border: 1rpx solid #f44;
          border-radius: 20rpx;
        }
      }
    }
  }
}
</style>
