<script setup lang="ts">
/**
 * 活动入口条（首页金刚区正下方一排等宽活动小卡，盒马式）。
 *
 * 数据（§5.5.3）：automatic 模式并行复用三个既有 loader，各取 1 条进行中活动；
 * 每类独立结算，单类失败/无活动只隐藏该卡，不阻断其余两卡，也不新增任何请求路径。
 *
 * 降级（§5.5.6）：
 * · 某类没有进行中活动 → 该卡不产出，剩余卡等宽重排；
 * · 三类全无 → 整条不渲染（默认 emptyStrategy=hide），不留空白占位；
 * · 单个 loader 失败 → 该卡隐藏，其余照常；
 * · 三类全部失败（网关/网络）→ 上抛给 invalidStrategy 兜底，与其它零售组件同协议；
 * · 远程图加载失败 → 首字色块兜底。
 *
 * 视觉（§5.5.5）：白底浅描边小卡、64rpx 缩略图、角标贴缩略图角、右侧淡箭头、整卡可点；
 * 活动名与缩略图同一行，钩子文案独占整卡宽的第二行（三张卡时也能完整显示）；
 * 卡片数由实际活动决定，不再配置列数/展示数量；颜色只走主题 token，多租户换肤自适应。
 */
import type { ActivityStripEntry, ActivityStripProps } from '@/components/diy/retail-types'

import { computed, ref } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import { loadDiscounts, loadLimitedActivities, loadSeckillSessions } from '@/components/diy/retail-data'
import RetailState from '@/components/diy/retail-state.vue'
import { retailCommonStyle } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useRetailData } from '@/composables/useRetailData'
import { resolveImageSrc } from '@/utils/image'

import {
  buildActivityStripEntries,
  resolveStripFallback,
  stripBadgeTone,
  stripGridColumns,
} from './activity-strip'

const props = withDefaults(defineProps<{ showData?: Partial<ActivityStripProps> }>(), {
  showData: () => ({}),
})

/** 卡片间距（rpx）：三列时给文案留足宽度，同时保持「一排小卡」的紧凑感 */
const GRID_GAP_RPX = 10

const showData = computed<ActivityStripProps>(() => ({
  commonStyle: props.showData.commonStyle || retailCommonStyle,
  dataSource: props.showData.dataSource || { mode: 'automatic' },
  // 与其它零售楼层不同：入口条没有活动时「整条不渲染」是默认行为（§5.5.6），
  // 运营显式配 placeholder 时才渲染空态文案。
  emptyStrategy: props.showData.emptyStrategy || 'hide',
  invalidStrategy: props.showData.invalidStrategy || 'hide',
}))
const dynamicStyles = useDiyStyle(computed(() => showData.value.commonStyle))

/** 三个内置 loader 只读 count/dataSource，其余字段给「无楼层头」的占位值即可 */
const builtinSourceBase = {
  commonStyle: retailCommonStyle,
  count: 1,
  dataSource: { mode: 'automatic' as const },
  emptyStrategy: 'hide' as const,
  invalidStrategy: 'hide' as const,
}

/**
 * 三类内置活动并行取数（各取 1 条进行中活动）。
 *
 * 用 `Promise.allSettled` 隔离单类失败：某一类接口超时/报错只让那张卡消失，
 * 其余两类照常渲染，页面不报错、不闪空；只有三类全部失败才上抛，
 * 交给 `useRetailData` 的 invalidStrategy 处理（与其它零售组件同一套 loading 协议）。
 */
async function loadActivityStripEntries(
  source: ActivityStripProps,
): Promise<ActivityStripEntry[]> {
  if (source.dataSource.mode === 'manual')
    return source.dataSource.entries || []

  const [seckill, groupBuy, discount] = await Promise.allSettled([
    loadSeckillSessions({
      ...builtinSourceBase,
      showCountdown: false,
      showProgress: false,
      subtitle: '',
      title: '',
    }),
    loadLimitedActivities({
      ...builtinSourceBase,
      layout: 'list',
      showCountdown: false,
      subtitle: '',
      title: '',
    }),
    loadDiscounts({
      ...builtinSourceBase,
      goodsLimit: 1,
      showCountdown: false,
      subtitle: '',
      title: '',
    }),
  ])

  if (seckill.status === 'rejected' && groupBuy.status === 'rejected' && discount.status === 'rejected')
    throw seckill.reason

  return buildActivityStripEntries({
    discount: discount.status === 'fulfilled' ? discount.value[0] ?? null : null,
    groupBuy: groupBuy.status === 'fulfilled' ? groupBuy.value[0] ?? null : null,
    seckill: seckill.status === 'fulfilled' ? seckill.value[0] ?? null : null,
  })
}

const { items, shouldRender, status } = useRetailData(showData, loadActivityStripEntries)

/** 远程图加载失败的卡下标：失败后回落首字色块，避免首页出现破图 */
const brokenImages = ref(new Set<number>())

const cards = computed(() =>
  items.value.map(entry => ({
    ...entry,
    fallback: resolveStripFallback(entry),
    tone: stripBadgeTone(entry.key),
  })),
)

