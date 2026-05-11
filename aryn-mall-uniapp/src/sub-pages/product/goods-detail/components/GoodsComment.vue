<script setup lang="ts">
import { computed, getCurrentInstance, ref, watch } from 'vue'
import { formatTime } from '@/utils/index'
import { getPage as getAppraisePage, getCount } from '@/api/product/appraise'

interface Props {
  spuId: string
}
interface AppraiseCount {
  allCount: number
  goodCount: number
  badCount: number
  negativeCount: number
  imageCount: number
}

const props = defineProps<Props>()
const router = useRouter()
// Create local reactive state for rateType
const rateType = ref()
const goodsAppraiseList = ref<any[]>([])
// 评价数量
const appraiseCount = ref<AppraiseCount>({
  allCount: 0,
  goodCount: 0,
  badCount: 0,
  negativeCount: 0,
  imageCount: 0,
})
// Watch for changes in the prop and update local state
watch(
  () => props.spuId,
  (newValue) => {
    getAppraiseCount(newValue)
    getGoodsAppraise(newValue)
  },
  { immediate: true }, // 如果一开始goodsId就有值，也会立即加载
)

// Handle rate type change
function onRateTypeChange(value: string) {
  rateType.value = value
  toAppraise()
}

// 使用计算属性来确保rateOptions响应appraiseCount的变化
const rateOptions = computed(() => [
  { value: 'all', label: '全部', count: appraiseCount.value.allCount || 0 },
  { value: 'good', label: '好评', count: appraiseCount.value.goodCount || 0 },
  { value: 'neutral', label: '中评', count: appraiseCount.value.badCount || 0 },
  { value: 'bad', label: '差评', count: appraiseCount.value.negativeCount || 0 },
  { value: 'pic', label: '有图', count: appraiseCount.value.imageCount || 0 },
])

const proxy = getCurrentInstance()?.proxy

// 获取scroll-comment元素位置的方法
function getScrollCommentRect() {
  return new Promise((resolve, reject) => {
    if (!proxy) {
      reject(new Error('组件实例 proxy 为 null'))
      return
    }
    const query = uni.createSelectorQuery().in(proxy)

    query
      .select('#scroll-comment')
      .boundingClientRect((data) => {
        if (data) {
          resolve(data)
        }
        else {
          reject(new Error('Element not found or query failed'))
        }
      })
      .exec()
  })
}

function getImageRadius(index: number, total: number) {
  if (index === 0)
    return '10px 0 0 10px' // 最左边
  if (index === total - 1)
    return '0 10px 10px 0' // 最右边
  return '0' // 中间无圆角
}
// 通过id查询商品评价
function getGoodsAppraise(id: string) {
  getAppraisePage({
    spuId: id,
    current: 1,
    size: 2,
    desc: 'create_time',
  }).then((response) => {
    goodsAppraiseList.value = response.records
  })
}
// 通过商品id查询评价数量
function getAppraiseCount(id: string) {
  getCount({ spuId: id }).then((response) => {
    appraiseCount.value = response
  })
}
function toAppraise() {
  router.push({
    name: 'goods-appraise',
    params: {
      spuId: props.spuId,
      rateType: rateType.value,
    },
  })
}
// 暴露方法给父组件
defineExpose({
  getScrollCommentRect,
})
</script>

<template>
  <view id="scroll-comment" class="m-2 rounded-xl bg-white p-2 dark:bg-[var(--wot-dark-background2)]">
    <view class="my-2 flex items-center justify-between">
      <view class="text-sm">
        评价 ({{ appraiseCount.allCount || 0 }})
      </view>
      <view class="flex items-center justify-center text-gray-400" @click="toAppraise">
        <view class="text-12px">
          查看更多
        </view>
        <wd-icon :size="12" name="arrow-right" color="var(--wot-cell-arrow-color)" />
      </view>
    </view>
    <!-- 自定义 radio group -->
    <view class="no-scrollbar flex overflow-x-auto">
      <view
        v-for="option in rateOptions"
        :key="option.value"
        class="mx-10rpx my-1 flex cursor-pointer items-center justify-center whitespace-nowrap rounded-lg bg-theme-bg px-3 py-1 text-14px text-secondary transition-all"
        @click="onRateTypeChange(option.value)"
      >
        {{ option.label }} <text class="pl-1">
          {{ option.count }}
        </text>
      </view>
    </view>
    <view v-for="(item, index) in goodsAppraiseList" :key="index" class="mt-20px">
      <view class="flex items-center justify-between">
        <view class="flex items-center">
          <wd-img width="40px" height="40px" round :src="item.avatarUrl" />
          <view class="px-10rpx">
            <view>
              {{ item.nickname }}
            </view>
          </view>
        </view>
        <view class="text-14px text-gray-400">
          {{ formatTime(item.createTime) }}
        </view>
      </view>

      <!-- 1张 或 2张：只显示第一张 -->
      <view
        v-if="item.picUrls && (item.picUrls.length === 1 || item.picUrls.length === 2)"
        class="flex items-start justify-between"
      >
        <view class="my-16rpx mr-2 flex-1 whitespace-pre-wrap break-words text-14px">
          {{ item.content }}
        </view>
        <view class="h-[50px] w-[50px] flex-shrink-0">
          <wd-img width="100%" height="100%" :src="item.picUrls[0]" radius="10px" />
        </view>
      </view>

      <!-- 3张及以上：一行显示 -->
      <view v-else>
        <view class="my-16rpx whitespace-pre-wrap break-words text-14px">
          {{ item.content }}
        </view>

        <view
          v-if="item.picUrls && item.picUrls.length >= 3"
          class="flex items-center justify-center gap-3rpx"
        >
          <view
            v-for="(pic, picIndex) in item.picUrls.slice(0, 4)"
            :key="picIndex"
            class="h-[86px] overflow-hidden"
            :style="{
              width: `calc(${100 / Math.min(item.picUrls.length, 4)}% - ${item.picUrls.length <= 4 ? 4 : 10}rpx)`,
            }"
          >
            <wd-img
              v-if="pic"
              width="100%"
              height="100%"
              mode="aspectFill"
              :src="pic"
              :radius="getImageRadius(picIndex, Math.min(item.picUrls.length, 4))"
            />
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.no-scrollbar {
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE & Edge */
}
.no-scrollbar::-webkit-scrollbar {
  display: none; /* Chrome、Safari */
}
</style>
