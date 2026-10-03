<script setup lang="ts">
import type { AppSeckillGoodsVO } from '@/api/promotion'
import type { SpecRowView } from '@/utils/goods-purchase'

import { computed, getCurrentInstance, onBeforeUnmount, ref, watch } from 'vue'
import { useShipContextStore } from '@/store/shipContextStore'
import { resolveDeliveryRow } from '@/utils/goods-purchase'

interface Props {
  goodsSpu: any
  couponList: Array<any>
  /** 规格行视图：父级在注入「默认」占位规格前用 resolvePurchaseDecision/resolveSpecRow 算好 */
  specRow: SpecRowView
  address: any
  /** 当前 SKU 的秒杀信息；null 表示无进行中的秒杀 */
  seckillInfo?: AppSeckillGoodsVO | null
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

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const shipContextStore = useShipContextStore()

/** 秒杀剩余库存：后端 Redis 缺失时以「总量-已售」兜底 */
const seckillRemaining = computed(() => {
  const info = props.seckillInfo
  if (!info)
    return 0
  return info.remainingStock ?? Math.max(0, (info.seckillStock || 0) - (info.soldCount || 0))
})

/** 场次是否已结束 */
const seckillFinished = ref(false)
const seckillCountdownText = ref('')

let seckillTimer: ReturnType<typeof setInterval> | null = null

/** 每秒刷新秒杀倒计时；场次结束时停表并展示「已结束」 */
function startSeckillCountdown() {
  stopSeckillCountdown()
  if (!props.seckillInfo?.sessionEndTime) {
    seckillFinished.value = true
    return
  }
  updateSeckillCountdown()
  seckillTimer = setInterval(updateSeckillCountdown, 1000)
}

function updateSeckillCountdown() {
  const end = props.seckillInfo?.sessionEndTime
  if (!end) {
    seckillFinished.value = true
    stopSeckillCountdown()
    return
  }
  // iOS 下 new Date('yyyy-MM-dd HH:mm:ss') 解析失败，统一替换斜杠
  const diff = new Date(end.replace(/-/g, '/')).getTime() - Date.now()
  if (diff <= 0) {
    seckillFinished.value = true
    seckillCountdownText.value = ''
    stopSeckillCountdown()
    return
  }
  seckillFinished.value = false
  const totalSeconds = Math.floor(diff / 1000)
  const hours = String(Math.floor(totalSeconds / 3600)).padStart(2, '0')
  const minutes = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, '0')
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  seckillCountdownText.value = `${hours}:${minutes}:${seconds}`
}

function stopSeckillCountdown() {
  if (seckillTimer) {
    clearInterval(seckillTimer)
    seckillTimer = null
  }
}

watch(() => props.seckillInfo, (info) => {
  if (info) {
    startSeckillCountdown()
  }
  else {
    stopSeckillCountdown()
    seckillFinished.value = false
    seckillCountdownText.value = ''
  }
}, { immediate: true })

onBeforeUnmount(stopSeckillCountdown)

/**
 * 发货行：已绑定船舶上下文时订单将按内部配送（delivery_way=4）履约，
 * 展示「配送至 船舶」+ 靠港信息（与结算页同口径），而不是收货地址——
 * 船供订单不走收货地址，原先显示「配送至：某某小区」是误导。
 */
const deliveryRow = computed(() => resolveDeliveryRow({
  hasVesselContext: shipContextStore.hasVesselContext,
  vesselName: shipContextStore.vesselName,
  portName: shipContextStore.portName,
  berth: shipContextStore.berth,
  deliveryWindowStart: shipContextStore.deliveryWindowStart,
  deliveryWindowEnd: shipContextStore.deliveryWindowEnd,
  freightType: props.goodsSpu?.freightType,
  fixedFreightPrice: props.goodsSpu?.fixedFreightPrice,
  addressText: formatAddressText(props.address),
}))

function formatAddressText(address: any) {
  if (!address?.provinceName)
    return ''
  return [address.provinceName, address.cityName, address.areaName, address.detailAddress]
    .filter(Boolean)
    .join('')
}

function handleSwiper(obj: any) {
  emit('swiper', obj)
}

function showCoupon() {
  emit('showCoupon')
}

function openSkuPopup(value: number) {
  emit('openSkuPopup', value)
}