const gridStyle = computed(() => ({
  gap: `${GRID_GAP_RPX}rpx`,
  // 卡数由实际活动/手配卡片决定，全部展示；一行最多三张，超出换行
  gridTemplateColumns: `repeat(${stripGridColumns(cards.value.length)}, minmax(0, 1fr))`,
}))

function onImageError(index: number) {
  brokenImages.value.add(index)
}
</script>

<template>
  <view v-if="shouldRender" class="activity-strip" :style="dynamicStyles">
    <view v-if="status === 'ready'" class="strip-grid" :style="gridStyle">
      <view
        v-for="(entry, index) in cards"
        :key="`${entry.key}-${index}`"
        class="strip-card"
        @click="followDecorationLink(entry.link)"
      >
        <view class="strip-main">
          <view class="strip-thumb">
            <image
              v-if="entry.imageUrl && !brokenImages.has(index)"
              class="strip-thumb__img"
              :src="resolveImageSrc(entry.imageUrl)"
              mode="aspectFill"
              lazy-load
              @error="onImageError(index)"
            />
            <view
              v-else
              class="strip-thumb__fallback"
              :style="{ background: entry.fallback.bgColor, color: entry.fallback.color }"
            >
              {{ entry.fallback.text }}
            </view>
            <view v-if="entry.badge" class="strip-badge" :class="`strip-badge--${entry.tone}`">
              {{ entry.badge }}
            </view>
          </view>
          <text class="strip-name">
            {{ entry.title }}
          </text>
        </view>
        <text v-if="entry.hook" class="strip-hook">
          {{ entry.hook }}
        </text>
        <view class="strip-arrow" />
      </view>
    </view>
    <RetailState v-else :status="status" />
  </view>
</template>

<!--
  视觉规格见需求文档 §5.5.5（750rpx 设计稿）：
  · 卡片等宽 1/卡数（一行最多三张）、白底、1rpx 浅描边 #F1F2F4、圆角 16rpx、内边距 12×16rpx、整卡可点；
  · 首行缩略图 64×64rpx 圆角 12rpx + 活动名，缺图用 resolveCategoryFallback 首字色块；
  · 活动名 24rpx/700 ink、钩子 20rpx/500 muted 独占第二行，单行省略；
  · 角标 18rpx/700 小胶囊贴缩略图角：秒杀主题渐变实心「抢」，拼团/折扣主题浅底「省¥X/X折」；
  · 右侧淡箭头与首行活动名垂直居中，无独立按钮、无大促渐变头；促销色只走 var(--wot-color-theme-*)。
-->
<style scoped lang="scss">
.activity-strip {
  display: block;
}

.strip-grid {
  display: grid;
  box-sizing: border-box;
  align-items: stretch;
}

.strip-card {
  position: relative;
  display: flex;
  box-sizing: border-box;
  min-width: 0;
  flex-direction: column;
  padding: 12rpx;
  gap: 8rpx;
  background: #fff;
  border: 1rpx solid #f1f2f4;
  border-radius: 16rpx;
}

/* 首行：缩略图 + 活动名；右侧给箭头留出 16rpx */
.strip-main {
  display: flex;
  min-width: 0;
  align-items: center;
  padding-right: 16rpx;
  gap: 8rpx;
}

.strip-thumb {
  position: relative;
  flex: 0 0 auto;
  width: 64rpx;
  height: 64rpx;
}

/* 小程序 <image> 基座自带 height:240px，必须显式给 rpx 宽高（同 diy-limited-activity 的坑） */
.strip-thumb__img {
  display: block;
  width: 64rpx;
  height: 64rpx;
  background: #f3f4f6;
  border-radius: 12rpx;
}

.strip-thumb__fallback {
  display: flex;
  width: 64rpx;
  height: 64rpx;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 1;
  border-radius: 12rpx;
}

.strip-name {
  display: block;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #1f2329;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 30rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 钩子文案独占第二行、通卡宽：三张卡时也能完整显示「午间场·整点开抢」 */
.strip-hook {
  display: block;
  overflow: hidden;
  color: #8a8f99;
  font-size: 20rpx;
  font-weight: 500;
  line-height: 24rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 角标：小胶囊贴在缩略图右下角并轻微出边，与原型图的「抢/省¥4/8折」同位 */
.strip-badge {
  position: absolute;
  right: -6rpx;
  bottom: -6rpx;
  display: flex;
  max-width: 120rpx;
  height: 28rpx;
  box-sizing: border-box;
  align-items: center;
  justify-content: center;
  padding: 0 8rpx;
  overflow: hidden;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
  border-radius: 999rpx;
}

/* 秒杀=强促销：主题渐变实心 */
.strip-badge--strong {
  color: #fff;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff6b7a), var(--wot-color-theme-primary, #ff2237));
}

/* 拼团/折扣=弱促销：主题浅底 + 主题字色，首页不铺三块实心大红 */
.strip-badge--soft {
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
}

/* 右侧淡箭头：与首行活动名垂直居中（12rpx + 64rpx/2 − 10rpx/2） */
.strip-arrow {
  position: absolute;
  top: 38rpx;
  right: 10rpx;
  width: 10rpx;
  height: 10rpx;
  border-top: 2rpx solid #c9cdd4;
  border-right: 2rpx solid #c9cdd4;
  transform: rotate(45deg);
}
</style>
