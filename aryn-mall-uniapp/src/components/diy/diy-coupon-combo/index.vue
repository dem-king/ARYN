<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { addObj } from '@/api/promotion/couponUser'
import { loadCouponCombo } from '@/components/diy/retail-data'
import type { CouponComboProps } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const componentProps = computed(() => props.showData as unknown as CouponComboProps)
const coupons = ref<any[]>([])
const loading = ref(true)
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

async function loadData() {
  loading.value = true
  try {
    coupons.value = await loadCouponCombo(componentProps.value)
  }
  catch {
    coupons.value = []
  }
  finally {
    loading.value = false
  }
}

onMounted(loadData)

async function receive(couponId: string) {
  try {
    await addObj({ couponId })
    uni.showToast({ title: '领取成功', icon: 'success' })
  }
  catch {
    uni.showToast({ title: '领取失败', icon: 'none' })
  }
}
</script>

<template>
  <view class="diy-coupon-combo" :style="dynamicStyles">
    <view v-if="loading" class="combo-empty">加载中…</view>
    <view v-else-if="coupons.length === 0" class="combo-empty">暂无可领优惠券</view>
    <view v-else class="combo-list">
      <view v-for="coupon in coupons" :key="coupon.id" class="combo-card">
        <view class="combo-value">
          {{ coupon.value }}
        </view>
        <view class="combo-info">
          <view class="combo-title">
            {{ coupon.title }}
          </view>
          <view v-if="componentProps.showThreshold !== false" class="combo-threshold">
            {{ coupon.thresholdText }}
          </view>
        </view>
        <view
          v-if="componentProps.showReceiveBtn !== false"
          class="combo-receive"
          @click="receive(coupon.id)"
        >
          领取
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.diy-coupon-combo {
  padding: 20rpx;
}

.combo-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.combo-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  color: #fff;
  background: linear-gradient(135deg, #ff5000, #ff8a00);
  border-radius: 12rpx;
}

.combo-value {
  min-width: 140rpx;
  font-size: 40rpx;
  font-weight: 700;
  text-align: center;
}

.combo-info {
  flex: 1;
}

.combo-title {
  font-size: 28rpx;
  font-weight: 600;
}

.combo-threshold {
  margin-top: 6rpx;
  font-size: 22rpx;
  opacity: 0.85;
}

.combo-receive {
  padding: 10rpx 28rpx;
  font-size: 26rpx;
  color: #ff5000;
  background: #fff;
  border-radius: 999rpx;
}

.combo-empty {
  padding: 40rpx;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>
