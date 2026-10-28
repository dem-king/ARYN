<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { getPage } from '@/sub-pages/api/order/orderRefunds'
import { useDict } from '@/sub-pages/utils/dict'

definePage({
  name: 'refunds-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '退款/售后',
  },
})
const pagingRef = ref()
const state = reactive<{ list: any }>({
  list: [],
})
const router = useRouter()
const globalLoading = useGlobalLoading()
const { t } = useI18n()
const { refund_status } = useDict('refund_status')
onLoad(() => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})
async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
      desc: 'create_time',
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}
function toDetail(id: string) {
  router.push({
    name: 'refunds-detail',
    params: {
      id,
    },
  })
}
const statusDescMap = computed<Record<string, string>>(() => ({
  1: t('afterSales.list.status.pendingReview'),
  2: t('afterSales.list.status.awaitingReturn'),
  3: t('afterSales.list.status.returnProcessing'),
  4: t('afterSales.list.status.returnCompleted'),
  5: t('afterSales.list.status.refundProcessing'),
  6: t('afterSales.list.status.refundSuccess'),
  7: t('afterSales.list.status.returnFailed'),
  8: t('afterSales.list.status.refundFailed'),
}))
function getStatusDesc(status: string) {
  return statusDescMap.value[status] || ''
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar :title="t('afterSales.list.navTitle')" />
    </template>
    <view v-for="(item, index) in state.list" :key="index" class="m-2 rounded-xl bg-white p-2" @click="toDetail(item.id)">
      <!-- 显示退款商品 -->
      <view class="flex items-center justify-between">
        <view v-if="item.refundTradeNo" class="flex items-center text-12px text-gray">
          {{ item.refundTradeNo }}
        </view>
        <view class="text-24rpx text-gray-400">
          {{ item.refundType === '1' ? t('afterSales.list.refund') : t('afterSales.list.return') }}
        </view>
      </view>
      <view v-if="item.orderItem" class="flex pt-10rpx">
        <image v-if="item.orderItem.picUrl" :src="item.orderItem.picUrl" class="h-160rpx w-160rpx flex-none rounded-lg" />
        <view class="ml-20rpx h-full flex flex-1 flex-col overflow-hidden">
          <view class="py-1rpx">
            <wd-text :lines="2" size="14px" color="inherit" :text="item.orderItem.spuName" />
          </view>
          <view v-if="item.orderItem.specsInfo">
            <wd-text custom-class="pt-1" size="12px" color="#909090" :text="item.orderItem.specsInfo" />
          </view>
          <view class="flex items-center justify-between pt-10rpx">
            <text class="text-12px">
              {{ t('afterSales.list.applyQty', { count: item.orderItem.buyQuantity }) }}
            </text>
            <wd-text :text="item.refundAmount" color="red" mode="price" prefix="￥" />
          </view>
        </view>
      </view>
      <!-- 状态 -->
      <view class="mt-2 rounded-xl bg-gray-100 p-1">
        <view class="flex items-center">
          <view class="p-1 text-14px">
            <dict-tag :options="refund_status" :value="item.status" />
          </view>
          <view class="p-1 text-12px text-gray-400">
            <text v-if="item.status === '9'">
              {{ item.refuseReason }}
            </text>
            <text v-else>
              {{ getStatusDesc(item.status) }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>
