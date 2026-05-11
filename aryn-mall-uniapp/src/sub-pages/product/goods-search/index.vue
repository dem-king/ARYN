<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getPage, getTop10HotSearchGoods } from '@/api/product/spu'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'

definePage({
  name: 'goods-search',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '商品搜索',
  },
})
interface State {
  hotTopList: any[]
  hotSearchList: any[]
  keyword: string
}
// 定义变量
const { confirm } = useGlobalMessage()
const router = useRouter()
const { historyList, addSearchHistory, clearSearchHistory } = useSearchHistory()
const isCanBack = ref(true)

const state = reactive<State>({
  hotTopList: [],
  hotSearchList: [],
  keyword: '',
})

onLoad(async (options) => {
  if (options?.keyword && options?.keyword !== 'undefined') {
    state.keyword = options.keyword
  }
  // 获取商品列表
  getHotTop()
  getHotSearch()
})

// 搜索
function doSearch(name: any) {
  if (!name) {
    return
  }
  const val = typeof name === 'string' ? name : name?.value
  if (val) {
    state.keyword = val
    addSearchHistory(val)
  }
  // 跳转商品列表
  router.push({
    name: 'goods-list',
    params: {
      keyword: state.keyword,
    },
  })
}

// 删除全部搜索记录
function delAll() {
  confirm({
    title: '提示',
    msg: '该操作将清空搜索历史是否继续？',
    closeOnClickModal: false,
    success: (res) => {
      if (res.action === 'confirm') {
        clearSearchHistory()
      }
    },
  })
}

function getHotTop() {
  getPage({
    current: 1,
    size: 10,
    desc: 'sales_volume',
  }).then((res) => {
    state.hotTopList = res.records
  })
}

// 获取热搜榜数据
function getHotSearch() {
  getTop10HotSearchGoods().then((res) => {
    state.hotSearchList = res
  })
}

function toGoods(id: string) {
  router.push({
    name: 'goods-detail',
    params: {
      id,
    },
  })
}
onMounted(() => {
  const pages = getCurrentPages()
  /**
   * 判断是否能返回
   */
  if (pages.length <= 1 || pages[pages.length - 1].route === 'pages/login/index') {
    isCanBack.value = false
  }
  else {
    isCanBack.value = true
  }
})
</script>

<template>
  <!-- 搜索框 -->
  <hr-search-navbar
    v-model="state.keyword"
    placeholder="搜索"
    :placeholder-left="true"
    :focus="true"
    @search="doSearch"
  />
  <view class="page-wrapper">
    <!-- 搜索历史 -->
    <view class="p-2">
      <view class="flex items-center justify-between">
        <view class="text-sm">
          搜索历史
        </view> <wd-icon v-if="historyList && historyList.length > 0" name="delete-thin" size="28rpx" @click="delAll" />
      </view>
      <template v-if="historyList && historyList.length > 0">
        <view class="flex flex-wrap pt-2">
          <view v-for="(item, index) in historyList" :key="index" class="p-1">
            <view class="flex items-center">
              <wd-tag round bg-color="#f2f2f2" color="#3a3a3a" @click="doSearch({ value: item })">
                {{ item }}
              </wd-tag>
            </view>
          </view>
        </view>
      </template>
      <template v-else>
        <view class="flex items-center justify-center pt-2 text-xs">
          暂无搜索历史
        </view>
      </template>
    </view>
    <!-- 热搜排行和销量排行 -->
    <view class="p-2">
      <view class="no-scrollbar flex overflow-x-auto">
        <view class="my-2 w-60% w-full flex-shrink-0 rounded-20rpx from-theme-bg to-white from-20% bg-gradient-to-b p-2 shadow-sm">
          <!-- 销量排行 -->
          <view class="mb-2 flex items-center justify-between">
            <view> 销量排行 </view>
          </view>
          <view
            v-for="(item, index) in state.hotTopList" :key="index" class="mb-2 flex items-center"
            @click="toGoods(item.id)"
          >
            <view
              class="pr-2 font-bold"
              :class="[index < 3 ? `text-${['red', 'orange', 'amber'][index]}-500` : 'text-gray-400']"
            >
              {{ index + 1 }}
            </view>
            <wd-img v-if="index < 3" radius="15rpx" width="45px" height="45px" :src="item.spuUrls[0]" />
            <view class="mx-10rpx flex-1">
              <wd-text
                :text="item.name" :lines="1" size="24rpx"
                :custom-class="index < 3 ? `text-${['red', 'orange', 'amber'][index]}-500` : ''"
              />
            </view>
            <view class="flex justify-end text-12px text-gray-500">
              {{ item.salesVolume }}
            </view>
          </view>
        </view>

        <!-- 间距 -->
        <view class="w-40rpx flex-shrink-0" />

        <!-- 热搜榜 -->
        <view class="my-20rpx w-60% w-full flex-shrink-0 rounded-20rpx from-theme-bg to-white from-20% bg-gradient-to-b p-20rpx shadow-sm">
          <view class="mb-20rpx flex items-center justify-between">
            <view> 热搜榜 </view>
          </view>
          <view
            v-for="(item, index) in state.hotSearchList" :key="index" class="mb-20rpx flex items-center"
            @click="toGoods(item.id)"
          >
            <view
              class="pr-20rpx font-bold"
              :class="[index < 3 ? `text-${['red', 'orange', 'amber'][index]}-500` : 'text-gray-400']"
            >
              {{ index + 1 }}
            </view>
            <wd-img v-if="index < 3" radius="15rpx" width="45px" height="45px" :src="item.spuUrls[0]" />
            <view class="mx-10rpx flex-1">
              <wd-text
                :text="item.name" :lines="1" size="24rpx"
                :custom-class="index < 3 ? `text-${['red', 'orange', 'amber'][index]}-500` : ''"
              />
            </view>
          <!-- <view class="flex justify-end text-14px text-gray-500">
            {{ item.salesVolume }}
          </view> -->
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page-wrapper {
  min-height: calc(100vh - var(--window-top));
  background-color: #fff;
}
.no-scrollbar {
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE & Edge */
}
.no-scrollbar::-webkit-scrollbar {
  display: none; /* Chrome、Safari */
}
</style>
