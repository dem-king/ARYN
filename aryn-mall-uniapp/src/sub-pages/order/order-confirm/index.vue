<script setup lang="ts">
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { reactive, ref } from 'vue'
import { orderCreate, orderSettlement } from '@/api/order/orderInfo'
import { useShipContextStore } from '@/store/shipContextStore'
import { getPage as getCouponList } from '@/api/promotion/couponUser'
import { getDefault } from '@/api/user/address'
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
  getDefaultAddress()
})

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
    // 公司港口/船舶内部配送：上下文来自船舶工作台，服务端结算时再次校验
    state.orderParams.deliveryWay = '4'
    Object.assign(state.orderParams, shipContextStore.deliveryContextParams)
  } else {
    state.orderParams.deliveryWay = '1'
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
  state.orderParams.deliveryWay = item.deliveryWay
  toSettlement()
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
  } else {
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

  // 商城配送（deliveryWay=3）同样需要收货地址
  if (state.orderParams.deliveryWay === '3' && !selectedAddress.value?.id) {
    return useGlobalToast().warning('请选择收货地址')
  }

  // 内部配送（deliveryWay=4）必须携带船舶与靠港计划上下文
  if (state.orderParams.deliveryWay === '4') {
    if (!shipContextStore.hasVesselContext) {
      return useGlobalToast().warning('请先在首页选择船舶和靠港计划')
    }
    Object.assign(state.orderParams, shipContextStore.deliveryContextParams)
  } else {
    delete state.orderParams.purchaseScene
    delete state.orderParams.vesselId
    delete state.orderParams.vesselCallId
  }

  submitting.value = true
  globalLoading.loading('加载中...')

  try {
  // 创建订单
    const response = await orderCreate(state.orderParams)
    router.replace({
      name: 'order-pay',
      params: {
        orderNo: response.orderNo,
      },
    })
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

    <!-- 订单列表 -->
    <ShopOrderItem
      :order="state.orderInfo"
      :coupon-user-list="state.couponUserList"
      :delivery-way="state.orderParams.deliveryWay"
      @delivery-way-change="deliveryWayChange" @remark-change="remarkChange" @show-coupon="showCoupon"
    />

    <wd-gap :height="60" />
    <!-- 支付底部 -->
    <PaymentFooter :payment-price="state.orderInfo.paymentPrice" @to-pay="toPay" />
    <!-- 优惠券选择 -->
    <CouponSelector
      :coupon-popup="couponState.couponPopup" :coupon-user-list="state.couponUserList"
      :order-price="couponState.orderPrice"
      :order-item-list="couponState.orderItemList"
      @close-coupon-popup="closeCouponPopup" @coupon-confirm="couponConfirm"
    />
  </view>
</template>
