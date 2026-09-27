<script setup lang="ts">
import type { CouponComboProps } from '@/components/diy/retail-types'

import { computed, onMounted, ref } from 'vue'
import { addObj } from '@/api/promotion/couponUser'
import { loadCouponCombo } from '@/components/diy/retail-data'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { mergeReceivedCouponIds } from '../coupon-received-state'

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
// 本地记录已领取的优惠券ID
const receivedIds = ref<Set<string>>(new Set())

async function loadData() {
  loading.value = true
  try {
    coupons.value = await loadCouponCombo(componentProps.value)
    receivedIds.value = mergeReceivedCouponIds(receivedIds.value, coupons.value)
  }
  catch {
    coupons.value = []
  }
  finally {
    loading.value = false
  }
}

onMounted(loadData)

function isReceived(couponId: string) {
  return receivedIds.value.has(couponId)
}

async function receive(couponId: string) {
  if (isReceived(couponId))
    return
  try {
    await addObj({ couponId })
    uni.showToast({ title: '领取成功', icon: 'success' })
    receivedIds.value.add(couponId)
  }
  catch (err: any) {
    const msg = err?.message || '领取失败'
    uni.showToast({ title: msg, icon: 'none' })
  }
}
</script>

<template>
  <view class="diy-coupon-combo" :style="dynamicStyles">
    <view v-if="loading" class="combo-empty">
      加载中…
    </view>
    <view v-else-if="coupons.length === 0" class="combo-empty">
      暂无可领优惠券
    </view>
    <view v-else class="combo-list">
      <view v-for="coupon in coupons" :key="coupon.id" class="combo-card">
        <view class="combo-face">
          <view class="combo-notch combo-notch--left" />
          <view class="combo-notch combo-notch--right" />
          <view class="combo-value">
            {{ coupon.value }}
          </view>
          <view class="combo-info">
            <view class="combo-pill">
              {{ coupon.typeLabel }}
            </view>
            <view class="combo-title">
              {{ coupon.title }}
            </view>
            <view v-if="componentProps.showThreshold !== false" class="combo-threshold">
              {{ coupon.thresholdText }}
            </view>
          </view>
          <!--
            按钮放在票面内部：放到票面外会直接压在描边渐变上，
            红色按钮叠红色描边既看不出形状也削弱可点暗示。
          -->
          <view
            v-if="componentProps.showReceiveBtn !== false"
            class="combo-receive"
            :class="{ 'combo-received': isReceived(coupon.id) }"
            @click.stop="receive(coupon.id)"
          >
            {{ isReceived(coupon.id) ? '已领取' : '领取' }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/coupon-ticket.scss';

.diy-coupon-combo {
  padding: 20rpx;
}

.combo-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

/*
  票面样式与券卡组件同源：渐变描边 + 浅色票面 + 票根缺口。
  这里保留「金额左、文案中、领取按钮右」的横版票布局，
  票根缺口与券卡一致地落在票面左右边缘的垂直中线上。
*/
.combo-card {
  position: relative;
  display: flex;
  flex-direction: row;
  box-sizing: border-box;
  align-items: center;
  padding: $coupon-rim-width;
  overflow: hidden;
  border-radius: $coupon-radius;
  @include coupon-rim($coupon-rim-start, $coupon-rim-end);
}

.combo-notch {
  position: absolute;
  top: 50%;
  z-index: 2;
  width: $coupon-notch-size;
  height: $coupon-notch-size;
  border-radius: 50%;
  transform: translateY(-50%);

  &--left {
    left: -($coupon-notch-size * 0.5);
    background: $coupon-notch-left;
  }

  &--right {
    right: -($coupon-notch-size * 0.5);
    background: $coupon-notch-right;
  }
}

.combo-face {
  position: relative;
  display: flex;
  flex-direction: row;
  box-sizing: border-box;
  flex: 1;
  align-items: center;
  min-height: 100rpx;
  padding: 14rpx 20rpx;
  border-radius: $coupon-radius - $coupon-rim-width;
  @include coupon-face($coupon-face-start, $coupon-face-end);
}

.combo-value {
  display: flex;
  flex-shrink: 0;
  align-items: baseline;
  justify-content: center;
  width: 140rpx;
  font-size: 44rpx;
  font-weight: 700;
  color: $coupon-value;
}

.combo-info {
  flex: 1;
  min-width: 0;
  padding-left: 16rpx;
}

.combo-pill {
  display: inline-block;
  padding: 2rpx 14rpx;
  font-size: 20rpx;
  color: $coupon-pill-text;
  background: $coupon-pill-bg;
  border-radius: 999rpx;
}

.combo-title {
  margin-top: 6rpx;
  overflow: hidden;
  font-size: 26rpx;
  font-weight: 600;
  color: $coupon-title;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.combo-threshold {
  margin-top: 4rpx;
  overflow: hidden;
  font-size: 20rpx;
  color: $coupon-desc;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.combo-receive {
  flex-shrink: 0;
  align-self: center;
  padding: 8rpx 22rpx;
  margin-left: 12rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: #fff;
  border-radius: 999rpx;
  @include coupon-rim($coupon-rim-start, $coupon-rim-end);

  // 已领取：降级为中性灰，避免看起来仍可点击
  &.combo-received {
    color: #fff;
    @include coupon-rim($coupon-muted-rim-start, $coupon-muted-rim-end);
  }
}

.combo-empty {
  padding: 40rpx;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>
