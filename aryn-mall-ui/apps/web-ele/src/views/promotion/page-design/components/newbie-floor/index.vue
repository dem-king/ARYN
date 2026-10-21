<script setup lang="ts">
import type {
  RetailNewbieFloorItem,
  RetailNewbieGoodsItem,
} from '../common/retail-preview/retail-data';
import type { NewbieFloorProps } from './types';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { loadNewbieFloor } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateNewbieFloor } from './types';

const props = defineProps<{ showData: NewbieFloorProps }>();
const controller = createRetailPreviewController<RetailNewbieFloorItem>();
const { state } = controller;

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateNewbieFloor(showData), () =>
      loadNewbieFloor(showData),
    ),
  { deep: true, immediate: true },
);

/** 楼层只渲染第一个进行中活动（与小程序同口径） */
const floor = computed(() => state.items[0]);
const goodsList = computed<RetailNewbieGoodsItem[]>(
  () => (floor.value?.goodsList ?? []) as RetailNewbieGoodsItem[],
);

const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
});
onBeforeUnmount(() => {
  clearInterval(timer);
  if (autoTimer) clearInterval(autoTimer);
  if (resumeTimer) clearTimeout(resumeTimer);
  endDrag();
});

/**
 * 时钟格式对齐 C 端 formatRemainingTime：HH:MM:SS，跨天加「N天 」前缀。
 * 拆成「前缀 + 时间」两段：前缀 .nf-cd-label 走中性灰、时间 .nf-cd-time 走价格色，
 * 与 C 端同一套胶囊结构（两端逐值对齐）。
 */
const countdown = computed<null | { prefix: string; text: string }>(() => {
  const endTime = floor.value?.endTime;
  if (!props.showData.showCountdown || !endTime) return null;
  const target = Date.parse(endTime);
  if (Number.isNaN(target)) return { prefix: '活动', text: '已结束' };
  const totalSeconds = Math.max(0, Math.floor((target - now.value) / 1000));
  if (totalSeconds <= 0) return { prefix: '活动', text: '已结束' };
  const days = Math.floor(totalSeconds / 86_400);
  const clock = [
    Math.floor((totalSeconds % 86_400) / 3600),
    Math.floor((totalSeconds % 3600) / 60),
    totalSeconds % 60,
  ]
    .map((value) => String(value).padStart(2, '0'))
    .join(':');
  return {
    prefix: '距结束',
    text: days > 0 ? `${days}天 ${clock}` : clock,
  };
});

function savedAmount(goods: RetailNewbieGoodsItem) {
  return Math.max(0, goods.originalPrice - goods.newbiePrice);
}

