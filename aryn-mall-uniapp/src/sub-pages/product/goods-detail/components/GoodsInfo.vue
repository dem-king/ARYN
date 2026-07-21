<script setup lang="ts">
import { getCurrentInstance } from 'vue'

interface Props {
  goodsSpu: any
  couponList: Array<any>
  selectArr: string
  address: any
}

interface Emits {
  (e: 'swiper', obj: any): void
  (e: 'showCoupon'): void
  (e: 'openSkuPopup', value: number): void
  (e: 'toAddress'): void
  (e: 'collect'): void
  (e: 'vipActivate'): void
  (e: 'share'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

function handleSwiper(obj: any) {
  emit('swiper', obj)
}

function showCoupon() {
  emit('showCoupon')
}

function openSkuPopup(value: number) {
  emit('openSkuPopup', value)
}

function toAddress() {
  emit('toAddress')
}

function handleCollect() {
  emit('collect')
}

const proxy = getCurrentInstance()?.proxy

// 获取scroll-pic元素位置的方法
function getScrollPicRect() {
  return new Promise((resolve, reject) => {
    if (!proxy) {
      reject(new Error('组件实例 proxy 为 null'))
      return
    }

    const query = uni.createSelectorQuery().in(proxy)
    query
      .select('#scroll-pic')
      .boundingClientRect((data) => {
        if (data) {
          resolve(data)
        }
        else {
          reject(new Error('Element not found or query failed'))
        }
      })
      .exec()
  })
}
// 分享
function handleShare() {
  emit('share')
}
// 暴露方法给父组件
defineExpose({
  getScrollPicRect,
})
</script>

<template>
  <view id="scroll-pic">
    <wd-swiper
      height="820rpx" :list="goodsSpu?.spuUrls" autoplay :indicator="{ type: 'dots-bar' }"
      @click="handleSwiper"
    />
    <view
      class="relative h-68px rounded-t-xl from-primary to-secondary bg-gradient-to-r p-2 -top-10px"
    >
      <view class="flex items-center justify-between pt-1">
        <!-- 价格 -->
        <view class="flex-1">
          <wd-text size="38rpx" prefix="￥" mode="price" color="white" bold :text="goodsSpu?.salesPrice" />
          <wd-text
            v-if="goodsSpu?.salesPrice < goodsSpu?.originalPrice" color="white" custom-class="pl-5px" size="28rpx" prefix="￥"
            mode="price" :text="goodsSpu?.originalPrice" decoration="line-through"
          />
        </view>
        <!-- 收藏分享 -->
        <view class="flex-shrink-0">
          <view class="flex flex-row">
            <view class="flex flex-col items-center" @click="handleCollect">
              <text v-if="goodsSpu?.collectId" class="i-flowbite:heart-solid text-18px text-red-5 text-white!" />
              <text v-else class="i-flowbite:heart-outline text-18px text-white!" />
              <text class="text-12px text-white!">
                {{ goodsSpu?.collectId ? '已收藏' : '收藏' }}
              </text>
            </view>
            <view class="flex flex-col items-center pl-10px" @click="handleShare">
              <text class="i-flowbite:share-all-outline text-18px text-white!" />
              <text class="text-12px text-white!">
                分享
              </text>
            </view>
          </view>
        </view>
      </view>
    </view>
    <view class="relative rounded-xl bg-white p-2" style="margin-top: -32px;">
      <view class="py-10rpx">
        <!--  商品标题 -->
        <wd-text :lines="2" size="28rpx" color="inherit" bold :text="goodsSpu?.name" />
        <!-- 副标题 -->
        <view v-if="goodsSpu?.subTitle" class="my-10rpx">
          <wd-text :lines="2" size="22rpx" :text="goodsSpu?.subTitle" />
        </view>
      </view>
      <view class="mt-10px flex justify-between px-4px">
        <view class="text-13px">
          库存<text class="px-4rpx">
            {{ goodsSpu.stock }}
          </text>件
        </view>
        <text class="text-13px">
          已售<text class="px-4rpx">
            {{ goodsSpu.salesVolume }}
          </text>件
        </text>
      </view>
    </view>
    <view class="m-2 rounded-xl bg-white p-2">
      <view class="overflow-hidden rounded-lg">
        <view v-if="couponList && couponList.length > 0" class="flex items-center py-3" @click="showCoupon">
          <view class="w-80rpx flex-shrink-0 text-28rpx text-gray-800">
            优惠
          </view>
          <view class="mx-5 flex-1 text-26rpx text-gray-600">
            <view class="line-clamp-1 flex">
              <view v-for="(item, index) in couponList.slice(0, 3)" :key="index" class="coupons_css">
                {{ item.couponName }}
                <view class="leftdot" />
                <view class="rightdot" />
              </view>
            </view>
          </view>
          <view class="flex-shrink-0 text-gray-400">
            <wd-icon :size="16" name="arrow-right" color="var(--wot-cell-arrow-color)" />
          </view>
        </view>
        <view class="flex items-center py-3" @click="openSkuPopup(1)">
          <view class="w-80rpx flex-shrink-0 text-28rpx text-gray-800">
            规格
          </view>
          <view class="mx-5 flex-1 text-left text-26rpx text-gray-600">
            {{ selectArr && selectArr !== '默认' ? selectArr : '选择规格' }}
          </view>
          <view class="flex-shrink-0 text-gray-400">
            <wd-icon :size="16" name="arrow-right" color="var(--wot-cell-arrow-color)" />
          </view>
        </view>
        <view class="flex items-center py-3" @click="toAddress">
          <view class="w-80rpx flex-shrink-0 text-28rpx text-gray-800">
            发货
          </view>
          <view class="mx-5 flex-1 text-left text-26rpx text-gray-600">
            {{ goodsSpu.freightType === '0' ? '包邮' : `运费：${goodsSpu.fixedFreightPrice || 0}元` }}
            <view v-if="address && address.provinceName" class="line-clamp-1 text-12px text-gray-400">
              配送至：{{ address.provinceName || '' }}{{ address.cityName || '' }}{{ address.areaName || '' }}{{
                address.detailAddress }}
            </view>
          </view>
          <view class="flex-shrink-0 text-gray-400">
            <wd-icon :size="16" name="arrow-right" color="var(--wot-cell-arrow-color)" />
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.coupons_css {
  height: 32upx;
  position: relative;
  margin-right: 20upx;
  margin-bottom: 2rpx;
  text-align: center;
  line-height: 32upx;
  padding: 0 14upx;
  border: 1upx solid #ff3d36;
  border-radius: 10upx;
  font-size: 22upx;
  color: #ff3d36;
}

.leftdot {
  position: absolute;
  top: 13upx;
  left: -3upx;
  width: 6upx;
  height: 10upx;
  border-top-right-radius: 20upx;
  border-bottom-right-radius: 20upx;
  border: 1upx solid #ff3d36;
  background-color: #fff;
  border-left: 0;
}

.rightdot {
  position: absolute;
  top: 13upx;
  right: -3upx;
  width: 6upx;
  height: 10upx;
  border-top-left-radius: 20upx;
  border-bottom-left-radius: 20upx;
  border: 1upx solid #ff3d36;
  background-color: #fff;
  border-right: 0;
}
</style>
