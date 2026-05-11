<script setup lang="ts">
import type { GroupBuyActivity } from '@/api/promotion/groupBuyActivity'
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getActivityPage } from '@/api/promotion/groupBuyActivity'

definePage({
  name: 'group-buy-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '拼团活动',
  },
})

const pagingRef = ref()
const state = reactive<{ list: GroupBuyActivity[] }>({
  list: [],
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
    const response = await getActivityPage({
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

function goDetail(activity: GroupBuyActivity) {
  if (activity.id) {
    uni.navigateTo({
      url: `/sub-pages/promotion/group-buy/group-buy-detail/index?id=${activity.id}`,
    })
  }
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="拼团活动" />
    </template>
    <view v-for="item in state.list" :key="item.id" class="m-2">
      <group-buy-card :activity="item" @click="goDetail" />
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
</style>
