<script setup lang="ts">
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { computed, nextTick, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getPage } from '@/api/order/orderInfo'
import PriceText from '@/components/price-text/index.vue'
import OrderOperation from '@/sub-pages/order/components/order-operation/index.vue'
import { DEFAULT_PAY_TIMEOUT_MINUTES } from '@/sub-pages/utils/pay-timeout'
import { useDict } from '@/sub-pages/utils/dict'
import { parseLocalDateTimeTimestamp } from '@/utils/datetime'
import { navigateToUrl } from '@/utils/tab-bar'

definePage({
  name: 'order-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的订单',
  },
})
interface TabChangeParams {
  index: number
  name: string
}
interface State {
  queryParams: {
    name: string
    status: string
    appraiseStatus: string
    keyword: string
  }
  tabCurrent: number
  orderList: Array<any>
}
// 多商品订单默认只露出前两件，避免一单撑满整屏
const COLLAPSED_ITEM_COUNT = 2
const DELIVERY_WAY_LABEL_KEYS: Record<string, string> = {  1: 'order.deliveryWay.express',
  2: 'order.deliveryWay.selfPickup',
  3: 'order.deliveryWay.mallDelivery',
  4: 'order.deliveryWay.vesselDelivery',
}
// 定义变量
const pagingRef = ref()
const router = useRouter()
const { t } = useI18n()
const globalLoading = useGlobalLoading()
const toast = useGlobalToast()
// 倒计时每秒 tick：仅待付款单有可见文案，其余状态不参与渲染
const { now: tickNow } = useCountdownTicker()
// 滑动相关变量
const touchStartX = ref(0)
const touchEndX = ref(0)
// 字典
const { order_item_status, order_status } = useDict('order_item_status', 'order_status')
// 字典标签由后端下发中文，页面按状态码映射到当前语言；未知状态保留后端原文
const ORDER_STATUS_LABEL_KEYS: Record<string, string> = {
  1: 'order.status.pendingPayment',
  2: 'order.status.pendingShipment',
  3: 'order.status.pendingReceipt',
  4: 'order.status.completed',
  5: 'order.status.refunding',
  11: 'order.status.cancelled',
}
const ORDER_ITEM_STATUS_LABEL_KEYS: Record<string, string> = {
  1: 'order.status.pendingShipment',
  2: 'order.status.shipped',
  3: 'order.status.afterSalesProcessing',
  6: 'order.status.refunded',
  8: 'order.status.completed',
}
function translateDictOptions(options: any[] | undefined, keys: Record<string, string>) {
  return (options || []).map((item: any) => {
    const key = keys[item.value]
    return key ? { ...item, label: t(key) } : item
  })
}
const orderStatusOptions = computed(() => translateDictOptions(order_status.value, ORDER_STATUS_LABEL_KEYS))
const orderItemStatusOptions = computed(() => translateDictOptions(order_item_status.value, ORDER_ITEM_STATUS_LABEL_KEYS))
const orderStatusList = computed(() => [
  {
    name: t('order.list.tabs.all'),
  },
  {
    name: t('order.list.tabs.pendingPayment'),
  },
  {
    name: t('order.list.tabs.pendingShipment'),
  },
  {
    name: t('order.list.tabs.pendingReceipt'),
  },
  {
    name: t('order.list.tabs.pendingReview'),
  },
])
const state = reactive<State>({
  queryParams: {
    name: '',
    status: '0',
    appraiseStatus: '',
    keyword: '',
  },
  tabCurrent: 0,
  orderList: [],
})
// 多商品订单的展开状态按订单 id 记录，切换 Tab 不串状态
const expandedOrders = reactive<Record<string, boolean>>({})

