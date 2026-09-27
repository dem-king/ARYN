<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getPage } from '@/api/promotion/couponInfo'
import { addObj } from '@/api/promotion/couponUser'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { couponThresholdText, couponType, couponValueParts } from '@/utils/coupon-display'
import { couponCardLayout } from '../coupon-card-layout'
import { mergeReceivedCouponIds } from '../coupon-received-state'

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
// 本地记录已领取的优惠券ID，避免领取后卡片消失
const receivedIds = ref<Set<string>>(new Set())
// 卡片宽度按实际券数决定：券不足一行时长大填满，但单张不超过半行
const cardLayout = computed(() => couponCardLayout(couponList.value.length, showNum.value))
const couponCardStyle = computed(() => showStyle.value === '1' ? cardLayout.value.style : undefined)

async function fetchCoupons() {
  try {
    const res = await getPage({
      current: 1,
      size: showNum.value,
    })
    couponList.value = res.records || []
    // 匿名领券列表不含个人领取计数；登录用户数据用于恢复刷新后的状态。
    receivedIds.value = mergeReceivedCouponIds(receivedIds.value, couponList.value)
  }
  catch {
    couponList.value = []
  }
}

function isReceived(item: any) {
  return receivedIds.value.has(item.id)
}

async function handleReceive(couponId: string) {
  // 已领取过的不再重复点击
  if (receivedIds.value.has(couponId))
    return
  try {
    await addObj({ couponId })
    uni.showToast({ title: '领取成功', icon: 'success' })
    // 本地标记为已领取，不重新拉列表，避免卡片因库存不足被后端过滤而消失
    receivedIds.value.add(couponId)
  }
  catch (err: any) {
    const msg = err?.message || '领取失败'
    uni.showToast({ title: msg, icon: 'none' })
  }
}

/** 金额区：拆成 符号 / 数值 / 单位 三段，与券卡组件同一口径 */
function valueParts(item: any) {
  return couponValueParts(item)
}

/** 券类型标签，对应券卡上的粉色胶囊 */
function typeLabel(item: any) {
  return couponType(item) === '2' ? '折扣券' : '满减券'
}

/** 门槛文案 */
function thresholdText(item: any) {
  return couponThresholdText(item)
}

onMounted(() => {
  fetchCoupons()
})
</script>

<template>
  <view class="coupon-receive" :style="dynamicStyles">
    <!--
      票面样式对齐券卡组件（浅色票面 + 渐变描边 + 底部状态带 + 票根缺口）：
      showStyle=1：横向一排小卡片（竖版票），券不足一行时居中
      showStyle=2：纵向大卡片（横版票），金额在左、名称在右
    -->
    <view
      class="coupon-list"
      :class="{
        'list-style': showStyle === '1',
        'card-style': showStyle === '2',
        'list-style-centered': showStyle === '1' && !cardLayout.fillsRow,
      }"
    >
      <view
        v-for="(item, index) in couponList"
        :key="index"
        class="coupon-item"
        :style="couponCardStyle"
      >
        <view class="coupon-face">
          <view class="coupon-notch coupon-notch--left" />
          <view class="coupon-notch coupon-notch--right" />

          <view class="coupon-pill">
            {{ typeLabel(item) }}
          </view>
          <view class="amount-row">
            <text v-if="valueParts(item).symbol" class="coupon-symbol">
              {{ valueParts(item).symbol }}
            </text>
            <text class="coupon-value">
              {{ valueParts(item).value }}
            </text>
            <text v-if="valueParts(item).unit" class="coupon-unit">
              {{ valueParts(item).unit }}
            </text>
          </view>
          <view class="coupon-name">
            {{ item.couponName }}
          </view>
        </view>

        <view class="coupon-band">
          <text class="coupon-condition">
            {{ thresholdText(item) }}
          </text>
          <view
            v-if="showReceiveBtn"
            class="coupon-btn"
            :class="{ 'coupon-btn--received': isReceived(item) }"
            @click.stop="handleReceive(item.id)"
          >
            {{ isReceived(item) ? '已领取' : '领取' }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/coupon-ticket.scss';

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

      // 券不足一行时居中，避免空白全部堆在右侧
      &.list-style-centered {
        justify-content: center;
      }

      .coupon-item {
        // 宽度由 couponCardLayout() 按实际券数以内联样式给出
        flex-direction: column;
      }
    }

    &.card-style {
      display: flex;
      flex-direction: column;
      gap: 16rpx;

      .coupon-item {
        flex-direction: column;
      }
    }

    .coupon-item {
      display: flex;
      box-sizing: border-box;
      padding: $coupon-rim-width;
      // 裁掉票根缺口伸到卡外的一半
      overflow: hidden;
      border-radius: $coupon-radius;
      @include coupon-rim($coupon-rim-start, $coupon-rim-end);
    }

    // 票面：竖版票居中排布，横版票（card-style）金额与名称左右分栏
    .coupon-face {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 16rpx 12rpx;
      border-radius: ($coupon-radius - $coupon-rim-width) ($coupon-radius - $coupon-rim-width) 0 0;
      @include coupon-face($coupon-face-start, $coupon-face-end);
    }

    .coupon-notch {
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

    .coupon-pill {
      padding: 1rpx 12rpx;
      font-size: 18rpx;
      color: $coupon-pill-text;
      background: $coupon-pill-bg;
      border-radius: 999rpx;
    }

    .amount-row {
      display: flex;
      align-items: baseline;
      justify-content: center;
      margin-top: 6rpx;
      color: $coupon-value;
    }

    .coupon-symbol {
      font-size: 24rpx;
      font-weight: 600;
    }

    .coupon-value {
      font-size: 44rpx;
      font-weight: 700;
      line-height: 1.05;
    }

    .coupon-unit {
      font-size: 24rpx;
      font-weight: 600;
    }

    .coupon-name {
      width: 100%;
      margin-top: 6rpx;
      overflow: hidden;
      font-size: 22rpx;
      color: $coupon-title;
      text-align: center;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    // 状态带：左门槛右领券按钮，把「能不能用 + 能不能领」放在同一行
    .coupon-band {
      display: flex;
      flex-direction: row;
      align-items: center;
      justify-content: space-between;
      box-sizing: border-box;
      height: $coupon-band-height;
      padding: 0 12rpx;
      border-radius: 0 0 ($coupon-radius - $coupon-rim-width) ($coupon-radius - $coupon-rim-width);
      @include coupon-band($coupon-band-start, $coupon-band-end);
    }

    .coupon-condition {
      overflow: hidden;
      font-size: 18rpx;
      color: #fff;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .coupon-btn {
      flex-shrink: 0;
      padding: 2rpx 12rpx;
      margin-left: 8rpx;
      font-size: 20rpx;
      color: $coupon-value;
      background: #fff;
      border-radius: 999rpx;

      // 已领取：白色按钮降级为半透明，避免看起来仍可点击
      &--received {
        color: #fff;
        background: rgba(255, 255, 255, 0.35);
      }
    }
  }
}
</style>
