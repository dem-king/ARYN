<script setup lang="ts">
import type { NewbiePriceDisplay } from '@/utils/newbie-price'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { orderCreate, orderSettlement } from '@/api/order/orderInfo'
import { getNewbieSkuPrices } from '@/api/promotion'
import { getPage as getCouponList } from '@/api/promotion/couponUser'
import { getDefault } from '@/api/user/address'
import { useShipContextStore } from '@/store/shipContextStore'
import {
  hasNewbiePrice,
  newbieIneligibleNotice,
  resolveNewbiePriceDisplay,
} from '@/utils/newbie-price'
import { mergeDuplicateSkuItems } from '@/utils/order-sku-merge'
import AddressSelector from './components/AddressSelector.vue'
import CouponSelector from './components/CouponSelector.vue'
import PaymentFooter from './components/PaymentFooter.vue'
import ShopOrderItem from './components/ShopOrderItem.vue'

definePage({
  name: 'order-confirm',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '确认订单',
  },
})
interface State {
  orderInfo: any
  couponUserList: any[]
  orderParams: any
  totalPrice: number
  freightPrice: number
  couponPrice: number
  paymentPrice: number
  createWay: string
  isAddressShow: boolean
}
interface CouponState {
  couponUserList: any[]
  orderPrice: number
  couponPopup: boolean
  couponUserId: string
  orderItemList: any[]
  /** 券门槛可计算金额（允许叠券明细折后合计，结算接口下发）：券弹层门槛判定用 */
  couponEligibleBase: number | null
}
interface Address {
  id: string
  detailAddress: string
  recipientName: string
  telephone: string
  provinceName: string
  cityName: string
  areaName: string
}
const goodsStore = useGoodsStore()
const shoppingCartStore = useShoppingCartStore()
const shipContextStore = useShipContextStore()
const router = useRouter()
const { t } = useI18n()
const { createGoodsList } = storeToRefs(goodsStore)
const globalLoading = useGlobalLoading()
const loading = ref(true)
const submitting = ref(false)
const pointsEnabled = ref(false)
const userStore = useUserStore()
let settlementSeq = 0
const selectedAddress = ref<Address>({
  id: '',
  detailAddress: '',
  recipientName: '',
  telephone: '',
  provinceName: '',
  cityName: '',
  areaName: '',
})

const couponState = reactive<CouponState>({
  couponUserList: [],
  orderPrice: 0,
  couponPopup: false,
  couponUserId: '',
  orderItemList: [],
  couponEligibleBase: null,

})
const state = reactive<State>({
  orderInfo: {},
  couponUserList: [],
  orderParams: {}, // 结算和下单参数
  totalPrice: 0, // 订单总金额
  freightPrice: 0, // 运费总金额
  couponPrice: 0, // 优惠券减免总金额
  paymentPrice: 0, // 支付总金额
  createWay: '2', // 订单创建方式：1.购物车下单；2.普通购买下单
  isAddressShow: false, // 是否显示收货地址
})
onLoad(async (options) => {
  state.createWay = options?.createWay || '2'
  state.orderParams.requestId = createOrderRequestId()
  // 拼团单：活动 SKU 由拼团详情页写入 goodsStore，此处只记录拼团记录 ID，
  // 服务端按记录校验资格并以拼团价成交（前端不传价格，防篡改）
  if (options?.groupBuyRecordId) {
    state.orderParams.groupBuyRecordId = options.groupBuyRecordId
  }
  getDefaultAddress()
})

/** 是否拼团单：拼团价由服务端重算，支付方式必须在线（成团判定依赖支付回调） */
const isGroupBuyOrder = computed(() => !!state.orderParams.groupBuyRecordId)

