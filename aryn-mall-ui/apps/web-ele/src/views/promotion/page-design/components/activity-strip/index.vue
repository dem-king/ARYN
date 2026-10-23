<script setup lang="ts">
import type { DecorationLink } from '../../../page-designer/schema/types';
import type {
  RetailActivityItem,
  RetailDiscountActivityItem,
  RetailSeckillSessionItem,
} from '../common/retail-preview/retail-data';
import type {
  ActivityStripEntry,
  ActivityStripEntryKey,
  ActivityStripProps,
} from './types';

import { computed, watch } from 'vue';

import { ElImage } from 'element-plus';

import {
  loadDiscounts,
  loadLimitedActivities,
  loadSeckillSessions,
} from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { ACTIVITY_STRIP_HALLS, validateActivityStrip } from './types';

/**
 * 活动入口条（首页金刚区正下方一排等宽活动小卡）。
 *
 * 与 C 端 diy-activity-strip 同源（取数、降级、视觉口径逐项对齐）：
 * · automatic：并行复用三个既有 loader，各取 1 条进行中活动，每类独立结算；
 * · manual：直接渲染运营手配的 entries；
 * · 某类无进行中活动 / 单个 loader 失败 → 只隐藏该卡，剩余卡等宽重排；
 * · 三类全无 → controller 置 empty，由 RetailPreviewFrame 渲染空态提示
 *   （C 端默认 emptyStrategy=hide 时整条不渲染，管理端用画布空态表达同一语义）。
 *
 * 视觉规格见需求文档 §5.5.5（750rpx 设计稿）：1rpx = 0.5px，
 * 375px 画布内所有 px 值都是 C 端 rpx 值的一半；改任一端必须同步另一端。
 */
const props = defineProps<{ showData: ActivityStripProps }>();
const controller = createRetailPreviewController<ActivityStripEntry>();
const { state } = controller;

/**
 * 三类内置 loader 只读 count/dataSource，其余字段给「无楼层头」的占位值
 * （与 C 端 index.vue 的 builtinSourceBase 同口径）。
 */
function builtinSourceBase(source: ActivityStripProps) {
  return {
    commonStyle: source.commonStyle,
    count: 1,
    dataSource: { mode: 'automatic' as const },
    emptyStrategy: 'hide' as const,
    invalidStrategy: 'hide' as const,
  };
}

/**
 * 金额文案：最多两位小数并去掉无意义的尾零（与 C 端 formatStripAmount 同源）。
 *
 * 角标只有几十 px 宽，`¥4.00` 比 `¥4` 多占字宽却没有信息增量；
 * `30.8 - 26.8` 这类浮点差会算出 `4.000000000000002`，必须按两位收敛。
 */
function formatStripAmount(value: number): string {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return '0';
  return String(Math.round(amount * 100) / 100);
}

/**
 * 折扣力度文案（与 C 端 formatDiscountValue 同源，§3.2 / R4）。
 *
 * 打折：`Math.round(discountValue * 100) / 10` —— 0.95 → 9.5折、0.8 → 8折；
 * 力度 >= 10 折说明折后价不低于原价，既没有「省」的信息量又会拼出
 * 不成词的「10折」，因此退化为中性「折扣」，保证角标永不出现「10折」。
 * 减价 / 固定价沿用后端语义：`减¥X` / `一口价`。
 */
