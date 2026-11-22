<script setup lang="ts">
import type { RetailGoodsItem } from '../../common/retail-preview/retail-data';
import type {
  GoodsTabConfig,
  GoodsTabsProps,
  GoodsWaterfallProps,
} from '../types';

import { computed, ref, watch } from 'vue';

import { Picture, ShoppingCart } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { shouldShowOriginalPrice } from '../../common/retail-preview/price-display';
import { loadGoodsWaterfall } from '../../common/retail-preview/retail-data';
import RetailPreviewFrame from '../../common/retail-preview/retail-preview-frame.vue';
import { stockHintText } from '../../common/retail-preview/stock-display';
import { shouldShowSubtitle } from '../../common/retail-preview/subtitle-display';
import { createRetailPreviewController } from '../../common/retail-preview/use-retail-preview';
import { validateGoodsTabs } from '../types';

const props = defineProps<{ showData: GoodsTabsProps }>();
const controller = createRetailPreviewController<RetailGoodsItem>();
const { state } = controller;
const activeIndex = ref(0);

const tabs = computed<GoodsTabConfig[]>(() => props.showData.tabs ?? []);
const activeTab = computed(
  () => tabs.value[activeIndex.value] || tabs.value[0],
);

/** 活动轮播卡：与 C 端同口径，只认 enabled 且有有效图 */
const feedCard = computed(() =>
  activeTab.value?.feedCard?.enabled === true ? activeTab.value.feedCard : null,
);
const feedItems = computed(() =>
  (feedCard.value?.items ?? []).filter((item) => Boolean(item.imageUrl)),
);

/** 会员权益条开关：只控制横幅渲染，不控制会员价（与 C 端口径一致） */
const memberBannerEnabled = computed(
  () => activeTab.value?.memberBanner?.enabled === true,
);
/** 会员专区 Tab：专区选品=会员标签，或旧配置开启权益条（预览无登录态，仅透出样式） */
const isMemberZone = computed(
  () =>
    activeTab.value?.dataSource.sort === 'member_price' ||
    memberBannerEnabled.value,
);
/** 出清/捡漏 Tab（含旧版 bargain）：价格主导样式；会员价优先，两者不同时出 */
const isClearanceTab = computed(
  () =>
    activeTab.value?.dataSource.sort === 'clearance' ||
    activeTab.value?.dataSource.sort === 'bargain',
);

/**
 * 会员权益条文案：编辑器预览按「会员」态展示，自定义文案里的占位符用样例值替换。
 * C 端按真实等级/折扣渲染会员/游客三态，预览无登录态，只负责把配置文案与样式透出。
 */
const memberBannerText = computed(() => {
  const custom = (activeTab.value?.memberBanner?.memberCopy ?? '').trim();
  if (!custom) return '您已是会员，本区享会员专享价';
  return custom
    .replaceAll(/\{levelName\}|\{等级\}/g, '会员')
    .replaceAll(/\{discount\}|\{折扣\}/g, '9.5')
    .replaceAll(/会员\s*会员/g, '会员')
    .replaceAll(/\s+/g, ' ')
    .trim();
});

watch(
  () => props.showData.tabs,
  () => {
    if (activeIndex.value >= tabs.value.length) activeIndex.value = 0;
  },
  { deep: true },
);

watch(
  [() => props.showData, activeIndex],
  ([showData]) => {
    const tab = activeTab.value;
    controller.load(validateGoodsTabs(showData), () =>
      tab
        ? loadGoodsWaterfall({
            columns: showData.columns,
            commonStyle: showData.commonStyle,
            count: showData.count,
            dataSource: tab.dataSource,
            showOriginalPrice: showData.showOriginalPrice,
            showPrice: showData.showPrice,
            showSales: showData.showSales,
            title: tab.title,
          } as GoodsWaterfallProps)
        : Promise.resolve([]),
    );
  },
  { deep: true, immediate: true },
);

/**
 * 卡片信息 chip：与 C 端 diy-goods-tabs 的 cardTags 同口径。
 * 规格与库存提示都只在确有内容时出现（库存充足不外露数字）。
 */