/**
 * 新人价资格提示。
 *
 * 只做提示、**不参与算价**：应付金额一律以 orderSettlement 返回的权威值为准
 * （后端已按「拼团 > 秒杀 > 新人价 > 折扣」重算并校验资格），前端再算一遍
 * 只会引入第二套口径，改错时反而与真实扣款不一致。
 *
 * 提示存在的意义是解释价差：用户从新人价楼层点进来，结算若按原价收，
 * 没有说明就是「页面标 0.01、结算收 5.90」的观感来源（验收用例 11）。
 *
 * 这里只存结构化状态，文案在 computed 里按当前语言生成：
 * 切换语言时提示条能实时刷新，不在异步回调里拼好中文串存 ref。
 */
type NewbieNoticeState =
  | { kind: 'none' }
  | { kind: 'ineligible', reason: string }
  | { kind: 'applied' }

const newbieNoticeState = ref<NewbieNoticeState>({ kind: 'none' })

const newbieNotice = computed(() => {
  const notice = newbieNoticeState.value
  switch (notice.kind) {
    case 'ineligible':
      return t('checkout.newbie.ineligibleSuffix', { reason: notice.reason })
    case 'applied':
      return t('checkout.newbie.applied')
    default:
      return ''
  }
})

/**
 * 本单是否真的按新人价成交。
 *
 * 用「服务端返回的明细成交单价 == 新人价」来判定，而不是看 promotionDetails：
 * `applyShipWholeDiscount` 在整单优惠为 0 时会提前 return（不写 promotionDetails），
 * 因此新人价订单大多数情况下 _没有_ detail 可比，用 detail 判定会永远为假。
 * 明细单价则是后端改价后的权威结果，比对它不存在口径漂移。
 *
 * @param priceBySku  本单 skuId → display（与明细按 skuId 对齐，不依赖数组顺序）
 */
function isNewbieApplied(priceBySku: Record<string, NewbiePriceDisplay>) {
  const items: any[] = state.orderInfo?.orderItemList || []
  if (!items.length)
    return false
  return items.some((item: any) => {
    const display = priceBySku[String(item?.skuId ?? '')]
    if (!display?.showNewbiePrice || display.newbiePrice === null)
      return false
    // 金额比对留 1 分钱余量：后端是 BigDecimal，前端是二进制浮点
    return Math.abs(Number(item?.salesPrice) - display.newbiePrice) < 0.01
  })
}

/**
 * 查询本单 SKU 的新人价与当前用户资格，生成提示文案。
 *
 * 失败静默：提示属增强信息，接口异常时页面照常按服务端金额展示与提交。
 */
async function loadNewbieHint() {
  const skuIds = (createGoodsList.value || [])
    .map((item: any) => String(item?.skuId || '').trim())
    .filter(Boolean)
  if (!skuIds.length) {
    newbieNoticeState.value = { kind: 'none' }
    return
  }
  try {
    const vo = await getNewbieSkuPrices(skuIds)
    const priceBySku: Record<string, NewbiePriceDisplay> = {}
    const displays = skuIds.map((skuId) => {
      const display = resolveNewbiePriceDisplay(vo, skuId)
      priceBySku[skuId] = display
      return display
    })
    // 「已登录但确定不符合」优先于正向提示：这是用户最需要解释的一种情况
    const ineligible = newbieIneligibleNotice(displays)
    if (ineligible) {
      newbieNoticeState.value = { kind: 'ineligible', reason: ineligible }
      return
    }
    // 正向提示只在「资格可享」且「服务端确实按新人价成交」时出现：
    // 只满足其一都说明两边判定不一致，此时不提示比提示错更好
    newbieNoticeState.value = hasNewbiePrice(displays) && isNewbieApplied(priceBySku)
      ? { kind: 'applied' }
      : { kind: 'none' }
  }
  catch {
    newbieNoticeState.value = { kind: 'none' }
  }
}

