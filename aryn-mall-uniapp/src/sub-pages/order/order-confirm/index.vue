<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import AddressSelector from './components/AddressSelector.vue'
import ShopOrderItem from './components/ShopOrderItem.vue'
import PaymentFooter from './components/PaymentFooter.vue'
import CouponSelector from './components/CouponSelector.vue'
import { getDefault } from '@/api/user/address'
import { getPage as getCouponList } from '@/api/promotion/couponUser'
import { orderCreate, orderSettlement } from '@/api/order/orderInfo'

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
  state.createWay = options?.createWay
  getDefaultAddress()
})
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
  state.orderParams.createWay = state.createWay
  state.orderParams.userAddressId = selectedAddress.value ? selectedAddress.value.id : ''
  if (!state.orderParams.deliveryWay) {
    return useGlobalToast().warning('请选择配送方式')
  }

  if (state.orderParams.deliveryWay === '1' && !selectedAddress.value) {
    return useGlobalToast().warning('请选择收货地址')
  }

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
// 监听页面返回事件，并接收参数
uni.$on('update:selectedAddress', (newAddress) => {
  selectedAddress.value = newAddress
})

// 在页面卸载时移除事件监听
uni.$once('beforeUnload', () => {
  uni.$off('update:selectedAddress')
})
</script>

<template>
  <hr-navbar title="订单确认" />
  <view v-if="!loading">
    <!-- 收货地址选择 -->
    <AddressSelector :selected-address="selectedAddress" @to-address="toAddress" />

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
