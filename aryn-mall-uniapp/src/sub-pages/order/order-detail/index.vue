<script setup lang="ts">
import { onLoad, onPageScroll } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getById, reorderPreview } from '@/api/order/orderInfo'
import { addShoppingCart } from '@/api/order/shoppingCart'
import DeliveryProgress from '@/components/delivery/delivery-progress.vue'
import PriceText from '@/components/price-text/index.vue'
import { useDecorationInfiniteScroll } from '@/composables/useDecorationInfiniteScroll'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import OrderOperation from '@/sub-pages/order/components/order-operation/index.vue'
import {
  DELIVERY_WAY_EXPRESS,
  DELIVERY_WAY_MALL_DELIVERY,
  DELIVERY_WAY_SELF_PICKUP,
  DELIVERY_WAY_VESSEL_INTERNAL,
} from '@/sub-pages/utils/delivery-way'
import { useDict } from '@/sub-pages/utils/dict'
import { DEFAULT_PAY_TIMEOUT_MINUTES } from '@/sub-pages/utils/pay-timeout'
import { customerServiceRoute } from '@/utils/message'
import { annotateNewbieSplitRows } from '@/utils/order-item-merge'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'order-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '订单详情',
  },
})
const { order_item_status, pay_type } = useDict(
  'order_item_status',
  'pay_type',
)
const { t } = useI18n()
const globalLoading = useGlobalLoading()
// 底部推荐商品流的页面滚动广播：滚动接近底部时自动加载下一页
useDecorationInfiniteScroll()

// 品牌色变量链：装修页级 → 商城换肤（wot ConfigProvider）→ 内置兜底，
// 与个人中心页头同源；顶栏底色与页头渐变首色一致才能无缝衔接
const BRAND_COLOR = 'var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237))'
const PRICE_COLOR = 'var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237))'

// 定义变量
const loading = ref(true)
const router = useRouter()
const state = reactive<{ order: any }>({
  order: {},
})

/**
 * 新人价首件优惠拆行展示：保留逐行（退款按行 id 提交，不能合并孤儿化入口），
 * 仅给拆行组打「首件/超出件」标注，避免同 SKU 两行被误认为重复加购。
 */
const displayItems = computed<any[]>(() => annotateNewbieSplitRows<any>(state.order.orderItemList || []))

// 字典标签由后端下发中文，页面按状态码映射到当前语言；未知状态保留后端原文
const ORDER_ITEM_STATUS_LABEL_KEYS: Record<string, string> = {
  1: 'order.status.pendingShipment',
  2: 'order.status.shipped',
  3: 'order.status.afterSalesProcessing',
  6: 'order.status.refunded',
  8: 'order.status.completed',
}
const PAY_TYPE_LABEL_KEYS: Record<string, string> = {
  0: 'order.payType.free',
  1: 'order.payType.wechat',
  2: 'order.payType.alipay',
  3: 'order.payType.cod',
}
function translateDictOptions(options: any[] | undefined, keys: Record<string, string>) {
  return (options || []).map((item: any) => {
    const key = keys[item.value]
    return key ? { ...item, label: t(key) } : item
  })
}
const orderItemStatusOptions = computed(() => translateDictOptions(order_item_status.value, ORDER_ITEM_STATUS_LABEL_KEYS))
const payTypeOptions = computed(() => translateDictOptions(pay_type.value, PAY_TYPE_LABEL_KEYS))

