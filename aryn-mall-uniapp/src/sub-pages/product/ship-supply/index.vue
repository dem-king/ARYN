<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'

import { alovaInstance } from '@/api/core/instance'
import { addShoppingCart } from '@/api/order/shoppingCart'
import { useShipContextStore } from '@/store/shipContextStore'
import hrNavbar from '@/components/hr-navbar/index.vue'

definePage({
  name: 'ship-supply',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '船供采购',
  },
})

const shipContextStore = useShipContextStore()
const loading = ref(false)
const keyword = ref('')
const state = reactive({
  records: [] as any[],
  total: 0,
  current: 1,
  size: 20,
})

/**
 * 船供目录搜索：支持 IMPA/ISSA/条码/内部编码/中英文（服务端模糊匹配）
 */
function fetchPage() {
  loading.value = true
  alovaInstance
    .Get<any>('/product/app/goodsspu/ship/page', {
      params: {
        current: state.current,
        size: state.size,
        impaCode: keyword.value || undefined,
        nameEn: keyword.value || undefined,
      },
    })
    .then((res) => {
      state.records = res.records ?? []
      state.total = res.total ?? 0
    })
    .finally(() => {
      loading.value = false
    })
}

function handleSearch() {
  state.current = 1
  fetchPage()
}

/**
 * 直接加购：数量按采购单位填写，MOQ/步长由服务端结算校验
 */
function handleAddToCart(item: any, quantity: number | undefined) {
  if (!quantity || quantity < 1) {
    uni.showToast({ title: '请填写采购数量', icon: 'none' })
    return
  }
  // 数量规则就地提示（服务端结算仍会重新校验）
  const moq = Number(item.moq) || 0
  const stepQty = Number(item.stepQty) || 0
  if (moq && quantity < moq) {
    uni.showToast({ title: `未达最小起订量 ${moq}`, icon: 'none' })
    return
  }
  if (stepQty && quantity % stepQty !== 0) {
    uni.showToast({ title: `数量需为 ${stepQty} 的整数倍`, icon: 'none' })
    return
  }
  addShoppingCart({
    skuId: item.skuId,
    quantity,
    addType: '2',
  })
    .then(() => {
      uni.showToast({ title: '已加入购物车', icon: 'success' })
    })
    .catch(() => {})
}

onLoad(() => {
  fetchPage()
})
</script>

<template>
  <view>
    <hr-navbar title="船供采购" />
    <!-- 当前船舶上下文 -->
    <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
      <view class="text-28rpx font-bold">
        {{ shipContextStore.vesselName || '未选择船舶' }}
      </view>
      <view class="mt-6rpx text-24rpx text-gray-500">
        {{ shipContextStore.portName || '请在首页选择船舶和靠港计划' }}
        {{ shipContextStore.berth }}
      </view>
    </view>

    <!-- 搜索：IMPA/ISSA/中英文/条码/内部编码 -->
    <view class="mx-20rpx mt-20rpx flex items-center">
      <input
        v-model="keyword"
        class="h-72rpx flex-1 rounded-40rpx bg-white px-30rpx text-26rpx"
        placeholder="搜索 IMPA / ISSA / 条码 / 中英文名"
        confirm-type="search"
        @confirm="handleSearch"
      >
      <view
        class="ml-16rpx text-28rpx text-blue-500"
        @tap="handleSearch"
      >
        搜索
      </view>
    </view>

    <!-- 船供商品列表：直填数量批量下单 -->
    <view
      v-for="item in state.records"
      :key="`${item.spuId}-${item.skuId}`"
      class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx"
    >
      <view class="text-28rpx font-bold">
        {{ item.name }}
      </view>
      <view v-if="item.nameEn" class="text-24rpx text-gray-500">
        {{ item.nameEn }}
      </view>
      <view class="mt-10rpx flex flex-wrap text-22rpx text-gray-500">
        <text v-if="item.impaCode" class="mr-20rpx">IMPA: {{ item.impaCode }}</text>
        <text v-if="item.internalItemCode" class="mr-20rpx">编码: {{ item.internalItemCode }}</text>
        <text v-if="item.packageSpec">箱规: {{ item.packageSpec }}</text>
      </view>
      <view class="mt-10rpx flex items-center justify-between">
        <view class="text-22rpx text-gray-500">
          <text v-if="item.purchaseUnit" class="mr-16rpx">单位: {{ item.purchaseUnit }}</text>
          <text v-if="item.moq" class="mr-16rpx">起订: {{ item.moq }}</text>
          <text v-if="item.stepQty">步长: {{ item.stepQty }}</text>
        </view>
        <view class="text-28rpx text-red-500">
          ￥{{ item.salesPrice ?? '-' }}
        </view>
      </view>
      <view class="mt-16rpx flex items-center justify-end">
        <input
          class="mr-16rpx h-60rpx w-140rpx rounded-8rpx border border-gray-200 text-center text-26rpx"
          type="number"
          :placeholder="String(item.moq ?? 1)"
          @input="(e: any) => (item._qty = e.detail.value)"
        >
        <button
          class="!m-0 h-60rpx !px-24rpx text-26rpx leading-60rpx"
          type="primary"
          size="mini"
          @tap="handleAddToCart(item, Number(item._qty))"
        >
          加入购物车
        </button>
      </view>
    </view>

    <view v-if="!loading && state.records.length === 0" class="py-80rpx text-center text-26rpx text-gray-400">
      暂无船供商品
    </view>
  </view>
</template>