/** 主色仅接受 6 位 hex，经 --nf-main 下发浅色底纹；空值跟随页面主题主色 */
const colorVars = computed(() => {
  const main = (props.showData.mainColor || '').trim();
  if (!/^#[0-9a-f]{6}$/i.test(main)) return undefined;
  return { '--nf-main': main };
});

/**
 * 自动前进：一次一张卡的分页式滚动，替代连续位移的跑马灯。
 * 连续跑马灯让内容无法阅读、用户无法接管（NN/g 自动轮播研究），
 * WCAG 2.2.2 也要求自动移动超过 5 秒的内容必须可暂停。
 * 手动横滑不再与自动互斥：触摸/按住即暂停，松手 5 秒后自动恢复。
 */
const shouldAutoAdvance = computed(
  () => props.showData.autoScroll && goodsList.value.length > 2,
);

/** 轮播间隔（毫秒）= 每张卡片的停留时长；历史草稿缺省回退 3000 */
const intervalMs = computed(() => Number(props.showData.interval) || 3000);

/** 触摸后自动恢复延迟：留出读完一张卡的时间 */
const RESUME_DELAY_MS = 5000;

const scrollEl = ref<HTMLElement>();
const dragging = ref(false);
let dragOrigin: undefined | { left: number; startX: number };
let autoTimer: ReturnType<typeof setInterval> | undefined;
let resumeTimer: ReturnType<typeof setTimeout> | undefined;
let paused = false;

/** 系统「减弱动态效果」偏好：命中时不自动前进（WCAG 2.3.3 精神） */
function prefersReducedMotion() {
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
}

function restartAuto() {
  if (autoTimer) {
    clearInterval(autoTimer);
    autoTimer = undefined;
  }
  if (!shouldAutoAdvance.value || prefersReducedMotion()) return;
  autoTimer = setInterval(stepScroll, intervalMs.value);
}

/** 前进一步：步进越界先落到末尾（保证最后一张完整可见），已在末尾则回卷首张 */
function stepScroll() {
  const el = scrollEl.value;
  if (paused || !el) return;
  const cards = el.querySelectorAll<HTMLElement>('.nf-card');
  if (cards.length < 2) return;
  const step =
    cards[1]!.getBoundingClientRect().left -
    cards[0]!.getBoundingClientRect().left;
  const max = el.scrollWidth - el.clientWidth;
  if (step <= 0 || max <= 0) return;
  const atEnd = el.scrollLeft >= max - 4;
  const target = atEnd ? 0 : Math.min(el.scrollLeft + step, max);
  el.scrollTo({ behavior: 'smooth', left: target });
  // 平滑滚动依赖动画帧：旧 WebView / 减弱动效设置下可能不落位，兜底直接落位。
  // 期间用户若已按住拖拽（paused）则不落位，避免把位置从用户手里抢回去。
  setTimeout(() => {
    if (!paused && !dragging.value && Math.abs(el.scrollLeft - target) > 1) {
      el.scrollLeft = target;
    }
  }, 480);
}

function pauseAuto() {
  paused = true;
  if (resumeTimer) {
    clearTimeout(resumeTimer);
    resumeTimer = undefined;
  }
}

function scheduleResume() {
  if (resumeTimer) clearTimeout(resumeTimer);
  resumeTimer = setTimeout(() => {
    paused = false;
  }, RESUME_DELAY_MS);
}

/**
 * 手动横滑拖拽：预览画布是浏览器环境，macOS 覆盖式滚动条平时不可见，
 * 只有 trackpad 双指滑动能滚，鼠标拖不动会让运营以为「滑不动」。
 * 与自动前进并存：按住即暂停，松手后 5 秒恢复。
 */
function startDrag(event: MouseEvent) {
  const el = scrollEl.value;
  if (!el || event.button !== 0) return;
  pauseAuto();
  // 阻止图片原生拖拽与文本选中，否则拖动时拖的是图片本身
  event.preventDefault();
  dragOrigin = { left: el.scrollLeft, startX: event.clientX };
  dragging.value = true;
  window.addEventListener('mousemove', onDragMove);
  window.addEventListener('mouseup', endDrag);
}

function onDragMove(event: MouseEvent) {
  const el = scrollEl.value;
  if (!dragOrigin || !el) return;
  el.scrollLeft = dragOrigin.left - (event.clientX - dragOrigin.startX);
}

function endDrag() {
  dragOrigin = undefined;
  dragging.value = false;
  window.removeEventListener('mousemove', onDragMove);
  window.removeEventListener('mouseup', endDrag);
  scheduleResume();
}

// 数据异步到达后再启动自动前进：挂载时 goodsList 还是空的
watch([shouldAutoAdvance, intervalMs, () => goodsList.value.length], () => {
  paused = false;
  restartAuto();
});
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
  >
    <div class="nf-panel" :style="colorVars">
      <div class="nf-head">
        <span v-if="showData.showTag" class="nf-mark">新</span>
        <span class="nf-title">{{ showData.title }}</span>
        <span v-if="showData.showTag" class="nf-subtitle">首单专享价</span>
        <span v-if="countdown" class="nf-countdown">
          <span class="nf-cd-label">{{ countdown.prefix }}</span>
          <span class="nf-cd-time">{{ countdown.text }}</span>
        </span>
      </div>
      <div v-if="floor" class="nf-perks">
        注册{{ floor.registerWithinDays }}天内新人 · 每人每款限{{
          floor.limitPerUser
        }}件
      </div>
      <div
        ref="scrollEl"
        class="nf-scroll"
        :class="{ 'is-dragging': dragging }"
        @mousedown="startDrag"
        @touchstart.passive="pauseAuto"
        @touchend="scheduleResume"
        @touchcancel="scheduleResume"
      >
        <div class="nf-track">
          <article
            v-for="goods in goodsList"
            :key="goods.skuId"
            class="nf-card"
          >
            <div class="nf-media">
              <span v-if="showData.showTag" class="nf-badge">新人价</span>
              <ElImage
                v-if="goods.goodsImage"
                :alt="goods.goodsName"
                class="nf-img"
                :src="goods.goodsImage"
                fit="cover"
              >
                <template #error>
                  <div class="nf-img nf-img--empty">
                    <ElIcon><Picture /></ElIcon>
                  </div>
                </template>
              </ElImage>
              <div v-else class="nf-img nf-img--empty">
                <ElIcon><Picture /></ElIcon>
              </div>
            </div>
            <div class="nf-body">
              <div class="nf-name">
                {{ goods.goodsName || '新人专享商品' }}
              </div>
              <div class="nf-price-row">
                <span class="nf-price">
                  <span class="nf-price-symbol">¥</span
                  >{{ goods.newbiePrice.toFixed(2) }}
                </span>
                <span class="nf-btn">抢</span>
              </div>
              <div
                v-if="goods.originalPrice > goods.newbiePrice"
                class="nf-meta"
              >
                <span class="nf-original"
                  >¥{{ goods.originalPrice.toFixed(2) }}</span
                >
                <span class="nf-save"
                  >省¥{{ savedAmount(goods).toFixed(2) }}</span
                >
              </div>
            </div>
          </article>
        </div>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-newbie-floor 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
  视觉方向（参考小象超市/盒马/叮咚等生鲜商超的新人专区）：
  浅色面板 + 一屏三张小卡，价格主导；不使用整块高饱和横幅与重投影。
  色彩角色：
    · 底纹 = --nf-main 以 9% 透明度斜向渐隐（运营可配，空值跟随页面主题）；
    · 价格 / 抢购 / 角标 / 省¥ / 倒计时时间 = 页面主题主色（与全站价格口径一致）；
    · 标题、商品名、划线原价、倒计时前缀 = 固定中性色。
-->
<style scoped lang="scss">
/* 白底 + 91% 白罩 + 主色斜向渐隐 = 等效 9% 淡色底纹，不依赖伪元素 */
.nf-panel {
  position: relative;
  padding: 10px 10px 8px;
  overflow: hidden;
  background:
    linear-gradient(rgb(255 255 255 / 91%), rgb(255 255 255 / 91%)),
    linear-gradient(
      135deg,
      var(--nf-main, var(--wot-color-theme-primary, #ff2237)) 0%,
      rgb(255 255 255 / 0%) 58%
    ),
    #fff;
  border: 0.5px solid #f2e7e2;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgb(31 35 41 / 4%);
}

.nf-head {
  display: flex;
  gap: 4px;
  align-items: center;
}

/* 「新」字标：小面积品牌渐变，承担促销识别 */
.nf-mark {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff7a45),
    var(--wot-color-theme-primary, #ff4d4f)
  );
  border-radius: 4px;
}

.nf-title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1;
  color: #1f2329;
  letter-spacing: 0.2px;
}

.nf-subtitle {
  flex: 0 0 auto;
  padding: 1px 4px;
  font-size: 9px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--wot-color-theme-primary, #ff2237);
  background: #fff0eb;
  border-radius: 2px;
}

/* 倒计时：浅色面板上直接排字，时间属价格语义层取全站价格色 */
.nf-countdown {
  display: flex;
  gap: 3px;
  align-items: baseline;
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.nf-cd-label {
  font-size: 10px;
  color: #8f959e;
}

.nf-cd-time {
  font-size: 11px;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
}

.nf-perks {
  margin-top: 3px;
  font-size: 10px;
  line-height: 1.4;
  color: #8f959e;
}

.nf-scroll {
  width: 100%;

  /* 底部留白承接卡片投影：overflow-y 裁切会把它切成一条硬边 */
  padding-bottom: 4px;
  margin-top: 7px;

  /* 自动前进与手动横滑并存（分页式 scrollLeft 步进，不再互斥） */
  overflow: auto hidden;

  /* 手动横滑：macOS 覆盖式滚动条平时不可见，纯 overflow 看不出能滑 */
  cursor: grab;
}

.nf-scroll.is-dragging {
  cursor: grabbing;
  user-select: none;
}

/* 轨道显式 max-content：flex 块级容器自身宽度=可视宽，滚动区量不到溢出的卡片 */
.nf-track {
  display: flex;
  gap: 6px;
  width: max-content;
}

/* 一屏三张：3×100 + 2×6 = 312px，外层 12px 区块内边距下仍能完整露出三张 */
.nf-card {
  box-sizing: border-box;
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  width: 100px;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid #f2eae6;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgb(31 35 41 / 4%);
}

.nf-media {
  position: relative;
  flex: 0 0 auto;
}

/* 贴边角标：左上角随卡片圆角，不再浮起投影 */
.nf-badge {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
  padding: 2px 5px;
  font-size: 9px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.1px;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff7a45),
    var(--wot-color-theme-primary, #ff4d4f)
  );
  border-radius: 8px 0 6px;
}

/* 图片垫底用中性灰：与商品横滑条/商品分组的 #f3f4f6 口径一致 */
.nf-img {
  display: block;
  width: 100%;
  height: 100px;
  background: #f3f4f6;
}

.nf-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--wot-color-theme-primary, #ff2237);
}

