<script setup lang="ts">
/**
 * 热门榜单卡（搜索页）。
 *
 * 把原「销量排行 / 热搜榜」两张 86% 宽横向滑动卡合并成一张固定卡：
 *  · 热搜榜（真实浏览足迹，近 30 天）为默认 Tab，热销榜（真实有效订单，近 90 天）副 Tab；
 *  · 前三名用大图 + 品牌色名次徽章强化，4–10 名为紧凑行；
 *  · 只表达「名次」，不展示任何销量/浏览次数（口径见 src/utils/stock-display.ts）。
 *
 * 数据口径与接口契约见 docs/50-需求文档/2026-10-25-搜索页与排行组件重构/README.md §2、§3。
 */
import type { RankGoodsItem } from '@/api/product/spu'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

interface Props {
  hotList?: RankGoodsItem[]
  salesList?: RankGoodsItem[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  hotList: () => [],
  salesList: () => [],
  loading: false,
})

const emit = defineEmits<{
  (e: 'select', id: string): void
}>()

type RankTab = 'hot' | 'sales'

const { t } = useI18n()

const TABS: Array<{ key: RankTab, labelKey: string }> = [
  { key: 'hot', labelKey: 'search.rankHot' },
  { key: 'sales', labelKey: 'search.rankSales' },
]

const activeTab = ref<RankTab>('hot')
/** 用户手动切过 Tab 后，数据到达不再改写其选择（默认态兜底只做一次） */
const userSwitched = ref(false)

watch(
  [() => props.hotList.length, () => props.salesList.length],
  ([hotCount, salesCount]) => {
    if (userSwitched.value)
      return
    // 热搜榜为空而热销榜有数据时默认落到热销榜，避免首屏是一张空卡
    activeTab.value = hotCount === 0 && salesCount > 0 ? 'sales' : 'hot'
  },
  { immediate: true },
)

const currentList = computed(() => (activeTab.value === 'hot' ? props.hotList : props.salesList))
const isEmpty = computed(() => !props.loading && currentList.value.length === 0)

/** 当前 Tab 的空态说明：区分「没人浏览」与「没有有效成交」，不共用一句含糊文案 */
const emptyDescription = computed(() =>
  activeTab.value === 'hot'
    ? t('search.rankHotEmpty')
    : t('search.rankSalesEmpty'),
)

function switchTab(tab: RankTab) {
  if (activeTab.value === tab)
    return
  activeTab.value = tab
  userSwitched.value = true
}

const mallThemeStore = useMallThemeStore()

/**
 * #2/#3 的「品牌浅底」按主题色实时调淡（12% 透明度叠白）。
 * 管理端可换肤，不能写死品牌红/橙的浅色值，用具名色再 JS 调淡才能跟随租户。
 */
const brandTintStyle = computed(() => {
  const hex = /^#([0-9a-f]{6})$/i.exec((mallThemeStore.primaryColor || '').trim())?.[1]
    ?? '#FF2237'
  const value = Number.parseInt(hex, 16)
  const r = (value >> 16) & 0xFF
  const g = (value >> 8) & 0xFF
  const b = value & 0xFF
  return { backgroundColor: `rgba(${r}, ${g}, ${b}, 0.12)` }
})

/** 图片加载失败的商品 id：失败后落灰色占位，不渲染破图边框 */
const failedImageIds = ref<Set<string>>(new Set())

function handleImageError(id: string) {
  failedImageIds.value.add(id)
}

function showImage(item: RankGoodsItem) {
  return !!item.picUrl && !failedImageIds.value.has(item.id)
}
</script>

