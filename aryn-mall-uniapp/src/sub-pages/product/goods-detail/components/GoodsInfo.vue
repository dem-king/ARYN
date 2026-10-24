<script setup lang="ts">
import type { AppSeckillGoodsVO } from '@/api/promotion'
import type { DiscountPriceDisplay } from '@/utils/discount-price'
import type { SpecRowView } from '@/utils/goods-purchase'
import type { NewbiePriceDisplay } from '@/utils/newbie-price'

import { computed, getCurrentInstance, onBeforeUnmount, ref, watch } from 'vue'
import { useShipContextStore } from '@/store/shipContextStore'
import { resolveDeliveryRow } from '@/utils/goods-purchase'
import { NEWBIE_BADGE_TEXT, newbieLimitHint } from '@/utils/newbie-price'
import GoodsAttrTags from './GoodsAttrTags.vue'

interface Props {
  goodsSpu: any
  couponList: Array<any>
  /** 规格行视图：父级在注入「默认」占位规格前用 resolvePurchaseDecision/resolveSpecRow 算好 */
  specRow: SpecRowView
  address: any
  /** 当前 SKU 的秒杀信息；null 表示无进行中的秒杀 */
  seckillInfo?: AppSeckillGoodsVO | null
  /**
   * 当前 SKU 的新人价展示：父级已按「秒杀 > 新人价」的优先级算好，
   * 秒杀进行中时传的是空展示，这里不用再重复判定优先级。
   */
  newbieDisplay: NewbiePriceDisplay
  /**
   * 当前 SKU 的限时折扣展示：父级已按「秒杀 > 新人价 > 限时折扣」的优先级算好，
   * 上层活动命中时传的是空展示，这里不用再重复判定优先级。
   */
  discountDisplay: DiscountPriceDisplay
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

/** 轮播页码：参考商超详情页的 1/5 角标，用户能感知还有多少张图 */
const currentImage = ref(0)
const imageCount = computed(() => props.goodsSpu?.spuUrls?.length || 0)
const isFreeShipping = computed(() => props.goodsSpu?.freightType === '0')

function onSwiperChange(event: any) {
  currentImage.value = Number(event?.current) || 0
}

/**
 * 价格区主价：秒杀 > 新人价 > 销售价（与后端 OrderPriceComputeService 的
 * 「拼团 > 秒杀 > 新人价 > 限时折扣 > 原价」同向，前端只覆盖到这个页面能拿到的三层）。
 */
const mainPrice = computed(() => {
  if (props.seckillInfo)
    return props.seckillInfo.seckillPrice
  if (props.newbieDisplay.showNewbiePrice)
    return props.newbieDisplay.newbiePrice
  if (props.discountDisplay.showDiscountPrice)
    return props.discountDisplay.discountPrice
  return props.goodsSpu?.salesPrice
})

/**
 * 价格区划线价：
 * - 秒杀：划当前售价（后端口径：秒杀价基于 salesPrice 降）；
 * - 新人价：划新人价里的 originalPrice（后端回填的当前售价），
 *   没有则不划——自己划自己只会制造噪音；
 * - 无活动：沿用既有「售价 < 原价」的划线口径（与 diy/price-display 同源）。
 */
const strikePrice = computed(() => {
  if (props.seckillInfo)
    return props.goodsSpu?.salesPrice
  if (props.newbieDisplay.showNewbiePrice)
    return props.newbieDisplay.originalPrice
  // 限时折扣的基准价是 SKU 当前售价（会场卡片同样划这个价），
  // 不能继续划商品原价（38.87）——那会把 8 折说成 7.7 折，与会场口径矛盾
  if (props.discountDisplay.showDiscountPrice)
    return props.discountDisplay.originalPrice
  return props.goodsSpu?.salesPrice < props.goodsSpu?.originalPrice
    ? props.goodsSpu?.originalPrice
    : null
})

/** 「省 ¥x」：有划线价才展示，给价格区一个正向的成交理由 */
const savingAmount = computed(() => {
  const main = Number(mainPrice.value)
  const strike = Number(strikePrice.value)
  if (!Number.isFinite(main) || !Number.isFinite(strike) || strike <= main)
    return ''
  return (strike - main).toFixed(2)
})

/** 副标题与商品名重复时整行隐藏：重复的灰字只会让首屏更空 */
const showSubtitle = computed(() => {
  const subtitle = String(props.goodsSpu?.subTitle || '').trim()
  const name = String(props.goodsSpu?.name || '').trim()
  return subtitle.length > 0 && subtitle !== name
})

/** 可享新人价时的限购提示；不适用时为空串（避免渲染空条） */
const newbieLimitText = computed(() => newbieLimitHint(props.newbieDisplay))

/**
 * 价格区下方是否存在活动说明条。
 *
 * 标题卡原设计用 `margin-top: -32px` 上提、与价格渐变区咬合；一旦中间插入了
 * 活动条（秒杀/新人价/限时折扣），上提量会把标题拖进条内、首行被裁掉。
 * 有条时取消上提，让标题卡排在条下方。
 */
const hasPriceStrip = computed(() =>
  Boolean(props.seckillInfo)
  || Boolean(newbieLimitText.value || props.newbieDisplay.ineligibleText)
  || props.discountDisplay.showDiscountPrice,
)

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
    <view class="goods-swiper">
      <wd-swiper
        height="750rpx" :list="goodsSpu?.spuUrls" autoplay :indicator="{ type: 'dots-bar' }"
        @click="handleSwiper" @change="onSwiperChange"
      />
      <view v-if="imageCount > 1" class="goods-swiper__counter">
        {{ currentImage + 1 }}/{{ imageCount }}
      </view>
    </view>
    <view
      class="relative h-72px rounded-t-xl from-primary to-secondary bg-gradient-to-r px-3 py-2 -top-10px"
    >
      <view class="flex items-center justify-between pt-1">
        <!-- 价格：秒杀 > 新人价 > 销售价；主价旁的划线价随主价口径切换 -->
        <view class="flex-1">
          <view class="flex flex-wrap items-center">
            <wd-text
              size="44rpx" prefix="￥" mode="price" color="white" bold
              :text="mainPrice"
            />
            <!-- 新人价角标：白字底上用白色描边胶囊，不引入额外色块压住渐变主色 -->
            <text v-if="newbieDisplay.showNewbiePrice" class="newbie-badge">
              {{ NEWBIE_BADGE_TEXT }}
            </text>
            <text v-if="discountDisplay.showDiscountPrice" class="discount-badge">
              {{ discountDisplay.badgeText }}
            </text>
            <text v-if="savingAmount" class="saving-badge">
              省￥{{ savingAmount }}
            </text>
          </view>
          <wd-text
            v-if="strikePrice != null" color="white" custom-class="pl-5px" size="28rpx" prefix="￥"
            mode="price" :text="strikePrice" decoration="line-through"
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
    <!--
      新人价条：只承载「为什么价格是这样」的信息，不做第二个活动入口。
      两种情况互斥：
        · 可享 → 限购提示（后端硬校验每人 1 件，提前说明避免结算才被拒）；
        · 确定不符合 → 说明原因，价格区已回落原价，否则用户会以为页面标错价。
      未登录时后端 eligible=null，display 按可享处理，不出现「你没有资格」这类误伤文案。
    -->
    <view v-if="newbieLimitText || newbieDisplay.ineligibleText" class="newbie-strip">
      <text v-if="newbieLimitText" class="newbie-strip-text">
        {{ newbieLimitText }}
      </text>
      <text v-else class="newbie-strip-text is-muted">
        {{ newbieDisplay.ineligibleText }}
      </text>
    </view>