.nf-body {
  min-width: 0;
  padding: 5px 6px 6px;
}

/* 名称固定两行高：卡片高度一致，横滑轨道不因文案长短抖动 */
.nf-name {
  display: -webkit-box;
  height: 32px;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
  color: #303133;
  overflow-wrap: break-word;
  -webkit-box-orient: vertical;
}

.nf-price-row {
  display: flex;
  gap: 3px;
  align-items: center;
  justify-content: space-between;
  height: 20px;
  margin-top: 2px;
}

.nf-price {
  display: flex;
  align-items: baseline;
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 700;
  line-height: 1;

  /* 价格统一全站价格色（与商品横滑条 .gs-price 同 token） */
  color: var(--wot-color-theme-primary, #ff2237);
  white-space: nowrap;
}

.nf-price-symbol {
  margin-right: 1px;
  font-size: 9px;
  font-weight: 600;
}

/* 「抢」改为小胶囊：与一屏三张的窄卡同尺度 */
.nf-btn {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  height: 18px;
  padding: 0 7px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  color: #fff;

  /* 与商品横滑条 .gs-btn 同款渐变，抢购动作全站一致 */
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff7a45),
    var(--wot-color-theme-primary, #ff4d4f)
  );
  border-radius: 999px;
  box-shadow: 0 1px 3px rgb(255 77 79 / 22%);
}

.nf-meta {
  display: flex;
  gap: 3px;
  align-items: center;
  justify-content: space-between;
  height: 12px;
  margin-top: 2px;
}

.nf-original {
  font-size: 9px;
  color: #b6b9c0;
  text-decoration: line-through;
}

/* 省额纯文字：不再用胶囊底色/内边距，避免与价格行抢视觉 */
.nf-save {
  font-size: 9px;
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
}
</style>