<template>
  <view class="rank-board">
    <!-- 卡头 -->
    <view class="rank-board__head">
      <view class="rank-board__bar" />
      <text class="rank-board__title">
        {{ t('search.rankTitle') }}
      </text>
      <text class="rank-board__subtitle">
        {{ t('search.rankSubtitle') }}
      </text>
    </view>

    <!-- 分段 Tab：等宽胶囊，激活态品牌渐变 -->
    <view class="rank-tabs">
      <view
        v-for="tab in TABS"
        :key="tab.key"
        class="rank-tab"
        :class="{ 'rank-tab--active': activeTab === tab.key }"
        hover-class="rank-tab--pressed"
        :hover-stay-time="80"
        @click="switchTab(tab.key)"
      >
        {{ t(tab.labelKey) }}
      </view>
    </view>

    <!-- 骨架屏：6 行，首 3 行按前三名高度 -->
    <view v-if="props.loading" class="rank-skeleton">
      <view
        v-for="n in 6"
        :key="n"
        class="rank-skeleton__row"
        :class="{ 'rank-skeleton__row--top': n <= 3 }"
      >
        <view class="rank-skeleton__badge skeleton-shimmer" />
        <view
          class="rank-skeleton__pic skeleton-shimmer"
          :class="{ 'rank-skeleton__pic--top': n <= 3 }"
        />
        <view class="rank-skeleton__lines">
          <view class="rank-skeleton__line skeleton-shimmer" />
          <view class="rank-skeleton__line rank-skeleton__line--short skeleton-shimmer" />
        </view>
      </view>
    </view>

    <!-- 空态：接口失败也走这里，不打断页面其余部分 -->
    <view v-else-if="isEmpty" class="rank-empty">
      <view class="rank-empty__icon">
        <text class="i-carbon:trophy rank-empty__glyph" />
      </view>
      <text class="rank-empty__title">
        {{ t('search.rankEmpty') }}
      </text>
      <text class="rank-empty__desc">
        {{ emptyDescription }}
      </text>
    </view>

    <!-- 榜单行 -->
    <view v-else class="rank-list">
      <view
        v-for="(item, index) in currentList"
        :key="item.id"
        class="rank-row"
        :class="{ 'rank-row--top': index < 3, 'rank-row--divided': index > 0 }"
        hover-class="rank-row--pressed"
        :hover-stay-time="80"
        @click="emit('select', item.id)"
      >
        <!-- 名次：前三名徽章，4–10 名灰色序号 -->
        <view
          v-if="index < 3"
          class="rank-badge"
          :class="`rank-badge--${index + 1}`"
          :style="index > 0 ? brandTintStyle : undefined"
        >
          {{ index + 1 }}
        </view>
        <text v-else class="rank-index">
          {{ index + 1 }}
        </text>

        <!-- 图片：lazy-load，缺失或加载失败落灰色占位 -->
        <image
          v-if="showImage(item)"
          class="rank-pic"
          :class="{ 'rank-pic--top': index < 3 }"
          :src="item.picUrl"
          mode="aspectFill"
          lazy-load
          @error="handleImageError(item.id)"
        />
        <view
          v-else
          class="rank-pic rank-pic--empty"
          :class="{ 'rank-pic--top': index < 3 }"
        >
          <text class="i-carbon:image rank-pic__glyph" />
        </view>

        <view class="rank-body">
          <text class="rank-name">
            {{ item.name }}
          </text>
          <text v-if="item.subTitle" class="rank-subtitle">
            {{ item.subTitle }}
          </text>
        </view>
        <view class="rank-arrow">
          <text class="i-carbon:chevron-right" />
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$rb-primary: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
$rb-secondary: var(--theme-color-secondary, var(--wot-color-theme-secondary, #FF6B7A));
$rb-title: #1f2329;
/* 辅助信息文字统一 #6b7280：白底 4.83:1，满足 WCAG AA；#8a94a6 仅 2.8:1 已弃用 */
$rb-muted: #6b7280;
/* 弱化色只给纯装饰元素（chevron / 占位图标）用 */
$rb-faint: #c0c6cf;
$rb-hairline: #f0f1f4;
$rb-fill: #f5f6f8;
$rb-skeleton-base: #f2f3f5;
$rb-skeleton-wave: #e9ebef;

.rank-board {
  box-sizing: border-box;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgba(31, 44, 65, 0.06);
}

/* ===================== 卡头 ===================== */

.rank-board__head {
  display: flex;
  align-items: center;
  min-width: 0;
}

.rank-board__bar {
  width: 6rpx;
  height: 30rpx;
  flex: none;
  margin-right: 14rpx;
  border-radius: 999rpx;
  background: linear-gradient(180deg, $rb-primary 0%, $rb-secondary 100%);
}

.rank-board__title {
  flex: none;
  color: $rb-title;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.3;
}

.rank-board__subtitle {
  flex: 1;
  min-width: 0;
  margin-left: 14rpx;
  overflow: hidden;
  color: $rb-muted;
  /* 24rpx = 12px：最小正文尺寸，避免 22rpx 在 390 宽下只有 11.44px */
  font-size: 24rpx;
  line-height: 1.3;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ===================== 分段 Tab ===================== */

.rank-tabs {
  display: flex;
  gap: 16rpx;
  margin-top: 22rpx;
}

.rank-tab {
  box-sizing: border-box;
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  /* 88rpx = 44px，可点区域满足触控下限 */
  min-height: 88rpx;
  border-radius: 999rpx;
  background: $rb-fill;
  color: #4e5969;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 1.3;
  transition: background 200ms ease-out, color 200ms ease-out, box-shadow 200ms ease-out;
}

.rank-tab--active {
  background: linear-gradient(135deg, $rb-primary 0%, $rb-secondary 100%);
  color: #fff;
  box-shadow: 0 8rpx 20rpx rgba(31, 44, 65, 0.14);
}

.rank-tab--pressed {
  transform: scale(0.98);
}

/* ===================== 榜单行 ===================== */

.rank-list {
  margin-top: 8rpx;
}

.rank-row {
  position: relative;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  min-height: 96rpx;
  /* 只改背景不改尺寸：按压不引起布局抖动 */
  border-radius: 12rpx;
  transition: background-color 160ms ease-out;
}

.rank-row--top {
  min-height: 112rpx;
}

/*
 * 发丝线用伪元素画在行顶部，不用 border-top：
 * 行本身有 12rpx 圆角，border 会跟着圆角左右翘起，分隔线两端看起来是弯的。
 * 第一行没有 --divided，最后一行天然也没有下一条线。
 */
.rank-row--divided::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 1rpx;
  background: $rb-hairline;
}

.rank-row--pressed {
  background-color: #f7f8fa;
}

/* H5 按下即时反馈（小程序走 hover-class） */
.rank-row:active {
  background-color: #f7f8fa;
}

/* 名次徽章（前三） */
.rank-badge {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 40rpx;
  flex: none;
  margin-right: 20rpx;
  border-radius: 12rpx;
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1;
}

.rank-badge--1 {
  background: linear-gradient(135deg, $rb-primary 0%, $rb-secondary 100%);
}

.rank-badge--2,
.rank-badge--3 {
  /* 浅底由 brandTintStyle 按主题色实时调淡，文字跟随主题色 */
  color: $rb-primary;
}

/* 4–10 名灰色序号：宽度与徽章对齐，行首不跳变 */
.rank-index {
  width: 40rpx;
  flex: none;
  margin-right: 20rpx;
  color: $rb-muted;
  font-size: 26rpx;
  font-weight: 600;
  line-height: 1;
  text-align: center;
}

/* 图片 */
.rank-pic {
  width: 64rpx;
  height: 64rpx;
  flex: none;
  border-radius: 12rpx;
  background-color: $rb-fill;
}

.rank-pic--top {
  width: 96rpx;
  height: 96rpx;
  border-radius: 16rpx;
}

.rank-pic--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #b6bec9;
}

.rank-pic__glyph {
  font-size: 36rpx;
}

.rank-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  margin-left: 20rpx;
}