const DELIVERY_WAY_LABEL_KEYS: Record<string, string> = {
  [DELIVERY_WAY_EXPRESS]: 'order.deliveryWay.express',
  [DELIVERY_WAY_SELF_PICKUP]: 'order.deliveryWay.selfPickup',
  [DELIVERY_WAY_MALL_DELIVERY]: 'order.deliveryWay.mallDelivery',
  [DELIVERY_WAY_VESSEL_INTERNAL]: 'order.deliveryWay.vesselDelivery',
}
// 已知配送方式走词条；未知取值明确提示，不静默展示成普通快递
const deliveryWayText = computed(() => {
  const way = state.order.deliveryWay
  const key = way ? DELIVERY_WAY_LABEL_KEYS[way] : ''
  return key ? t(key) : way ? t('order.deliveryWay.unknown') : ''
})
// 与 pay-timeout 工具同口径：整小时按小时展示，缺省回落默认分钟数
const payTimeoutText = computed(() => {
  const value = Number(state.order.payTimeoutMinutes)
  const minutes = Number.isFinite(value) && value > 0 ? value : DEFAULT_PAY_TIMEOUT_MINUTES
  return minutes >= 60 && minutes % 60 === 0
    ? t('order.time.hours', { hours: minutes / 60 })
    : t('order.time.minutes', { minutes })
})
// 已在线支付，或货到付款单（payStatus 在确认收款前仍为未收款，收货后由管理端线下确认）
const paidOrCod = computed(() => state.order.payStatus === '1' || state.order.paymentType === '3')
// 货到付款单在待发货阶段的副标题前缀不同（无“已付款”事实）
const isCod = computed(() => state.order.paymentType === '3')
// 实付总额：管理端确认收款登记的实收金额（线下收款可能有折扣），未登记时回退应收金额
const actualPayPrice = computed(() => state.order.actualPayPrice ?? state.order.paymentPrice)
// 实收低于应收（线下收款折扣）时，详情页同时展示「订单金额 + 实付款」双行
const hasPayDiscount = computed(() =>
  state.order.actualPayPrice != null
  && state.order.paymentPrice != null
  && Number(state.order.actualPayPrice) < Number(state.order.paymentPrice))
// 推荐上下文：订单商品去重后既作为分类来源，也排除，避免重复推荐已购商品
const orderRecommendSpuIds = computed(() => {
  const items: any[] = state.order.orderItemList || []
  const ids = items
    .map(item => String(item?.spuId ?? '').trim())
    .filter(Boolean)
  return [...new Set(ids)]
})
const orderRecommendSourceIds = computed(() => orderRecommendSpuIds.value.slice(0, 20))
const orderRecommendExcludeIds = computed(() => orderRecommendSpuIds.value.slice(0, 50))
// 动态标题
const navbarTitle = computed(() => {
  const status = state.order.status
  const way = state.order.deliveryWay
  switch (status) {
    case '1':
      return t('order.detail.title.pendingPayment')
    case '2':
      if (way === DELIVERY_WAY_EXPRESS)
        return t('order.detail.title.pendingShipment')
      if (way === DELIVERY_WAY_MALL_DELIVERY || way === DELIVERY_WAY_VESSEL_INTERNAL)
        return t('order.detail.title.preparing')
      return t('order.detail.title.sellerPreparing')
    case '3':
      if (way === DELIVERY_WAY_EXPRESS)
        return t('order.detail.title.awaitingReceipt')
      if (way === DELIVERY_WAY_MALL_DELIVERY)
        return t('order.detail.title.delivering')
      if (way === DELIVERY_WAY_VESSEL_INTERNAL)
        return t('order.detail.title.deliveringToVessel')
      return t('order.detail.title.awaitingPickup')
    case '4':
      return t('order.detail.title.completed')
    case '5':
      return t('order.detail.title.refunding')
    case '11':
      return t('order.detail.title.cancelled')
    default:
      return t('order.detail.title.fallback')
  }
})
// 动态副标题
const navbarSubTitle = computed(() => {
  const status = state.order.status
  const way = state.order.deliveryWay
  switch (status) {
    case '1':
      return t('order.pay.timeout', { time: payTimeoutText.value })
    case '2': {
      const paidPrefix = isCod.value ? t('order.detail.subtitle.codPrefix') : t('order.detail.subtitle.paidPrefix')
      if (way === DELIVERY_WAY_EXPRESS)
        return `${paidPrefix}${t('order.detail.subtitle.awaitingShipment')}`
      if (way === DELIVERY_WAY_MALL_DELIVERY)
        return `${paidPrefix}${t('order.detail.subtitle.companyPreparing')}`
      if (way === DELIVERY_WAY_VESSEL_INTERNAL)
        return `${paidPrefix}${t('order.detail.subtitle.warehousePreparing')}`
      return `${paidPrefix}${t('order.detail.subtitle.sellerPreparing')}`
    }
    case '3':
      if (way === DELIVERY_WAY_EXPRESS)
        return t('order.detail.subtitle.shippedAwaitingReceipt')
      if (way === DELIVERY_WAY_MALL_DELIVERY)
        return t('order.detail.subtitle.courierDelivering')
      if (way === DELIVERY_WAY_VESSEL_INTERNAL)
        return t('order.detail.subtitle.driverToVessel')
      return t('order.detail.subtitle.readyForPickup')
    default:
      return ''
  }
})
// 状态主视觉图标：类名全量字面量列出，供 UnoCSS 扫描生成；未知状态走文档图标
const ORDER_STATUS_HERO_ICONS: Record<string, string> = {
  1: 'i-carbon:time',
  2: 'i-carbon:package',
  3: 'i-carbon:delivery-truck',
  4: 'i-carbon:checkmark-filled',
  5: 'i-carbon:renew',
  11: 'i-carbon:close',
}
const heroIcon = computed(() => ORDER_STATUS_HERO_ICONS[String(state.order.status)] ?? 'i-carbon:document')
// 首卡是否为商品卡：无地址/船舶卡（自提等）时由商品卡顶替压盖页头
const heroOverlapCard = computed(() => !['1', '3', '4'].includes(state.order.deliveryWay))
// 添加更多信息公开关状态
const showMoreInfo = ref(false)

