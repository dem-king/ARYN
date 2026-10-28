<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getByOrderId } from '@/sub-pages/api/order/delivery'
import { useDict } from '@/sub-pages/utils/dict'

definePage({
  name: 'order-logistics',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '订单物流',
  },
})
const orderId = ref()
const deliveryInfo = ref<any>({})
const { order_delivery_status } = useDict('order_delivery_status')
const { t } = useI18n()
const globalLoading = useGlobalLoading()

// 字典标签由后端下发中文，页面按状态码映射到当前语言；未知状态保留后端原文
const DELIVERY_STATUS_LABEL_KEYS: Record<string, string> = {
  0: 'order.deliveryStatus.inTransit',
  1: 'order.deliveryStatus.collected',
  2: 'order.deliveryStatus.exception',
  3: 'order.deliveryStatus.signed',
  4: 'order.deliveryStatus.returnRefused',
  5: 'order.deliveryStatus.delivering',
  6: 'order.deliveryStatus.returning',
  7: 'order.deliveryStatus.redirected',
  8: 'order.deliveryStatus.customs',
}
const deliveryStatusOptions = computed(() => (order_delivery_status.value || []).map((item: any) => {
  const key = DELIVERY_STATUS_LABEL_KEYS[item.value]
  return key ? { ...item, label: t(key) } : item
}))
onLoad((options) => {
  orderId.value = options?.id
  getLogistics()
})

async function getLogistics() {
  try {
    globalLoading.loading(t('common.loading'))
    deliveryInfo.value = await getByOrderId(orderId.value)
  }
  finally {
    globalLoading.close()
  }
}

function copy(logisticsNo: string) {
  uni.setClipboardData({
    data: logisticsNo,
  })
}
</script>

<template>
  <hr-navbar :title="t('order.logistics.title')" />
  <view class="p-2">
    <!-- 单个包裹显示 -->
    <template v-if="deliveryInfo">
      <view class="rounded-xl bg-white p-4">
        <view class="item flex items-center justify-between pb-3">
          <wd-text size="28rpx" color="inherit" :text="t('order.logistics.company')" />
          <wd-text size="28rpx" color="inherit" :text="deliveryInfo.logisticsCompanyName" />
        </view>
        <view class="item flex items-center justify-between pb-3">
          <wd-text size="28rpx" color="inherit" :text="t('order.logistics.trackingNo')" />
          <view class="flex items-center">
            <wd-text custom-class="hx-pr10" size="28rpx" color="inherit" :text="deliveryInfo.logisticsNo" />
            <wd-button size="small" type="text" @click="copy(deliveryInfo.logisticsNo)">
              {{ t('order.actions.copy') }}
            </wd-button>
          </view>
        </view>
        <view class="item flex items-center justify-between pb-3">
          <wd-text size="28rpx" color="inherit" :text="t('order.logistics.status')" />
          <dict-tag :options="deliveryStatusOptions" :value="deliveryInfo.deliveryStatus" />
        </view>
      </view>
      <view class="logistics-title mx-0 my-30rpx pl-20rpx text-30rpx font-bold">
        {{ t('order.logistics.info') }}
      </view>

      <view class="p-20rpx">
        <wd-steps
          v-if="deliveryInfo.logisticsList && deliveryInfo.logisticsList.length > 0" :active="0" vertical
          dot
        >
          <wd-step
            v-for="(item, index) in deliveryInfo.logisticsList" :key="index" :title="item.logisticsContext"
            :description="item.logisticsTime"
          />
        </wd-steps>
        <wd-status-tip v-else image="content" :tip="t('order.logistics.empty')" />
      </view>
    </template>
  </view>
</template>