onLoad(async (options) => {
  if (options?.status) {
    state.tabCurrent = Number(options.status)
    state.queryParams.status = options.status
    if (options.status === '4') {
      state.queryParams.appraiseStatus = '0'
    }
  }
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
      status: state.queryParams.status !== '0' ? state.queryParams.status : '',
      appraiseStatus: state.queryParams.appraiseStatus,
      desc: 'create_time',
      keyword: state.queryParams.keyword,
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}
function toOrderDetail(orderId: string) {
  router.push({
    name: 'order-detail',
    params: { id: orderId },
  })
}
function goShopping() {
  navigateToUrl('/pages/home/index')
}

function changeTab({ index }: TabChangeParams) {
  state.queryParams.status = `${index}`
  state.queryParams.appraiseStatus = ''
  if (index === 4) {
    state.queryParams.appraiseStatus = '0'
  }
  pagingRef.value?.reload()
}

// 处理触摸开始事件
function handleTouchStart(e: any) {
  touchStartX.value = e.touches[0].clientX
}

// 处理触摸结束事件
function handleTouchEnd(e: any) {
  touchEndX.value = e.changedTouches[0].clientX
  handleSwipe()
}

// 处理滑动逻辑
function handleSwipe() {
  const deltaX = touchEndX.value - touchStartX.value
  const minSwipeDistance = 120 // 最小滑动距离

  // 向左滑动 - 切换到下一个tab
  if (deltaX < -minSwipeDistance) {
    if (state.tabCurrent < orderStatusList.value.length - 1) {
      const newIndex = state.tabCurrent + 1
      state.tabCurrent = newIndex
      changeTab({ index: newIndex, name: orderStatusList.value[newIndex].name })
    }
  }
  // 向右滑动 - 切换到上一个tab
  else if (deltaX > minSwipeDistance) {
    if (state.tabCurrent > 0) {
      const newIndex = state.tabCurrent - 1
      state.tabCurrent = newIndex
      changeTab({ index: newIndex, name: orderStatusList.value[newIndex].name })
    }
  }
}

function orderDel(index: number) {
  state.orderList.splice(index, 1)
}
function orderReceiver() {
  pagingRef.value?.reload()
}
function orderCancel() {
  pagingRef.value?.reload()
}
// 跳转到退款申请页面
function toRefunds(orderItemId: string, status: string) {
  if (status === '1' || status === '2') {
    router.push({ name: 'refunds-submit', params: { orderItemId } })
  }
}
function handleRefresh() {
  pagingRef.value?.reload()
}

/* ---------------------
  展示层格式化
--------------------- */
function formatOrderTime(value?: string) {
  if (!value) {
    return ''
  }
  const text = String(value).slice(0, 16)
  const year = String(new Date().getFullYear())
  return text.startsWith(year) ? text.slice(5) : text
}
function deliveryWayText(order: any) {
  const key = DELIVERY_WAY_LABEL_KEYS[String(order?.deliveryWay)]
  return key ? t(key) : ''
}
function orderStatusText(order: any) {
  // 自提待收货、自配送备货中沿用详情页口径，字典只覆盖后端标准状态
  if (order?.deliveryWay === '2' && order?.status === '3') {
    return t('order.status.pendingPickup')
  }
  if (order?.status === '2' && (order?.deliveryWay === '3' || order?.deliveryWay === '4')) {
    return t('order.status.preparing')
  }
  const option = orderStatusOptions.value.find(item => String(item.value) === String(order?.status))
  return option?.label || ''
}
function statusToneClass(order: any) {
  if (String(order?.status) === '11') {
    return 'ol-head__status--muted'
  }
  if (String(order?.status) === '4') {
    return 'ol-head__status--done'
  }
  return 'ol-head__status--active'
}
function itemStatusText(status: string) {
  const option = orderItemStatusOptions.value.find(item => String(item.value) === String(status))
  return option?.label || ''
}
function totalQuantity(order: any) {
  const items: any[] = order?.orderItemList || []
  return items.reduce((sum, item) => sum + Number(item?.buyQuantity || 0), 0)
}
function formatAmount(value: any) {
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(2) : (value ?? '')
}
function visibleItems(order: any) {
  const items: any[] = order?.orderItemList || []
  return expandedOrders[order.id] ? items : items.slice(0, COLLAPSED_ITEM_COUNT)
}
function toggleExpand(orderId: string) {
  expandedOrders[orderId] = !expandedOrders[orderId]
}
function isExpanded(orderId: string) {
  return !!expandedOrders[orderId]
}
/** 待付款倒计时：列表接口下发支付时限，缺省与详情页同口径回落默认值 */
function payRemainingText(order: any) {
  if (order?.status !== '1' || order?.payStatus !== '0') {
    return ''
  }
  const createdAt = parseLocalDateTimeTimestamp(order?.createTime)
  if (createdAt === null) {
    return ''
  }
  const minutes = Number(order?.payTimeoutMinutes)
  const timeout = Number.isFinite(minutes) && minutes > 0 ? minutes : DEFAULT_PAY_TIMEOUT_MINUTES
  const remaining = createdAt + timeout * 60_000 - tickNow.value
  if (remaining <= 0) {
    return t('order.list.payExpired')
  }
  const totalSeconds = Math.floor(remaining / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const mins = Math.floor((totalSeconds % 3600) / 60)
  const secs = totalSeconds % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  const time = hours > 0 ? `${pad(hours)}:${pad(mins)}:${pad(secs)}` : `${pad(mins)}:${pad(secs)}`
  return t('order.list.payRemaining', { time })
}
function copyOrderNo(orderNo?: string) {
  if (!orderNo) {
    return
  }
  uni.setClipboardData({
    data: String(orderNo),
    success: () => toast.success(t('order.list.orderNoCopied')),
  })
}

uni.$on('refresh', handleRefresh)

onUnload(() => {
  uni.$off('refresh', handleRefresh)
})
</script>

<template>
  <z-paging
    ref="pagingRef" v-model="state.orderList" :auto="false"
    bg-color="var(--theme-color-background, var(--wot-color-theme-background, #FFF0F0))"
    :refresher-default-text="t('order.list.refresherPull')"
    :refresher-pulling-text="t('order.list.refresherRelease')"
    :refresher-refreshing-text="t('order.list.refresherRefreshing')"
    :refresher-complete-text="t('order.list.refresherDone')"
    :loading-more-default-text="t('order.list.loadMore')"
    :loading-more-loading-text="t('common.loading')"
    :loading-more-fail-text="t('order.list.loadMoreFailed')"
    :loading-more-no-more-text="t('order.list.noMore')"
    @query="queryList" @touchstart="handleTouchStart" @touchend="handleTouchEnd"
  >
    <template #empty>
      <view class="ol-empty">
        <image class="ol-empty__img" src="/static/promotion/empty/empty-order.png" mode="aspectFit" />
        <text class="ol-empty__title">
          {{ t('order.list.empty') }}
        </text>
        <wd-button size="small" round class="ol-empty__btn" @click="goShopping">
          {{ t('order.list.goShopping') }}
        </wd-button>
      </view>
    </template>
    <template #top>
      <hr-navbar :title="t('order.list.title')" />
      <view class="ol-tabs">
        <wd-tabs
          v-model="state.tabCurrent"
          slidable="always"
          swipeable
          :line-width="24"
          :line-height="3"
          inactive-color="#6B7280"
          @change="changeTab"
        >
          <block v-for="(item, index) in orderStatusList" :key="index">
            <wd-tab :title="item.name" />
          </block>
        </wd-tabs>
      </view>
    </template>

    <view class="ol-list">
      <view
        v-for="(order, index) in state.orderList"
        :key="order.id ?? index"
        class="ol-card"
        hover-class="ol-card--pressed"
        :hover-stay-time="60"
      >
        <!-- 卡头：时间/配送方式/订单号 + 状态优先 -->
        <view class="ol-head" @click.stop="toOrderDetail(order.id)">
          <view class="ol-head__left">
            <view class="ol-head__meta">
              <text class="ol-head__time">{{ formatOrderTime(order.createTime) }}</text>
              <template v-if="deliveryWayText(order)">
                <text class="ol-head__dot">·</text>
                <text class="ol-head__way">{{ deliveryWayText(order) }}</text>
              </template>
            </view>
            <view v-if="order.orderNo" class="ol-head__no" @click.stop="copyOrderNo(order.orderNo)">
              <text class="ol-head__no-text">{{ order.orderNo }}</text>
              <text class="ol-head__copy i-carbon:copy" />
            </view>
          </view>
          <view class="ol-head__right">
            <text class="ol-head__status" :class="statusToneClass(order)">
              {{ orderStatusText(order) }}
            </text>
            <text v-if="payRemainingText(order)" class="ol-head__countdown">
              {{ payRemainingText(order) }}
            </text>
          </view>
        </view>

        <!-- 商品行 -->
        <view class="ol-items" @click.stop="toOrderDetail(order.id)">
          <view v-for="(item, itemIndex) in visibleItems(order)" :key="item.id ?? itemIndex" class="ol-item">
            <image :src="item.picUrl" class="ol-item__pic" mode="aspectFill" />
            <view class="ol-item__info">
              <view class="ol-item__top">
                <view class="ol-item__main">
                  <text class="ol-item__name">{{ item.spuName }}</text>
                  <text v-if="item.specsInfo" class="ol-item__specs">{{ item.specsInfo }}</text>
                  <view v-if="order.payStatus === '1' && ['1', '2', '3', '6'].includes(item.status)" class="ol-item__foot">
                    <text
                      v-if="item.status === '1' || item.status === '2'"
                      class="ol-item__after"
                      @click.stop="toRefunds(item.id, item.status)"
                    >
                      {{ t('order.actions.applyAfterSales') }}
                      <text class="ol-item__after-arrow">›</text>
                    </text>
                    <text v-else class="ol-item__state">{{ itemStatusText(item.status) }}</text>
                  </view>
                </view>
                <view class="ol-item__price-wrap">
                  <price-text
                    class="ol-item__price"
                    :value="item.paymentPrice ?? item.salesPrice"
                    symbol="¥"
                  />
                  <text class="ol-item__qty">×{{ item.buyQuantity }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 多商品折叠 -->
        <view
          v-if="(order.orderItemList?.length || 0) > COLLAPSED_ITEM_COUNT"
          class="ol-more"
          @click.stop="toggleExpand(order.id)"
        >
          <text class="ol-more__text">
            {{ isExpanded(order.id) ? t('order.list.collapse') : t('order.list.moreItems', { count: order.orderItemList.length - COLLAPSED_ITEM_COUNT }) }}
          </text>
          <text class="ol-more__icon i-carbon:chevron-down" :class="{ 'ol-more__icon--up': isExpanded(order.id) }" />
        </view>

        <!-- 合计 -->
        <view class="ol-total" @click.stop="toOrderDetail(order.id)">
          <text class="ol-total__count">
            {{ t('order.list.totalItems', { count: totalQuantity(order) }) }}
          </text>
          <text class="ol-total__amount">
            <text class="ol-total__currency">¥</text>{{ formatAmount(order.actualPayPrice ?? order.paymentPrice) }}
          </text>
        </view>

        <!-- 操作区：主操作实心品牌色，次操作中性描边 -->
        <order-operation
          mode="list"
          :order-info="order"
          @order-del="orderDel(index)"
          @order-receiver="orderReceiver"
          @order-cancel="orderCancel"
        />
      </view>
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
/*
 * 订单列表（主流电商式重排）：
 * 状态优先卡头 → 紧凑商品行（超两件折叠）→ 合计 → 胶囊操作。
 * 与订单详情页共用同一套颜色/圆角/留白口径。
 */
$brand: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$ink: var(--app-text);
$ink-2: var(--app-text-2);
$ink-3: var(--app-text-3);
$hairline: var(--app-border);

.ol-tabs {
  background: var(--app-card);
  box-shadow: 0 4rpx 16rpx rgba(17, 24, 39, 0.03);
}

.ol-list {
  padding: 20rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
}

.ol-card {
  padding: 0 24rpx;
  margin-bottom: 20rpx;
  background: var(--app-card);
  border-radius: 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(17, 24, 39, 0.04);
  transition: background-color 0.15s ease;
}

.ol-card--pressed {
  background: var(--app-card-2);
}

/* ===================== 卡头 ===================== */

.ol-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 24rpx 0 20rpx;
  border-bottom: 1rpx solid $hairline;
}

.ol-head__left {
  flex: 1;
  min-width: 0;
  margin-right: 16rpx;
}

.ol-head__meta {
  display: flex;
  align-items: center;
  min-width: 0;
}

.ol-head__time {
  flex-shrink: 0;
  font-size: 24rpx;
  color: $ink-2;
}

.ol-head__dot {
  margin: 0 8rpx;
  font-size: 22rpx;
  color: $ink-3;
}

.ol-head__way {
  overflow: hidden;
  font-size: 22rpx;
  color: $ink-3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ol-head__no {
  display: flex;
  align-items: center;
  margin-top: 6rpx;
}

.ol-head__no-text {
  font-size: 22rpx;
  color: $ink-3;
}

.ol-head__copy {
  margin-left: 8rpx;
  font-size: 22rpx;
  color: $ink-3;
}

.ol-head__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

.ol-head__status {
  font-size: 28rpx;
  font-weight: 600;
  line-height: 36rpx;
}

.ol-head__status--active {
  color: $brand;
}

.ol-head__status--done {
  color: $ink-2;
}

.ol-head__status--muted {
  font-weight: 500;
  color: $ink-3;
}

.ol-head__countdown {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $brand;
  font-variant-numeric: tabular-nums;
}

/* ===================== 商品行 ===================== */

.ol-items {
  padding: 4rpx 0;
}

.ol-item {
  display: flex;
  padding: 20rpx 0;
}

.ol-item__pic {
  width: 136rpx;
  height: 136rpx;
  border-radius: 16rpx;
  background: var(--app-card-2);
  flex-shrink: 0;
}

.ol-item__info {
  display: flex;
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
}

.ol-item__top {
  display: flex;
  flex: 1;
  justify-content: space-between;
  min-width: 0;
}

.ol-item__main {
  flex: 1;
  min-width: 0;
  margin-right: 16rpx;
}

.ol-item__name {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: 28rpx;
  line-height: 38rpx;
  color: $ink;
  word-break: break-all;
}

.ol-item__specs {
  display: block;
  overflow: hidden;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $ink-3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ol-item__foot {
  display: flex;
  align-items: center;
  margin-top: 10rpx;
}

.ol-item__after {
  display: inline-flex;
  align-items: center;
  padding: 6rpx 0;
  font-size: 22rpx;
  line-height: 30rpx;
  color: $ink-2;
}

.ol-item__after-arrow {
  margin-left: 2rpx;
  font-size: 20rpx;
  color: $ink-3;
}
.ol-item__state {
  font-size: 22rpx;
  color: $ink-3;
}

.ol-item__price-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

.ol-item__price {
  font-size: 28rpx;
  font-weight: 600;
  line-height: 36rpx;
  color: $ink;
}

.ol-item__qty {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $ink-3;
}

/* ===================== 折叠 ===================== */

.ol-more {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  margin: 0 -24rpx;
  border-top: 1rpx solid $hairline;
}

.ol-more__text {
  font-size: 24rpx;
  color: $ink-2;
}

.ol-more__icon {
  margin-left: 6rpx;
  font-size: 24rpx;
  color: $ink-3;
  transition: transform 0.2s ease;
}

.ol-more__icon--up {
  transform: rotate(180deg);
}

/* ===================== 合计与操作 ===================== */

.ol-total {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  padding: 18rpx 0 4rpx;
}

.ol-total__count {
  margin-right: 12rpx;
  font-size: 24rpx;
  color: $ink-3;
}

.ol-total__amount {
  font-size: 32rpx;
  font-weight: 700;
  color: $brand;
  font-variant-numeric: tabular-nums;
}

.ol-total__currency {
  margin-right: 2rpx;
  font-size: 24rpx;
  font-weight: 600;
}

/* ===================== 空态 ===================== */

.ol-empty {
  display: flex;
  padding-top: 140rpx;
  flex-direction: column;
  align-items: center;
}

.ol-empty__img {
  width: 260rpx;
  height: 260rpx;
  opacity: 0.9;
}

.ol-empty__title {
  margin-top: 20rpx;
  font-size: 28rpx;
  color: $ink-2;
}

.ol-empty__btn {
  margin-top: 32rpx;
}
</style>
