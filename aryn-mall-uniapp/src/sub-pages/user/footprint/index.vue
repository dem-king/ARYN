<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getPage } from '@/api/product/footprint'

definePage({
  name: 'footprint',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的足迹',
  },
})

const list = ref<any>([])
const globalLoading = useGlobalLoading()

const router = useRouter()
const pagingRef = ref()
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
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}
function toGoodsDetail(id: string) {
  router.push({
    name: 'goods-detail',
    params: {
      id,
    },

  })
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="我的足迹" />
    </template>
    <view class="py-1">
      <view v-for="(item, index) in list" :key="index" class="m-2 rounded-xl bg-white p-2">
        <view class="text-28rpx font-bold">
          {{ item.browseDate }}
        </view>
        <view v-for="(g, i) in item.items" :key="i" class="flex p-2" @click="toGoodsDetail(g.spuId)">
          <template v-if="g">
            <image :src="g.spuUrls" class="h-20 w-20 flex-none rounded-xl" />
            <view class="ml-5 flex flex-1 flex-col overflow-hidden">
              <wd-text :lines="2" size="26rpx" color="inherit" :text="g.name" />
              <view class="mt-2 flex items-center justify-between">
                <wd-text :text="g.salesPrice" color="red" mode="price" prefix="￥" />
                <wd-text size="22rpx" :text="`库存 ${g.stock}`" />
              </view>
              <view class="mt-2 flex justify-end">
                <wd-text size="24rpx" :text="g.createTime" />
              </view>
            </view>
          </template>
        </view>
      </view>
    </view>
  </z-paging>
</template>
