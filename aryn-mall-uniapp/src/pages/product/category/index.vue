<script setup lang="ts">
import { getRect, isArray } from 'wot-design-uni/components/common/util'
import { nextTick, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getTree } from '@/api/product/category'

definePage({
  name: 'category',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '分类',
  },
})
const router = useRouter()
const active = ref<number>(0)
const scrollTop = ref<number>(0)
const itemScrollTop = ref<number[]>([])
const loading = ref(true)
const state = reactive<any>({
  category: [],
})
onLoad(async () => {
  getCategory()
})
async function getCategory() {
  loading.value = true
  const response = await getTree()
  state.category = response
  await measureCategoryBoxes()
}
/** 分类列表渲染后再量高度：须先结束 loading，否则 v-else 里的 .category-box 尚未挂载 */
async function measureCategoryBoxes() {
  itemScrollTop.value = []
  scrollTop.value = 0
  if (!state.category?.length)
    return

  await nextTick()
  // 小程序部分机型单帧内节点未就绪，再延后一帧更稳
  await new Promise<void>(resolve => setTimeout(resolve, 0))

  try {
    const rects = await getRect('.category-box', true)
    if (isArray(rects) && rects.length > 0) {
      itemScrollTop.value = rects.map(item => (item.top as number) - 44 || 0)
      scrollTop.value = 0
    }
  }
  catch {
    // 无节点或查询失败时不阻断页面
  }
}

function handleChange({ value }: any) {
  active.value = value
  scrollTop.value = itemScrollTop.value[value]
}
function onScroll(e: any) {
  const { scrollTop } = e.detail
  const threshold = 50 // 下一个标题与顶部的距离
  if (scrollTop < threshold) {
    active.value = 0
    return
  }
  const index = itemScrollTop.value.findIndex(top => top > scrollTop && top - scrollTop <= threshold)
  if (index > -1) {
    active.value = index
  }
}

// 跳转商品列表
function toGoodsList(categoryId: string) {
  router.push({
    path: `/sub-pages/product/goods-list/index?categoryId=${categoryId}`,
  })
}
</script>

<template>
  <hr-navbar title="分类" :left-arrow="false" />
  <view class="category-wraper">
    <wd-sidebar v-model="active" @change="handleChange">
      <wd-sidebar-item v-for="(item, index) in state.category" :key="index" :value="index" :label="item.name" />
    </wd-sidebar>
    <scroll-view
      class="content bg-[#f4f4f4]" scroll-y scroll-with-animation :scroll-top="scrollTop" :throttle="false"
      @scroll="onScroll"
    >
      <view v-for="(item, index) in state.category" :key="index" class="m-20rpx">
        <image v-if="item.categoryPic" class="category-box h-180rpx w-100% rounded-[18rpx]" :src="item.categoryPic" mode="widthFix" lazy-load />
        <view class="category mt-20rpx bg-white">
          <view class="item-title">
            <wd-text size="26rpx" color="inherit" :text="item.name" />
          </view>
          <view class="item-container">
            <view
              v-for="(childItem, childIndex) in item.children" :key="childIndex" class="thumb-box"
              @click="toGoodsList(childItem.id)"
            >
              <image v-if="childItem.categoryPic" class="item-menu-image" :src="childItem.categoryPic" mode="widthFix" lazy-load />
              <view class="mt-10rpx text-center text-22rpx">
                {{ childItem.name }}
              </view>
            </view>
          </view>
        </view>
      </view>
      <wd-gap :height="5" />
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.category-wraper {
  display: flex;

  // #ifdef MP
  height: calc(100vh - var(--window-top) - 44px - env(safe-area-inset-bottom) - 20rpx);
  // #endif
  // #ifdef H5
  height: calc(100vh - var(--window-top) - 84px - env(safe-area-inset-bottom) - 20rpx);
  // #endif
}
.content {
  flex: 1;

  .category {
    margin-bottom: 30rpx;
    padding: 16rpx;
    border-radius: 18rpx;
  }

  .item-container {
    display: flex;
    flex-wrap: wrap;
  }

  .thumb-box {
    width: 33.333333%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    margin-top: 20rpx;
  }

  .item-menu-image {
    width: 120rpx;
    height: 120rpx;
  }
}
</style>
