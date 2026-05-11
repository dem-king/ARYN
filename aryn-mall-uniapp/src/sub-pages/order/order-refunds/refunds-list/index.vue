<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getPage } from '@/api/order/orderRefunds'
import { useDict } from '@/utils/dict'

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
const { refund_status } = useDict('refund_status')
onLoad(() => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})
async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading('加载中...')
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
function getStatusDesc(status: string) {
  const statusMap: Record<string, string> = {
    1: '退款申请已提交，等待商家审核',
    2: '商家同意退货，请尽快退回商品',
    3: '退货正在处理中，请耐心等待',
    4: '商品已退回，等待商家确认',
    5: '退款正在处理中，请耐心等待',
    6: '退款已完成，资金将原路返回',
    7: '退货失败，请联系商家处理',
    8: '退款失败，请联系商家处理',
  }
  return statusMap[status] || ''
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="退款/售后" />
    </template>
    <view v-for="(item, index) in state.list" :key="index" class="m-2 rounded-xl bg-white p-2" @click="toDetail(item.id)">
      <!-- 显示退款商品 -->
      <view class="flex items-center justify-between">
        <view v-if="item.refundTradeNo" class="flex items-center text-12px text-gray">
          {{ item.refundTradeNo }}
        </view>
        <view class="text-24rpx text-gray-400">
          {{ item.refundType === '1' ? '退款' : '退货' }}
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
              申请数量：{{ item.orderItem.buyQuantity }}
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
