<script setup lang="ts">
import type { GoodsRecommendProps } from '@/components/diy/retail-types'
import type { ListZonePrice } from '@/utils/zone-price'

import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { loadGoodsRecommend } from '@/components/diy/retail-data'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { useZonePrices } from '@/composables/useZonePrices'
import { zonePriceBadgeText } from '@/utils/zone-price'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
  /** 当前商品 ID（商详页装修上下文，automatic 模式按其分类取数） */
  goodsId: {
    type: String,
    default: '',
  },
})

const { t } = useI18n()
const componentProps = computed(() => props.showData as unknown as GoodsRecommendProps)
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

const goodsList = ref<any[]>([])
const loading = ref(false)

/**
 * 列表「专区价」批量补价句柄：会员价/出清价都不在商品列表接口里，
 * 列表先渲染、价格后补，失败静默（见 utils/zone-price 与 composables/useZonePrices）。
 */
const zone = useZonePrices()

/** 本轮推荐商品补专区价：不 await，避免价格接口阻塞商详主内容 */
function loadZonePrices(items: any[]) {
  const ids = items.map(item => String(item?.id || '').trim()).filter(Boolean)
  if (!ids.length) return
  void zone.loadForSpuIds(ids)
}

async function load() {
  loading.value = true
  try {
    goodsList.value = await loadGoodsRecommend(componentProps.value, props.goodsId || undefined)
    loadZonePrices(goodsList.value)
  }
  catch {
    // 推荐位加载失败静默隐藏，不阻塞商详主内容
    goodsList.value = []
  }
  finally {
    loading.value = false
  }
}

// goodsId 变化（切换商品）时重取；同商品命中 loadGoodsRecommend 内的 TTL 缓存
watch(
  () => [componentProps.value, props.goodsId] as const,
  () => load(),
  { immediate: true, deep: true },
)

function openGoods(id: string) {
  uni.navigateTo({ url: `/sub-pages/product/goods-detail/index?id=${id}` })
}

/**
 * 卡片专区价：会员价/捡漏价命中时切换主价与划线，未命中回落普通价与未达标引导，
 * 统一走共享口径 `utils/zone-price#resolveListZonePrice`（与会员专区/低价捡漏一致）。
 * 划线受装修「显示原价」开关约束，主价/角标不受影响。
 */
function cardDisplay(item: any): ListZonePrice {
  return zone.cardPriceOf(item, componentProps.value.showOriginalPrice)
}

function cardMainPrice(item: any): number {
  return cardDisplay(item).mainPrice
}

function cardStrikePrice(item: any): number | null {
  return cardDisplay(item).strikePrice
}

function cardBadgeText(item: any): string {
  return zonePriceBadgeText(cardDisplay(item).badgeKind)
}

function cardBadgeClass(item: any): string {
  return cardDisplay(item).badgeKind === 'member'
    ? 'gr-price-badge--member'
    : 'gr-price-badge--clearance'
}

function cardGuideText(item: any): string {
  return cardDisplay(item).guideText || ''
}
</script>

