<script setup lang="ts">
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { reactive, ref } from 'vue'
import { orderCreate, orderSettlement } from '@/api/order/orderInfo'
import { getPage as getCouponList } from '@/api/promotion/couponUser'
import { getDefault } from '@/api/user/address'
import { useShipContextStore } from '@/store/shipContextStore'
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
const { createGoodsList } = storeToRefs(goodsStore)
const globalLoading = useGlobalLoading()
const loading = ref(true)
const submitting = ref(false)
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

function createOrderRequestId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}
// 初始化订单
function initData() {
  loading.value = true
  const orderItemList: Array<any> = []
  createGoodsList.value.forEach((item: any) => {
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

// 去结算
async function toSettlement() {
  globalLoading.loading('加载中...')
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
    Object.assign(state.orderInfo, response)
    // 查询优惠券
    getCouponList({ status: '0' }).then((res) => {
      state.couponUserList = res.records
    })
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}
// 去支付
async function toPay() {
  if (submitting.value)
    return
  state.orderParams.createWay = state.createWay
  state.orderParams.userAddressId = selectedAddress.value ? selectedAddress.value.id : ''
  if (!state.orderParams.deliveryWay) {
    return useGlobalToast().warning('请选择配送方式')
  }

  if (state.orderParams.deliveryWay === '1' && !selectedAddress.value?.id) {
    return useGlobalToast().warning('请选择收货地址')
  }

  // 拼团单兜底：支付方式只允许在线支付（服务端同样校验，防前端状态残留）
  if (isGroupBuyOrder.value) {
    state.orderParams.paymentType = ''
  }

  // 商城配送（deliveryWay=3）同样需要收货地址
  if (state.orderParams.deliveryWay === '3' && !selectedAddress.value?.id) {
    return useGlobalToast().warning('请选择收货地址')
  }

  // 防串船：购物车下单时所选行的靠港归属必须与当前上下文一致（无归属的旧行放行）
  if (state.createWay === '1' && shipContextStore.vesselCallId) {
    const mismatched = (createGoodsList.value || []).filter(
      (item: any) => item.vesselCallId && item.vesselCallId !== shipContextStore.vesselCallId,
    )
    if (mismatched.length > 0) {
      return useGlobalToast().warning(
        `有 ${mismatched.length} 件商品属于其他靠港计划，请切换船舶或调整购物车`,
      )
    }
  }

  // 内部配送（deliveryWay=4）必须携带船舶与靠港计划上下文
  if (state.orderParams.deliveryWay === '4') {
    if (!shipContextStore.hasVesselContext) {
      return useGlobalToast().warning('请先选择船舶和靠港计划')
    }
    Object.assign(state.orderParams, shipContextStore.deliveryContextParams)
  }
  else {
    delete state.orderParams.purchaseScene
    delete state.orderParams.vesselId
    delete state.orderParams.vesselCallId
  }

  submitting.value = true
  globalLoading.loading('加载中...')

  try {
  // 创建订单
    const response = await orderCreate(state.orderParams)
    if (state.orderParams.paymentType === '3') {
      // 货到付款：无需在线支付，直接进订单详情
      useGlobalToast().success('下单成功，收货后请线下支付货款')
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
  <hr-navbar title="订单确认" />
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
        配送至：{{ shipContextStore.vesselName }}
      </view>
      <view style="color: #909399; font-size: 24rpx">
        {{ shipContextStore.portName }} {{ shipContextStore.berth }}
        <text v-if="shipContextStore.deliveryWindowStart">
          （{{ shipContextStore.deliveryWindowStart }} ~
          {{ shipContextStore.deliveryWindowEnd }}）
        </text>
      </view>
      <view style="color: #10b981; font-size: 24rpx; margin-top: 8rpx">
        公司司机按靠港计划送达港口/船舶
      </view>
    </view>

    <!-- 收货地址选择（普通快递/商城配送） -->
    <AddressSelector
      v-if="state.orderParams.deliveryWay !== '4'"
      :selected-address="selectedAddress"
      @to-address="toAddress"
    />

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
        营销优惠
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
        {{ detail.activityName }}：已优惠
        <template v-if="detail.activityType === '4'">
          阶梯单价（明细已按档价计价）
        </template>
        <template v-else>
          ¥{{ detail.discountAmount }}
        </template>
      </view>
      <view
        v-if="state.orderInfo.promoPrice > 0"
        style="color: #e67e22; font-size: 24rpx; margin-top: 8rpx"
      >
        整船优惠合计：-¥{{ state.orderInfo.promoPrice }}
      </view>
    </view>

    <!-- 订单列表 -->
    <ShopOrderItem
      :order="state.orderInfo"
      :coupon-user-list="state.couponUserList"
      :delivery-way="state.orderParams.deliveryWay"
      :payment-way="state.orderParams.paymentType"
      :group-buy-order="isGroupBuyOrder"
      @delivery-way-change="deliveryWayChange" @payment-way-change="paymentWayChange" @remark-change="remarkChange" @show-coupon="showCoupon"
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
      @close-coupon-popup="closeCouponPopup" @coupon-confirm="couponConfirm"
    />
  </view>
</template>
