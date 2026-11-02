<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { getCount, getPage } from '@/sub-pages/api/product/appraise'
import { formatTime } from '@/utils/index'

const { handlePageScroll } = useFrostedNavbar()

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
const { t } = useI18n()

const pagingRef = ref()
const spuId = ref('')
const state = reactive<{ list: any[], queryParam: any }>({
  list: [],
  queryParam: {
    rateType: 'all',
  },
})

// tabs选项：名称随语言切换，数量直接取接口结果，避免手动回写两份状态
const tabs = computed(() => [
  { key: 'all', name: t('product.review.all'), count: appraiseCount.value.allCount },
  { key: 'good', name: t('product.review.good'), count: appraiseCount.value.goodCount },
  { key: 'neutral', name: t('product.review.neutral'), count: appraiseCount.value.badCount },
  { key: 'bad', name: t('product.review.bad'), count: appraiseCount.value.negativeCount },
  { key: 'pic', name: t('product.review.withImage'), count: appraiseCount.value.imageCount },
])

// 切换tab
function switchTab(key: string) {
  state.queryParam.rateType = key
  pagingRef.value?.reload()
}

onLoad(async (options) => {
  spuId.value = options?.spuId
  // 详情页评价标签带着 rateType 进来；只接受接口认识的枚举，避免 URL 传参把列表查空
  const allowedRateTypes = ['all', 'good', 'neutral', 'bad', 'pic']
  const requestedRateType = String(options?.rateType || '')
  state.queryParam.rateType = allowedRateTypes.includes(requestedRateType)
    ? requestedRateType
    : 'all'
  nextTick(() => {
    pagingRef.value?.reload()
    getAppraiseCount()
  })
})
async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading(t('common.loading'))
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
  })
}
function previewImage(index: number, picUrls: string[]) {
  uni.previewImage({
    current: index,
    urls: picUrls,
  })
}

function isAnonymousItem(item: any) {
  return Number(item?.isAnonymous) === 1
}

function displayName(item: any) {
  return isAnonymousItem(item) ? t('product.review.anonymous') : (item?.nickname || t('product.review.anonymous'))
}

function avatarText(item: any) {
  return String(displayName(item) || t('product.review.anonymous')).slice(0, 1)
}

/** 系统默认评价 goodsScore 为 null：显示「未评分」而不是渲染 0 星 */
function hasScore(value: any) {
  const score = Number(value)
  return Number.isFinite(score) && score > 0
}

function hasAdvance(item: any) {
  return Boolean(item?.advanceTime) || Boolean(item?.advanceContent) || (item?.advancePicUrls?.length ?? 0) > 0
}
</script>

<template>
  <hr-navbar :title="t('product.reviewTitle')" />
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList" @scroll="handlePageScroll">
    <!-- 顶栏覆盖层等高占位：列表整屏滚动，首屏内容从顶栏下沿开始 -->
    <hr-navbar-space />
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
        <!-- 左边：头像+昵称（匿名评价由后端脱敏，前端兜底展示「匿名用户」） -->
        <view class="flex items-center">
          <wd-img v-if="item.avatarUrl && !isAnonymousItem(item)" :width="40" :height="40" round :src="item.avatarUrl" />
          <view v-else class="h-[40px] w-[40px] flex items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
            <text class="text-24rpx text-gray-400 font-medium">
              {{ avatarText(item) }}
            </text>
          </view>
          <view class="pl-20rpx text-28rpx font-medium">
            {{ displayName(item) }}
          </view>
        </view>

        <!-- 右边：评分+时间（系统默认评价无星级） -->
        <view class="flex flex-col items-end gap-12rpx text-gray-400">
          <wd-text size="24rpx" :text="formatTime(item.createTime)" />
          <wd-rate v-if="hasScore(item.goodsScore)" :model-value="Number(item.goodsScore)" :num="5" readonly size="12px" />
          <text v-else class="text-22rpx text-gray-400">{{ t('product.review.scoreMissing') }}</text>
        </view>
      </view>

      <!-- 内容 -->
      <view class="mb-16rpx whitespace-pre-wrap break-words text-26rpx text-gray-800 dark:text-gray-200">
        {{ item.content || t('product.review.emptyContent') }}
      </view>

      <!-- 快捷标签 -->
      <view v-if="item.tags && item.tags.length > 0" class="mb-10rpx flex flex-wrap gap-8rpx">
        <text
          v-for="tag in item.tags"
          :key="tag"
          class="rounded-full bg-gray-100 px-16rpx py-4rpx text-22rpx text-gray-600 dark:bg-gray-700 dark:text-gray-300"
        >
          {{ tag }}
        </text>
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

      <!-- 追评：后端仅一次，展示追评内容/图片与时间 -->
      <view
        v-if="hasAdvance(item)"
        class="mt-18rpx rounded-10rpx bg-gray-50 p-16rpx dark:bg-gray-700/50"
      >
        <view class="flex items-center justify-between">
          <text class="text-24rpx text-gray-500 font-medium dark:text-gray-300">
            {{ t('product.review.advance') }}
          </text>
          <text v-if="item.advanceTime" class="text-22rpx text-gray-400">
            {{ formatTime(item.advanceTime) }}
          </text>
        </view>
        <view
          v-if="item.advanceContent"
          class="mt-8rpx whitespace-pre-wrap break-words text-26rpx text-gray-700 leading-relaxed dark:text-gray-200"
        >
          {{ item.advanceContent }}
        </view>
        <view v-if="item.advancePicUrls && item.advancePicUrls.length > 0" class="mt-10rpx flex gap-6rpx">
          <view
            v-for="(pic, picIndex) in item.advancePicUrls.slice(0, 3)"
            :key="picIndex"
            class="h-[76px] w-[76px] overflow-hidden rounded-10rpx"
          >
            <wd-img
              width="100%" height="100%" mode="aspectFill" :src="pic"
              @click="previewImage(picIndex, item.advancePicUrls)"
            />
          </view>
        </view>
      </view>

      <!-- 商家回复 -->
      <view
        v-if="item.replyTime && item.businessReply"
        class="mt-18rpx rounded-10rpx bg-gray-100 p-16rpx text-24rpx text-gray-700 dark:bg-gray-700 dark:text-gray-200"
      >
        {{ t('product.review.merchantReply', { content: item.businessReply }) }}
      </view>
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
/* 移除所有传统样式，完全使用UnoCSS */
</style>
