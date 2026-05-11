<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getUserPage } from '@/api/product/appraise'

definePage({
  name: 'user-appraise',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的评价',
  },
})
interface List {
  avatarUrl: string
  nickname: string
  goodsScore: number
  createTime: string
  content: string
  picUrls: string[]
  replyTime: string
  businessReply: string
}
// 定义变量
const list = ref<List[]>([])
const pagingRef = ref()
const globalLoading = useGlobalLoading()
onShow(async () => {
  await nextTick()
  pagingRef.value?.reload()
})

async function queryList(pageNo: number, pageSize: number) {
  try {
    globalLoading.loading('加载中...')
    const response: any = await getUserPage({
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

function previewImage(current: number, urls: string[]) {
  uni.previewImage({
    urls,
    current,
  })
}

function formatTime(time: string) {
  return time ? time.split(' ')[0] : ''
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="我的评价" />
    </template>
    <view
      v-for="(item, index) in list"
      :key="index"
      class="m-2 rounded-xl bg-white p-2 dark:bg-[var(--wot-dark-background2)]"
    >
      <view class="mb-2 flex items-center justify-between">
        <!-- 左边：头像+昵称 -->
        <view class="flex items-center">
          <wd-img :width="40" :height="40" round :src="item.avatarUrl" />
          <view class="pl-2 text-28rpx font-medium">
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
      <view class="mb-16rpx whitespace-pre-wrap break-words text-26rpx text-gray-800 leading-relaxed dark:text-gray-200">
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
        class="mt-18rpx whitespace-pre-wrap break-words rounded-10rpx bg-gray-100 p-16rpx text-24rpx text-gray-700 leading-relaxed dark:bg-gray-700 dark:text-gray-200"
      >
        商家回复：{{ item.businessReply }}
      </view>
    </view>
  </z-paging>
</template>

<style lang="scss"></style>