function cardTags(item: RetailGoodsItem): string[] {
  const tags = item.specsInfo ? [item.specsInfo] : [];
  const hint = stockHintText(item.stock);
  if (hint) tags.push(hint);
  return tags;
}

/**
 * 会员专区（专区选品=会员标签，或旧配置开启权益条）：与 C 端一致，会员本人按会员价样式呈现专区成交价。
 * 预览不查询会员成交价，只覆盖「专区成交价」路径；实际成交数字以实机为准。
 */
function showMemberPrice(item: RetailGoodsItem): boolean {
  return isMemberZone.value && shouldShowOriginalPrice(item);
}

/** 出清/捡漏 Tab（含旧版 bargain）：价格主导样式；会员价优先，两者不同时出 */
function showBargainPrice(item: RetailGoodsItem): boolean {
  if (!isClearanceTab.value || showMemberPrice(item)) return false;
  return shouldShowOriginalPrice(item);
}

/** 捡漏价整数/小数拆段，与 C 端 bargainPriceParts 同口径 */
function priceParts(item: RetailGoodsItem): {
  decimal: string;
  integer: string;
} {
  const [integer = '0', decimal = '00'] = item.price.toFixed(2).split('.');
  return { decimal, integer };
}
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    type-label="多Tab商品流"
  >
    <div v-if="tabs.length > 1" class="goods-tabs-bar">
      <div
        v-for="(tab, index) in tabs"
        :key="tab.id"
        class="goods-tabs-tab"
        :class="{ 'goods-tabs-tab--active': index === activeIndex }"
        @click="activeIndex = index"
      >
        <span class="goods-tabs-label">{{ tab.title }}</span>
        <span v-if="index === activeIndex" class="goods-tabs-indicator"></span>
      </div>
    </div>

    <!-- 会员权益条：与 C 端 goods-tabs__member-banner 同款金色会员态 -->
    <div v-if="memberBannerEnabled" class="goods-tabs-member-banner">
      <span class="goods-tabs-member-banner-tag">会员</span>
      <span class="goods-tabs-member-banner-text">{{ memberBannerText }}</span>
    </div>

    <div class="goods-tabs-inner">
      <div
        class="goods-tabs-grid"
        :style="{
          gridTemplateColumns: `repeat(${showData.columns || 2}, minmax(0, 1fr))`,
        }"
      >
        <article v-if="feedItems.length > 0" class="goods-tabs-feed-card">
          <ElImage
            v-if="feedItems[0]?.imageUrl"
            class="goods-tabs-feed-image"
            :src="feedItems[0].imageUrl"
            fit="cover"
          >
            <template #error>
              <div class="goods-tabs-feed-image goods-tabs-feed-image--empty">
                <ElIcon><Picture /></ElIcon>
              </div>
            </template>
          </ElImage>
          <div
            v-else
            class="goods-tabs-feed-image goods-tabs-feed-image--empty"
          >
            <ElIcon><Picture /></ElIcon>
          </div>
          <div class="goods-tabs-feed-shade"></div>
          <div v-if="feedItems[0]?.badge" class="goods-tabs-feed-badge">
            {{ feedItems[0].badge }}
          </div>
          <div class="goods-tabs-feed-copy">
            <div v-if="feedItems[0]?.kicker" class="goods-tabs-feed-kicker">
              {{ feedItems[0].kicker }}
            </div>
            <div class="goods-tabs-feed-title">{{ feedItems[0]?.title }}</div>
            <div v-if="feedItems[0]?.subtitle" class="goods-tabs-feed-subtitle">
              {{ feedItems[0].subtitle }}
            </div>
            <div v-if="feedItems[0]?.ctaText" class="goods-tabs-feed-cta">
              {{ feedItems[0].ctaText }} <span>›</span>
            </div>
          </div>
          <div class="goods-tabs-feed-dots">
            <span
              v-for="(item, index) in feedItems"
              :key="item.id"
              class="goods-tabs-feed-dot"
              :class="{ 'goods-tabs-feed-dot--active': index === 0 }"
            ></span>
          </div>
        </article>
        <article
          v-for="item in state.items"
          :key="item.id"
          class="goods-tabs-card"
          :class="{ 'goods-tabs-card--soldout': item.stock <= 0 }"
        >
          <div class="goods-tabs-image-box">
            <ElImage
              v-if="item.imageUrl"
              :alt="item.name"
              class="goods-tabs-image"
              :src="item.imageUrl"
              fit="contain"
            >
              <template #error>
                <div class="goods-tabs-image goods-tabs-image--empty">
                  <ElIcon><Picture /></ElIcon>
                </div>
              </template>
            </ElImage>
            <div v-else class="goods-tabs-image goods-tabs-image--empty">
              <ElIcon><Picture /></ElIcon>
            </div>
            <div v-if="item.stock <= 0" class="goods-tabs-sold-out">
              暂时缺货
            </div>
          </div>
          <div class="goods-tabs-name">{{ item.name }}</div>
          <div v-if="shouldShowSubtitle(item)" class="goods-tabs-subtitle">
            {{ item.subTitle }}
          </div>
          <div v-if="cardTags(item).length > 0" class="goods-tabs-tags">
            <span
              v-for="tag in cardTags(item)"
              :key="tag"
              class="goods-tabs-tag"
            >
              {{ tag }}
            </span>
          </div>
          <div class="goods-tabs-meta">
            <div class="goods-tabs-meta-text">
              <!-- 价格区优先级：会员价 > 捡漏价 > 普通价，与 C 端同序 -->
              <div
                v-if="showData.showPrice !== false"
                class="goods-tabs-price-line"
              >
                <template v-if="showMemberPrice(item)">
                  <div class="goods-tabs-member-price">
                    <div class="goods-tabs-member-price-main">
                      <span class="goods-tabs-member-badge">会员价</span>
                      <span class="goods-tabs-price goods-tabs-price--member">
                        ¥{{ item.price.toFixed(2) }}
                      </span>
                    </div>
                    <div
                      v-if="
                        showData.showOriginalPrice &&
                        shouldShowOriginalPrice(item)
                      "
                      class="goods-tabs-member-price-original"
                    >
                      <span class="goods-tabs-member-price-label">日常价</span>
                      <span
                        class="goods-tabs-price-original goods-tabs-price-original--member"
                      >
                        ¥{{ item.originalPrice.toFixed(2) }}
                      </span>
                    </div>
                  </div>
                </template>
                <template v-else-if="showBargainPrice(item)">
                  <div class="goods-tabs-bargain-price">
                    <div class="goods-tabs-bargain-price-main">
                      <span class="goods-tabs-bargain-label">捡漏价</span>
                      <span class="goods-tabs-bargain-amount">
                        <span class="goods-tabs-bargain-symbol">¥</span>
                        <span class="goods-tabs-bargain-integer">{{
                          priceParts(item).integer
                        }}</span>
                        <span class="goods-tabs-bargain-decimal"
                          >.{{ priceParts(item).decimal }}</span
                        >
                      </span>
                      <span
                        v-if="
                          showData.showOriginalPrice &&
                          shouldShowOriginalPrice(item)
                        "
                        class="goods-tabs-bargain-original-price"
                      >
                        ¥{{ item.originalPrice.toFixed(2) }}
                      </span>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <span class="goods-tabs-price-amount">
                    <span class="goods-tabs-price-symbol">¥</span>
                    <span class="goods-tabs-price-integer">{{
                      priceParts(item).integer
                    }}</span>
                    <span class="goods-tabs-price-decimal"
                      >.{{ priceParts(item).decimal }}</span
                    >
                  </span>
                  <!-- 划线原价：仅原价严格高于售价时显示（存量商品原价多为 0，会划出￥0） -->
                  <span
                    v-if="
                      showData.showOriginalPrice &&
                      shouldShowOriginalPrice(item)
                    "
                    class="goods-tabs-price-original"
                  >
                    ¥{{ item.originalPrice.toFixed(2) }}
                  </span>
                </template>
              </div>
            </div>
            <!-- 小程序卡片右下角是真实可点的快捷加购按钮，这里按同尺寸静态呈现 -->
            <span v-if="item.stock > 0" class="goods-tabs-cart">
              <ElIcon><ShoppingCart /></ElIcon>
            </span>
          </div>
        </article>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-goods-tabs 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.goods-tabs-bar {
  display: flex;
  align-items: center;
  height: 44px;
  background: #fff;
  border-bottom: 0.5px solid #f2f3f5;
}

