<script setup lang="ts">
import type { PortVehicle } from '@/api/vessel'

/**
 * 港口报备车辆（船员靠港外出打车电话簿）
 *
 * 纯只读：名单由船务公司在管理后台按港口维护，本页只做查询与拨打。
 * 默认带出当前购物靠港的港口（shipContext），无靠港上下文时用港口清单兜底切换。
 */
import { onShow } from '@dcloudio/uni-app'

import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getPortVehiclePorts, getPortVehicles } from '@/api/vessel'
import { useGlobalToast } from '@/composables/useGlobalToast'
import { ensureShipContextLoaded } from '@/composables/useShipContextLoad'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'

definePage({
  name: 'vessel-port-vehicles',
  style: {
    navigationBarTitleText: '港口报备车辆',
  },
})

const { t } = useI18n()
const { show: showToast } = useGlobalToast()
const authStore = useAuthStore()
const shipContextStore = useShipContextStore()

const loading = ref(false)
const listLoading = ref(false)
const ports = ref<string[]>([])
const activePort = ref('')
const vehicles = ref<PortVehicle[]>([])

const hasContextPort = computed(() => !!shipContextStore.portName)

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

/** 拨打司机电话（整卡可点，与配送模块同口径） */
function handleCallPhone(phone?: string) {
  if (!phone) {
    showToast(t('vessel.portVehicles.noPhone'))
    return
  }
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      showToast(t('vessel.portVehicles.callFailed'))
    },
  })
}

async function loadVehicles(port: string) {
  if (!port) {
    vehicles.value = []
    return
  }
  listLoading.value = true
  try {
    vehicles.value = (await getPortVehicles(port)) ?? []
  }
  catch (error: any) {
    vehicles.value = []
    showToast(error?.message || t('vessel.portVehicles.loadFailed'))
  }
  finally {
    listLoading.value = false
  }
}

function switchPort(port: string) {
  if (activePort.value === port)
    return
  activePort.value = port
  loadVehicles(port)
}

async function init() {
  if (!authStore.isLoggedIn)
    return
  loading.value = true
  try {
    // 尽力装载当前购物靠港；失败不影响后面的港口清单兜底
    try {
      await ensureShipContextLoaded()
    }
    catch {
      // 上下文不可用，走港口清单兜底
    }
    const list = (await getPortVehiclePorts()) ?? []
    ports.value = list
    const contextPort = shipContextStore.portName
    // 当前靠港在名单里→直接展示；不在→选第一个有车的港口；清单为空→仍按当前靠港展示空态
    const initial = contextPort && list.includes(contextPort)
      ? contextPort
      : list[0] || contextPort || ''
    activePort.value = initial
    if (initial) {
      await loadVehicles(initial)
    }
    else {
      vehicles.value = []
    }
  }
  finally {
    loading.value = false
  }
}

onMounted(init)

/**
 * 本页是全仓唯一使用原生导航栏的页面（没有 navigationStyle: 'custom' / hr-navbar）：
 * 静态 navigationBarTitleText 在英文下不会变，必须在每次显示时按当前语言重设标题，
 * 这样从设置页切换语言再回到本页也能刷新。
 */
onShow(() => {
  uni.setNavigationBarTitle({ title: t('vessel.portVehicles.navTitle') })
})
</script>