onLoad((options) => {
  getOrder(options?.id)
})

// 订单查询
async function getOrder(id: string) {
  loading.value = true
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getById(id)
    state.order = response
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}
// 复制订单编号
function copyOrderNo() {
  uni.setClipboardData({
    data: state.order.orderNo,
  })
}
// 订单删除
function orderDel() {
  // 走 router.back：实例上的 navigation lock 会拦掉首屏无效返回与连点重复返回
  router.back()
}
// 订单确认收货
function orderReceiver(row: any) {
  getOrder(row.id)
}
// 订单取消
function orderCancel(row: any) {
  getOrder(row.id)
}
// 跳转到退款申请页面
function toRefunds(orderItemId: string, status: string) {
  if (status === '1' || status === '2') {
    router.push({
      name: 'refunds-submit',
      params: { orderItemId },
    })
  }
}
// 跳转到退款详情页面
function handleRefundDetail(item: any) {
  const id = item.orderRefund.id
  if (!id) {
    return
  }
  router.push({
    name: 'refunds-detail',
    params: { id },
  })
}

/**
 * 再来一单：预览原明细当前可购状态，可购项一键加入购物车（重新确认船舶/靠港/价格后结算）
 */
async function handleReorder() {
  const orderId = state.order.id
  if (!orderId)
    return
  const preview = await reorderPreview(orderId)
  const purchasable = (preview.items || []).filter((item: any) => item.purchasable)
  const blocked = (preview.items || []).filter((item: any) => !item.purchasable)
  if (purchasable.length === 0) {
    uni.showToast({ title: blocked[0]?.reason || t('order.detail.itemUnavailable'), icon: 'none' })
    return
  }
  for (const item of purchasable) {
    await addShoppingCart({ skuId: item.skuId, quantity: item.originalQuantity, addType: '2' })
  }
  const tips = blocked.length > 0 ? t('order.detail.reorderSkipped', { count: blocked.length }) : ''
  uni.showToast({ title: `${t('order.detail.reorderSuccess')}${tips}`, icon: 'none' })
  // 延时跳转期间用户可能已自行离开本页：对已销毁页面发路由会触发
  // 微信 `routeDone with a webviewId xxx is not found`，故先记住当前页再确认。
  const currentPage = getCurrentPages().at(-1)
  setTimeout(() => {
    if (getCurrentPages().at(-1) !== currentPage)
      return
    uni.switchTab({ url: '/pages/user/shopping-cart/index' })
  }, 800)
}
// 切换更多信息显示状态
function toggleMoreInfo() {
  showMoreInfo.value = !showMoreInfo.value
}
function toCustomerService() {
  const firstItem = state.order.orderItemList?.[0]
  uni.navigateTo({
    url: customerServiceRoute({
      messageType: 'ORDER_CARD',
      payload: {
        amount: actualPayPrice.value ? `￥${actualPayPrice.value}` : '',
        image: firstItem?.picUrl || '',
        orderId: String(state.order.id),
        statusText: navbarTitle.value,
        title: t('order.detail.orderCardTitle', { no: state.order.orderNo || state.order.id }),
      },
    }),
  })
}

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <hr-navbar
    :title="navbarTitle"
    :bordered="false"
    :background-color="BRAND_COLOR"
    text-color="#ffffff"
  />
  <view v-if="!loading" class="od-page">
    <!-- 状态页头：品牌渐变铺底 + 大字状态 + 副标题，底部留白给首卡压盖 -->
    <view class="od-hero">
      <view class="od-hero__headline">
        <text class="od-hero__title">
          {{ navbarTitle }}
        </text>
        <text class="od-hero__icon" :class="heroIcon" />
      </view>
      <text v-if="navbarSubTitle" class="od-hero__subtitle">
        {{ navbarSubTitle }}
      </text>
    </view>

    <!-- 收货地址（普通快递 / 商城配送） -->
    <view
      v-if="state.order.deliveryWay === '1' || state.order.deliveryWay === '3'"
      class="od-card od-card--overlap"
    >
      <view class="od-address">
        <view class="od-address__badge">
          <text class="od-address__badge-icon i-carbon:location" />
        </view>
        <view class="od-address__body">
          <view class="od-address__name-row">
            <text class="od-address__name">
              {{ state.order.recipientName }}
            </text>
            <text class="od-address__name od-address__name--muted">
              {{ state.order.recipientPhone }}
            </text>
          </view>
          <text class="od-address__detail">
            {{ state.order.recipientProvince }} {{ state.order.recipientCity }}
            {{ state.order.recipientArea }}
            {{ state.order.recipientAddress }}
          </text>
        </view>
      </view>
    </view>

    <!-- 配送至船舶（公司港口/船舶内部配送 way=4） -->
    <view
      v-if="state.order.deliveryWay === '4'"
      class="od-card od-card--overlap"
    >
      <view class="od-address">
        <view class="od-address__badge">
          <text class="od-address__badge-icon i-carbon:sailboat-coastal" />
        </view>
        <view class="od-address__body">
          <text class="od-address__name od-address__name--block">
            {{ state.order.vesselName || t('order.detail.vesselDelivery') }}
          </text>
          <text class="od-address__detail">
            {{ state.order.portName }} {{ state.order.berth }}
          </text>
          <text
            v-if="state.order.deliveryWindowStart"
            class="od-address__meta"
          >
            {{ t('order.detail.deliveryWindow', {
              start: state.order.deliveryWindowStart,
              end: state.order.deliveryWindowEnd,
            }) }}
          </text>
          <text class="od-address__hint">
            {{ t('order.detail.vesselDeliveryHint') }}
          </text>
        </view>
      </view>
    </view>

    <!-- 商品清单：无地址卡（自提等）时由它压盖页头 -->
    <view class="od-card" :class="{ 'od-card--overlap': heroOverlapCard }">
      <view
        v-for="(item, index) in displayItems"
        :key="index"
        class="od-item"
        :class="{ 'od-item--divided': index > 0 }"
      >
        <image
          :src="item.picUrl"
          class="od-item__pic"
          mode="aspectFill"
        />
        <view class="od-item__info">
          <view class="od-item__top">
            <view class="od-item__name-wrap">
              <wd-text
                :lines="2"
                size="28rpx"
                color="inherit"
                custom-class="od-item__name"
                :text="item.spuName"
              />
              <text v-if="item.specsInfo" class="od-item__specs">
                {{ item.specsInfo }}
              </text>
              <!-- 共享采购按人拆行：标明这件商品是谁要的，配送时按此贴标签 -->
              <view v-if="item.contributorName" class="od-item__contributor">
                <text class="od-item__contributor-text">
                  {{ t('order.detail.boughtFor', { name: item.contributorName }) }}
                </text>
              </view>
              <!-- 新人价首件优惠拆行标注：解释同 SKU 两行，不是重复加购 -->
              <text v-if="item.newbieSplitHint" class="od-item__split-hint">
                {{ t('checkout.goods.newbieFirstUnitHint') }}
              </text>
              <text v-else-if="item.newbieRemainderHint" class="od-item__split-hint">
                {{ t('checkout.goods.newbieRemainderHint') }}
              </text>
            </view>
            <view class="od-item__price-wrap">
              <price-text
                class="od-item__price"
                :value="item.paymentPrice"
                symbol="￥"
              />
              <text class="od-item__qty">
                x{{ item.buyQuantity }}
              </text>
            </view>
          </view>
          <view
            v-if="paidOrCod"
            class="od-item__actions"
          >
            <wd-button
              v-if="item.status === '1' || item.status === '2'"
              size="small"
              plain
              hairline
              type="info"
              @tap.stop="toRefunds(item.id, item.status)"
            >
              {{ t('order.actions.applyAfterSales') }}
            </wd-button>
            <wd-button
              v-else
              type="info"
              size="small"
              plain
              hairline
              @click="handleRefundDetail(item)"
            >
              <dict-tag :options="orderItemStatusOptions" :value="item.status" />
            </wd-button>
          </view>
        </view>
      </view>
    </view>

    <!-- 商城配送进度时间线（deliveryWay=3/4 且已付款或货到付款后展示） -->
    <view
      v-if="
        ['3', '4'].includes(state.order.deliveryWay)
          && paidOrCod
      "
      class="od-section"
    >
      <DeliveryProgress :order-id="state.order.id" />
    </view>

    <!-- 费用与订单信息 -->
    <view class="od-card od-card--tail">
      <view class="od-summary">
        <!-- 费用明细（仅展开时显示） -->
        <view v-if="showMoreInfo" class="od-summary__row">
          <text class="od-summary__label">
            {{ t('order.detail.subtotal') }}
          </text>
          <wd-text
            custom-class="od-summary__value"
            size="26rpx"
            color="inherit"
            :text="state.order.totalPrice"
            mode="price"
            prefix="￥"
          />
        </view>
        <view v-if="showMoreInfo" class="od-summary__row">
          <text class="od-summary__label">
            {{ t('order.detail.shipping') }}
          </text>
          <wd-text
            custom-class="od-summary__value"
            size="26rpx"
            color="inherit"
            :text="state.order.freightPrice"
            mode="price"
            prefix="￥"
          />
        </view>
        <view v-if="showMoreInfo" class="od-summary__row">
          <text class="od-summary__label">
            {{ t('order.detail.coupon') }}
          </text>
          <wd-text
            custom-class="od-summary__value"
            size="26rpx"
            :color="PRICE_COLOR"
            :text="state.order.couponPrice"
            mode="price"
            prefix="-￥"
          />
        </view>
        <view
          v-if="showMoreInfo && Number(state.order.memberDiscountPrice) > 0"
          class="od-summary__row"
        >
          <text class="od-summary__label">
            {{ t('order.detail.memberDiscount') }}
          </text>
          <wd-text
            custom-class="od-summary__value"
            size="26rpx"
            :color="PRICE_COLOR"
            :text="state.order.memberDiscountPrice"
            mode="price"
            prefix="-￥"
          />
        </view>
        <view
          v-if="showMoreInfo && Number(state.order.pointsDeductPrice) > 0"
          class="od-summary__row"
        >
          <text class="od-summary__label">
            {{ t('order.detail.pointsDeduction', { points: state.order.pointsUsed || 0 }) }}
          </text>
          <wd-text
            custom-class="od-summary__value"
            size="26rpx"
            :color="PRICE_COLOR"
            :text="state.order.pointsDeductPrice"
            mode="price"
            prefix="-￥"
          />
        </view>
        <!-- 订单金额（线下收款有折扣时展示应收口径） -->
        <view v-if="hasPayDiscount" class="od-summary__row">
          <text class="od-summary__label">
            {{ t('order.detail.orderTotal') }}
          </text>
          <wd-text
            custom-class="od-summary__value"
            size="26rpx"
            color="inherit"
            :text="state.order.paymentPrice"
            mode="price"
            prefix="￥"
          />
        </view>
        <!-- 实付款 -->
        <view class="od-summary__row od-summary__row--paid">
          <text class="od-summary__paid-label">
            {{ t('order.detail.amountPaid') }}
          </text>
          <wd-text
            custom-class="od-summary__paid-value"
            size="32rpx"
            :color="PRICE_COLOR"
            :text="actualPayPrice"
            mode="price"
            prefix="￥"
          />
        </view>

        <view v-if="showMoreInfo" class="od-summary__divider" />

        <!-- 配送方式 -->
        <view v-if="showMoreInfo" class="od-summary__row">
          <text class="od-summary__label">
            {{ t('order.detail.deliveryMethod') }}
          </text>
          <text class="od-summary__value">
            {{ deliveryWayText }}
          </text>
        </view>
        <!-- 订单编号（始终显示，整行可点复制） -->
        <view class="od-summary__row" @click="copyOrderNo">
          <text class="od-summary__label">
            {{ t('order.detail.orderNo') }}
          </text>
          <view class="od-summary__order-no">
            <text class="od-summary__value">
              {{ state.order.orderNo }}
            </text>
            <text class="od-summary__copy i-carbon:copy" />
          </view>
        </view>
        <!-- 支付方式 -->
        <view v-if="showMoreInfo && paidOrCod" class="od-summary__row">
          <text class="od-summary__label">
            {{ t('order.detail.paymentMethod') }}
          </text>
          <view class="od-summary__value">
            <dict-tag :options="payTypeOptions" :value="state.order.paymentType" />
          </view>
        </view>
        <!-- 创建时间（始终显示） -->
        <view class="od-summary__row">
          <text class="od-summary__label">
            {{ t('order.detail.createdAt') }}
          </text>
          <text class="od-summary__value">
            {{ state.order.createTime }}
          </text>
        </view>
        <!-- 付款 / 发货 / 收货 / 取消时间与备注（仅展开时显示） -->
        <template v-if="showMoreInfo">
          <view v-if="state.order.paymentTime" class="od-summary__row">
            <text class="od-summary__label">
              {{ t('order.detail.paidAt') }}
            </text>
            <text class="od-summary__value">
              {{ state.order.paymentTime }}
            </text>
          </view>
          <view v-if="state.order.deliverTime" class="od-summary__row">
            <text class="od-summary__label">
              {{ t('order.detail.shippedAt') }}
            </text>
            <text class="od-summary__value">
              {{ state.order.deliverTime }}
            </text>
          </view>
          <view v-if="state.order.receiverTime" class="od-summary__row">
            <text class="od-summary__label">
              {{ t('order.detail.receivedAt') }}
            </text>
            <text class="od-summary__value">
              {{ state.order.receiverTime }}
            </text>
          </view>
          <view v-if="state.order.cancelTime" class="od-summary__row">
            <text class="od-summary__label">
              {{ t('order.detail.cancelledAt') }}
            </text>
            <text class="od-summary__value">
              {{ state.order.cancelTime }}
            </text>
          </view>
          <view v-if="state.order.remark" class="od-summary__row">
            <text class="od-summary__label">
              {{ t('order.detail.note') }}
            </text>
            <text class="od-summary__value">
              {{ state.order.remark }}
            </text>
          </view>
        </template>

        <!-- 展开/收起切换条 -->
        <view class="od-summary__toggle" @click="toggleMoreInfo">
          <text class="od-summary__toggle-text">
            {{ showMoreInfo ? t('order.detail.collapse') : t('order.detail.moreInfo') }}
          </text>
          <text
            class="od-summary__toggle-icon"
            :class="showMoreInfo ? 'i-carbon:arrow-up' : 'i-carbon:arrow-down'"
          />
        </view>
      </view>
    </view>

    <WaterfallGoods
      :source-spu-ids="orderRecommendSourceIds"
      :exclude-spu-ids="orderRecommendExcludeIds"
    />
    <!-- 底部操作栏占位：栏高 + 安全区，防内容被盖 -->
    <view class="od-footer-spacer" />
    <view class="od-footer">
      <wd-button
        plain
        type="info"
        icon="service"
        custom-style="flex:1"
        @click="toCustomerService"
      >
        {{ t('order.actions.support') }}
      </wd-button>
      <wd-button
        v-if="paidOrCod"
        size="small"
        plain
        hairline
        type="primary"
        @click="handleReorder"
      >
        {{ t('order.actions.reorder') }}
      </wd-button>
      <order-operation
        :order-info="state.order"
        @order-del="orderDel"
        @order-receiver="orderReceiver($event)"
        @order-cancel="orderCancel($event)"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