.goods-tabs-tab {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 0 6px;
  font-size: 15px;
  color: #4e5969;
  cursor: pointer;
}

.goods-tabs-tab--active {
  font-weight: 600;
  color: #1f2329;
}

.goods-tabs-indicator {
  position: absolute;
  bottom: 5px;
  left: 50%;
  width: 20px;
  height: 3px;
  margin-left: -10px;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 999px;
}

/* 会员权益条：与 C 端 goods-tabs__member-banner 同值（12rpx↔6px 等） */
.goods-tabs-member-banner {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 7px 9px;
  margin: 6px 10px 0;
  font-size: 12px;
  line-height: 1.4;
  color: #8a5a1c;
  background: linear-gradient(90deg, #fff6e8, #ffe9cf);
  border-radius: 6px;
}

.goods-tabs-member-banner-tag {
  flex: none;
  padding: 1px 5px;
  font-size: 10px;
  line-height: 1.6;
  color: #fff;
  background: #c8a05a;
  border-radius: 999px;
}

.goods-tabs-member-banner-text {
  flex: 1;
  min-width: 0;
}

.goods-tabs-inner {
  padding: 8px 10px 10px;
}

.goods-tabs-grid {
  display: grid;
  gap: 10px;
}

/* 活动轮播卡：与 C 端 goods-tabs__feed 同款 3:4 竖卡（预览只展示首项） */
.goods-tabs-feed-card {
  position: relative;
  min-height: 200px;
  aspect-ratio: 3 / 4;
  overflow: hidden;
  background: #0b1a2b;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgb(31 35 41 / 8%);
}

.goods-tabs-feed-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.goods-tabs-feed-image--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: #1c2b3a;
}