<template>
  <view v-if="goodsList.length > 0" class="gr-box" :style="dynamicStyles">
    <view class="gr-inner">
      <view v-if="componentProps.title" class="gr-head">
        <view class="gr-bar" />
        <text class="gr-title">
          {{ componentProps.title }}
        </text>
      </view>
      <scroll-view class="gr-scroll" scroll-x :scroll-with-animation="true">
        <view class="gr-track">
          <view
            v-for="item in goodsList"
            :key="item.id"
            class="gr-card"
            @click="openGoods(item.id)"
          >
            <image
              v-if="item.imageUrl"
              class="gr-pic"
              :src="resolveImageSrc(item.imageUrl)"
              mode="aspectFill"
              lazy-load
            />
            <view v-else class="gr-pic gr-pic--empty">
              {{ t('diy.common.product') }}
            </view>
            <view class="gr-name">
              {{ item.name }}
            </view>
            <view v-if="shouldShowSubtitle(item.name, item.subTitle)" class="gr-subtitle">
              {{ item.subTitle }}
            </view>
            <view class="gr-price-line">
              <text v-if="componentProps.showPrice !== false" class="gr-price">
                <!-- 角标：会员价 / 捡漏价；主价与划线均由共享专区价口径给出 -->
                <text
                  v-if="cardBadgeText(item)"
                  class="gr-price-badge"
                  :class="cardBadgeClass(item)"
                >{{ cardBadgeText(item) }}</text>
                <text class="gr-price-symbol">
                  ¥
                </text>{{ cardMainPrice(item).toFixed(2) }}
              </text>
              <!-- 划线价：会员价/捡漏价命中划当前售价，普通价仍是「原价 > 售价才划线」 -->
              <text
                v-if="cardStrikePrice(item) !== null"
                class="gr-price-original"
              >
                ¥{{ Number(cardStrikePrice(item)).toFixed(2) }}
              </text>
            </view>
            <!-- 未达标/未登录的会员价引导：只透出「会员专享 ¥X」，主价不动 -->
            <view v-if="cardGuideText(item)" class="gr-member-guide">
              {{ cardGuideText(item) }}
            </view>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 留白放在内层而不是根节点：根节点的 padding 会被装修 commonStyle 的
   内联样式覆盖成 0（配置缺省即零值），挂根上会让卡片直接顶到屏幕边缘 */
.gr-inner {
  padding: 20rpx;
}

.gr-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.gr-bar {
  width: 8rpx;
  height: 28rpx;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 4rpx;
}

.gr-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #303133;
}

/* 横滑轨道：卡片 200rpx 宽、圆角 16rpx，与管理端预览（100px/8px）同源 */
.gr-scroll {
  width: 100%;
  white-space: nowrap;
}

.gr-track {
  display: flex;
  gap: 16rpx;
}

.gr-card {
  flex: 0 0 200rpx;
  width: 200rpx;
  overflow: hidden;
  background: #fff;
  border: 1rpx solid #eef0f3;
  border-radius: 16rpx;
}

.gr-pic {
  width: 200rpx;
  height: 200rpx;
  display: block;
}

.gr-pic--empty {
  display: grid;
  place-items: center;
  background: #f2f3f5;
  color: #c0c4cc;
  font-size: 24rpx;
}

.gr-name {
  padding: 12rpx 12rpx 0;
  font-size: 26rpx;
  color: #303133;
  line-height: 1.4;
  height: 74rpx;
  display: -webkit-box;
  overflow: hidden;
  /* 复位横滑轨道的 nowrap（.gr-scroll），否则商品名不换行会被卡片硬切成半截 */
  white-space: normal;
  word-break: normal;
  overflow-wrap: break-word;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.gr-subtitle {
  padding: 2rpx 12rpx 0;
  overflow: hidden;
  color: #909399;
  font-size: 20rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gr-price-line {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  padding: 4rpx 12rpx 12rpx;
}

.gr-price {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 30rpx;
  font-weight: 600;
}

.gr-price-symbol {
  font-size: 22rpx;
}

/* 专区价角标：会员价走浅金、捡漏价走主题红，小号标签不抢价格层级 */
.gr-price-badge {
  margin-right: 4rpx;
  padding: 0 6rpx;
  border-radius: 4rpx;
  font-size: 18rpx;
  font-weight: 600;
  line-height: 1.5;
}

.gr-price-badge--member {
  border: 1rpx solid #ecd4a5;
  background: #fffaf0;
  color: #a06a1f;
}

.gr-price-badge--clearance {
  background: var(--wot-color-theme-primary, #ff2237);
  color: #fff;
}

/* 划线原价：灰字小一号，仅作价格锚点 */
.gr-price-original {
  flex: none;
  color: #999;
  font-size: 22rpx;
  font-weight: normal;
  text-decoration: line-through;
}

/* 会员价引导：未达标/未登录时的一行小字，不改变主价与划线口径 */
.gr-member-guide {
  padding: 0 12rpx 12rpx;
  color: #a06a1f;
  font-size: 20rpx;
  line-height: 1.4;
}
</style>
