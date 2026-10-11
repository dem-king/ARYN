<script setup lang="ts">
import type { SuggestItem } from '@/sub-pages/utils/search-suggest'
import { onLoad } from '@dcloudio/uni-app'
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { getPage, getSuggestWords, getTop10HotSearchGoods } from '@/api/product/spu'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'
import { useSearchHistory } from '@/sub-pages/composables/useSearchHistory'
import { mergeSuggestWords, suggestFromLabel } from '@/sub-pages/utils/search-suggest'

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
  /** 接口返回的联想词（真实商品名）；失败/无匹配为空数组 */
  apiSuggestWords: string[]
}
// 前三名高亮色必须写成静态字符串：运行时模板字符串拼出的类名 UnoCSS 提取不到，样式永远不会生成
const RANK_COLORS = ['text-red-500', 'text-orange-500', 'text-amber-500'] as const
// 定义变量
const { confirm } = useGlobalMessage()
const router = useRouter()
const { historyList, addSearchHistory, clearSearchHistory } = useSearchHistory()
const isCanBack = ref(true)

const state = reactive<State>({
  hotTopList: [],
  hotSearchList: [],
  keyword: '',
  apiSuggestWords: [],
})

/**
 * 联想词 = 接口词（真实在售商品名）+ 本地历史/热搜兜底，合并去重后展示。
 * 接口失败或无匹配时自然退化为纯本地联想，面板不白屏。
 */
const suggestList = computed<SuggestItem[]>(() => mergeSuggestWords(
  state.apiSuggestWords,
  state.keyword,
  historyList.value,
  state.hotTopList.map(item => item.name).filter(Boolean),
))

/**
 * 高亮分段在 script 里预切成三段，模板保持单行渲染——text 内的换行缩进
 * 会被当成文本渲染出多余空格，不能让格式化工具把嵌套 text 拆成多行。
 */
const suggestRows = computed(() => suggestList.value.map((item) => {
  const hit = item.hitStart !== undefined && item.hitEnd !== undefined
  return {
    item,
    before: hit ? item.word.slice(0, item.hitStart) : item.word,
    hitText: hit ? item.word.slice(item.hitStart, item.hitEnd) : '',
    after: hit ? item.word.slice(item.hitEnd) : '',
  }
}))

/** 是否处于「输入中」：有输入且没在展示联想以外的面板 */
const showSuggest = computed(() => state.keyword.trim().length > 0)

/** 联想请求竞态守卫：只采纳最后一次输入的响应，快速输入不闪旧词 */
let suggestSeq = 0
let suggestTimer: ReturnType<typeof setTimeout> | null = null

watch(() => state.keyword, (rawKeyword) => {
  if (suggestTimer)
    clearTimeout(suggestTimer)
  const keyword = rawKeyword.trim()
  if (!keyword) {
    suggestSeq++
    state.apiSuggestWords = []
    return
  }
  // 250ms 防抖：停止输入后才请求，键入到出词体感约 300ms（对标小象）
  suggestTimer = setTimeout(() => {
    void fetchSuggestWords(keyword)
  }, 250)
})

async function fetchSuggestWords(keyword: string) {
  const seq = ++suggestSeq
  try {
    const words = await getSuggestWords(keyword)
    if (seq !== suggestSeq)
      return
    state.apiSuggestWords = Array.isArray(words) ? words : []
  }
  catch {
    // 静默降级：置空后由本地历史/热搜兜底出词，不打断搜索主流程
    if (seq !== suggestSeq)
      return
    state.apiSuggestWords = []
  }
}

onUnmounted(() => {
  if (suggestTimer)
    clearTimeout(suggestTimer)
  suggestSeq++
})