.goods-tabs-feed-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgb(8 18 30 / 5%) 0%,
    rgb(8 18 30 / 0%) 38%,
    rgb(8 18 30 / 55%) 66%,
    rgb(8 18 30 / 90%) 100%
  );
}

.goods-tabs-feed-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 3px 7px;
  font-size: 9px;
  line-height: 1.4;
  color: #fff;
  background: rgb(10 22 36 / 55%);
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 9px;
}

.goods-tabs-feed-copy {
  position: absolute;
  right: 10px;
  bottom: 24px;
  left: 10px;
  color: #fff;
}

.goods-tabs-feed-kicker {
  margin-bottom: 3px;
  font-size: 10px;
  font-weight: 600;
  color: #ffcf8a;
}

.goods-tabs-feed-title {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.05;
  text-shadow: 0 1px 8px rgb(0 0 0 / 35%);
}

.goods-tabs-feed-subtitle {
  margin-top: 4px;
  font-size: 10px;
  line-height: 1.4;
  opacity: 0.92;
}

.goods-tabs-feed-cta {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  padding: 4px 9px;
  margin-top: 7px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
  color: #fff;
  background: #ff6a00;
  border-radius: 11px;
  box-shadow: 0 2px 8px rgb(255 106 0 / 35%);
}

.goods-tabs-feed-dots {
  position: absolute;
  right: 0;
  bottom: 8px;
  left: 0;
  display: flex;
  gap: 4px;
  justify-content: center;
}

.goods-tabs-feed-dot {
  width: 4px;
  height: 4px;
  background: rgb(255 255 255 / 50%);
  border-radius: 50%;
}

.goods-tabs-feed-dot--active {
  width: 10px;
  background: #fff;
  border-radius: 3px;
}

.goods-tabs-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid #f0f1f3;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgb(31 35 41 / 5%);
}

/* 售罄：图片压暗 + 居中胶囊，与 C 端同款 */
.goods-tabs-card--soldout .goods-tabs-image {
  opacity: 0.45;
}

/* 图片 1:1 容器 + contain 完整展示，与小程序 goods-tabs__image-box 对齐 */
.goods-tabs-image-box {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  background: #fff;
}

.goods-tabs-image {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: #a8abb2;
}

.goods-tabs-image--empty {
  background: #f3f4f6;
}

