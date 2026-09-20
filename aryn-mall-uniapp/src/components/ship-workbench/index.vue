<script setup lang="ts">
/**
 * 首页船舶工作台
 *
 * 展示当前船舶、下一靠港与配送时间窗。
 *
 * 商品统一（2026-09-20）后不再提供独立的「船供采购」商品目录入口：
 * 商品在一个目录里选购，船供差异体现在下单时的船舶/靠港上下文与内部配送方式。
 *
 * 状态语义（必须严格区分，不可合并）：
 * - guest    未登录
 * - noVessel 已登录但无船舶成员关系 —— 中性引导，不阻断浏览商品与常购
 * - noCall   已绑定船舶但该船暂无在营靠港计划 —— 提示到港后可下单
 * - ready    船舶与靠港齐备，可走公司内部配送
 * - error    船舶服务不可用
 *
 * 历史缺陷：noVessel 与 noCall 曾共用 unbound 一个状态，导致「已绑定船舶
 * 但暂无靠港计划」的用户被提示「尚未绑定船舶，请联系客户管理员配置」——
 * 既是事实错误，也把用户指向一个并不存在的角色。
 */
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'

import { getMyVessels, getVesselCalls } from '@/api/vessel'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()
const visible = ref(false)
const loading = ref(false)
const pickerVisible = ref(false)
const status = ref<'error' | 'guest' | 'noCall' | 'noVessel' | 'ready'>('guest')

/** 船舶名：noCall 文案需要指明是哪条船 */
const vesselName = computed(() => shipContextStore.vesselName || '该船舶')
/** 有在船船舶（ready / noCall）时才允许打开船舶与靠港选择器 */
const canPickContext = computed(() => status.value === 'ready' || status.value === 'noCall')

function openPicker() {
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  if (!canPickContext.value) {
    // 未绑定时直接去绑定页，而不是提示一句无法执行的「需先关联船舶」
    if (status.value === 'noVessel') {
      goBind()
      return
    }
    uni.showToast({ title: '等待船舶信息加载完成', icon: 'none' })
    return
  }
  pickerVisible.value = true
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

async function loadWorkbench() {
  if (!authStore.isLoggedIn) {
    visible.value = true
    status.value = 'guest'
    return
  }
  visible.value = true
  loading.value = true
  try {
    const vessels = await getMyVessels()
    if (!vessels || vessels.length === 0) {
      shipContextStore.reset()
      status.value = 'noVessel'
      return
    }
    const vessel = vessels.find(item => item.id === shipContextStore.vesselId) ?? vessels[0]
    shipContextStore.setVesselContext({ vesselId: vessel.id, vesselName: vessel.vesselName })

    // 用可用靠港列表（而非仅「下一靠港」）以便保留用户的显式选择：
    // 只有当选中的靠港已不可用（过期/已取消/换船被清空）时才回落到最早的可用靠港。
    const calls = await getVesselCalls(vessel.id)
    if (!calls || calls.length === 0) {
      status.value = 'noCall'
      return
    }
    const picked = calls.find(call => call.id === shipContextStore.vesselCallId) ?? calls[0]
    shipContextStore.setVesselCall({
      berth: picked.berth,
      deliveryWindowEnd: picked.deliveryWindowEnd,
      deliveryWindowStart: picked.deliveryWindowStart,
      id: picked.id,
      portCode: picked.portCode,
      portName: picked.portName,
    })
    status.value = 'ready'
  }
  catch {
    status.value = 'error'
  }
  finally {
    loading.value = false
  }
}

/**
 * 未绑定船舶时给出可执行的出路。
 * 此前 noVessel 只是一句说明，用户知道缺什么却无处可去——绑定入口只在管理端后台。
 */
function goBind() {
  uni.navigateTo({ url: '/sub-pages/vessel/bind/index' })
}

/**
 * 进入统一商品目录。
 *
 * 商品统一（2026-09-20）后不再预设 purchaseScene：船供场景由结算时是否选择
 * 「公司港口/船舶内部配送」决定（order-confirm 会据此带上船舶与靠港上下文），
 * 因此这里只做导航，不替用户预设购买场景。
 */
function goPersonal() {
  uni.switchTab({ url: '/pages/product/category/index' })
}

function goFrequent() {
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  uni.navigateTo({ url: '/sub-pages/product/frequent/index' })
}

onShow(loadWorkbench)
</script>

<template>
  <view v-if="visible" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-30rpx">
    <view class="flex items-center justify-between">
      <view class="flex items-center" @tap="openPicker">
        <text class="text-30rpx font-bold">
          {{ shipContextStore.vesselName || '我的船舶' }}
        </text>
        <text v-if="canPickContext" class="ml-10rpx text-24rpx text-blue-500">
          切换 &gt;
        </text>
      </view>
      <view
        v-if="status === 'ready'"
        class="rounded-full bg-green-100 px-16rpx py-4rpx text-22rpx text-green-600"
      >
        已选靠港
      </view>
    </view>
    <view class="mt-10rpx text-24rpx text-gray-500">
      <template v-if="loading">
        正在加载船舶信息...
      </template>
      <template v-else-if="status === 'ready'">
        下一靠港：{{ shipContextStore.portName }}
        {{ shipContextStore.berth }}
        <text v-if="shipContextStore.deliveryWindowStart">
          （{{ shipContextStore.deliveryWindowStart }} 起）
        </text>
      </template>
      <template v-else-if="status === 'guest'">
        登录后可绑定船舶，享受靠港配送
      </template>
      <template v-else-if="status === 'noVessel'">
        <text>暂未关联船舶，可先正常选购；关联船舶后即可使用内部配送到船</text>
        <text class="ml-8rpx text-blue-500" @tap="goBind">
          去绑定 &gt;
        </text>
      </template>
      <template v-else-if="status === 'noCall'">
        「{{ vesselName }}」暂无靠港计划，到港后可下单
      </template>
      <template v-else-if="status === 'error'">
        船舶服务暂时不可用，请稍后重试
      </template>
    </view>
    <!-- 商品统一（2026-09-20）：不再区分「船供采购 / 个人购买」两个商品目录。
         选购统一走商品分类；船供的差异只体现在下单时的船舶/靠港与内部配送。 -->
    <view class="mt-24rpx flex gap-20rpx">
      <button
        class="!m-0 flex-1 rounded-40rpx bg-blue-500 text-26rpx text-white"
        @tap="goPersonal"
      >
        去选购
      </button>
      <button
        class="!m-0 flex-1 rounded-40rpx bg-amber-100 text-26rpx text-amber-700"
        :disabled="loading"
        @tap="goFrequent"
      >
        常购
      </button>
    </view>

    <!-- 船舶与靠港选择器：补齐 switchVessel / getVesselCalls 的用户入口 -->
    <ShipContextPicker v-model="pickerVisible" @change="loadWorkbench" />
  </view>
</template>