function createOrderRequestId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}
// 初始化订单
function initData() {
  loading.value = true
  // 购物车按靠港分组，同一 SKU 可能合法地出现多行（已结束靠港的滞留行 +
  // 当前无靠港行）；结算/下单接口只接受一个 SKU 一行，这里按 SKU 合并数量后再提交。
  // 注意：防串船校验仍用原始 createGoodsList 逐行比对，不能用合并后的列表
  const mergedGoodsList = mergeDuplicateSkuItems(createGoodsList.value || [])
  const orderItemList: Array<any> = []
  mergedGoodsList.forEach((item: any) => {
    orderItemList.push({
      spuId: item.spuId,
      picUrl: item.picUrl,
      spuName: item.spuName,
      quantity: item.quantity,
      specsInfo: item.specsInfo,
      skuId: item.skuId,
    })
  })
  if (shipContextStore.hasVesselContext) {
    // 入口统一：在船成员只走内部配送（way=4），结算页不再提供地址簿类配送方式；
    // 上下文来自船舶工作台，服务端结算时再次校验
    state.orderParams.deliveryWay = '4'
    Object.assign(state.orderParams, shipContextStore.deliveryContextParams)
  }
  else {
    // 未绑定上下文（岸上场景）：默认商城配送（deliveryWay=3），用户可在结算页弹层里改选
    state.orderParams.deliveryWay = '3'
  }
  // 商城配送（3）/内部配送（4）默认货到付款，收货后线下结算；用户仍可在支付方式行切回在线支付。
  // 拼团单例外：必须在线支付（成团判定依赖支付回调），不默认、也不允许切货到付款
  if (!isGroupBuyOrder.value
    && (state.orderParams.deliveryWay === '3' || state.orderParams.deliveryWay === '4')) {
    state.orderParams.paymentType = '3'
  }
  state.orderParams.skuReqList = orderItemList
  toSettlement()
}
/**
 * 查询默认地址
 */
function getDefaultAddress() {
  getDefault().then((res) => {
    selectedAddress.value = res
    initData()
  })
}
// 配送方式切换
function deliveryWayChange(item: any) {
  const previousWay = state.orderParams.deliveryWay
  state.orderParams.deliveryWay = item.deliveryWay
  // 货到付款仅商城配送（3）/内部配送（4）可用：切走时重置为在线支付，
  // 从其它方式切入时默认货到付款；3/4 之间互切保留用户已选的支付方式。
  // 拼团单恒为在线支付（成团判定依赖支付回调）
  if (isGroupBuyOrder.value) {
    state.orderParams.paymentType = ''
  }
  else {
    const codAvailable = item.deliveryWay === '3' || item.deliveryWay === '4'
    if (!codAvailable) {
      state.orderParams.paymentType = ''
    }
    else if (previousWay !== '3' && previousWay !== '4') {
      state.orderParams.paymentType = '3'
    }
  }
  toSettlement()
}

// 支付方式切换（货到付款不影响结算价格，无需重新结算）
function paymentWayChange(paymentWay: string) {
  state.orderParams.paymentType = paymentWay
}
/**
 * 备注输入监听
 */
function remarkChange(item: any) {
  state.orderParams.remark = item.remark
}

function showCoupon(item: any) {
  if (item.couponUserId) {
    couponState.couponUserId = item.couponUserId
  }
  couponState.couponPopup = true
  couponState.orderPrice = item.totalPrice
  couponState.orderItemList = item.orderItemList
  const eligible = Number(item.couponEligibleAmount)
  couponState.couponEligibleBase = Number.isFinite(eligible) && eligible > 0 ? eligible : null
}
// 选中优惠券回调
function couponConfirm(couponUserId: string) {
  state.orderParams.couponUserId = couponUserId
  couponState.couponPopup = false
  toSettlement()
}
function closeCouponPopup() {
  couponState.couponPopup = false
}

function pointsToggle(enabled: boolean) {
  if (enabled && Number(state.orderInfo?.pointsDeductMax) <= 0) {
    useGlobalToast().warning(t('checkout.points.noneDeductible'))
    return
  }
  pointsEnabled.value = enabled
  toSettlement()
}