/** 内部配送的船舶/靠港在购物车与工作台切换，行本身只做展示 */
function onDeliveryRowTap() {
  if (deliveryRow.value.tappable)
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
        <!-- 价格：秒杀进行中时以秒杀价为主价，销售价划线 -->
        <view class="flex-1">
          <wd-text
            size="38rpx" prefix="￥" mode="price" color="white" bold
            :text="seckillInfo ? seckillInfo.seckillPrice : goodsSpu?.salesPrice"
          />
          <wd-text
            v-if="seckillInfo || goodsSpu?.salesPrice < goodsSpu?.originalPrice" color="white" custom-class="pl-5px" size="28rpx" prefix="￥"
            mode="price" :text="seckillInfo ? goodsSpu?.salesPrice : goodsSpu?.originalPrice" decoration="line-through"
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
    <!-- 秒杀条：有进行中的秒杀才展示；已抢完/已结束仅提示，不影响加购 -->
    <view v-if="seckillInfo" class="seckill-strip">
      <view class="seckill-tag">
        限时秒杀
      </view>
      <view v-if="!seckillFinished" class="seckill-countdown">
        还剩
        <text class="seckill-time">
          {{ seckillCountdownText }}
        </text>
      </view>
      <text v-else class="seckill-over">
        本场已结束
      </text>
      <view class="seckill-meta">
        <text v-if="seckillInfo.limitPerUser">
          每人限购{{ seckillInfo.limitPerUser }}件
        </text>
        <text v-if="seckillRemaining > 0" class="seckill-remaining">
          仅剩{{ seckillRemaining }}件
        </text>
        <text v-else class="seckill-remaining">
          已抢完
        </text>
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
        <!--
          规格行：多规格是「选择规格」入口；单规格显示真实规格值（没有规格值时整行隐藏，
          规格信息在商品名/船供箱规里），点击只调数量——弹层对单规格不渲染规格选择区。
        -->
        <view v-if="specRow.visible" class="flex items-center py-3" @click="openSkuPopup(2)">
          <view class="w-80rpx flex-shrink-0 text-28rpx text-gray-800">
            规格
          </view>
          <view class="mx-5 flex-1 text-left text-26rpx text-gray-600">
            {{ specRow.text }}
          </view>
          <view class="flex-shrink-0 text-gray-400">
            <wd-icon :size="16" name="arrow-right" color="var(--wot-cell-arrow-color)" />
          </view>
        </view>
        <view class="flex items-center py-3" @click="onDeliveryRowTap">
          <view class="w-80rpx flex-shrink-0 text-28rpx text-gray-800">
            配送
          </view>
          <view class="mx-5 flex-1 text-left text-26rpx text-gray-600">
            {{ deliveryRow.text }}
            <view v-if="deliveryRow.detail" class="line-clamp-1 text-12px text-gray-400">
              {{ deliveryRow.detail }}
            </view>
          </view>
          <view v-if="deliveryRow.tappable" class="flex-shrink-0 text-gray-400">
            <wd-icon :size="16" name="arrow-right" color="var(--wot-cell-arrow-color)" />
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
// 秒杀条：紧贴价格渐变区下方，展示倒计时与限购信息
.seckill-strip {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: -20rpx 16rpx 12rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background: #fff1ee;

  .seckill-tag {
    flex-shrink: 0;
    padding: 4rpx 14rpx;
    border-radius: 8rpx;
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b35), var(--wot-color-theme-primary, #ff4500));
    color: #fff;
    font-size: 22rpx;
    font-weight: 600;
  }

  .seckill-countdown {
    color: var(--wot-color-theme-primary, #ff2237);
    font-size: 24rpx;

    .seckill-time {
      font-weight: 700;
    }
  }

  .seckill-over {
    color: #999;
    font-size: 24rpx;
  }

  .seckill-meta {
    display: flex;
    flex: 1;
    justify-content: flex-end;
    gap: 16rpx;
    color: #999;
    font-size: 22rpx;

    .seckill-remaining {
      color: var(--wot-color-theme-primary, #ff2237);
    }
  }
}

.coupons_css {
  height: 32upx;
  position: relative;
  margin-right: 20upx;
  margin-bottom: 2rpx;
  text-align: center;
  line-height: 32upx;
  padding: 0 14upx;
  border: 1upx solid var(--wot-color-theme-primary, #ff2237);
  border-radius: 10upx;
  font-size: 22upx;
  color: var(--wot-color-theme-primary, #ff2237);
}

.leftdot {
  position: absolute;
  top: 13upx;
  left: -3upx;
  width: 6upx;
  height: 10upx;
  border-top-right-radius: 20upx;
  border-bottom-right-radius: 20upx;
  border: 1upx solid var(--wot-color-theme-primary, #ff2237);
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
  border: 1upx solid var(--wot-color-theme-primary, #ff2237);
  background-color: #fff;
  border-right: 0;
}
</style>
