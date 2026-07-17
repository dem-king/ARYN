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
    <view v-if="showStyle === '1'" class="coupon-list">
      <view
        v-for="(item, index) in couponList"
        :key="index"
        class="coupon-item"
      >
        <view class="coupon-item-left">
          <view class="coupon-value">
            {{ getCouponValue(item) }}
          </view>
          <view class="coupon-threshold">
            {{ getThresholdText(item) }}
          </view>
        </view>
        <view class="coupon-item-right">
          <view class="coupon-name">
            {{ item.couponName }}
          </view>
          <view v-if="showReceiveBtn" class="coupon-btn" @click="handleReceive(item.id)">
            领取
          </view>
        </view>
      </view>
    </view>
    <view v-else class="coupon-cards">
      <view
        v-for="(item, index) in couponList"
        :key="index"
        class="coupon-card"
      >
        <view class="coupon-card-top">
          <view class="coupon-value">
            {{ getCouponValue(item) }}
          </view>
          <view class="coupon-threshold">
            {{ getThresholdText(item) }}
          </view>
        </view>
        <view class="coupon-card-bottom">
          <view class="coupon-name">
            {{ item.couponName }}
          </view>
          <view v-if="showReceiveBtn" class="coupon-btn" @click="handleReceive(item.id)">
            立即领取
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
    display: flex;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;

    &::-webkit-scrollbar {
      display: none;
    }

    .coupon-item {
      flex-shrink: 0;
      width: 240px;
      display: flex;
      margin-right: 10px;
      border-radius: 8px;
      overflow: hidden;
      background: #fff;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);

      .coupon-item-left {
        width: 80px;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        background: linear-gradient(135deg, #ff6034, #ee0a24);
        color: #fff;
        padding: 12px 0;

        .coupon-value {
          font-size: 20px;
          font-weight: bold;
        }

        .coupon-threshold {
          font-size: 10px;
          margin-top: 4px;
          opacity: 0.9;
        }
      }

      .coupon-item-right {
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: center;
        padding: 10px 12px;

        .coupon-name {
          font-size: 13px;
          color: #333;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .coupon-btn {
          margin-top: 8px;
          align-self: flex-start;
          padding: 2px 10px;
          font-size: 12px;
          color: #ee0a24;
          border: 1px solid #ee0a24;
          border-radius: 12px;
        }
      }
    }
  }

  .coupon-cards {
    display: flex;
    flex-direction: column;

    .coupon-card {
      width: 100%;
      border-radius: 8px;
      overflow: hidden;
      background: #fff;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
      margin-bottom: 10px;

      .coupon-card-top {
        display: flex;
        align-items: center;
        padding: 12px 16px;
        background: linear-gradient(135deg, #ff6034, #ee0a24);
        color: #fff;

        .coupon-value {
          font-size: 24px;
          font-weight: bold;
          margin-right: 8px;
        }

        .coupon-threshold {
          font-size: 12px;
          opacity: 0.9;
        }
      }

      .coupon-card-bottom {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 10px 16px;

        .coupon-name {
          font-size: 14px;
          color: #333;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          flex: 1;
        }

        .coupon-btn {
          flex-shrink: 0;
          margin-left: 12px;
          padding: 4px 14px;
          font-size: 13px;
          color: #fff;
          background: linear-gradient(135deg, #ff6034, #ee0a24);
          border-radius: 14px;
        }
      }
    }
  }
}
</style>