<template>
  <view class="min-h-screen pb-40rpx" style="background: #F5F6F7">
    <!-- 未登录 -->
    <view v-if="!authStore.isLoggedIn" class="flex flex-col items-center px-60rpx pt-180rpx text-center">
      <view
        class="h-168rpx w-168rpx flex items-center justify-center rounded-full"
        style="background: linear-gradient(155deg, #E3F0FB 0%, #F4F9FE 78%); box-shadow: 0 12rpx 32rpx rgba(55, 138, 221, 0.12)"
      >
        <text class="i-carbon-user-avatar text-84rpx" style="color: #378ADD" />
      </view>
      <view class="mt-40rpx text-32rpx font-bold" style="color: #1D2129">
        {{ t('vessel.portVehicles.loginTitle') }}
      </view>
      <view class="mt-14rpx text-26rpx leading-42rpx" style="color: #86909C">
        {{ t('vessel.portVehicles.loginDesc') }}
      </view>
      <button
        class="btn-primary mt-56rpx h-88rpx w-360rpx rounded-full text-28rpx font-medium leading-88rpx !m-0"
        hover-class="btn-press"
        @tap="goLogin"
      >
        {{ t('vessel.portVehicles.goLogin') }}
      </button>
    </view>

    <template v-else>
      <!-- 当前靠港提示 -->
      <view
        v-if="hasContextPort"
        class="mx-20rpx mt-20rpx flex items-center gap-8rpx rounded-16rpx px-24rpx py-16rpx text-24rpx"
        style="background: #EAF3FC; color: #2A79C6"
      >
        <text class="i-carbon-anchor text-26rpx" />
        {{ t('vessel.portVehicles.currentPort', { port: shipContextStore.portName }) }}
      </view>

      <!-- 港口切换（有靠港上下文时默认已选中，无需操作） -->
      <view v-if="ports.length > 1" class="mx-20rpx mt-20rpx">
        <scroll-view scroll-x class="whitespace-nowrap">
          <view class="inline-flex gap-16rpx py-4rpx">
            <view
              v-for="port in ports"
              :key="port"
              class="rounded-full px-28rpx py-12rpx text-26rpx transition-all"
              :style="activePort === port
                ? 'background:#378ADD;color:#fff;box-shadow:0 6rpx 16rpx rgba(55,138,221,0.32)'
                : 'background:#fff;color:#4E5969'"
              @tap="switchPort(port)"
            >
              {{ port }}
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 加载中 -->
      <view v-if="loading || listLoading" class="px-20rpx pt-20rpx">
        <view class="skeleton h-180rpx rounded-24rpx" />
        <view class="skeleton mt-20rpx h-180rpx rounded-24rpx" />
      </view>

      <!-- 车辆列表 -->
      <template v-else-if="vehicles.length > 0">
        <view class="mx-20rpx mt-24rpx text-24rpx" style="color: #86909C">
          {{ t('vessel.portVehicles.summary', { port: activePort, count: vehicles.length }) }}
        </view>
        <view
          v-for="vehicle in vehicles"
          :key="vehicle.id"
          class="vehicle-card mx-20rpx mt-16rpx rounded-24rpx bg-white p-32rpx"
          hover-class="vehicle-card--press"
          @tap="handleCallPhone(vehicle.driverPhone)"
        >
          <view class="flex items-center justify-between gap-16rpx">
            <view class="min-w-0">
              <view class="flex items-center gap-12rpx">
                <text class="i-carbon-car text-30rpx" style="color: #378ADD" />
                <text class="text-34rpx font-bold tracking-wide" style="color: #1D2129">
                  {{ vehicle.plateNo }}
                </text>
              </view>
              <view class="mt-10rpx text-26rpx" style="color: #4E5969">
                {{ vehicle.driverName || t('vessel.portVehicles.driver') }}
                <text v-if="vehicle.seatCount">
                  {{ t('vessel.portVehicles.seats', { count: vehicle.seatCount }) }}
                </text>
                <text v-if="vehicle.vehicleDesc">
                  · {{ vehicle.vehicleDesc }}
                </text>
              </view>
            </view>
            <view
              class="h-72rpx flex flex-none items-center gap-8rpx rounded-full px-32rpx text-26rpx text-white font-medium"
              style="background: #378ADD; box-shadow: 0 6rpx 16rpx rgba(55, 138, 221, 0.28)"
              hover-class="btn-press"
              @tap.stop="handleCallPhone(vehicle.driverPhone)"
            >
              <text class="i-carbon-phone text-28rpx" />
              {{ t('vessel.portVehicles.call') }}
            </view>
          </view>
          <view
            v-if="vehicle.pickupPoint"
            class="mt-16rpx flex items-start gap-8rpx text-24rpx"
            style="color: #C87D2A"
          >
            <text class="i-carbon-location mt-2rpx flex-none text-24rpx" />
            {{ t('vessel.portVehicles.pickupPoint', { point: vehicle.pickupPoint }) }}
          </view>
          <view v-if="vehicle.remark" class="mt-8rpx text-24rpx" style="color: #86909C">
            {{ vehicle.remark }}
          </view>
        </view>
      </template>

      <!-- 空态 -->
      <view v-else class="flex flex-col items-center px-60rpx pt-180rpx text-center">
        <view
          class="h-168rpx w-168rpx flex items-center justify-center rounded-full"
          style="background: linear-gradient(155deg, #EEF3F8 0%, #F7F9FB 78%); box-shadow: 0 12rpx 32rpx rgba(29, 33, 41, 0.06)"
        >
          <text class="i-carbon-taxi text-84rpx" style="color: #A8ABB2" />
        </view>
        <view class="mt-40rpx text-30rpx font-bold" style="color: #1D2129">
          {{ activePort ? t('vessel.portVehicles.emptyWithPort', { port: activePort }) : t('vessel.portVehicles.empty') }}
        </view>
        <view class="mt-14rpx text-26rpx leading-42rpx" style="color: #86909C">
          {{ t('vessel.portVehicles.emptyDesc') }}
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
/* 品牌蓝主按钮：去掉微信原生按钮默认边框与灰底 */
.btn-primary {
  color: #fff;
  background: #378ADD;
  box-shadow: 0 10rpx 24rpx rgba(55, 138, 221, 0.35);
}

.btn-primary::after {
  border: none;
}

.btn-press {
  transform: scale(0.96);
  opacity: 0.88;
}

.vehicle-card {
  box-shadow: 0 4rpx 16rpx rgba(29, 33, 41, 0.04);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.vehicle-card--press {
  transform: scale(0.985);
}

/* 列表加载骨架：微光扫过 */
.skeleton {
  background: linear-gradient(90deg, #EFF1F4 25%, #F7F8FA 37%, #EFF1F4 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

@keyframes skeleton-loading {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}
</style>
