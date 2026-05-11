<script setup lang="ts">
import { computed } from 'vue'
import type { CouponInfo } from '@/api/promotion/couponInfo'
import { addObj } from '@/api/promotion/couponUser'

interface Props {
  coupon: CouponInfo
  /**
   * 优惠券状态
   * available: 可领取
   * received: 已领取
   */
  status?: 'available' | 'received'
}

const props = withDefaults(defineProps<Props>(), {
  status: 'available',
  selectable: false,
})
const emit = defineEmits(['receive'])
const router = useRouter()
const globalLoading = useGlobalLoading()
const userStore = useUserStore()
const { show } = useGlobalToast()

// 优惠券金额显示
const amountDisplay = computed(() => {
  if (props.coupon.couponType === '1') {
    // 满减券
    return `￥${props.coupon.amount}`
  }
  else {
    // 折扣券
    return `${props.coupon.discount}折`
  }
})

// 优惠券描述
const description = computed(() => {
  if (props.coupon.threshold && props.coupon.threshold > 0) {
    return `满${props.coupon.threshold}元可用`
  }
  return '无门槛优惠券'
})

// 领取/使用事件处理
async function handleReceive() {
  if (props.status === 'available') {
    globalLoading.loading('领取中...')
    try {
      await addObj({ couponId: props.coupon.id })
      show('领取成功')
      emit('receive', props.coupon)
      // 刷新本地缓存
      userStore.refreshUserCouponCount()
    }
    catch (err: any) {
      console.log(err.message)
    }
    finally {
      globalLoading.close()
    }
  }
  else if (props.status === 'received') {
    router.push({
      name: 'goods-list',
    })
  }
}
</script>

<template>
  <view class="coupon-card coupon-img-style" @click="handleClick">
    <!-- 左侧金额区 -->
    <view class="coupon-left-img">
      <view class="coupon-amount-img">
        {{ amountDisplay }}
      </view>
      <view class="coupon-threshold-img">
        {{ description }}
      </view>
      <view class="coupon-notch-img" />
    </view>
    <!-- 右侧信息区 -->
    <view class="coupon-right-img">
      <view class="coupon-title-img">
        {{ coupon.couponName }}
      </view>
      <view class="coupon-desc-img">
        {{ coupon.useRange === '1' ? '全部商品可用' : '部分商品可用' }}
      </view>
    </view>
    <!-- 按钮/选择区 -->
    <view v-if="status === 'available'" class="coupon-btn-img" @click.stop="handleReceive">
      立即领取
    </view>
    <!-- 已领取：根据 selectable 切换为按钮或图标选择 -->
    <view v-else-if="status === 'received' && !selectable" class="coupon-btn-img" @click.stop="handleReceive">
      去使用
    </view>
  </view>
</template>

<style lang="scss" scoped>
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
.coupon-shop-img {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
}
.coupon-title-img {
  color: #fff;
  font-size: 28rpx;
  font-weight: bold;
  margin-bottom: 8rpx;
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
  width: 100rpx;
  text-align: center;
  border: none;
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
