<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getPage } from '@/api/product/collect'
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import { stockHintText } from '@/utils/stock-display'

definePage({
  name: 'user-collect',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的收藏',
  },
})

interface Spu {
  spuUrls: string[]
  name: string
  salesPrice: number
  originalPrice?: number
  stock: number
}
interface List {
  id: string
  goodsSpu: Spu
  salesPrice: string
  createTime: string
  spuId: string
}
const { t } = useI18n()
const list = ref<List[]>([])
const router = useRouter()
const globalLoading = useGlobalLoading()
const pagingRef = ref()
onLoad(async () => {
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
      <hr-navbar :title="t('accountSettings.collect.navTitle')" />
    </template>
    <view v-for="(item, index) in list" :key="index" class="m-2 flex rounded-xl bg-white px-2 py-4 dark:bg-[var(--wot-dark-background2)]" @click="toGoodsDetail(item.spuId)">
      <template v-if="item.goodsSpu">
        <image :src="item.goodsSpu.spuUrls[0]" class="h-20 w-20 flex-none rounded-xl" />
        <view class="ml-5 flex flex-1 flex-col overflow-hidden">
          <wd-text :lines="2" size="26rpx" color="inherit" :text="item.goodsSpu.name" />
          <view class="mt-2 flex items-center justify-between">
            <view class="flex items-baseline">
              <wd-text :text="item.goodsSpu.salesPrice" color="red" mode="price" prefix="￥" />
              <text
                v-if="shouldShowOriginalPrice(item.goodsSpu.salesPrice, item.goodsSpu.originalPrice)"
                class="price-original"
              >
                ￥{{ item.goodsSpu.originalPrice }}
              </text>
            </view>
            <wd-text v-if="stockHintText(item.goodsSpu.stock)" size="22rpx" :text="stockHintText(item.goodsSpu.stock)" />
          </view>
          <view class="mt-2 flex justify-end">
            <wd-text size="24rpx" :text="item.createTime" />
          </view>
        </view>
      </template>
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
.price-original {
  margin-left: 8rpx;
  font-size: 20rpx;
  color: #a8abb2;
  text-decoration: line-through;
}
</style>
