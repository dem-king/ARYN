<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getCount, getPage } from '@/api/product/appraise'
import { formatTime } from '@/utils/index'

definePage({
  name: 'goods-appraise',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '商品评价',
  },
})
interface AppraiseCount {
  allCount: number
  goodCount: number
  badCount: number
  negativeCount: number
  imageCount: number
}
// 评价数量
const appraiseCount = ref<AppraiseCount>({
  allCount: 0,
  goodCount: 0,
  badCount: 0,
  negativeCount: 0,
  imageCount: 0,
})
// 定义变量
const globalLoading = useGlobalLoading()

const pagingRef = ref()
const spuId = ref('')
const state = reactive<{ list: any[], queryParam: any }>({
  list: [],
  queryParam: {
    rateType: 'all',
  },
})

// tabs选项
const tabs = ref([
  { key: 'all', name: '全部', count: appraiseCount.value.allCount },
  { key: 'good', name: '好评', count: appraiseCount.value.goodCount },
  { key: 'neutral', name: '中评', count: appraiseCount.value.badCount },
  { key: 'bad', name: '差评', count: appraiseCount.value.negativeCount },
  { key: 'pic', name: '有图', count: appraiseCount.value.imageCount },
])

// 切换tab
function switchTab(key: string) {
  state.queryParam.rateType = key
  pagingRef.value?.reload()
}

onLoad(async (options) => {
  spuId.value = options?.spuId
  nextTick(() => {
    pagingRef.value?.reload()
    getAppraiseCount()
  })
})
async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading('加载中...')
  try {
    const response = await getPage(Object.assign({
      current: pageNo,
      size: pageSize,
      spuId: spuId.value,
      desc: 'create_time',
    }, state.queryParam))
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}
// 通过商品id查询评价数量
function getAppraiseCount() {
  getCount({ spuId: spuId.value }).then((response) => {
    appraiseCount.value = response
    // 更新tabs中的count值
    tabs.value[0].count = response.allCount
    tabs.value[1].count = response.goodCount
    tabs.value[2].count = response.badCount
    tabs.value[3].count = response.negativeCount
    tabs.value[4].count = response.imageCount
  })
}
function previewImage(index: number, picUrls: string[]) {
  uni.previewImage({
    current: index,
    urls: picUrls,
  })
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="商品评价" />
    </template>
    <view class="bg-white p-26rpx">
      <!-- 自定义tabs 换行显示 -->
      <view class="flex flex-wrap">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          class="mb-16rpx mr-16rpx cursor-pointer rounded-sm px-24rpx py-12rpx text-26rpx transition-all"
          :class="[
            state.queryParam.rateType === tab.key
              ? 'bg-primary text-white'
              : 'bg-gray-100 text-gray-600',
          ]"
          @click="switchTab(tab.key)"
        >
          {{ tab.name }} <text
            class="text-sm" :class="[
              state.queryParam.rateType === tab.key
                ? 'text-white'
                : 'text-gray-600',
            ]"
          >
            <text class="pl-1">
              {{ tab.count || 0 }}
            </text>
          </text>
        </view>
      </view>
    </view>
    <view
      v-for="(item, index) in state.list"
      :key="index"
      class="my-20rpx rounded-20rpx bg-white p-20rpx dark:bg-[var(--wot-dark-background2)]"
    >
      <view class="mb-20rpx flex items-center justify-between">
        <!-- 左边：头像+昵称 -->
        <view class="flex items-center">
          <wd-img :width="40" :height="40" round :src="item.avatarUrl" />
          <view class="pl-20rpx text-28rpx font-medium">
            {{ item.nickname }}
          </view>
        </view>

        <!-- 右边：评分+时间 -->
        <view class="flex flex-col items-end gap-12rpx text-gray-400">
          <wd-text size="24rpx" :text="formatTime(item.createTime)" />
          <wd-rate v-model="item.goodsScore" :num="5" readonly size="12px" />
        </view>
      </view>

      <!-- 内容 -->
      <view class="mb-16rpx whitespace-pre-wrap break-words text-26rpx text-gray-800 dark:text-gray-200">
        {{ item.content || '该用户未填写' }}
      </view>

      <!-- 图片区域 -->
      <view v-if="item.picUrls && item.picUrls.length > 0" class="mt-10rpx">
        <!-- 1 或 2 张：只显示第一张 -->
        <view v-if="item.picUrls.length <= 2" class="h-[120px] w-[120px]">
          <wd-img width="100%" height="100%" :src="item.picUrls[0]" radius="12rpx" :enable-preview="true" />
        </view>

        <!-- 3~4 张：一行等分 -->
        <view
          v-else-if="item.picUrls.length <= 4"
          class="flex overflow-hidden rounded-12rpx"
        >
          <view
            v-for="(pic, picIndex) in item.picUrls"
            :key="picIndex"
            class="h-[100px] flex-1 overflow-hidden"
            :class="[
              picIndex === 0 ? 'rounded-l-12rpx' : '',
              picIndex === item.picUrls.length - 1 ? 'rounded-r-12rpx' : 'mx-2rpx',
            ]"
          >
            <wd-img width="100%" height="100%" :src="pic" @click="previewImage(picIndex, item.picUrls)" />
          </view>
        </view>

        <!-- ≥5 张：最多显示4张，最后一张+角标 -->
        <view v-else class="flex gap-6rpx">
          <view
            v-for="(pic, picIndex) in item.picUrls.slice(0, 4)"
            :key="picIndex"
            class="relative h-[86px] flex-1 overflow-hidden rounded-10rpx"
          >
            <wd-img width="100%" height="100%" :src="pic" @click="previewImage(picIndex, item.picUrls)" />
            <!-- +N 角标 -->
            <view
              v-if="picIndex === 3 && item.picUrls.length > 4"
              class="absolute inset-0 flex items-center justify-center bg-black/40 text-28rpx text-white font-bold"
            >
              +{{ item.picUrls.length - 4 }}
            </view>
          </view>
        </view>
      </view>

      <!-- 商家回复 -->
      <view
        v-if="item.replyTime && item.businessReply"
        class="mt-18rpx rounded-10rpx bg-gray-100 p-16rpx text-24rpx text-gray-700 dark:bg-gray-700 dark:text-gray-200"
      >
        商家回复：{{ item.businessReply }}
      </view>
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
/* 移除所有传统样式，完全使用UnoCSS */
</style>
