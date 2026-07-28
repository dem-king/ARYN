<script setup lang="ts">
import type { MallDeliveryAvailability } from '@/api/order/mallDelivery'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { reactive, ref } from 'vue'
import { getMallDeliveryAvailability } from '@/api/order/mallDelivery'
import { orderCreate, orderSettlement } from '@/api/order/orderInfo'
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
const router = useRouter()
const { createGoodsList } = storeToRefs(goodsStore)
const globalLoading = useGlobalLoading()
const loading = ref(true)
const submitting = ref(false)
const mallDeliveryAvailability = ref<MallDeliveryAvailability>({
  available: false,
  reason: '请选择收货地址',
})
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
  state.orderParams.deliveryWay = '1'
  state.orderParams.skuReqList = orderItemList
  toSettlement()
}
/**
 * 查询默认地址
 */
function getDefaultAddress() {
  getDefault().then(async (res) => {
    selectedAddress.value = res
    await refreshMallDeliveryAvailability()
    initData()
  })
}
// 配送方式切换
function deliveryWayChange(item: any) {
  state.orderParams.deliveryWay = item.deliveryWay
  toSettlement()
}
async function refreshMallDeliveryAvailability() {
  if (!selectedAddress.value?.id) {
    mallDeliveryAvailability.value = {
      available: false,
      reason: '请选择收货地址',
    }
    return
  }
  try {
    mallDeliveryAvailability.value = await getMallDeliveryAvailability(
      selectedAddress.value.id,
    )
  }
  catch {
    mallDeliveryAvailability.value = {
      available: false,
      reason: '配送范围查询失败，请稍后重试',
    }
  }
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
  state.orderParams.userAddressId = selectedAddress.value
    ? selectedAddress.value.id
    : ''

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
  state.orderParams.userAddressId = selectedAddress.value
    ? selectedAddress.value.id
    : ''
  if (!state.orderParams.deliveryWay) {
    return useGlobalToast().warning('请选择配送方式')
  }

  if (
    ['1', '3'].includes(state.orderParams.deliveryWay)
    && !selectedAddress.value?.id
  ) {
    return useGlobalToast().warning('请选择收货地址')
  }
  if (
    state.orderParams.deliveryWay === '3'
    && !mallDeliveryAvailability.value.available
  ) {
    return useGlobalToast().warning(
      mallDeliveryAvailability.value.reason || '当前地址不支持商城配送',
    )
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
      placeChooseId:
        selectedAddress.value && selectedAddress.value.id
          ? selectedAddress.value.id
          : '',
    },
  })
}
async function handleSelectedAddressUpdate(newAddress: Address) {
  selectedAddress.value = newAddress
  await refreshMallDeliveryAvailability()
  if (
    state.orderParams.deliveryWay === '3'
    && !mallDeliveryAvailability.value.available
  ) {
    state.orderParams.deliveryWay = '1'
    useGlobalToast().warning('新地址不支持商城配送，已切换为普通快递')
  }
  await toSettlement()
}

uni.$on('update:selectedAddress', handleSelectedAddressUpdate)

onUnload(() => {
  uni.$off('update:selectedAddress', handleSelectedAddressUpdate)
})
</script>

<template>
  <hr-navbar title="订单确认" />
  <view v-if="!loading">
    <!-- 收货地址选择 -->
    <AddressSelector
      :selected-address="selectedAddress"
      @to-address="toAddress"
    />

    <!-- 订单列表 -->
    <ShopOrderItem
      :order="state.orderInfo"
      :coupon-user-list="state.couponUserList"
      :delivery-way="state.orderParams.deliveryWay"
      :mall-delivery-availability="mallDeliveryAvailability"
      @delivery-way-change="deliveryWayChange"
      @remark-change="remarkChange"
      @show-coupon="showCoupon"
    />

    <wd-gap :height="60" />
    <!-- 支付底部 -->
    <PaymentFooter
      :payment-price="state.orderInfo.paymentPrice"
      @to-pay="toPay"
    />
    <!-- 优惠券选择 -->
    <CouponSelector
      :coupon-popup="couponState.couponPopup"
      :coupon-user-list="state.couponUserList"
      :order-price="couponState.orderPrice"
      :order-item-list="couponState.orderItemList"
      @close-coupon-popup="closeCouponPopup"
      @coupon-confirm="couponConfirm"
    />
  </view>
</template>
