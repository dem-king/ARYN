<script setup lang="ts">
/**
 * 船舶工作台（装修组件形态）
 *
 * 背景（2026-09-21）：该模块原先硬编码在首页 `diy-page` 的 `below-navbar` 插槽里，
 * 运营既不能调整位置也不能隐藏。改为装修组件后，可自由排序与删除，
 * 服务端 `PageDesignComponentTypes` 同步登记，并对「同页重复」发布阻断。
 *
 * 可见性由组件自身按关系分层判断（与租户 business_mode 能力一致）：
 *
 * | 状态                        | 表现           |
 * |----------------------------|----------------|
 * | 有船有靠港（ready）          | 状态条 + 常购   |
 * | 有船无靠港（noCall）         | 状态条（可申报） |
 * | 已登录无船 / 未登录           | 不渲染         |
 * | 纯零售租户 business_mode=2   | 不渲染         |
 * | 船舶服务异常                 | 不渲染         |
 *
 * 未绑定用户的绑定入口在「我的」页，不在首页堆引导文案。
 */
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'

import { getMyVessels, getVesselCalls } from '@/api/vessel'
import type { ShipWorkbenchProps } from '@/components/diy/retail-types'
import { retailCommonStyle } from '@/components/diy/retail-types'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { formatCallTime } from '@/utils/vessel-call-time'

const props = withDefaults(defineProps<{ showData?: Partial<ShipWorkbenchProps> }>(), {
  showData: () => ({}),
})

/**
 * 与其他零售组件保持一致：运营在编辑器配置的「通用样式」必须真正生效，
 * 否则后台的设置项就是在骗运营。emptyStrategy / invalidStrategy 同样透传。
 */
const showData = computed<ShipWorkbenchProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  count: Number(props.showData.count) || 1,
  dataSource: props.showData.dataSource || { mode: 'current-tenant' },
  emptyStrategy: props.showData.emptyStrategy || 'hide',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
  showFrequent: props.showData.showFrequent !== false,
}))

const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()
const tenantCapabilityStore = useTenantCapabilityStore()

const pickerVisible = ref(false)
const loading = ref(false)
const hasVessel = ref(false)
const status = ref<'error' | 'noCall' | 'noVessel' | 'ready'>('noVessel')

/** 运营可关闭「常购」入口，避免在不需要复购的租户里制造干扰 */
const showFrequent = computed(() => showData.value.showFrequent)

const visible = computed(() => {
  if (!tenantCapabilityStore.resolved)
    return false
  if (!tenantCapabilityStore.shipSupplyEnabled)
    return false
  if (!authStore.isLoggedIn)
    return false
  return hasVessel.value && status.value !== 'error'
})

const summary = computed(() => {
  if (loading.value)
    return '正在加载…'
  if (status.value === 'noCall')
    return '暂无靠港计划，点击申报'

  const parts: string[] = []
  if (shipContextStore.portName) {
    parts.push(`${shipContextStore.portName}${shipContextStore.berth ? ` ${shipContextStore.berth}` : ''}`)
  }
  const arrival = formatCallTime(shipContextStore.deliveryWindowStart)
  if (arrival)
    parts.push(arrival)
  return parts.length > 0 ? parts.join(' · ') : '靠港信息待完善'
})

async function loadWorkbench() {
  // 未登录用户永远不会看到状态条，无需为租户能力发起请求
  if (!authStore.isLoggedIn) {
    hasVessel.value = false
    return
  }

  // 再确认租户是否具备船供能力：纯零售租户直接不显示，也不发起船舶请求
  await tenantCapabilityStore.ensureLoaded()
  if (!tenantCapabilityStore.shipSupplyEnabled) {
    hasVessel.value = false
    return
  }

  loading.value = true
  try {
    const vessels = await getMyVessels()
    if (!vessels || vessels.length === 0) {
      shipContextStore.reset()
      hasVessel.value = false
      status.value = 'noVessel'
      return
    }

    const vessel = vessels.find(item => item.id === shipContextStore.vesselId) ?? vessels[0]
    shipContextStore.setVesselContext({ vesselId: vessel.id, vesselName: vessel.vesselName })
    hasVessel.value = true

    // 用可用靠港列表（而非仅「下一靠港」）以便保留用户的显式选择：
    // 只有当选中的靠港已不可用（过期/已取消/换船被清空）时才回落到最早的可用靠港。
    const calls = await getVesselCalls(vessel.id)
    if (!calls || calls.length === 0) {
      shipContextStore.clearVesselCall()
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
    // 首页第一屏不出现告警文案：服务不可用时整条状态条隐藏，
    // 用户仍可在购物车/结算等页面通过各自的选择器接入船舶上下文。
    status.value = 'error'
    hasVessel.value = false
  }
  finally {
    loading.value = false
  }
}

function openPicker() {
  pickerVisible.value = true
}

function goFrequent() {
  uni.navigateTo({ url: '/sub-pages/product/frequent/index' })
}

onShow(loadWorkbench)
</script>

<template>
  <view
    v-if="visible"
    class="ship-workbench"
    :style="dynamicStyles"
  >
    <!-- 左侧为主热区：查看/切换船舶与靠港计划 -->
    <view class="min-w-0 flex flex-1 items-center" @tap="openPicker">
      <text class="i-carbon:sailboat-coastal mr-12rpx flex-none text-32rpx text-primary" />
      <text class="flex-none text-26rpx font-bold">
        {{ shipContextStore.vesselName }}
      </text>
      <text class="mx-10rpx flex-none text-gray-300">
        ·
      </text>
      <text class="truncate text-24rpx text-gray-500">
        {{ summary }}
      </text>
      <text class="i-carbon:chevron-right ml-6rpx flex-none text-24rpx text-gray-400" />
    </view>

    <!-- 常购为船舶场景专属动作，靠港齐备时才提供 -->
    <view
      v-if="showFrequent && status === 'ready'"
      class="ml-16rpx flex-none border-0 border-l border-gray-200 border-solid pl-20rpx text-24rpx text-amber-700"
      @tap.stop="goFrequent"
    >
      常购
    </view>

    <ShipContextPicker v-model="pickerVisible" @change="loadWorkbench" />
  </view>
</template>

<style scoped>
.ship-workbench {
  display: flex;
  align-items: center;
  height: 88rpx;
  background: #fff;
}
</style>
