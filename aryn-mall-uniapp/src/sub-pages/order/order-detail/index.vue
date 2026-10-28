<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getById, reorderPreview } from '@/api/order/orderInfo'
import { addShoppingCart } from '@/api/order/shoppingCart'
import DeliveryProgress from '@/components/delivery/delivery-progress.vue'
import { useDecorationInfiniteScroll } from '@/composables/useDecorationInfiniteScroll'
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

// 定义变量
const loading = ref(true)
const router = useRouter()
const state = reactive<{ order: any }>({
  order: {},
})

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
  uni.navigateBack({
    delta: 1,
  })
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
</script>

<template>
  <hr-navbar :title="navbarTitle" />
  <view v-if="!loading">
    <!-- 状态提示（可选显示在标题下方） -->
    <view
      v-if="navbarSubTitle"
      class="bg-white py-10px text-center text-24rpx text-gray-400"
    >
      {{ navbarSubTitle }}
    </view>
    <!-- 收货地址（普通快递 / 商城配送） -->
    <view
      v-if="state.order.deliveryWay === '1' || state.order.deliveryWay === '3'"
      class="m-2 rounded-xl bg-white p-2"
    >
      <view class="flex items-center">
        <view class="flex items-center">
          <wd-icon name="location" size="22px" />
        </view>
        <view class="pl-10px">
          <view>
            <text class="text-14px">
              {{ state.order.recipientName }}
            </text>
            <text class="pl-10px text-14px">
              {{ state.order.recipientPhone }}
            </text>
          </view>
          <view class="py-8px text-13px">
            {{ state.order.recipientProvince }} {{ state.order.recipientCity }}
            {{ state.order.recipientArea }}
            {{ state.order.recipientAddress }}
          </view>
        </view>
      </view>
    </view>

    <!-- 配送至船舶（公司港口/船舶内部配送 way=4） -->
    <view
      v-if="state.order.deliveryWay === '4'"
      class="m-2 rounded-xl bg-white p-2"
    >
      <view class="flex items-center">
        <view class="flex items-center">
          <wd-icon name="location" size="22px" />
        </view>
        <view class="flex-1 pl-10px">
          <view class="text-14px font-bold">
            {{ state.order.vesselName || t('order.detail.vesselDelivery') }}
          </view>
          <view class="py-8px text-13px text-gray-500">
            {{ state.order.portName }} {{ state.order.berth }}
          </view>
          <view
            v-if="state.order.deliveryWindowStart"
            class="text-12px text-gray-400"
          >
            {{ t('order.detail.deliveryWindow', {
              start: state.order.deliveryWindowStart,
              end: state.order.deliveryWindowEnd,
            }) }}
          </view>
          <view class="pt-6rpx text-12px text-green-600">
            {{ t('order.detail.vesselDeliveryHint') }}
          </view>
        </view>
      </view>
    </view>
    <view class="m-2 rounded-xl bg-white p-2">
      <view
        v-for="(item, index) in state.order.orderItemList"
        :key="index"
        class="flex pt-20rpx"
      >
        <image
          :src="item.picUrl"
          class="h-160rpx w-160rpx flex-none rounded-lg"
        />
        <view class="ml-20rpx h-full flex flex-1 flex-col overflow-hidden">
          <view class="flex justify-between">
            <view class="mr-20rpx flex-1 overflow-hidden">
              <wd-text
                :lines="2"
                size="26rpx"
                color="inherit"
                :text="item.spuName"
              />
              <view v-if="item.specsInfo" class="pt-10rpx">
                <wd-text
                  custom-class="pt-10rpx"
                  size="24rpx"
                  color="#909090"
                  :text="item.specsInfo"
                />
              </view>
              <!-- 共享采购按人拆行：标明这件商品是谁要的，配送时按此贴标签 -->
              <view v-if="item.contributorName" class="pt-6rpx">
                <wd-text
                  size="22rpx"
                  color="#185FA5"
                  :text="t('order.detail.boughtFor', { name: item.contributorName })"
                />
              </view>
            </view>
            <view class="flex flex-shrink-0 flex-col items-end">
              <wd-text
                :text="item.paymentPrice"
                size="14px"
                color="red"
                mode="price"
                prefix=" ￥"
              />
              <wd-text size="13px" :text="`x${item.buyQuantity}`" />
            </view>
          </view>
          <view
            v-if="paidOrCod"
            class="flex justify-end pt-10rpx"
          >
            <wd-button
              size="small"
              plain
              hairline
              type="primary"
              class="mr-10rpx"
              @tap.stop="handleReorder"
            >
              {{ t('order.actions.reorder') }}
            </wd-button>
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
      class="m-2"
    >
      <DeliveryProgress :order-id="state.order.id" />
    </view>
    <view class="m-2 rounded-xl bg-white p-2">
      <view class="p-20rpx">
        <!-- 信息列表 -->
        <view>
          <!-- 商品金额（仅展开时显示） -->
          <view
            v-if="showMoreInfo"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.subtotal') }}
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.totalPrice"
              mode="price"
              prefix="￥"
            />
          </view>

          <!-- 运费 -->
          <view
            v-if="showMoreInfo"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.shipping') }}
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.freightPrice"
              mode="price"
              prefix="￥"
            />
          </view>

          <!-- 优惠券 -->
          <view
            v-if="showMoreInfo"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.coupon') }}
            </text>
            <wd-text
              size="26rpx"
              :text="state.order.couponPrice"
              color="red"
              mode="price"
              prefix="-￥"
            />
          </view>
          <!-- 会员优惠 -->
          <view
            v-if="showMoreInfo && Number(state.order.memberDiscountPrice) > 0"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.memberDiscount') }}
            </text>
            <wd-text
              size="26rpx"
              :text="state.order.memberDiscountPrice"
              color="red"
              mode="price"
              prefix="-￥"
            />
          </view>
          <!-- 积分抵扣 -->
          <view
            v-if="showMoreInfo && Number(state.order.pointsDeductPrice) > 0"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.pointsDeduction', { points: state.order.pointsUsed || 0 }) }}
            </text>
            <wd-text
              size="26rpx"
              :text="state.order.pointsDeductPrice"
              color="red"
              mode="price"
              prefix="-￥"
            />
          </view>
          <!-- 订单金额（线下收款有折扣时展示应收口径） -->
          <view
            v-if="hasPayDiscount"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.orderTotal') }}
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.paymentPrice"
              mode="price"
              prefix="￥"
            />
          </view>
          <!-- 实付款 -->
          <view class="flex items-center justify-between pb-20rpx">
            <text class="text-14px">
              {{ t('order.detail.amountPaid') }}
            </text>
            <wd-text
              custom-class="pl-10rpx"
              size="28rpx"
              :text="actualPayPrice"
              color="red"
              mode="price"
              prefix="￥"
            />
          </view>
          <wd-divider v-if="showMoreInfo" color="#E8E8E8" />

          <!-- 配送方式 -->
          <view
            v-if="showMoreInfo"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.deliveryMethod') }}
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="deliveryWayText"
            />
          </view>
          <!-- 订单编号（始终显示） -->
          <view class="flex items-center justify-between pb-20rpx">
            <text class="text-14px">
              {{ t('order.detail.orderNo') }}
            </text>
            <view class="flex items-center">
              <wd-text
                size="26rpx"
                color="inherit"
                :text="state.order.orderNo"
              />
              <wd-icon
                name="file-copy"
                color="var(--wot-color-theme)"
                custom-class="ml-10rpx"
                @click="copyOrderNo"
              />
            </view>
          </view>
          <!-- 支付方式 -->
          <view
            v-if="showMoreInfo && paidOrCod"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.paymentMethod') }}
            </text>
            <dict-tag :options="payTypeOptions" :value="state.order.paymentType" />
          </view>
          <!-- 创建时间（始终显示） -->
          <view class="flex items-center justify-between pb-20rpx">
            <text class="text-14px">
              {{ t('order.detail.createdAt') }}
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.createTime"
            />
          </view>
          <!-- 付款时间 -->
          <view
            v-if="showMoreInfo && state.order.paymentTime"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.paidAt') }}
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.paymentTime"
            />
          </view>

          <!-- 发货时间 -->
          <view
            v-if="showMoreInfo && state.order.deliverTime"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.shippedAt') }}
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.deliverTime"
            />
          </view>

          <!-- 收货时间 -->
          <view
            v-if="showMoreInfo && state.order.receiverTime"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.receivedAt') }}
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.receiverTime"
            />
          </view>

          <!-- 取消时间 -->
          <view
            v-if="showMoreInfo && state.order.cancelTime"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.cancelledAt') }}
            </text>
            <wd-text
              size="26rpx"
              color="inherit"
              :text="state.order.cancelTime"
            />
          </view>
          <!-- 备注 -->
          <view
            v-if="showMoreInfo && state.order.remark"
            class="flex items-center justify-between pb-20rpx"
          >
            <text class="text-14px">
              {{ t('order.detail.note') }}
            </text>
            <wd-text size="26rpx" color="inherit" :text="state.order.remark" />
          </view>
        </view>

        <!-- 展开/收起按钮 -->
        <view
          class="mt-10rpx flex items-center justify-between border-t border-gray-200 dark:border-gray-700"
          @click="toggleMoreInfo"
        >
          <text class="text-14px">
            {{ t('order.detail.moreInfo') }}
          </text>
          <view>
            <text class="mr-10rpx text-14px">
              {{ showMoreInfo ? t('order.detail.collapse') : t('order.detail.expand') }}
            </text>
            <wd-icon :name="showMoreInfo ? 'arrow-up' : 'arrow-down'" />
          </view>
        </view>
      </view>
      <wd-divider color="#E8E8E8" />
    </view>
    <WaterfallGoods
      :source-spu-ids="orderRecommendSourceIds"
      :exclude-spu-ids="orderRecommendExcludeIds"
    />
    <wd-gap :height="80" />
    <view
      class="fixed bottom-0 left-0 right-0 flex gap-12rpx bg-white p-20rpx pb-[max(env(safe-area-inset-bottom),16rpx)]"
    >
      <wd-button plain type="info" icon="service" @click="toCustomerService">
        {{ t('order.actions.support') }}
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