.rank-name {
  overflow: hidden;
  color: $rb-title;
  font-size: 28rpx;
  font-weight: 500;
  line-height: 1.35;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.rank-subtitle {
  margin-top: 4rpx;
  overflow: hidden;
  color: $rb-muted;
  /* 24rpx = 12px：最小正文尺寸，避免 22rpx 在 390 宽下只有 11.44px */
  font-size: 24rpx;
  line-height: 1.3;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/*
 * 图标色给容器而不是图标元素：UnoCSS 图标类的 color:inherit 与 scoped 类
 * 同特异性且注入更晚，直接写在 <text class="i-carbon:*"> 上会被盖成继承色。
 */
.rank-arrow {
  display: flex;
  flex: none;
  align-items: center;
  margin-left: 12rpx;
  color: $rb-faint;
  font-size: 28rpx;
}

/* ===================== 空态 ===================== */

.rank-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 52rpx 0 28rpx;
}

.rank-empty__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: $rb-fill;
  color: #b6bec9;
}

.rank-empty__glyph {
  font-size: 52rpx;
}

.rank-empty__title {
  margin-top: 20rpx;
  color: #4e5969;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1.3;
}

.rank-empty__desc {
  margin-top: 8rpx;
  color: #6b7280;
  font-size: 24rpx;
  line-height: 1.4;
  text-align: center;
}

/* ===================== 骨架 ===================== */

.rank-skeleton {
  margin-top: 8rpx;
}

.rank-skeleton__row {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  min-height: 96rpx;
}

.rank-skeleton__row--top {
  min-height: 112rpx;
}

.rank-skeleton__badge {
  width: 40rpx;
  height: 40rpx;
  flex: none;
  margin-right: 20rpx;
  border-radius: 12rpx;
}

.rank-skeleton__pic {
  width: 64rpx;
  height: 64rpx;
  flex: none;
  border-radius: 12rpx;
}

.rank-skeleton__pic--top {
  width: 96rpx;
  height: 96rpx;
  border-radius: 16rpx;
}

.rank-skeleton__lines {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  margin-left: 20rpx;
}

.rank-skeleton__line {
  width: 54%;
  height: 26rpx;
  border-radius: 8rpx;
}

.rank-skeleton__line--short {
  width: 34%;
  height: 20rpx;
  margin-top: 10rpx;
}

/* 柔和 shimmer：1.2s 循环，与会员中心骨架同语言 */
.skeleton-shimmer {
  background: linear-gradient(90deg, $rb-skeleton-base 25%, $rb-skeleton-wave 37%, $rb-skeleton-base 63%);
  background-size: 400% 100%;
  animation: rank-shimmer 1.2s ease infinite;
}

@keyframes rank-shimmer {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}

/* 尊重系统「减少动态效果」：关掉 shimmer 与过渡 */
@media (prefers-reduced-motion: reduce) {
  .rank-tab,
  .rank-row,
  .skeleton-shimmer {
    transition: none;
    animation: none;
  }
}
</style>