/*
 * 订单详情（主流电商式重排）：
 * 彩色状态页头 → 白卡上浮（地址/船舶/商品）→ 进度 → 金额信息 → 磨砂操作栏。
 * 主题变量链与个人中心同源：装修页级 → wot 换肤 → 内置兜底。
 */
$brand: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$brand-secondary: var(--theme-color-secondary, var(--wot-color-theme-secondary, #FF6B7A));
$ink: var(--app-text);
$ink-2: var(--app-text-2);
$ink-3: var(--app-text-3);
$hairline: var(--app-border);

.od-page {
  min-height: 100vh;
  background: var(--theme-color-background, var(--wot-color-theme-background, #FFF0F0));
}

/* ===================== 状态页头 ===================== */

.od-hero {
  position: relative;
  overflow: hidden;
  padding: 24rpx 40rpx 132rpx;
  background: linear-gradient(180deg, $brand 0%, $brand-secondary 100%);

  /* 装饰圆：白色半透明，纯 CSS 无额外节点，被 overflow 裁出层次 */
  &::before,
  &::after {
    content: '';
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.1);
    pointer-events: none;
  }

  &::before {
    top: -140rpx;
    right: -80rpx;
    width: 340rpx;
    height: 340rpx;
  }

  &::after {
    bottom: -60rpx;
    left: -60rpx;
    width: 220rpx;
    height: 220rpx;
    background: rgba(255, 255, 255, 0.08);
  }
}

.od-hero__headline {
  display: flex;
  align-items: center;
}

.od-hero__title {
  flex: 1;
  min-width: 0;
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.3;
  color: #fff;
}

.od-hero__icon {
  margin-left: 24rpx;
  font-size: 56rpx;
  color: rgba(255, 255, 255, 0.9);
}

.od-hero__subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.85);
}

/* ===================== 卡片 ===================== */

.od-card {
  margin: 24rpx 24rpx 0;
  background: var(--app-card);
  border-radius: 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(17, 24, 39, 0.04);
}

/* 首卡压盖页头：主流订单页「彩色头部 + 白卡上浮」结构 */
.od-card--overlap {
  position: relative;
  margin-top: -92rpx;
}

.od-card--tail {
  margin-bottom: 24rpx;
}

.od-section {
  margin: 24rpx 24rpx 0;
}

/* ===================== 地址 / 船舶卡 ===================== */

.od-address {
  display: flex;
  align-items: flex-start;
  padding: 28rpx 24rpx;
}

.od-address__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--app-card-2);
  flex-shrink: 0;
}

