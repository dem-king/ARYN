<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface Props {
  couponPopup: boolean
  couponUserList: any[]
  orderPrice: number
  orderItemList: any[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  closeCouponPopup: []
  couponConfirm: [couponUserId: string]
}>()

// 使用本地响应式变量替代直接绑定 prop
const localCouponPopup = ref(props.couponPopup)
const selectedCouponId = ref()
// 监听 prop 变化同步到本地变量
watch(() => props.couponPopup, (newVal) => {
  localCouponPopup.value = newVal
})
const couponUnavailableMap = computed(getCouponUnavailableMap)

function closeCouponPopup() {
  emit('closeCouponPopup')
}

function couponConfirm() {
  emit('couponConfirm', selectedCouponId.value)
}
// 选择事件处理：选择当前优惠券（并转为 string）
function handleSelect(item: any) {
  const unavailable = couponUnavailableMap.value?.[item.id]?.unavailable
  if (unavailable) {
    return
  }
  if (item.couponInfo.threshold > 0 && props.orderPrice < item.couponInfo.threshold) {
    return
  }
  if (selectedCouponId.value === item.id) {
    selectedCouponId.value = ''
  }
  else {
    selectedCouponId.value = item.id
  }
}
function getCouponUnavailableMap() {
  return props.couponUserList.reduce((map, item) => {
    map[item.id] = getCouponUnavailableInfo(item)
    return map
  }, {})
}
function getCouponUnavailableInfo(item: any) {
  // 默认返回结果
  const result = { unavailable: false, reason: '' }

  // 优惠券门槛金额
  const threshold = item?.couponInfo?.threshold ?? 0
  // 优惠券使用范围（1：全场通用，2：部分商品可用）
  const useRange = item?.couponInfo?.useRange
  const orderSpuIds = props.orderItemList.map(i => i.spuId)

  // ② 判断是否部分商品可用
  if (useRange === '2') {
    const couponSpuIds = (item.couponGoodsList || []).map(i => i.spuId)
    const hasMatched = orderSpuIds.some(spuId => couponSpuIds.includes(spuId))
    if (!hasMatched) {
      result.unavailable = true
      result.reason = '仅部分商品可用（本单无可用商品）'
      return result
    }
  }

  // ① 判断门槛金额
  if (threshold > 0 && props.orderPrice < threshold) {
    result.unavailable = true
    result.reason = `订单未满 ${threshold} 元`
    return result
  }

  return result
}
</script>

<template>
  <wd-action-sheet
    v-model="localCouponPopup" safe-area-inset-bottom custom-class="coupon-action" title="选择优惠券"
    @close="closeCouponPopup"
  >
    <scroll-view scroll-y class="mb-4 h-400px">
      <view v-for="item in couponUserList" :key="item.id" class="px-4 py-1">
        <view class="coupon-card coupon-img-style" :class="{ 'coupon-disabled': couponUnavailableMap[item.id]?.unavailable }" @click="handleSelect(item)">
          <!-- 左侧金额区 -->
          <view class="coupon-left-img">
            <view class="coupon-amount-img">
              {{ item.couponInfo.couponType === '1' ? `￥${item.couponInfo.amount}` : `${item.couponInfo.discount}折` }}
            </view>
            <view class="coupon-threshold-img">
              {{ item.couponInfo.threshold && item.couponInfo.threshold > 0 ? `满${item.couponInfo.threshold}元可用` : '无门槛优惠券' }}
            </view>
            <view class="coupon-notch-img" />
          </view>
          <!-- 右侧信息区 -->
          <view class="coupon-right-img">
            <view class="coupon-title-img">
              {{ item.couponInfo.couponName }}
            </view>
            <view class="coupon-desc-img">
              {{ item.couponInfo.useRange === '1' ? '全部商品可用' : '部分商品可用' }}
            </view>
          </view>
          <view class="coupon-btn-img">
            <!-- 选中显示对勾图标；未选中显示圆圈图标 -->
            <wd-icon
              v-if="selectedCouponId === item.id"
              name="check-outline"
              size="22px"
            />
            <wd-icon
              v-else
              name="circle1"
              size="22px"
            />
          </view>
          <!-- 不可用蒙层 -->
          <view v-if="couponUnavailableMap[item.id].unavailable" class="coupon-mask-img" />
        </view>
        <view v-if="couponUnavailableMap[item.id].unavailable" class="flex items-center px-2 py-1 text-12px text-primary">
          优惠券不可用原因：{{ couponUnavailableMap[item.id].reason }}
        </view>
      </view>
    </scroll-view>
    <view class="border-1 border-gray-300 border-t-solid p-1">
      <wd-button block type="primary" @click="couponConfirm">
        确认
      </wd-button>
    </view>
  </wd-action-sheet>
</template>

<style lang="scss" scoped>
.coupon-action {
  max-height: 500px;
}
.coupon-card {
  position: relative;
  width: 100%;
  height: 160rpx;
  border-radius: 24rpx;
  overflow: hidden;
  display: flex;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  &:active {
    transform: scale(0.98);
  }
}
.coupon-disabled {
  pointer-events: none;
}
.coupon-disabled:active {
  transform: none;
}
.coupon-img-style {
  display: flex;
  align-items: stretch;
  border-radius: 20rpx;
  background: linear-gradient(90deg, #ff5a4a 0%, #ff2d2d 100%);
  border: 2px solid #ff2d2d;
  position: relative;
  min-height: 140rpx;
  overflow: visible;
}
.coupon-left-img {
  width: 180rpx;
  background: #fff;
  border-top-left-radius: 20rpx;
  border-bottom-left-radius: 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20rpx 0;
  position: relative;
  z-index: 100;
  &::after{
    content: "";
    position: absolute;
    right: -0.8rem;
    top: 50%;
    transform: translateY(-50%);
    width: 2rem;
    height: 100%;
    border-radius: 50%;
    background: #fff;
    z-index: -10;
  }
}
.coupon-amount-img {
  color: #ff2d2d;
  font-size: 44rpx;
  font-weight: bold;
  line-height: 1.1;
}
.coupon-threshold-img {
  color: #bfbfbf;
  font-size: 20rpx;
  margin-top: 4rpx;
}
.coupon-notch-img {
  position: absolute;
  right: -20rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 40rpx;
  height: 40rpx;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
  z-index: 2;
}
.coupon-right-img {
  flex: 1;
  padding: 18rpx 24rpx 18rpx 18rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  margin-left: 10px;
}
.coupon-title-img {
  color: #fff;
  font-size: 28rpx;
  font-weight: bold;
  margin-bottom: 8rpx;
}
.coupon-countdown-img {
  color: #fff;
  font-size: 22rpx;
  font-weight: bold;
  margin-bottom: 4rpx;
}
.coupon-desc-img {
  color: #fff;
  font-size: 20rpx;
  margin-top: 2rpx;
}
.coupon-btn-img {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  background: #fff;
  color: #ff2d2d;
  font-size: 22rpx;
  font-weight: bold;
  border-radius: 24rpx;
  padding: 8rpx 18rpx;
  box-shadow: 0 2rpx 8rpx rgba(255, 107, 107, 0.08);
  cursor: pointer;
  transition: background 0.2s;
  border: none;
}
.coupon-btn-img:active {
  background: #ffeaea;
}
.coupon-tag-img {
  position: absolute;
  top: 0;
  right: 0;
  background: #ff2d2d;
  color: #fff;
  font-size: 20rpx;
  font-weight: bold;
  padding: 0 24rpx;
  height: 40rpx;
  line-height: 40rpx;
  border-top-right-radius: 20rpx;
  border-bottom-left-radius: 20rpx;
  transform: rotate(25deg) translate(18rpx, -12rpx);
  box-shadow: 0 2rpx 8rpx rgba(255, 107, 107, 0.12);
  z-index: 2;
}
.coupon-mask-img {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(200, 200, 200, 0.2);
  border-radius: 20rpx;
  z-index: 1;
  pointer-events: auto;
}
@media (max-width: 750rpx) {
  .coupon-left-img {
    width: 110rpx;
    padding: 12rpx 0;
  }
  .coupon-amount-img {
    font-size: 32rpx;
  }
  .coupon-title-img {
    font-size: 22rpx;
  }
  .coupon-btn-img {
    font-size: 18rpx;
    padding: 6rpx 18rpx;
  }
}
</style>
