<script setup lang="ts">
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'

import { alovaInstance } from '@/api/core/instance'
import { addSharedCartItem, getSharedCart, type SharedCart } from '@/api/order/sharedCart'
import { addShoppingCart } from '@/api/order/shoppingCart'
import { useShipContextStore } from '@/store/shipContextStore'
import hrNavbar from '@/components/hr-navbar/index.vue'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'

definePage({
  name: 'ship-supply',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '船供采购',
  },
})

const shipContextStore = useShipContextStore()
const loading = ref(false)
const loadingMore = ref(false)
const loadFailed = ref(false)
const keyword = ref('')
const state = reactive({
  records: [] as any[],
  total: 0,
  current: 1,
  size: 20,
})

const hasMore = computed(() => state.records.length < state.total)

/**
 * 共享购物车选货模式：携带 sharedCartId 进入时，加购写入共享购物车而非个人购物车。
 * 共享购物车自身已绑定船舶与靠港计划，因此展示它的上下文而非当前上下文，
 * 避免用户在错误的船/靠港下选货。
 */
const sharedCartId = ref('')
const sharedCart = ref<SharedCart | null>(null)
const isSharedMode = computed(() => !!sharedCartId.value)
/** 船舶与靠港选择器可见性 */
const shipPickerVisible = ref(false)

/**
 * 船供目录分页加载。
 *
 * 搜索统一走 keyword 参数：服务端在「IMPA/ISSA/条码/内部编码」与
 * 「中英文品名/搜索别名」两组之间取 OR。
 * 历史缺陷：前端把同一关键词同时塞进 impaCode 与 nameEn，
 * 后端两个 <if> 是 AND 关系，导致按 IMPA 码或按中文品名单独搜索均返回 0 条。
 */
function fetchPage(reset = false) {
  if (reset) {
    state.current = 1
    state.records = []
    loadFailed.value = false
  }
  const target = reset ? loading : loadingMore
  target.value = true
  return alovaInstance
    .Get<any>('/product/app/goodsspu/ship/page', {
      params: {
        current: state.current,
        size: state.size,
        keyword: keyword.value || undefined,
      },
    })
    .then((res) => {
      const records = res.records ?? []
      state.records = reset ? records : [...state.records, ...records]
      state.total = res.total ?? 0
      loadFailed.value = false
    })
    .catch(() => {
      loadFailed.value = true
    })
    .finally(() => {
      target.value = false
    })
}

function handleSearch() {
  fetchPage(true)
}

/** 上拉加载下一页；失败时保留已加载内容，用户可再次触发 */
function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  state.current += 1
  fetchPage()
}

/** 进入共享购物车列表 */
function goSharedCartList() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/list' })
}

/** 打开船舶与靠港选择器（此前提示「请在首页选择」但首页没有入口） */
function openShipPicker() {
  shipPickerVisible.value = true
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
  const request = isSharedMode.value
    ? addSharedCartItem(sharedCartId.value, {
        spuId: item.spuId,
        skuId: item.skuId,
        requestedQuantity: quantity,
      })
    : addShoppingCart({
        skuId: item.skuId,
        quantity,
        addType: '2',
      })
  request
    .then(() => {
      item._qty = ''
      uni.showToast({
        title: isSharedMode.value ? '已加入共享购物车' : '已加入购物车',
        icon: 'success',
      })
    })
    .catch(() => {})
}

onLoad((options) => {
  sharedCartId.value = options?.sharedCartId ?? ''
  if (sharedCartId.value) {
    getSharedCart(sharedCartId.value)
      .then((res) => {
        sharedCart.value = res ?? null
      })
      .catch(() => {})
  }
  fetchPage(true)
})

onReachBottom(loadMore)
</script>

<template>
  <view>
    <hr-navbar :title="isSharedMode ? '共享购物车选货' : '船供采购'" />

    <!-- 共享购物车选货模式：加购写入共享购物车，船舶/靠港取自购物车本身 -->
    <view
      v-if="isSharedMode"
      class="mx-20rpx mt-20rpx rounded-20rpx p-24rpx"
      style="background:#E6F1FB"
    >
      <view class="text-26rpx" style="color:#185FA5">
        正在为共享购物车选货，加购后由采购确认人统一提交
      </view>
      <view class="mt-6rpx text-24rpx" style="color:#185FA5">
        {{ sharedCart?.vesselName || '船舶信息加载中' }}
        {{ sharedCart?.portName }}
        {{ sharedCart?.berth }}
      </view>
    </view>

    <!-- 当前船舶上下文 -->
    <view v-if="!isSharedMode" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
      <view class="flex items-center justify-between">
        <view class="text-28rpx font-bold">
          {{ shipContextStore.vesselName || '未选择船舶' }}
        </view>
        <text class="text-24rpx text-blue-500" @tap="goSharedCartList">
          共享购物车 &gt;
        </text>
      </view>
      <view class="mt-6rpx text-24rpx text-gray-500">
        <template v-if="shipContextStore.hasVesselContext">
          {{ shipContextStore.portName }}
          {{ shipContextStore.berth }}
        </template>
        <text v-else class="text-blue-500" @tap="openShipPicker">
          选择船舶和靠港计划 &gt;
        </text>
      </view>
    </view>

    <!-- 船舶与靠港选择器 -->
    <ShipContextPicker v-model="shipPickerVisible" />

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
          {{ isSharedMode ? '加入共享车' : '加入购物车' }}
        </button>
      </view>
    </view>

    <view
      v-if="loading && state.records.length === 0"
      class="py-80rpx text-center text-26rpx text-gray-400"
    >
      加载中...
    </view>

    <!-- 加载失败与无数据必须区分，避免把请求失败误报为「暂无商品」 -->
    <view v-else-if="loadFailed && state.records.length === 0" class="py-80rpx text-center">
      <view class="text-26rpx text-gray-400">
        加载失败，请检查网络后重试
      </view>
      <view class="mt-20rpx text-26rpx text-blue-500" @tap="fetchPage(true)">
        重新加载
      </view>
    </view>

    <view
      v-else-if="!loading && state.records.length === 0"
      class="py-80rpx text-center text-26rpx text-gray-400"
    >
      暂无船供商品
    </view>

    <!-- 分页状态：船供目录单页 20 条，必须能继续加载 -->
    <view v-else class="py-30rpx text-center text-24rpx text-gray-400">
      <text v-if="loadingMore">加载中...</text>
      <text v-else-if="hasMore">上拉加载更多</text>
      <text v-else>已显示全部 {{ state.total }} 条</text>
    </view>
  </view>
</template>