    <!-- 限时折扣条：解释「为什么主价是折后价」，活动名让用户知道价从哪来 -->
    <view v-if="discountDisplay.showDiscountPrice" class="discount-strip">
      <text class="discount-strip-tag">
        限时折扣
      </text>
      <text class="discount-strip-name">
        {{ discountDisplay.activityName || '活动商品' }}
      </text>
      <text class="discount-strip-meta">
        结算自动生效
      </text>
    </view>
    <view
      class="goods-title-card relative rounded-xl bg-white dark:bg-[var(--wot-dark-background2)]"
      :class="{ 'goods-title-card--striped': hasPriceStrip }"
    >
      <view class="goods-title-card__main">
        <!--  商品标题 -->
        <wd-text :lines="2" size="32rpx" color="inherit" bold :text="goodsSpu?.name" />
        <!-- 副标题 -->
        <view v-if="showSubtitle" class="goods-title-card__subtitle">
          <wd-text :lines="2" size="24rpx" color="#8a8f99" :text="goodsSpu?.subTitle" />
        </view>
        <view v-if="isFreeShipping" class="goods-title-card__tags">
          <text class="goods-tag">
            包邮
          </text>
        </view>
      </view>
      <!--
        运营配置的属性标签（地标/冷藏/上榜…）：横滑卡条，放在标题之后、
        库存已售之前。后端未部署或旧数据无 attrTags 时组件内部整块不渲染，
        首屏与改动前完全一致。
      -->
      <GoodsAttrTags :attr-tags="goodsSpu?.attrTags" />
      <view class="goods-title-card__meta">
        <text class="goods-title-card__meta-item">
          库存 <text class="goods-title-card__meta-value">{{ goodsSpu.stock || 0 }}</text> 件
        </text>
        <text class="goods-title-card__meta-divider" />
        <text class="goods-title-card__meta-item">
          已售 <text class="goods-title-card__meta-value">{{ goodsSpu.salesVolume || 0 }}</text> 件
        </text>
      </view>
    </view>
    <view class="goods-cells mx-3 my-2 rounded-xl bg-white p-3 dark:bg-[var(--wot-dark-background2)]">
      <view class="overflow-hidden rounded-lg">
        <view
          v-if="couponList && couponList.length > 0"
          class="goods-cell goods-cell--border flex items-center py-3"
          hover-class="goods-cell--pressed"
          @click="showCoupon"
        >
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
        <view
          v-if="specRow.visible"
          class="goods-cell goods-cell--border flex items-center py-3"
          hover-class="goods-cell--pressed"
          @click="openSkuPopup(2)"
        >
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
        <view
          class="goods-cell flex items-center py-3"
          :hover-class="deliveryRow.tappable ? 'goods-cell--pressed' : 'none'"
          @click="onDeliveryRowTap"
        >
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
.goods-swiper {
  position: relative;

  // 与参考商详一致：页码常驻右下，图片再少也能一眼看到总张数
  &__counter {
    position: absolute;
    right: 24rpx;
    bottom: 44rpx;
    padding: 4rpx 16rpx;
    border-radius: 999rpx;
    background: rgb(0 0 0 / 34%);
    color: #fff;
    font-size: 22rpx;
    line-height: 1.5;
  }
}

.goods-title-card {
  // 默认上提，与价格渐变区咬合；有活动说明条时由 --striped 取消（见 hasPriceStrip）
  margin-top: -32px;
  padding: 24rpx;
  box-shadow: 0 6rpx 18rpx rgb(22 34 51 / 5%);

  &__main {
    min-width: 0;
  }

  &__subtitle {
    margin-top: 8rpx;
  }

  &__tags {
    display: flex;
    align-items: center;
    gap: 8rpx;
    margin-top: 12rpx;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 16rpx;
    margin-top: 16rpx;
    padding-top: 16rpx;
    border-top: 1rpx solid #f5f6f8;
    color: #8a8f99;
    font-size: 22rpx;
  }

  &__meta-value {
    color: #3a3f47;
    font-size: 24rpx;
    font-weight: 600;
  }

  &__meta-divider {
    width: 1rpx;
    height: 18rpx;
    background: #e8eaf0;
  }
}

.goods-tag {
  display: inline-flex;
  align-items: center;
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
  background: #f5f6f8;
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));
  font-size: 20rpx;
  font-weight: 500;
}