.goods-tabs-sold-out {
  position: absolute;
  top: 50%;
  left: 50%;
  padding: 5px 11px;
  font-size: 11px;
  line-height: 1.4;
  color: #fff;
  background: rgb(17 24 39 / 72%);
  border-radius: 999px;
  transform: translate(-50%, -50%);
}

.goods-tabs-name {
  display: -webkit-box;
  min-height: 1.4em;
  margin: 8px 10px 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  color: #1f2329;
  word-break: normal;
  overflow-wrap: break-word;
  -webkit-box-orient: vertical;
}

/* 商品子标题：与 C 端 goods-tabs__subtitle 同值（1px = 2rpx） */
.goods-tabs-subtitle {
  margin: 3px 10px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  line-height: 1.4;
  color: #8a919f;
  white-space: nowrap;
}

.goods-tabs-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 5px 10px 0;
}

.goods-tabs-tag {
  padding: 1px 5px;
  font-size: 10px;
  line-height: 1.6;
  color: #6b7280;
  background: #f4f5f7;
  border-radius: 999px;
}

.goods-tabs-meta {
  display: flex;
  gap: 4px;
  align-items: flex-end;
  justify-content: space-between;
  padding: 7px 10px 10px;
  margin-top: auto;
}

.goods-tabs-meta-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.goods-tabs-price-line {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-items: baseline;
  min-width: 0;
}

.goods-tabs-price {
  font-size: 16px;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
}

/* 普通价：整数大、小数小、符号降一档，与 C 端同值 */
.goods-tabs-price-amount {
  display: inline-flex;
  align-items: baseline;
  min-width: 0;
  color: var(--wot-color-theme-primary, #ff2237);
}

.goods-tabs-price-symbol {
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.goods-tabs-price-integer {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.05;
}

.goods-tabs-price-decimal {
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.goods-tabs-price-original {
  flex: none;
  font-size: 11px;
  font-weight: 400;
  color: #999;
  text-decoration: line-through;
}

.goods-tabs-price--member {
  font-size: 17px;
  font-weight: 700;
  color: #1f2329;
}

/* 会员价：金色小标签 + 近黑主价 + 灰色日常价，与 C 端同值 */
.goods-tabs-member-price {
  display: flex;
  flex-direction: column;
  gap: 1px;
  align-self: flex-start;
  min-width: 0;
  max-width: 100%;
}

.goods-tabs-member-price-main {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  align-items: baseline;
  min-width: 0;
}

.goods-tabs-member-badge {
  flex: none;
  padding: 1px 4px;
  font-size: 9px;
  font-weight: 600;
  line-height: 1.5;
  color: #a06a1f;
  background: #fffaf0;
  border: 0.5px solid #ecd4a5;
  border-radius: 3px;
}

.goods-tabs-member-price-original {
  display: flex;
  gap: 2px;
  align-items: baseline;
  min-width: 0;
}

.goods-tabs-member-price-label {
  flex: none;
  font-size: 9px;
  line-height: 1.5;
  color: #9aa3ad;
}

.goods-tabs-price-original--member {
  font-size: 10px;
  color: #9aa3ad;
}

/*
 * 低价捡漏：价格主导。小号「捡漏价」标签 + 放大红色价格（整数大、小数小）+
 * 灰色原价划线，与 C 端 bargain 分支同值。
 */
.goods-tabs-bargain-price {
  align-self: flex-start;
  min-width: 0;
  max-width: 100%;
}

.goods-tabs-bargain-price-main {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  align-items: baseline;
  min-width: 0;
}

.goods-tabs-bargain-label {
  flex: none;
  font-size: 9px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--wot-color-theme-primary, #ff2237);
}

.goods-tabs-bargain-amount {
  display: inline-flex;
  align-items: baseline;
  min-width: 0;
  color: var(--wot-color-theme-primary, #ff2237);
}

.goods-tabs-bargain-symbol {
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.goods-tabs-bargain-integer {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.05;
}

.goods-tabs-bargain-decimal {
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.goods-tabs-bargain-original-price {
  flex: none;
  font-size: 10px;
  font-weight: 400;
  color: #9aa3ad;
  text-decoration: line-through;
}

.goods-tabs-cart {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 12px;
  color: #fff;
  background: var(--wot-color-theme-primary, #4d7fff);
  border-radius: 50%;
}
</style>
