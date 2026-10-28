<script setup lang="ts">
/**
 * 商品搜索页（搜索历史 / 热门搜索 / 热门榜单 / 输入联想）。
 *
 * 视觉口径对齐会员中心与个人中心：浅灰底 #f6f7f9 + 白色圆角卡片 + 品牌色变量；
 * 榜单数据只来自真实行为（热搜=近 30 天浏览足迹、热销=近 90 天有效订单），
 * 页面只表达名次，不展示任何销量/浏览次数（口径见 src/utils/stock-display.ts）。
 *
 * 规格见 docs/50-需求文档/2026-10-25-搜索页与排行组件重构/README.md §3、§4.2。
 */
import type { RankGoodsItem } from '@/api/product/spu'
import type { SuggestItem } from '@/sub-pages/utils/search-suggest'
import { onLoad } from '@dcloudio/uni-app'
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getSalesRankGoods, getSuggestWords, getTop10HotSearchGoods } from '@/api/product/spu'
import HrSearchNavbar from '@/components/hr-search-navbar/index.vue'
import { useSearchHistory } from '@/sub-pages/composables/useSearchHistory'
import { mergeSuggestWords } from '@/sub-pages/utils/search-suggest'
import RankBoard from './components/rank-board.vue'

definePage({
  name: 'goods-search',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '商品搜索',
  },
})

/** 热门搜索词取热搜榜前几个商品名（与榜单同源，避免两套「热门」口径） */
const HOT_SEARCH_WORD_LIMIT = 8
/** 榜单展示上限（接口本身返回 Top10，端上再兜一层防超长列表） */
const RANK_LIST_LIMIT = 10
/**
 * 「热门搜索」加载占位胶囊的宽度类（8 个，两两成行 = 4 行）。
 *
 * 现有实现里热门搜索区要等榜单接口返回后才挂载，热缓存 reload 时会把下方
 * 「热门榜单」卡整体下推 228px（CLS≈0.22）。这里在加载期先占住与真实胶囊区
 * 等高的位置（同 chip 尺寸、4 行 × 88rpx），数据到达后只换内容不换高度。
 * 宽度写静态 scoped class 而非动态拼类名，避免 UnoCSS 提取不到。
 */
const HOT_CHIP_PLACEHOLDER_CLASSES = [
  'chip-ph--w1',
  'chip-ph--w2',
  'chip-ph--w3',
  'chip-ph--w1',
  'chip-ph--w2',
  'chip-ph--w3',
  'chip-ph--w4',
  'chip-ph--w2',
] as const

const { t } = useI18n()
const { confirm } = useGlobalMessage()
const router = useRouter()
const { historyList, addSearchHistory, clearSearchHistory } = useSearchHistory()

const keyword = ref('')
/** 接口返回的联想词（真实商品名）；失败/无匹配为空数组 */
const apiSuggestWords = ref<string[]>([])
const hotRankList = ref<RankGoodsItem[]>([])
const salesRankList = ref<RankGoodsItem[]>([])
const rankLoading = ref(true)
const suggestLoading = ref(false)

/** 是否处于「输入中」：有输入时用联想面板替换历史/热门/榜单 */
const showSuggest = computed(() => keyword.value.trim().length > 0)

/**
 * 联想词 = 接口词（真实在售商品名）+ 本地历史/热搜兜底，合并去重后展示。
 * 接口失败或无匹配时自然退化为纯本地联想，面板不白屏。
 */