function formatDiscountValue(
  activity: Pick<RetailDiscountActivityItem, 'discountType' | 'discountValue'>,
): string {
  switch (Number(activity.discountType)) {
    case 1: {
      const zhe = Math.round(Number(activity.discountValue) * 100) / 10;
      if (!Number.isFinite(zhe) || zhe <= 0 || zhe >= 10) return '折扣';
      return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`;
    }
    case 2: {
      return `减¥${formatStripAmount(activity.discountValue)}`;
    }
    case 3: {
      return '一口价';
    }
    default: {
      return '折扣';
    }
  }
}

/** 拼团卡名：groupNum 缺失按默认「2人拼团」，多人团按真实人数表达（与 C 端同源） */
function formatGroupBuyTitle(
  activity: Pick<RetailActivityItem, 'groupNum'>,
): string {
  const groupNum = Number(activity.groupNum);
  return groupNum > 0 ? `${groupNum}人拼团` : '2人拼团';
}

/** 拼团钩子：`{groupNum}人成团立省`；存量活动没有 groupNum 时退化为「多人成团」 */
function formatGroupBuyHook(
  activity: Pick<RetailActivityItem, 'groupNum'>,
): string {
  const groupNum = Number(activity.groupNum);
  return groupNum > 0 ? `${groupNum}人成团立省` : '多人成团';
}

/**
 * 拼团角标：原价严格大于拼团价才显示省额，否则退化为「拼」（与 C 端同源）。
 * 不满足条件时绝不能按 0 兜底造出「省¥0」——那是编造的优惠。
 */
function formatGroupBuyBadge(
  activity: Pick<RetailActivityItem, 'activityPrice' | 'originalPrice'>,
): string {
  const saving =
    Number(activity.originalPrice) - Number(activity.activityPrice);
  if (!(saving > 0)) return '拼';
  return `省¥${formatStripAmount(saving)}`;
}

/** 秒杀钩子：`{sessionName}·整点开抢`；场次名缺失时只留节奏词 */
function formatSeckillHook(
  session: Pick<RetailSeckillSessionItem, 'sessionName'>,
): string {
  const name = (session.sessionName || '').trim();
  return name ? `${name}·整点开抢` : '整点开抢';
}

/** 三类内置卡自动取数结果：某类没有进行中活动（或 loader 失败）时传空，不产出该卡 */
interface ActivityStripSources {
  discount?: null | RetailDiscountActivityItem;
  groupBuy?: null | RetailActivityItem;
  seckill?: null | RetailSeckillSessionItem;
}

/** 内置三类入口的固定会场路由（与 C 端 activity-strip.ts 的 ACTIVITY_STRIP_HALLS 同源） */
function hallLink(path: string): DecorationLink {
  return { params: {}, path, type: 'custom' };
}

/**
 * 组装内置三类卡，顺序固定「秒杀 → 拼团 → 折扣」（与 C 端
 * buildActivityStripEntries 同源，§5.5.3 表格顺序）。
 *
 * 只认「进行中」：秒杀场次 status=1、拼团 activityStatus=active、折扣 status=1；
 * 未开始/已结束的数据即使被 loader 带回来也不会出现在入口条上。
 */
function buildActivityStripEntries(
  sources: ActivityStripSources,
): ActivityStripEntry[] {
  const entries: ActivityStripEntry[] = [];

  const seckill = sources.seckill;
  if (seckill && Number(seckill.status) === 1) {
    entries.push({
      badge: '抢',
      hook: formatSeckillHook(seckill),
      imageUrl: seckill.goodsList[0]?.goodsImage || '',
      key: 'seckill',
      link: hallLink(ACTIVITY_STRIP_HALLS.seckill),
      title: '限时秒杀',
    });
  }

  const groupBuy = sources.groupBuy;
  if (groupBuy && groupBuy.status === 'active') {
    entries.push({
      badge: formatGroupBuyBadge(groupBuy),
      hook: formatGroupBuyHook(groupBuy),
      imageUrl: groupBuy.imageUrl || '',
      key: 'group-buy',
      link: hallLink(ACTIVITY_STRIP_HALLS['group-buy']),
      title: formatGroupBuyTitle(groupBuy),
    });
  }

  const discount = sources.discount;
  if (discount && Number(discount.status) === 1) {
    const value = formatDiscountValue(discount);
    entries.push({
      badge: value,
      hook: `${value}专区`,
      imageUrl: discount.goodsList[0]?.goodsImage || '',
      key: 'discount',
      link: hallLink(ACTIVITY_STRIP_HALLS.discount),
      title: '限时折扣',
    });
  }

  return entries;
}

/**
 * 三类内置活动并行取数（各取 1 条进行中活动）。
 *
 * 用 `Promise.allSettled` 隔离单类失败（与 C 端同源）：某一类接口超时/报错
 * 只让那张卡消失，其余两类照常渲染；只有三类全部失败才上抛，交给 controller
 * 的 error 态（对应 C 端 invalidStrategy 兜底协议）。
 */
async function loadActivityStripEntries(
  source: ActivityStripProps,
): Promise<ActivityStripEntry[]> {
  if (source.dataSource?.mode === 'manual')
    return source.dataSource.entries ?? [];

  const base = builtinSourceBase(source);
  const [seckill, groupBuy, discount] = await Promise.allSettled([
    loadSeckillSessions({
      ...base,
      showCountdown: false,
      showProgress: false,
      subtitle: '',
      title: '',
    }),
    loadLimitedActivities({
      ...base,
      layout: 'list',
      showCountdown: false,
      subtitle: '',
      title: '',
    }),
    loadDiscounts({
      ...base,
      goodsLimit: 1,
      showCountdown: false,
      subtitle: '',
      title: '',
    }),
  ]);

  if (
    seckill.status === 'rejected' &&
    groupBuy.status === 'rejected' &&
    discount.status === 'rejected'
  )
    throw seckill.reason;

  return buildActivityStripEntries({
    discount:
      discount.status === 'fulfilled' ? (discount.value[0] ?? null) : null,
    groupBuy:
      groupBuy.status === 'fulfilled' ? (groupBuy.value[0] ?? null) : null,
    seckill: seckill.status === 'fulfilled' ? (seckill.value[0] ?? null) : null,
  });
}

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateActivityStrip(showData), () =>
      loadActivityStripEntries(showData),
    ),
  { deep: true, immediate: true },
);

/** 角标强度：秒杀=强促销（主题渐变实心），拼团/折扣/自定义=弱促销（主题浅底） */
function stripBadgeTone(key: ActivityStripEntryKey): 'soft' | 'strong' {
  return key === 'seckill' ? 'strong' : 'soft';
}

/** 缺图兜底：中性灰底 + 标题首字（C 端用类目色板，管理端预览按需求走中性灰） */
function fallbackText(entry: ActivityStripEntry): string {
  return (entry.title || '活').trim().slice(0, 1) || '活';
}

/** 展示全部取到的卡片：卡数由实际活动/手配卡片决定，不再按配置截断 */
const cards = computed(() =>
  state.items.map((entry) => ({
    ...entry,
    tone: stripBadgeTone(entry.key),
  })),
);

/**
 * 自动排布：按实际卡数等宽铺满一行；手配超过三张时一行三张换行。
 *
 * 「每行列数 / 展示数量」已下线（自动模式最多三类活动各一张，
 * 手配的卡片数就是运营添加的数量），两端都不再读取旧字段。
 */
function stripGridColumns(cardCount: number): number {
  const count = Math.max(1, Math.floor(Number(cardCount)) || 0);
  return Math.min(count, 3);
}

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${stripGridColumns(cards.value.length)}, minmax(0, 1fr))`,
}));
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    type-label="活动入口条"
  >
    <div class="strip-grid" :style="gridStyle">
      <div
        v-for="(entry, index) in cards"
        :key="`${entry.key}-${index}`"
        class="strip-card"
      >
        <div class="strip-main">
          <div class="strip-thumb">
            <ElImage
              v-if="entry.imageUrl"
              :alt="entry.title"
              class="strip-thumb__img"
              :src="entry.imageUrl"
              fit="cover"
            >
              <template #error>
                <div class="strip-thumb__fallback">
                  {{ fallbackText(entry) }}
                </div>
              </template>
            </ElImage>
            <div v-else class="strip-thumb__fallback">
              {{ fallbackText(entry) }}
            </div>
            <span
              v-if="entry.badge"
              class="strip-badge"
              :class="`strip-badge--${entry.tone}`"
            >
              {{ entry.badge }}
            </span>
          </div>
          <span class="strip-name">{{ entry.title }}</span>
        </div>
        <span v-if="entry.hook" class="strip-hook">{{ entry.hook }}</span>
        <div class="strip-arrow"></div>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  视觉规格见需求文档 §5.5.5，与 C 端 diy-activity-strip/index.vue 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
  色彩：卡片/描边/文案用固定中性色；促销色只走 var(--wot-color-theme-*)，
  多租户换肤自适应（禁止写死促销红）。描边沿用发丝线口径 1rpx→1px
  （0.5px 在多数设备被取整为 1px，与既有 .gs-card 等组件一致）。
