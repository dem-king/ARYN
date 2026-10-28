<script setup lang="ts">
import type { SharedCartCreatePayload, SharedCartSummary } from '@/api/order/sharedCart'

/**
 * 导入清单下单（向导入口）。
 *
 * 场景：管事/大副线下已统计好整船采购清单（Excel），只想「传文件 → 确认 → 下单」，
 * 不关心共享购物车这个中间概念。但 Excel 里只有行数据，单据头（船舶/靠港/配送窗口）
 * 必须落在购物车上，且导入接口挂在具体购物车下 —— 所以向导做的是把
 * 「创建/复用购物车 → 进入导入」两步串成一步直达，而不是绕道列表页和详情页。
 *
 * 流程：
 * 1. 有进行中的购物车 → 直接跳导入页（复用，不让用户感知购物车存在）；
 * 2. 没有 → 本页内联一张轻量创建表单（船舶上下文 + 备注），创建成功后直接跳导入页；
 * 3. 创建接口自带同船期复用（adoptedExisting），并发场景不会造出第二张单。
 */
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { createSharedCart, getActiveSharedCartSummary } from '@/api/order/sharedCart'

import hrNavbar from '@/components/hr-navbar/index.vue'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'

definePage({
  name: 'shared-cart-import-entry',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '导入清单下单',
  },
})

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()
const tenantCapabilityStore = useTenantCapabilityStore()
const { t } = useI18n()

const resolving = ref(true)
/** 纯零售租户：船供能力关闭，向导整体不可用 */
const unsupported = ref(false)
const summary = ref<SharedCartSummary | null>(null)

/** 创建表单（与列表页同口径：收集截止由服务端按创建时刻 +24h 决定） */
const createState = ref({
  submitting: false,
  remark: '',
})
const shipPickerVisible = ref(false)

const canCreate = computed(() => authStore.isLoggedIn && shipContextStore.hasVesselContext)

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function goCartList() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/list' })
}

function goImport(cartId: string) {
  // redirectTo：导入页接管返回栈，返回键直接回首页，不在向导空壳上停留
  uni.redirectTo({ url: `/sub-pages/order/shared-cart/import?cartId=${cartId}` })
}

async function resolveTargetCart() {
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  resolving.value = true
  await tenantCapabilityStore.ensureLoaded()
  if (!tenantCapabilityStore.shipSupplyEnabled) {
    unsupported.value = true
    resolving.value = false
    return
  }
  try {
    summary.value = await getActiveSharedCartSummary(shipContextStore.vesselCallId || undefined)
  }
  catch {
    // 摘要失败不阻塞向导：创建接口兜底同船期复用，重复单在服务端被挡住
    summary.value = null
  }
  // 已有进行中的补给单：直接复用进导入，用户无需感知"购物车"这一层
  if (summary.value?.cart?.id) {
    uni.showToast({ title: t('sharedCart.importEntry.toastExisting'), icon: 'none' })
    goImport(summary.value.cart.id)
    return
  }
  resolving.value = false
}

function submitCreate() {
  if (createState.value.submitting)
    return
  // 上下文缺失时先补齐船舶/靠港，而不是一句无法执行的提示；
  // 未绑船的岸上客户没有船可选，引导到列表页按收货地址发起（创建后再回详情导入）
  if (!canCreate.value) {
    if (!shipContextStore.vesselId) {
      uni.showToast({ title: t('sharedCart.importEntry.toastNeedAddress'), icon: 'none' })
      goCartList()
      return
    }
    shipPickerVisible.value = true
    return
  }
  createState.value.submitting = true
  const payload: SharedCartCreatePayload = {
    vesselId: shipContextStore.vesselId,
    vesselCallId: shipContextStore.vesselCallId,
    remark: createState.value.remark || undefined,
  }
  createSharedCart(payload)
    .then((cart) => {
      // 同一船舶已有进行中的采购时，服务端复用该购物车而非新建（与列表页同口径提示）
      uni.showToast({
        title: cart?.adoptedExisting
          ? t('sharedCart.importEntry.toastAdopted')
          : t('sharedCart.importEntry.toastCreated'),
        icon: 'none',
      })
      if (cart?.id) {
        goImport(cart.id)
      }
    })
    .catch(() => {})
    .finally(() => {
      createState.value.submitting = false
    })
}

onShow(() => {
  void resolveTargetCart()
})
</script>

<template>
  <view>
    <hr-navbar :title="t('sharedCart.importEntry.navTitle')" />

    <view v-if="resolving" class="py-80rpx text-center text-26rpx text-gray-400">
      {{ t('common.loading') }}
    </view>

    <template v-else-if="unsupported">
      <view class="py-80rpx text-center">
        <view class="text-26rpx text-gray-400">
          {{ t('sharedCart.importEntry.unsupported') }}
        </view>
      </view>
    </template>

    <!-- 无进行中补给单：内联创建，成功后直达导入 -->
    <template v-else>
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          {{ t('sharedCart.importEntry.sectionTitle') }}
        </view>
        <view class="mt-16rpx text-24rpx text-gray-500">
          {{ t('sharedCart.importEntry.sectionDesc') }}
        </view>
        <view class="mt-20rpx rounded-12rpx bg-gray-50 px-24rpx py-20rpx">
          <view class="text-26rpx font-bold">
            {{ shipContextStore.vesselName || t('sharedCart.importEntry.myVessel') }}
          </view>
          <view class="mt-6rpx text-24rpx text-gray-500">
            <template v-if="shipContextStore.hasVesselContext">
              {{ shipContextStore.portName }} {{ shipContextStore.berth }}
            </template>
            <text v-else class="text-blue-500" @tap="shipPickerVisible = true">
              {{ t('sharedCart.importEntry.chooseVesselCall') }} &gt;
            </text>
          </view>
        </view>
        <input
          v-model="createState.remark"
          class="mt-20rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          :placeholder="t('sharedCart.importEntry.remarkPlaceholder')"
          :maxlength="120"
        >
        <button
          class="mt-20rpx h-68rpx text-26rpx leading-68rpx !m-0"
          type="primary"
          :disabled="createState.submitting"
          @tap="submitCreate"
        >
          {{ t('sharedCart.importEntry.createAndImport') }}
        </button>
        <view class="mt-20rpx text-center text-22rpx text-gray-400">
          {{ t('sharedCart.importEntry.validityHint') }}
          <text class="text-blue-500" @tap="goCartList">
            {{ t('sharedCart.importEntry.viewAllCarts') }}
          </text>
        </view>
      </view>
    </template>

    <ShipContextPicker v-model="shipPickerVisible" />
  </view>
</template>
