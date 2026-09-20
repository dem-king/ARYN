<script setup lang="ts">
/**
 * 共享购物车列表：我发起或我被邀请的全部购物车。
 *
 * 创建入口仅在当前已绑定船舶+靠港计划时可用——共享购物车创建时即绑定
 * 船舶与靠港计划且不可变更，缺少上下文无法创建。
 */
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'

import { createSharedCart, getMySharedCarts, type SharedCart, type SharedCartCreatePayload } from '@/api/order/sharedCart'
import hrNavbar from '@/components/hr-navbar/index.vue'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { cartStatusLabel, cartStatusTheme, isCartCollecting } from '@/utils/shared-cart'

definePage({
  name: 'shared-cart-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '共享购物车',
  },
})

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()

const loading = ref(false)
const loadFailed = ref(false)
const records = ref<SharedCart[]>([])
/** 船舶与靠港选择器：创建共享购物车必须先有船舶+靠港上下文 */
const shipPickerVisible = ref(false)

/** 创建表单 */
const createState = reactive({
  visible: false,
  submitting: false,
  /** 收集窗口（小时）：24 / 48 / 0 表示不限期 */
  remark: '',
})

const canCreate = computed(() => authStore.isLoggedIn && shipContextStore.hasVesselContext)

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function fetchList() {
  if (!authStore.isLoggedIn) {
    records.value = []
    loadFailed.value = false
    return Promise.resolve()
  }
  loading.value = true
  return getMySharedCarts()
    .then((res) => {
      records.value = res ?? []
      loadFailed.value = false
    })
    .catch(() => {
      loadFailed.value = true
    })
    .finally(() => {
      loading.value = false
      uni.stopPullDownRefresh()
    })
}

function openCreate() {
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  // 缺少船舶/靠港上下文时直接给出选择入口，而不是一句无法执行的提示
  if (!canCreate.value) {
    shipPickerVisible.value = true
    return
  }
  createState.remark = ''
  createState.visible = true
}

function submitCreate() {
  if (createState.submitting) return
  createState.submitting = true
  const payload: SharedCartCreatePayload = {
    vesselId: shipContextStore.vesselId,
    vesselCallId: shipContextStore.vesselCallId,
    remark: createState.remark || undefined,
  }
  createSharedCart(payload)
    .then((cart) => {
      createState.visible = false
      // 同一船舶已有进行中的采购时，服务端复用该购物车而非新建，
      // 需明确告知用户是被并入，避免误以为刚创建了一个新的。
      uni.showToast({
        title: cart?.adoptedExisting ? '该船已在进行中，已为你打开' : '已创建',
        icon: 'none',
      })
      if (cart?.id) {
        uni.navigateTo({ url: `/sub-pages/order/shared-cart/detail?id=${cart.id}` })
      }
      else {
        void fetchList()
      }
    })
    .catch(() => {})
    .finally(() => {
      createState.submitting = false
    })
}

function goDetail(cart: SharedCart) {
  uni.navigateTo({ url: `/sub-pages/order/shared-cart/detail?id=${cart.id}` })
}

onShow(() => {
  void fetchList()
})

onPullDownRefresh(() => {
  void fetchList()
})
</script>

<template>
  <view>
    <hr-navbar title="共享购物车" />

    <!-- 当前船舶上下文 + 创建入口 -->
    <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
      <view class="text-28rpx font-bold">
        {{ shipContextStore.vesselName || '我的船舶' }}
      </view>
      <view class="mt-6rpx text-24rpx text-gray-500">
        <template v-if="shipContextStore.hasVesselContext">
          {{ shipContextStore.portName }} {{ shipContextStore.berth }}
        </template>
        <text v-else class="text-blue-500" @tap="shipPickerVisible = true">
          选择船舶和靠港计划 &gt;
        </text>
      </view>
      <button
        class="!m-0 mt-20rpx h-68rpx text-26rpx leading-68rpx"
        type="primary"
        :disabled="!canCreate"
        @tap="openCreate"
      >
        发起共享购物车
      </button>
    </view>

    <!-- 创建表单 -->
    <view v-if="createState.visible" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
      <view class="text-28rpx font-bold">
        收集设置
      </view>
      <view class="mt-16rpx text-24rpx text-gray-500">
        收集截止：创建后 24 小时内有效，超时自动关闭
      </view>
      <input
        v-model="createState.remark"
        class="mt-20rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
        placeholder="备注（选填），如：本次补给含甲板部需求"
        :maxlength="120"
      >
      <view class="mt-20rpx flex gap-20rpx">
        <button class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx" @tap="createState.visible = false">
          取消
        </button>
        <button
          class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx"
          type="primary"
          :disabled="createState.submitting"
          @tap="submitCreate"
        >
          创建
        </button>
      </view>
    </view>

    <!-- 列表 -->
    <view
      v-for="cart in records"
      :key="cart.id"
      class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx"
      @tap="goDetail(cart)"
    >
      <view class="flex items-center justify-between">
        <view class="text-28rpx font-bold">
          {{ cart.vesselName || '船舶信息加载中' }}
        </view>
        <view
          class="rounded-full px-16rpx py-4rpx text-22rpx"
          :style="`background:${cartStatusTheme(cart.status).bg};color:${cartStatusTheme(cart.status).text}`"
        >
          {{ cartStatusLabel(cart.status) }}
        </view>
      </view>
      <view class="mt-8rpx text-24rpx text-gray-500">
        {{ cart.portName || '港口待定' }} {{ cart.berth }}
        <text v-if="cart.deliveryWindowStart"> · 配送时间窗 {{ cart.deliveryWindowStart }} 起</text>
      </view>
      <view class="mt-8rpx text-24rpx text-gray-500">
        编号 {{ cart.cartNo }}
      </view>
      <view class="mt-12rpx flex items-center justify-between">
        <view class="text-22rpx text-gray-400">
          {{ cart.itemCount ?? 0 }} 项商品 · {{ cart.memberCount ?? 0 }} 名成员
          <text v-if="isCartCollecting(cart.status) && cart.expiresAt">
            · {{ cart.expiresAt }} 截止
          </text>
        </view>
        <view class="text-24rpx text-blue-500">
          查看
        </view>
      </view>
    </view>

    <!-- 状态区分：加载失败与无数据不可混用同一文案 -->
    <view v-if="loading && records.length === 0" class="py-80rpx text-center text-26rpx text-gray-400">
      加载中...
    </view>
    <view v-else-if="loadFailed && records.length === 0" class="py-80rpx text-center">
      <view class="text-26rpx text-gray-400">
        加载失败，请检查网络后重试
      </view>
      <view class="mt-20rpx text-26rpx text-blue-500" @tap="fetchList">
        重新加载
      </view>
    </view>
    <view
      v-else-if="!authStore.isLoggedIn"
      class="py-80rpx text-center text-26rpx text-gray-400"
    >
      登录后可查看共享购物车
    </view>
    <view v-else-if="records.length === 0" class="py-80rpx text-center text-26rpx text-gray-400">
      暂无共享购物车，可发起一个与同船成员合并采购
    </view>

    <!-- 船舶与靠港选择器：创建共享购物车前必须先选定上下文 -->
    <ShipContextPicker v-model="shipPickerVisible" />
  </view>
</template>