.od-address__badge-icon {
  font-size: 36rpx;
  color: $brand;
}

.od-address__body {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.od-address__name-row {
  display: flex;
  align-items: baseline;
}

.od-address__name {
  font-size: 28rpx;
  font-weight: 600;
  color: $ink;
}

.od-address__name--muted {
  margin-left: 16rpx;
  font-weight: 400;
  color: $ink-2;
}

.od-address__name--block {
  display: block;
}

.od-address__detail {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: $ink-2;
}

.od-address__meta {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $ink-3;
}

.od-address__hint {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #16a34a;
}

/* ===================== 商品清单 ===================== */

.od-item {
  display: flex;
  align-items: flex-start;
  padding: 28rpx 24rpx;
}

.od-item--divided {
  border-top: 1rpx solid $hairline;
}

.od-item__pic {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  background: var(--app-card-2);
  flex-shrink: 0;
}

.od-item__info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  margin-left: 24rpx;
}

.od-item__top {
  display: flex;
  justify-content: space-between;
}

.od-item__name-wrap {
  flex: 1;
  min-width: 0;
  margin-right: 16rpx;
}

.od-item__name {
  font-weight: 500;
  line-height: 38rpx;
  color: $ink;
}

.od-item__specs {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: $ink-3;
}

.od-item__contributor {
  display: inline-flex;
  margin-top: 12rpx;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(24, 95, 165, 0.08);
}

