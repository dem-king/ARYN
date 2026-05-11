<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getByOrderId } from '@/api/order/delivery'
import { useDict } from '@/utils/dict'

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
const globalLoading = useGlobalLoading()
onLoad((options) => {
  orderId.value = options?.id
  getLogistics()
})

async function getLogistics() {
  try {
    globalLoading.loading('加载中...')
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
  <hr-navbar title="订单物流" />
  <view class="p-2">
    <!-- 单个包裹显示 -->
    <template v-if="deliveryInfo">
      <view class="rounded-xl bg-white p-4">
        <view class="item flex items-center justify-between pb-3">
          <wd-text size="28rpx" color="inherit" text="物流公司" />
          <wd-text size="28rpx" color="inherit" :text="deliveryInfo.logisticsCompanyName" />
        </view>
        <view class="item flex items-center justify-between pb-3">
          <wd-text size="28rpx" color="inherit" text="物流单号" />
          <view class="flex items-center">
            <wd-text custom-class="hx-pr10" size="28rpx" color="inherit" :text="deliveryInfo.logisticsNo" />
            <wd-button size="small" type="text" @click="copy(deliveryInfo.logisticsNo)">
              复制
            </wd-button>
          </view>
        </view>
        <view class="item flex items-center justify-between pb-3">
          <wd-text size="28rpx" color="inherit" text="物流状态" />
          <dict-tag :options="order_delivery_status" :value="deliveryInfo.deliveryStatus" />
        </view>
      </view>
      <view class="logistics-title mx-0 my-30rpx pl-20rpx text-30rpx font-bold">
        物流信息
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
        <wd-status-tip v-else image="content" tip="暂无物流信息" />
      </view>
    </template>
  </view>
</template>