function applySuggest(item: SuggestItem) {
  state.keyword = item.word
  doSearch(item.word)
}

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
    <!-- 联想词：有输入时优先展示，替代默认面板 -->
    <view v-if="showSuggest" class="p-2">
      <view class="text-sm">
        猜你想搜
      </view>
      <view v-if="suggestList.length > 0" class="pt-2">
        <view
          v-for="row in suggestRows"
          :key="`${row.item.from}-${row.item.word}`"
          class="suggest-row"
          @click="applySuggest(row.item)"
        >
          <wd-icon name="search" size="28rpx" color="#999" />
          <!-- 命中片段三段渲染并高亮（不拼 HTML，规避 XSS 与小程序 v-html 兼容坑）。
               用 flex 容器 + 兄弟 text 分段：嵌套 text 会被格式化拆行，
               拆行产生的空白文本节点会渲染成多余空格，flex 下则不会。 -->
          <view class="suggest-word">
            <text class="suggest-seg">
              {{ row.before }}
            </text>
            <text v-if="row.hitText" class="suggest-seg suggest-hit">
              {{ row.hitText }}
            </text>
            <text class="suggest-seg suggest-seg--tail">
              {{ row.after }}
            </text>
          </view>
          <text v-if="suggestFromLabel(row.item.from)" class="suggest-from">
            {{ suggestFromLabel(row.item.from) }}
          </text>
        </view>
      </view>
      <view v-else class="flex items-center justify-center pt-2 text-xs">
        没找到相关联想，直接搜索试试
      </view>
    </view>

    <!-- 搜索历史（有输入时让位给联想面板，避免两个面板同时出现） -->
    <view v-if="!showSuggest" class="p-2">
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
    <view v-if="!showSuggest" class="p-2">
      <view class="no-scrollbar flex overflow-x-auto">
        <!-- 只保留一个宽度类：w-full 会在样式表顺序上覆盖 w-60%，把板块撑满整屏，
             右侧热搜榜完全不可见；小程序默认 content-box，须显式 box-border 才不会因
             内边距把销量数字顶出可视区 -->
        <view class="my-2 box-border w-86% flex-shrink-0 rounded-20rpx from-theme-bg to-white from-20% bg-gradient-to-b p-2 shadow-sm">
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
              :class="index < 3 ? RANK_COLORS[index] : 'text-gray-400'"
            >
              {{ index + 1 }}
            </view>
            <wd-img v-if="index < 3" radius="15rpx" width="45px" height="45px" :src="item.spuUrls[0]" />
            <view class="mx-10rpx flex-1">
              <wd-text
                :text="item.name" :lines="1" size="24rpx"
                :custom-class="index < 3 ? RANK_COLORS[index] : ''"
              />
            </view>
            <view class="flex flex-shrink-0 justify-end pl-2 text-12px text-gray-500">
              {{ item.salesVolume }}
            </view>
          </view>
        </view>

        <!-- 间距 -->
        <view class="w-40rpx flex-shrink-0" />

        <!-- 热搜榜 -->
        <view class="my-20rpx box-border w-86% flex-shrink-0 rounded-20rpx from-theme-bg to-white from-20% bg-gradient-to-b p-20rpx shadow-sm">
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
                :custom-class="index < 3 ? RANK_COLORS[index] : ''"
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
.suggest-row {
  display: flex;
  align-items: center;
  padding: 20rpx 8rpx;
  border-bottom: 1rpx solid #f2f2f2;

  &:last-child {
    border-bottom: none;
  }
}

.suggest-word {
  display: flex;
  flex: 1;
  min-width: 0;
  margin-left: 12rpx;
  overflow: hidden;
  color: #303133;
  font-size: 28rpx;
}

.suggest-seg {
  flex: none;
  white-space: nowrap;
}

/* 尾段允许收缩并出省略号：超长商品名从词尾截断 */
.suggest-seg--tail {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 命中片段高亮：跟随商城主题色（与商品价签等主色口径一致，带兜底色） */
.suggest-hit {
  color: var(--wot-color-theme-primary, #ff2237);
}

.suggest-from {
  flex: none;
  margin-left: 12rpx;
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
  background: #f2f3f5;
  color: #8a97a6;
  font-size: 20rpx;
}

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