.goods-cell {
  transition: opacity 120ms ease;

  &--border {
    border-bottom: 1rpx solid #f5f6f8;
  }

  &--pressed {
    opacity: 0.66;
  }
}

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

/**
 * 新人价角标。
 *
 * 价格区底色是 primary→secondary 渐变、文字全白：角标若用高饱和实色块会在白字旁
 * 形成两个互相争夺注意力的色块，且小屏上易与主价糊成一片。
 * 因此用「半透明白底 + 白字 + 细白描边」，只靠形状区分层级，保持白字可读。
 */
.newbie-badge {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border: 1rpx solid rgb(255 255 255 / 70%);
  border-radius: 999rpx;
  background: rgb(255 255 255 / 18%);
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 1.6;
}

// 「省￥x」与新人价角标同一套白描边语言，避免在渐变区再引入第二个色块
.saving-badge {
  margin-left: 10rpx;
  padding: 2rpx 12rpx;
  border: 1rpx solid rgb(255 255 255 / 55%);
  border-radius: 999rpx;
  background: rgb(255 255 255 / 12%);
  color: #fff;
  font-size: 20rpx;
  line-height: 1.6;
}

// 有活动条时标题卡不再上提，避免首行被条裁切
.goods-title-card--striped {
  margin-top: 0;
}

// 限时折扣角标：与新人价角标同一套白描边语言（价格区为渐变主色底）
.discount-badge {
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border: 1rpx solid rgb(255 255 255 / 70%);
  border-radius: 999rpx;
  background: rgb(255 255 255 / 18%);
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 1.6;
}

// 限时折扣条：独立 class，避免以后调秒杀/新人价样式时被连带改掉
.discount-strip {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: -20rpx 16rpx 12rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background: #fff1ee;

  .discount-strip-tag {
    flex-shrink: 0;
    padding: 4rpx 14rpx;
    border-radius: 8rpx;
    background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b35), var(--wot-color-theme-primary, #ff4500));
    color: #fff;
    font-size: 22rpx;
    font-weight: 600;
  }

  .discount-strip-name {
    flex: 1;
    overflow: hidden;
    color: #7a3412;
    font-size: 22rpx;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .discount-strip-meta {
    flex-shrink: 0;
    color: #b25a3a;
    font-size: 22rpx;
  }
}

// 新人价条：与秒杀条同位置同节奏，但独立 class——两类活动不能共用样式，
// 否则以后改秒杀样式会顺带改掉新人价（二者语义与后续迭代都不同）
.newbie-strip {
  position: relative;
  z-index: 1;
  margin: -20rpx 16rpx 12rpx;
  padding: 14rpx 20rpx;
  border-radius: 12rpx;
  background: #fff7e8;

  .newbie-strip-text {
    color: #b25a00;
    font-size: 22rpx;

    // 「不符合资格」是解释性文案，弱化一级：不需要用户采取任何动作
    &.is-muted {
      color: #8a6d4b;
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