-->
<style scoped lang="scss">
.strip-grid {
  box-sizing: border-box;
  display: grid;
  gap: 5px;
  align-items: stretch;
}

/* 卡片：白底、1rpx 浅描边、16rpx 圆角、16rpx 内边距、整卡等宽 */
.strip-card {
  position: relative;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 6px;
  background: #fff;
  border: 1px solid #f1f2f4;
  border-radius: 8px;
}

/* 首行：缩略图 + 活动名；右侧给箭头留出 16rpx（8px） */
.strip-main {
  display: flex;
  gap: 4px;
  align-items: center;
  min-width: 0;
  padding-right: 8px;
}

.strip-thumb {
  position: relative;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
}

.strip-thumb__img {
  display: block;
  width: 32px;
  height: 32px;
  background: #f3f4f6;
  border-radius: 6px;
}

/* 缺图兜底：中性灰底 + 标题首字（与商品图垫底的 #f3f4f6 同口径） */
.strip-thumb__fallback {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  color: #8a8f99;
  background: #f3f4f6;
  border-radius: 6px;
}

.strip-name {
  display: block;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  font-weight: 700;
  line-height: 15px;
  color: #1f2329;
  white-space: nowrap;
}

/* 钩子文案独占一行、通卡宽：三张卡时也能完整显示「午间场·整点开抢」 */
.strip-hook {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  font-weight: 500;
  line-height: 12px;
  color: #8a8f99;
  white-space: nowrap;
}

/* 角标：小胶囊贴在缩略图右下角并轻微出边（-6rpx → -3px） */
.strip-badge {
  position: absolute;
  right: -3px;
  bottom: -3px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 60px;
  height: 14px;
  padding: 0 4px;
  overflow: hidden;
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
  border-radius: 999px;
}

/* 秒杀=强促销：主题渐变实心 */
.strip-badge--strong {
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--wot-color-theme-secondary, #ff6b7a),
    var(--wot-color-theme-primary, #ff2237)
  );
}

/* 拼团/折扣=弱促销：主题浅底 + 主题字色，首页不铺三块实心大红 */
.strip-badge--soft {
  color: var(--wot-color-theme-primary, #ff2237);
  background: var(--wot-color-theme-background, #fff1ee);
}

/* 右侧淡箭头：与首行活动名垂直居中（12rpx + 64rpx/2 − 10rpx/2） */
.strip-arrow {
  position: absolute;
  top: 19px;
  right: 5px;
  width: 5px;
  height: 5px;
  border-top: 1px solid #c9cdd4;
  border-right: 1px solid #c9cdd4;
  transform: rotate(45deg);
}
</style>