// 去结算
async function toSettlement() {
  const seq = ++settlementSeq
  if (pointsEnabled.value) {
    // 开关语义为「使用全部可用积分」：由服务端按最新优惠与余额重新计算额度，
    // 避免优惠变化后前端仍按旧额度抵扣导致确认金额与实际扣款不一致
    state.orderParams.useMaxPoints = true
    delete state.orderParams.usePoints
  }
  else {
    delete state.orderParams.useMaxPoints
    delete state.orderParams.usePoints
  }
  globalLoading.loading(t('common.loading'))
  state.orderParams.createWay = state.createWay
  state.orderParams.userAddressId = selectedAddress.value ? selectedAddress.value.id : ''
  if (state.orderParams.deliveryWay === '4' && shipContextStore.deliveryContextParams) {
    Object.assign(state.orderParams, shipContextStore.deliveryContextParams)
  }
  else {
    delete state.orderParams.purchaseScene
    delete state.orderParams.vesselId
    delete state.orderParams.vesselCallId
  }

  // 结算订单
  try {
    const response = await orderSettlement(state.orderParams)
    // 快速切换积分/优惠券会并发多次结算，只应用最后一次响应
    if (seq !== settlementSeq)
      return
    state.orderInfo = response
    if (pointsEnabled.value && Number(response?.pointsUsed) <= 0) {
      pointsEnabled.value = false
      delete state.orderParams.useMaxPoints
    }
    // 叠加规则：所选券对全部明细不可叠时服务端不下发券额（couponStackReason 说明原因），
    // 自动取消选择避免提交时被 60067 硬拒；金额以本次结算（未用券）为准
    if (response?.couponStackReason && state.orderParams.couponUserId) {
      state.orderParams.couponUserId = ''
      useGlobalToast().warning(response.couponStackReason)
    }
    // 资格提示依赖登录态与活动，不 await：结算金额已是权威值，
    // 提示晚到一帧不影响用户看到正确金额
    void loadNewbieHint()
    // 查询优惠券
    getCouponList({ status: '0' }).then((res) => {
      state.couponUserList = res.records
    })
  }
  finally {
    if (seq === settlementSeq) {
      loading.value = false
      globalLoading.close()
    }
  }
}
// 去支付
async function toPay() {
  if (submitting.value)
    return
  state.orderParams.createWay = state.createWay
  state.orderParams.userAddressId = selectedAddress.value ? selectedAddress.value.id : ''
  if (!state.orderParams.deliveryWay) {
    return useGlobalToast().warning(t('checkout.toast.selectDelivery'))
  }

  if (state.orderParams.deliveryWay === '1' && !selectedAddress.value?.id) {
    return useGlobalToast().warning(t('checkout.toast.selectAddress'))
  }

  // 拼团单兜底：支付方式只允许在线支付（服务端同样校验，防前端状态残留）
  if (isGroupBuyOrder.value) {
    state.orderParams.paymentType = ''
  }

  // 商城配送（deliveryWay=3）同样需要收货地址
  if (state.orderParams.deliveryWay === '3' && !selectedAddress.value?.id) {
    return useGlobalToast().warning(t('checkout.toast.selectAddress'))
  }

  // 防串船：购物车下单时所选行的靠港归属必须与当前上下文一致（无归属的旧行放行）
  if (state.createWay === '1' && shipContextStore.vesselCallId) {
    const mismatched = (createGoodsList.value || []).filter(
      (item: any) => item.vesselCallId && item.vesselCallId !== shipContextStore.vesselCallId,
    )
    if (mismatched.length > 0) {
      return useGlobalToast().warning(
        t('checkout.toast.otherVesselItems', { count: mismatched.length }),
      )
    }
  }

  // 内部配送（deliveryWay=4）必须携带船舶与靠港计划上下文
  if (state.orderParams.deliveryWay === '4') {
    if (!shipContextStore.hasVesselContext) {
      return useGlobalToast().warning(t('checkout.toast.selectVesselContext'))
    }
    Object.assign(state.orderParams, shipContextStore.deliveryContextParams)
  }
  else {
    delete state.orderParams.purchaseScene
    delete state.orderParams.vesselId
    delete state.orderParams.vesselCallId
  }

  submitting.value = true
  globalLoading.loading(t('common.loading'))

  try {
    // 携带确认页金额：服务端重算不一致时会拒绝成单，避免优惠额度变化后静默涨价
    const expectedPaymentPrice = Number(state.orderInfo?.paymentPrice)
    if (Number.isFinite(expectedPaymentPrice)) {
      state.orderParams.expectedPaymentPrice = expectedPaymentPrice
    }
    else {
      delete state.orderParams.expectedPaymentPrice
    }
    // 创建订单
    const response = await orderCreate(state.orderParams)
    // 下单后刷新积分余额；刷新属增强信息，失败不打断下单成功流程
    userStore.refreshPointsInfo().catch(() => {})
    if (state.orderParams.paymentType === '3') {
      // 货到付款：无需在线支付，直接进订单详情
      useGlobalToast().success(t('checkout.toast.codSuccess'))
      router.replace({
        name: 'order-detail',
        params: {
          id: response.id,
        },
      })
    }
    else {
      router.replace({
        name: 'order-pay',
        params: {
          orderNo: response.orderNo,
        },
      })
    }
    if (state.createWay === '1') {
      // 刷新购物车数量
      shoppingCartStore.fetchCartCount()
    }
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}
// 跳转到收货地址页面
function toAddress() {
  router.push({
    name: 'address-list',
    params: {
      placeChooseFlag: 'true',
      placeChooseId: selectedAddress.value && selectedAddress.value.id ? selectedAddress.value.id : '',
    },
  })
}
function handleSelectedAddressUpdate(newAddress: Address) {
  selectedAddress.value = newAddress
}

uni.$on('update:selectedAddress', handleSelectedAddressUpdate)

onUnload(() => {
  uni.$off('update:selectedAddress', handleSelectedAddressUpdate)
})
</script>

<template>
  <hr-navbar :title="t('checkout.title')" />
  <view v-if="!loading">
    <!-- 内部配送上下文（delivery_way=4） -->
    <view
      v-if="state.orderParams.deliveryWay === '4'"
      class="hx-mb10"
      style="
        background: #fff;
        border-radius: 12rpx;
        margin: 20rpx;
        padding: 24rpx;
      "
    >
      <view style="font-weight: bold">
        {{ t('checkout.delivery.deliverTo', { name: shipContextStore.vesselName }) }}
      </view>
      <view style="color: #909399; font-size: 24rpx">
        {{ shipContextStore.portName }} {{ shipContextStore.berth }}
        <text v-if="shipContextStore.deliveryWindowStart">
          ({{ shipContextStore.deliveryWindowStart }} ~
          {{ shipContextStore.deliveryWindowEnd }})
        </text>
      </view>
      <view style="color: #10b981; font-size: 24rpx; margin-top: 8rpx">
        {{ t('checkout.delivery.toVesselNote') }}
      </view>
    </view>

    <!-- 收货地址选择（普通快递/商城配送） -->
    <AddressSelector
      v-if="state.orderParams.deliveryWay !== '4'"
      :selected-address="selectedAddress"
      @to-address="toAddress"
    />

    <!--
      新人价资格提示：价格本身由服务端结算重算，这里只解释「为什么是这个价」。
      放在营销优惠明细之前——它是对整单价格的说明，不是某条优惠的明细。
    -->
    <view v-if="newbieNotice" class="newbie-notice">
      <text class="newbie-notice-text">
        {{ newbieNotice }}
      </text>
    </view>

    <!-- 营销优惠明细（阶梯价/整船优惠，结算返回） -->
    <view
      v-if="
        (state.orderInfo.promotionDetails && state.orderInfo.promotionDetails.length)
          || state.orderInfo.promoPrice > 0
      "
      class="hx-mb10"
      style="
        background: #fff;
        border-radius: 12rpx;
        margin: 20rpx;
        padding: 24rpx;
      "
    >
      <view style="font-weight: bold">
        {{ t('checkout.promotion.title') }}
      </view>
      <view
        v-for="detail in state.orderInfo.promotionDetails || []"
        :key="detail.activityId"
        style="
          color: #e67e22;
          font-size: 24rpx;
          margin-top: 8rpx;
        "
      >
        {{ t('checkout.promotion.activitySaved', { name: detail.activityName }) }}
        <template v-if="detail.activityType === '4'">
          {{ t('checkout.promotion.tieredPrice') }}
        </template>
        <template v-else>
          ¥{{ detail.discountAmount }}
        </template>
      </view>
      <view
        v-if="state.orderInfo.promoPrice > 0"
        style="color: #e67e22; font-size: 24rpx; margin-top: 8rpx"
      >
        {{ t('checkout.promotion.vesselTotal', { amount: state.orderInfo.promoPrice }) }}
      </view>
    </view>

    <!-- 买赠赠品预览（结算已按真实库存过滤；库存不足的赠品不展示即不赠） -->
    <view
      v-if="state.orderInfo.giftItems && state.orderInfo.giftItems.length"
      class="hx-mb10"
      style="
        background: #fff;
        border-radius: 12rpx;
        margin: 20rpx;
        padding: 24rpx;
      "
    >
      <view style="font-weight: bold">
        {{ t('checkout.promotion.gifts') }}
      </view>
      <view
        v-for="gift in state.orderInfo.giftItems"
        :key="gift.skuId"
        style="color: #e67e22; font-size: 24rpx; margin-top: 8rpx"
      >
        {{ t('checkout.promotion.giftItem', { name: gift.spuName || gift.skuId, qty: gift.quantity }) }}
      </view>
    </view>

    <!-- 订单列表 -->
    <ShopOrderItem
      :order="state.orderInfo"
      :coupon-user-list="state.couponUserList"
      :delivery-way="state.orderParams.deliveryWay"
      :payment-way="state.orderParams.paymentType"
      :group-buy-order="isGroupBuyOrder"
      :points-enabled="pointsEnabled"
      @delivery-way-change="deliveryWayChange" @payment-way-change="paymentWayChange" @remark-change="remarkChange" @show-coupon="showCoupon" @points-toggle="pointsToggle"
    />

    <wd-gap :height="60" />
    <!-- 支付底部：货到付款时按钮为「提交订单」，下单后直接进详情不再拉起收银台 -->
    <PaymentFooter
      :payment-price="state.orderInfo.paymentPrice"
      :payment-way="state.orderParams.paymentType"
      @to-pay="toPay"
    />
    <!-- 优惠券选择 -->
    <CouponSelector
      :coupon-popup="couponState.couponPopup" :coupon-user-list="state.couponUserList"
      :order-price="couponState.orderPrice"
      :order-item-list="couponState.orderItemList"
      :coupon-eligible-base="couponState.couponEligibleBase"
      @close-coupon-popup="closeCouponPopup" @coupon-confirm="couponConfirm"
    />
  </view>
</template>

<style scoped lang="scss">
/*
 * 新人价提示条。
 *
 * 本页其余卡片都是内联样式（白底圆角、margin 20rpx），这里跟随同一节奏，
 * 但用浅暖底 + 左侧色条区分「说明」与「可操作区域」：
 * 它是解释性信息，不该读起来像第二张营销优惠卡片。
 */
.newbie-notice {
  margin: 20rpx;
  padding: 18rpx 24rpx;
  border-left: 6rpx solid var(--wot-color-theme-primary, #ff2237);
  border-radius: 12rpx;
  background: #fff7e8;

  .newbie-notice-text {
    color: #8a4b3a;
    font-size: 24rpx;
    line-height: 36rpx;
  }
}
</style>
