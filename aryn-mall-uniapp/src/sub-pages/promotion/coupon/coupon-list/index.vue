<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getPage } from '@/api/promotion/couponInfo'

definePage({
  name: 'coupon-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '领券中心',
  },
})
const pagingRef = ref()
const state = reactive<{ list: any[], hotCouponList: any[] }>({
  list: [],
  hotCouponList: [],
})
const globalLoading = useGlobalLoading()
onLoad(async () => {
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
// 领取优惠券
async function handleReceive(coupon: any) {
  const item = state.list.find(item => item.id === coupon.id)
  if (item) {
    item.userReceiveCount = item.userReceiveCount + 1
  }
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="领券中心" />
    </template>
    <view v-for="item in state.list" :key="item.id" class="m-2">
      <coupon-card :coupon="item" :status="item.userReceiveCount > 0 ? 'received' : 'available'" @receive="handleReceive" />
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
.page-wrapper {
  min-height: calc(100vh - var(--window-top));
}
.coupon-list-page {
  padding: 24rpx;
}
.hot-coupon-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24rpx;
  margin-bottom: 32rpx;
}
.hot-coupon-item {
  /* 让卡片撑满格子 */
  width: 100%;
  display: flex;
  justify-content: center;
}
</style>
