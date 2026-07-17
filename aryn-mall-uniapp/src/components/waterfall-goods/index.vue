<script setup lang="ts">
import { reactive } from 'vue'
import { getPage } from '@/api/product/spu'

// 定义变量
const state = reactive<any>({
  list: [],
})

const router = useRouter()
const pagingRef = ref()
function queryList(pageNo: number, pageSize: number) {
  getPage(
    Object.assign({
      current: pageNo,
      size: pageSize,
    }),
  ).then((response) => {
    pagingRef.value?.complete(response.records)
  })
}

// 跳转商品详情
function toDetail(id: string) {
  router.push({
    name: 'goods-detail',
    params: {
      id,
    },
  })
}
</script>

<template>
  <view>
    <z-paging ref="pagingRef" v-model="state.list" use-page-scroll :refresher-enabled="false" @query="queryList">
      <view class="p-2">
        <view class="flex flex-wrap justify-between">
          <view v-for="(item, index) in state.list" :key="index" class="mb-20rpx box-border w-[calc(50%-10rpx)] rounded-xl bg-white shadow-sm dark:bg-[var(--wot-dark-background2)]" @click="toDetail(item.id)">
            <image :src="item.spuUrls[0]" class="block h-160px w-full rounded-lg" />
            <view class="p-10rpx">
              <view class="px-10rpx">
                <wd-text size="14px" :lines="2" :text="item.name" />
              </view>
              <view class="m-t-10px flex flex-wrap gap-8rpx">
                <view
                  v-if="item.freightType === '0'"
                  class="rounded-8rpx bg-orange-50 px-12rpx py-4rpx text-10px text-orange-400"
                >
                  包邮
                </view>
              </view>
              <view class="flex items-center justify-between pt-10rpx">
                <wd-text mode="price" :text="item.salesPrice" prefix="￥" color="red" />
              </view>
            </view>
          </view>
        </view>
      </view>
    </z-paging>
  </view>
</template>