const suggestList = computed<SuggestItem[]>(() => mergeSuggestWords(
  apiSuggestWords.value,
  keyword.value,
  historyList.value,
  hotRankList.value.map(item => item.name).filter(Boolean),
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

/** 热门搜索胶囊：热搜榜商品名去重后取前 8 个 */
const hotSearchWords = computed(() => {
  const seen = new Set<string>()
  const words: string[] = []
  for (const item of hotRankList.value) {
    const word = (item.name || '').trim()
    if (!word || seen.has(word))
      continue
    seen.add(word)
    words.push(word)
    if (words.length >= HOT_SEARCH_WORD_LIMIT)
      break
  }
  return words
})

/**
 * 热门搜索区显示条件：加载中显示占位（预留高度防 CLS），加载完有词才显示；
 * 加载完为空则整块移除（不渲染空胶囊区）。
 */
const showHotSearch = computed(() => rankLoading.value || hotSearchWords.value.length > 0)

// ---------------- 输入联想（250ms 防抖 + 竞态守卫） ----------------

let suggestSeq = 0
let suggestTimer: ReturnType<typeof setTimeout> | null = null

function cancelSuggestTimer() {
  if (suggestTimer) {
    clearTimeout(suggestTimer)
    suggestTimer = null
  }
}

watch(keyword, (rawKeyword) => {
  cancelSuggestTimer()
  const query = rawKeyword.trim()
  if (!query) {
    suggestSeq++
    apiSuggestWords.value = []
    suggestLoading.value = false
    return
  }
  // 等待防抖期间先出骨架，避免「没找到相关联想」在请求前闪一下
  suggestLoading.value = true
  // 250ms 防抖：停止输入后才请求，键入到出词体感约 300ms（对标小象）
  suggestTimer = setTimeout(() => {
    void fetchSuggestWords(query)
  }, 250)
})

async function fetchSuggestWords(query: string) {
  const seq = ++suggestSeq
  try {
    const words = await getSuggestWords(query)
    if (seq !== suggestSeq)
      return
    apiSuggestWords.value = Array.isArray(words) ? words : []
  }
  catch {
    // 静默降级：置空后由本地历史/热搜兜底出词，不打断搜索主流程
    if (seq !== suggestSeq)
      return
    apiSuggestWords.value = []
  }
  finally {
    // 只有最后一次请求能摘掉骨架；过期响应不许关掉新请求的 loading
    if (seq === suggestSeq)
      suggestLoading.value = false
  }
}

onUnmounted(() => {
  cancelSuggestTimer()
  suggestSeq++
})

// ---------------- 榜单数据 ----------------

/**
 * 榜单商品兜底归一化：接口约定返回 RankGoodsItem[]，这里仍过滤缺 id/name 的脏数据，
 * 并把可选字段收敛成字符串（后端换 VO 时也不会把坏形状直接渲染进模板）。
 */
function normalizeRankList(raw: unknown): RankGoodsItem[] {
  if (!Array.isArray(raw))
    return []
  const list: RankGoodsItem[] = []
  for (const row of raw) {
    if (!row || typeof row !== 'object')
      continue
    const record = row as Record<string, unknown>
    const id = record.id === null || record.id === undefined ? '' : String(record.id)
    const name = typeof record.name === 'string' ? record.name.trim() : ''
    if (!id || !name)
      continue
    list.push({
      id,
      name,
      subTitle: typeof record.subTitle === 'string' ? record.subTitle : '',
      picUrl: typeof record.picUrl === 'string' ? record.picUrl : '',
    })
    if (list.length >= RANK_LIST_LIMIT)
      break
  }
  return list
}

/**
 * 并行拉两条榜单：互不阻塞，任一失败只置空该榜（榜单空态），
 * 不抛到页面级 —— 历史/搜索/联想都不依赖榜单，页面不因接口故障白屏。
 */
async function loadRankData() {
  rankLoading.value = true
  const [hotResult, salesResult] = await Promise.allSettled([
    getTop10HotSearchGoods(),
    getSalesRankGoods(),
  ])
  hotRankList.value = hotResult.status === 'fulfilled' ? normalizeRankList(hotResult.value) : []
  salesRankList.value = salesResult.status === 'fulfilled' ? normalizeRankList(salesResult.value) : []
  rankLoading.value = false
}

// ---------------- 交互 ----------------

/** 连点节流窗口：导航是异步的，窗口内重复提交只会把 goods-list 重复压栈 */
const SEARCH_COMMIT_THROTTLE_MS = 600
let lastCommitAt = 0

/**
 * 提交搜索。
 *
 * 两条必须守住的交互约束（对应「点搜索先闪一屏猜你想搜、再出现商品」的问题）：
 *
 * 1. **提交时绝不把搜索词写回 `keyword`**。写回会触发 keyword 的 watch：
 *    默认面板立刻被替换成联想面板，同时再发一次联想请求；而目标页加载期间
 *    搜索页仍在屏上（小程序首次进入页面要编译/拉数据，可达数百毫秒），用户就会
 *    先看到一屏与结果无关的联想骨架、乃至「没找到相关联想」空态，然后才跳到结果页。
 *    从热门词 / 历史词 / 联想词进入时输入框保持原样，过渡帧与提交前一模一样。
 * 2. **提交后作废在途联想**：页面即将被结果页覆盖，迟到的联想响应不应再回写面板。
 *
 * 空关键词（含纯空格）不搜索。
 */
function doSearch(rawWord?: string) {
  const word = (typeof rawWord === 'string' ? rawWord : keyword.value).trim()
  if (!word)
    return
  const now = Date.now()
  if (now - lastCommitAt < SEARCH_COMMIT_THROTTLE_MS)
    return
  lastCommitAt = now
  cancelSuggestTimer()
  suggestSeq++
  suggestLoading.value = false
  addSearchHistory(word)
  router.push({
    name: 'goods-list',
    params: { keyword: word },
  })
}

function delAll() {
  confirm({
    title: t('search.clearConfirmTitle'),
    msg: t('search.clearConfirmMessage'),
    closeOnClickModal: false,
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    success: (res) => {
      if (res.action === 'confirm')
        clearSearchHistory()
    },
  })
}

function toGoods(id: string) {
  if (!id)
    return
  router.push({
    name: 'goods-detail',
    params: { id },
  })
}

/** 联想来源标签（历史/热搜）；商品名联想是主词源，不展示标签 */
function suggestSourceLabel(from: SuggestItem['from']) {
  if (from === 'goods')
    return ''
  return from === 'history' ? t('search.suggestFromHistory') : t('search.suggestFromHot')
}

onLoad((options) => {
  if (options?.keyword && options.keyword !== 'undefined')
    keyword.value = options.keyword
  void loadRankData()
})
</script>

<template>
  <view class="search-root">
    <hr-search-navbar
      v-model="keyword"
      :placeholder="t('search.inputPlaceholder')"
      :placeholder-left="true"
      :focus="true"
      @search="doSearch"
    />
    <view class="search-page">
      <!-- 输入联想：有输入时替换下方默认面板 -->
      <view v-if="showSuggest" class="search-card suggest-card">
        <view class="search-card__head">
          <text class="search-card__title">
            {{ t('search.suggestTitle') }}
          </text>
        </view>

        <!--
          直接搜索行：常驻面板首行，点击即用当前输入搜索。
          联想词为空时（典型：输入与在售商品名完全相同，联想接口按「同词不重复推荐」
          过滤掉）它就是面板唯一内容 —— 用可执行动作替代原先「没找到相关联想」的死空态。
        -->
        <view
          class="suggest-row suggest-row--direct"
          hover-class="suggest-row--pressed"
          :hover-stay-time="80"
          @click="doSearch(keyword)"
        >
          <view class="suggest-row__icon suggest-row__icon--direct">
            <text class="i-carbon:search" />
          </view>
          <view class="suggest-word">
            <text class="suggest-seg">
              {{ t('search.directSearchPrefix') }}
            </text>
            <text class="suggest-seg suggest-hit suggest-direct__word">
              {{ keyword.trim() }}
            </text>
            <text class="suggest-seg">
              {{ t('search.directSearchSuffix') }}
            </text>
          </view>
        </view>

        <!-- 请求中：直接搜索行下方的联想骨架 -->
        <view v-if="suggestLoading" class="suggest-block">
          <view v-for="n in 3" :key="n" class="suggest-skeleton__row">
            <view class="suggest-skeleton__icon skeleton-shimmer" />
            <view class="suggest-skeleton__line skeleton-shimmer" :style="{ width: `${66 - n * 12}%` }" />
          </view>
        </view>

        <!-- 有结果：搜索图标 + 命中片段高亮 + 来源标签 -->
        <view v-else-if="suggestRows.length > 0" class="suggest-block">
          <view
            v-for="(row, index) in suggestRows"
            :key="`${row.item.from}-${row.item.word}`"
            class="suggest-row"
            :class="{ 'suggest-row--divided': index > 0 }"
            hover-class="suggest-row--pressed"
            :hover-stay-time="80"
            @click="doSearch(row.item.word)"
          >
            <view class="suggest-row__icon">
              <text class="i-carbon:search" />
            </view>
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
            <text v-if="suggestSourceLabel(row.item.from)" class="suggest-from">
              {{ suggestSourceLabel(row.item.from) }}
            </text>
          </view>
        </view>
      </view>

      <template v-else>
        <!-- 搜索历史 + 热门搜索：同一张白卡内分区，发丝线分隔 -->
        <view class="search-card">
          <view class="search-card__head">
            <text class="search-card__title">
              {{ t('search.historyTitle') }}
            </text>
            <view
              v-if="historyList.length > 0"
              class="clear-history"
              hover-class="clear-history--pressed"
              @click="delAll"
            >
              <text class="i-carbon:trash-can clear-history__icon" />
              <text class="clear-history__text">
                {{ t('search.clear') }}
              </text>
            </view>
          </view>

          <view v-if="historyList.length > 0" class="chips">
            <view
              v-for="word in historyList"
              :key="word"
              class="chip-hit"
              @click="doSearch(word)"
            >
              <view class="chip" hover-class="chip--pressed" :hover-stay-time="80">
                <text class="chip__text">
                  {{ word }}
                </text>
              </view>
            </view>
          </view>
          <view v-else class="history-empty">
            {{ t('search.historyEmpty') }}
          </view>

          <template v-if="showHotSearch">
            <view class="card-divider" />
            <view class="search-card__head search-card__head--flat">
              <view class="hot-search__icon">
                <text class="i-carbon:fire" />
              </view>
              <text class="search-card__title">
                {{ t('search.hotSearches') }}
              </text>
            </view>
            <!-- 加载中：占位胶囊与真实胶囊同尺寸，热门搜索区从首帧就占住高度 -->
            <view v-if="rankLoading" class="chips">
              <view
                v-for="(placeholderClass, index) in HOT_CHIP_PLACEHOLDER_CLASSES"
                :key="index"
                class="chip-hit"
              >
                <view class="chip chip--placeholder skeleton-shimmer" :class="placeholderClass" />
              </view>
            </view>
            <view v-else class="chips">
              <view
                v-for="word in hotSearchWords"
                :key="word"
                class="chip-hit"
                @click="doSearch(word)"
              >
                <view class="chip" hover-class="chip--pressed" :hover-stay-time="80">
                  <text class="chip__text">
                    {{ word }}
                  </text>
                </view>
              </view>
            </view>
          </template>
        </view>

        <!-- 热门榜单：热搜榜 / 热销榜双 Tab -->
        <rank-board
          :hot-list="hotRankList"
          :sales-list="salesRankList"
          :loading="rankLoading"
          @select="toGoods"
        />
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$sp-primary: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$sp-title: #1f2329;
$sp-text: #4e5969;
/* 辅助信息文字统一 #6b7280（白底 4.83:1，满足 WCAG AA）；#8a94a6 仅 2.8:1 已弃用 */
$sp-muted: #6b7280;
/* 弱化色只给纯装饰元素（占位图标等）用 */
$sp-faint: #c0c6cf;
$sp-hairline: #f0f1f4;
$sp-fill: #f5f6f8;
$sp-shadow: 0 8rpx 24rpx rgba(31, 44, 65, 0.06);
$sp-gutter: 20rpx;

/*
 * 根节点铺满一屏浅灰底：导航栏是 fixed 的，自身占位在根节点内，
 * 这样短内容（空态/联想态）时页面底部不会露出一条别的底色，
 * 也不会因为按导航栏占位去扣 min-height 而出现伪滚动。
 */
.search-root {
  box-sizing: border-box;
  min-height: 100vh;
  background-color: #f6f7f9;
}

.search-page {
  box-sizing: border-box;
  padding: 20rpx $sp-gutter;
  padding-bottom: calc(24rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

/* ===================== 通用白卡 ===================== */

.search-card {
  box-sizing: border-box;
  margin-bottom: 20rpx;
  padding: 8rpx 28rpx 28rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: $sp-shadow;
}

.search-card:last-child {
  margin-bottom: 0;
}

.search-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 88rpx 行高同时承担卡头高度与「清空」的 44px 命中区 */
  min-height: 88rpx;
}

.search-card__head--flat {
  justify-content: flex-start;
  min-height: 80rpx;
}

.search-card__title {
  color: $sp-title;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.3;
}

/* ===================== 搜索历史 ===================== */

.clear-history {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding-left: 24rpx;
  color: $sp-muted;
  font-size: 24rpx;
  line-height: 1.3;
}

.clear-history--pressed {
  opacity: 0.6;
}

.clear-history__icon {
  margin-right: 6rpx;
  font-size: 26rpx;
}

.history-empty {
  padding: 2rpx 0 16rpx;
  color: $sp-muted;
  font-size: 24rpx;
  line-height: 1.5;
}

/* 胶囊换行：chip-hit 上下各 12rpx 命中区把 64rpx 胶囊补到 88rpx（44px） */
.chips {
  display: flex;
  flex-wrap: wrap;
  margin: -12rpx -8rpx 0;
}

.chip-hit {
  box-sizing: border-box;
  max-width: 100%;
  padding: 12rpx 8rpx;
}

.chip {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  max-width: 100%;
  height: 64rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: $sp-fill;
  color: $sp-text;
  font-size: 26rpx;
  line-height: 1.2;
  transition: background-color 160ms ease-out;
}

.chip--pressed {
  background: #eceef1;
}

/*
 * 热门搜索占位胶囊：尺寸与真实胶囊一致（64rpx 高、行内 12rpx 命中 padding → 88rpx 行高），
 * 只换成 shimmer 灰块，保证加载完成时只替换内容、不改变热门搜索区高度。
 */
.chip--placeholder {
  background: $sp-fill;
}

.chip-ph--w1 {
  width: 300rpx;
}

.chip-ph--w2 {
  width: 270rpx;
}

.chip-ph--w3 {
  width: 280rpx;
}

.chip-ph--w4 {
  width: 290rpx;
}

.chip__text {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 卡内分区发丝线 */
.card-divider {
  height: 1rpx;
  margin-top: 18rpx;
  background: $sp-hairline;
}

/* ===================== 热门搜索 ===================== */

/*
 * 图标色必须给「容器」而不是图标元素本身：UnoCSS 图标类的 color:inherit
 * 与组件 scoped 类同特异性且注入更晚，直接写在 <text class="i-carbon:*"> 上会被盖掉。
 */
.hot-search__icon {
  display: flex;
  align-items: center;
  margin-right: 8rpx;
  color: $sp-primary;
  font-size: 30rpx;
}

/* ===================== 输入联想 ===================== */

.suggest-card {
  margin-bottom: 0;
}

.suggest-row {
  position: relative;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  /* 96rpx = 48px 触控行高 */
  min-height: 96rpx;
  border-radius: 12rpx;
  transition: background-color 160ms ease-out;
}

/* 发丝线用伪元素：行有圆角，border-top 会跟着圆角两端翘起 */
.suggest-row--divided::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 1rpx;
  background: $sp-hairline;
}

.suggest-row--pressed {
  background-color: #f7f8fa;
}

/* H5 按下即时反馈（小程序走 hover-class） */
.suggest-row:active {
  background-color: #f7f8fa;
}

/* 纯装饰图标用弱化色，不承载信息；颜色给容器，图标元素只继承 */
.suggest-row__icon {
  display: flex;
  flex: none;
  align-items: center;
  margin-right: 16rpx;
  color: $sp-faint;
  font-size: 30rpx;
}

.suggest-word {
  display: flex;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: $sp-title;
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
  color: $sp-primary;
  font-weight: 600;
}

/*
 * 来源标签：历史/热搜。白底描边款，文字用统一辅助色 #6b7280：
 * 灰底（#f2f3f5）上 #6b7280 只有 4.33:1，白底可达 4.83:1（AA）；
 * 字号 24rpx = 12px，不出现 11.44px 小字。
 */
.suggest-from {
  flex: none;
  margin-left: 16rpx;
  padding: 2rpx 12rpx;
  border: 1rpx solid $sp-hairline;
  border-radius: 999rpx;
  background: #fff;
  color: $sp-muted;
  font-size: 24rpx;
  line-height: 1.3;
}

/* ===================== 直接搜索行（面板首行常驻） ===================== */

/*
 * 直接搜索行用品牌色图标与品牌色关键词，明确「这是提交动作」而非又一条联想词；
 * 行高沿用 96rpx 触控行，与联想行保持同一节奏。
 */
.suggest-row--direct .suggest-row__icon--direct {
  color: $sp-primary;
}

/* 关键词允许收缩省略：超长商品名从词尾截断，右侧「」保持可见 */
.suggest-direct__word {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 直接搜索行与下方联想列表/骨架之间的发丝线 */
.suggest-block {
  margin-top: 6rpx;
  border-top: 1rpx solid $sp-hairline;
}

/* 联想骨架 */
.suggest-skeleton__row {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  min-height: 96rpx;
}

.suggest-skeleton__icon {
  width: 30rpx;
  height: 30rpx;
  flex: none;
  margin-right: 16rpx;
  border-radius: 50%;
}

.suggest-skeleton__line {
  height: 28rpx;
  border-radius: 8rpx;
}

/* 柔和 shimmer：1.2s 循环，与榜单骨架同语言 */
.skeleton-shimmer {
  background: linear-gradient(90deg, #f2f3f5 25%, #e9ebef 37%, #f2f3f5 63%);
  background-size: 400% 100%;
  animation: search-shimmer 1.2s ease infinite;
}

@keyframes search-shimmer {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chip,
  .suggest-row,
  .skeleton-shimmer {
    transition: none;
    animation: none;
  }
}
</style>