.od-item__contributor-text {
  font-size: 22rpx;
  color: #185fa5;
}

.od-item__split-hint {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $ink-3;
}

.od-item__price-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

/* 商品单价：整数大、小数小（price-text 组件内用 em 跟随本字号） */
.od-item__price {
  color: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
  font-size: 15px;
  line-height: 1;
}

.od-item__price {
  font-weight: 600;
}

.od-item__qty {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: $ink-3;
}

.od-item__actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 20rpx;
}

/* ===================== 金额与订单信息 ===================== */

.od-summary {
  padding: 12rpx 24rpx 8rpx;
}

.od-summary__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 0;
}

.od-summary__label {
  flex-shrink: 0;
  font-size: 26rpx;
  color: $ink-2;
}

.od-summary__value {
  flex: 1;
  min-width: 0;
  margin-left: 24rpx;
  font-size: 26rpx;
  text-align: right;
  color: $ink;
}

.od-summary__order-no {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 1;
  min-width: 0;
  margin-left: 24rpx;
}

.od-summary__copy {
  margin-left: 12rpx;
  font-size: 26rpx;
  color: $brand;
}

.od-summary__row--paid {
  padding: 16rpx 0 20rpx;
}

.od-summary__paid-label {
  flex-shrink: 0;
  font-size: 28rpx;
  font-weight: 600;
  color: $ink;
}

.od-summary__paid-value {
  flex-shrink: 0;
  margin-left: 24rpx;
  font-size: 32rpx;
  font-weight: 700;
}

.od-summary__divider {
  height: 1rpx;
  margin: 8rpx 0;
  background: $hairline;
}

.od-summary__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 88rpx;
  margin-top: 8rpx;
  border-top: 1rpx solid $hairline;
}

.od-summary__toggle-text {
  font-size: 26rpx;
  color: $ink-2;
}

.od-summary__toggle-icon {
  margin-left: 8rpx;
  font-size: 24rpx;
  color: $ink-3;
}

/* ===================== 底部操作栏 ===================== */

.od-footer-spacer {
  height: calc(140rpx + env(safe-area-inset-bottom));
}

.od-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(max(env(safe-area-inset-bottom), 16rpx));
  background: rgba(255, 255, 255, 0.92);
  border-top: 1rpx solid rgba(15, 23, 42, 0.06);
  /* 大写 WEBKIT 避开 autoprefixer 的过时前缀清理（iOS 17- 的 WKWebView 只认带前缀的写法） */
  -WEBKIT-backdrop-filter: blur(20px) saturate(160%);
  backdrop-filter: blur(20px) saturate(160%);
}
</style>
