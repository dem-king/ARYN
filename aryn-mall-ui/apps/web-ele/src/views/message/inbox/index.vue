<script setup lang="ts">
import type { NoticeType } from '@vben/utils';

import type { InboxFilterKey } from './inbox-filter';
import type { NoticeTypeMeta } from './notice-type-meta';

import type { NoticeInboxItem } from '#/api/message/types';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { formatNoticeDateTime, noticeTypeOf } from '@vben/utils';

import { ArrowRight, Check, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElEmpty,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSkeleton,
} from 'element-plus';

import {
  getStaffNoticeInbox,
  hideStaffNotice,
  markAllStaffNoticesRead,
  markStaffNoticeRead,
} from '#/api/message/notice';
import { useMessageStore } from '#/store/message';
import { resolveNoticeLink } from '#/utils/notice-link';
import { formatMoney } from '#/utils/util';

import {
  filterInbox,
  INBOX_FILTER_KEYS,
  inboxCardMeta,
  inboxFilterCounts,
  inboxStats,
  searchInbox,
} from './inbox-filter';
import { NOTICE_TYPE_META } from './notice-type-meta';

const messageStore = useMessageStore();
const router = useRouter();

/**
 * 收件箱里读完必须回刷铃铛。
 *
 * 本页直接调已读接口、没有经过 store 的 markRead，铃铛的 unreadCount 会停在
 * 读之前的数字；之后新消息把后端计数顶回同值时，「上升沿」判定看不出变化，
 * 新订单就静默了。
 */
async function syncBell() {
  try {
    await messageStore.refreshNotifications();
  } catch {
    // 回刷失败不影响本地已读态；下一次推送/刷新会自愈
  }
}

/**
 * 消息类型 → 右侧动作文案：让用户点之前就知道会去到哪种业务页。
 *
 * 类型判定在 `@vben/utils` 的 `noticeTypeOf`，图标/配色在 `notice-type-meta`，
 * 这样「新订单」与「买家取消订单」不再共用同一个购物车图标。
 */
const CTA_LABELS: Record<NoticeType, string> = {
  'cod-pay-remind': '查看订单',
  'delivery-task': '查看任务',
  'order-cancelled': '查看订单',
  'order-created': '查看订单',
  'order-refund': '查看退款',
  'vessel-bind': '查看申请',
  conversation: '查看会话',
  system: '查看详情',
};

/** 页签文案：顺序与 INBOX_FILTER_KEYS 保持一致。 */
const FILTER_LABELS: Record<InboxFilterKey, string> = {
  all: '全部',
  order: '订单',
  refund: '退款',
  system: '系统',
  unread: '未读',
  vessel: '船舶',
};

const records = ref<NoticeInboxItem[]>([]);
const nextCursor = ref<string>();
const hasMore = ref(false);
const loading = ref(false);
const loadingMore = ref(false);
const readingAll = ref(false);
const searchQuery = ref('');
const activeFilter = ref<InboxFilterKey>('all');

/** 已加载记录的分类/未读统计（搜索无关）。 */
const stats = computed(() => inboxStats(records.value));

/** 搜索只影响页签计数与最终筛选，不改变“已加载”集合。 */
const searchedRecords = computed(() =>
  searchInbox(records.value, searchQuery.value),
);
const filterCounts = computed(() => inboxFilterCounts(searchedRecords.value));
const visibleRecords = computed(() =>
  filterInbox(searchedRecords.value, activeFilter.value),
);

const filterOptions = computed(() =>
  INBOX_FILTER_KEYS.map((key) => ({
    count: filterCounts.value[key],
    key,
    label: FILTER_LABELS[key],
  })),
);

/**
 * 头部未读数量取「已加载未读」与铃铛服务端未读的较大值：
 * 收件箱只加载了第一页时，服务端计数可能还有更早的未读，不能少报。
 */
const unreadTotal = computed(() =>
  Math.max(stats.value.unread, messageStore.unreadCount),
);

const hasActiveQuery = computed(
  () => activeFilter.value !== 'all' || searchQuery.value.trim() !== '',
);

/** 空态文案：沿用原来的业务说明，搜索/筛选/初始三种口径分别给出原因。 */
const emptyDescription = computed(() => {
  if (hasActiveQuery.value) {
    const keyword = searchQuery.value.trim();
    if (keyword) {
      return `已加载的 ${stats.value.total} 条通知里没有包含「${keyword}」的内容，换个关键词试试。`;
    }
    return '当前筛选下还没有已加载的通知，可以切换分类或加载更早通知。';
  }
  return '新的订单、退款与船舶提醒会出现在这里，处理完记得标记已读。';
});

/** 渲染行：把分类/跳转/卡片文案/时间一次算好，模板不再重复解析 JSON。 */
interface InboxRow {
  amount?: string;
  cardTitle?: string;
  ctaLabel: string;
  /** 可交互：未读（点击标已读）或有业务跳转；已读且无跳转的卡片不做伪按钮 */
  interactive: boolean;
  item: NoticeInboxItem;
  link?: string;
  timeText: string;
  /** 消息类型：新订单 / 已取消 / 退款审核 / 待收款 …，决定图标、配色与标签。 */
  type: NoticeType;
  typeMeta: NoticeTypeMeta;
  unread: boolean;
}

function toRow(item: NoticeInboxItem): InboxRow {
  const type = noticeTypeOf(item);
  const link = resolveNoticeLink(item);
  const card = inboxCardMeta(item);
  return {
    // 卡片标题与通知标题相同时不再重复展示
    amount: card.amount,
    cardTitle: card.title && card.title !== item.title ? card.title : undefined,
    ctaLabel: link ? CTA_LABELS[type] : '',
    interactive: item.readStatus === '0' || Boolean(link),
    item,
    link,
    timeText: formatNoticeDateTime(item.publishTime || item.receivedTime),
    type,
    typeMeta: NOTICE_TYPE_META[type],
    unread: item.readStatus === '0',
  };
}

const rows = computed(() => visibleRecords.value.map((item) => toRow(item)));

function rowAriaLabel(row: InboxRow): string {
  const status = row.unread ? '未读，' : '';
  return row.link
    ? `${status}${row.typeMeta.label}，${row.item.title}，${row.ctaLabel}`
    : `${status}${row.typeMeta.label}，${row.item.title}，点击标记已读`;
}

async function load(reset = false) {
  if (reset) {
    loading.value = true;
  } else {
    loadingMore.value = true;
  }
  try {
    const page = await getStaffNoticeInbox({
      cursor: reset ? undefined : nextCursor.value,
      limit: 20,
    });
    records.value = reset ? page.records : [...records.value, ...page.records];
    nextCursor.value = page.nextCursor;
    hasMore.value = page.hasMore;
  } catch {
    // 请求层已统一提示；保留已加载数据，避免整页被清空
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
}

/** 单条已读：失败不阻断跳转（请求层统一提示），用户仍能打开详情。 */
async function read(item: NoticeInboxItem) {
  if (item.readStatus === '1') {
    return;
  }
  try {
    await markStaffNoticeRead(item.messageId);
    item.readStatus = '1';
    void syncBell();
  } catch {
    // 已读失败由请求层提示
  }
}

/** 点击卡片：先标已读，再按解析出的安全路由跳转；无路由时只标已读。 */
async function open(item: NoticeInboxItem) {
  await read(item);
  const link = resolveNoticeLink(item);
  if (link) {
    void router.push(link);
  }
}

async function hide(item: NoticeInboxItem) {
  try {
    await ElMessageBox.confirm(
      '隐藏只影响你的收件箱，不会删除通知历史。',
      '隐藏通知',
      {
        cancelButtonText: '取消',
        confirmButtonText: '隐藏',
        type: 'warning',
      },
    );
  } catch {
    // 用户取消
    return;
  }
  await hideStaffNotice(item.messageId);
  records.value = records.value.filter(
    (row) => row.messageId !== item.messageId,
  );
  ElMessage.success('已从收件箱隐藏');
  // 铃铛仍可能展示这条未读通知，隐藏后回刷保持两端一致
  void syncBell();
}

async function readAll() {
  if (unreadTotal.value === 0 || readingAll.value) {
    return;
  }
  readingAll.value = true;
  try {
    await markAllStaffNoticesRead();
    records.value.forEach((item) => (item.readStatus = '1'));
    await syncBell();
    ElMessage.success('已全部标记为已读');
  } catch {
    // 请求层已统一提示
  } finally {
    readingAll.value = false;
  }
}

function selectFilter(key: InboxFilterKey) {
  activeFilter.value = key;
}

function clearQuery() {
  searchQuery.value = '';
  activeFilter.value = 'all';
}

onMounted(() => load(true));
</script>

<template>
  <main class="inbox-page">
    <header class="inbox-hero">
      <div class="hero-copy">
        <p class="eyebrow">MESSAGE CENTER</p>
        <h1>消息中心</h1>
        <p class="hero-desc">
          订单、退款、收款与船舶提醒集中在这里；图标、颜色与类型标签区分新订单、已取消、退款审核等消息，点击可直接进入对应业务详情。
        </p>
      </div>
      <div class="hero-side">
        <div class="unread-stat" :class="{ quiet: unreadTotal === 0 }">
          <strong>{{ unreadTotal }}</strong>
          <span>{{ unreadTotal > 0 ? '条未读' : '已全部读完' }}</span>
        </div>
        <ElButton
          class="read-all-btn"
          type="primary"
          size="large"
          :icon="Check"
          :disabled="unreadTotal === 0 || readingAll"
          :loading="readingAll"
          @click="readAll"
        >
          全部已读
        </ElButton>
      </div>
    </header>

    <section class="inbox-toolbar" aria-label="消息筛选与搜索">
      <div class="filter-tabs">
        <button
          v-for="option in filterOptions"
          :key="option.key"
          type="button"
          class="filter-tab"
          :class="{
            active: activeFilter === option.key,
            empty: option.count === 0,
          }"
          :aria-pressed="activeFilter === option.key"
          @click="selectFilter(option.key)"
        >
          <span
            v-if="option.key !== 'all' && option.key !== 'unread'"
            class="filter-dot"
            :class="option.key"
            aria-hidden="true"
          ></span>
          {{ option.label }}
          <span class="filter-count">{{ option.count }}</span>
        </button>
      </div>
      <div class="toolbar-search">
        <ElInput
          v-model="searchQuery"
          class="search-input"
          clearable
          :prefix-icon="Search"
          placeholder="搜索标题、摘要、内容或卡片文案"
          aria-label="搜索通知"
        />
        <span class="load-hint">
          已加载 {{ stats.total }} 条<template v-if="searchQuery.trim()">
            · 匹配 {{ filterCounts.all }} 条</template
          >
        </span>
      </div>
    </section>

    <section class="inbox-list" :aria-busy="loading">
      <template v-if="loading && records.length === 0">
        <div
          v-for="index in 3"
          :key="index"
          class="notice-card skeleton-card"
          aria-hidden="true"
        >
          <ElSkeleton :rows="2" animated />
        </div>
      </template>

      <template v-else-if="rows.length > 0">
        <article
          v-for="row in rows"
          :key="row.item.recipientRecordId"
          class="notice-card"
          :class="{ 'is-static': !row.interactive, 'is-unread': row.unread }"
          :role="row.interactive ? 'button' : undefined"
          :tabindex="row.interactive ? 0 : undefined"
          :aria-label="row.interactive ? rowAriaLabel(row) : undefined"
          @click="row.interactive && open(row.item)"
          @keydown.enter.prevent="row.interactive && open(row.item)"
          @keydown.space.prevent="row.interactive && open(row.item)"
        >
          <div
            class="notice-icon"
            :style="{
              color: row.typeMeta.color,
              background: row.typeMeta.soft,
            }"
            aria-hidden="true"
            role="presentation"
          >
            <component :is="row.typeMeta.icon" />
          </div>

          <div class="notice-body">
            <div class="notice-head">
              <h2>{{ row.item.title }}</h2>
              <span
                v-if="row.unread"
                class="unread-dot"
                aria-hidden="true"
              ></span>
              <div class="notice-tags">
                <span
                  class="tag-type"
                  :style="{
                    color: row.typeMeta.color,
                    background: row.typeMeta.soft,
                  }"
                >
                  {{ row.typeMeta.label }}
                </span>
                <span v-if="row.item.priority === 'HIGH'" class="tag-priority">
                  重要
                </span>
              </div>
            </div>

            <p class="summary">
              {{ row.item.summary || row.item.content }}
            </p>

            <div v-if="row.cardTitle || row.amount" class="card-chips">
              <span v-if="row.cardTitle" class="chip chip-title">
                {{ row.cardTitle }}
              </span>
              <span v-if="row.amount" class="chip chip-amount hx-money">
                ￥{{ formatMoney(row.amount) }}
              </span>
            </div>

            <div class="notice-foot">
              <span class="sender">{{ row.item.senderName || '系统' }}</span>
              <span class="dot-sep" aria-hidden="true">·</span>
              <time :title="row.item.publishTime || row.item.receivedTime">
                {{ row.timeText }}
              </time>
            </div>
          </div>

          <div class="notice-actions">
            <ElButton
              class="notice-hide"
              link
              type="info"
              size="small"
              title="从收件箱隐藏"
              aria-label="从收件箱隐藏这条通知"
              @click.stop="hide(row.item)"
              @keydown.stop
            >
              隐藏
            </ElButton>
            <span v-if="row.link" class="notice-cta" aria-hidden="true">
              {{ row.ctaLabel }}
              <ElIcon><ArrowRight /></ElIcon>
            </span>
          </div>
        </article>

        <div class="list-footer">
          <ElButton
            v-if="hasMore"
            class="load-more"
            :loading="loadingMore"
            @click="load(false)"
          >
            加载更早通知
          </ElButton>
          <p v-else class="list-end">没有更早的通知了</p>
        </div>
      </template>

      <ElEmpty v-else :description="emptyDescription" :image-size="88">
        <div class="empty-actions">
          <ElButton v-if="hasActiveQuery" @click="clearQuery">
            清除筛选
          </ElButton>
          <ElButton v-if="hasMore" :loading="loadingMore" @click="load(false)">
            加载更早通知
          </ElButton>
          <ElButton
            v-else-if="!hasActiveQuery"
            :icon="Refresh"
            @click="load(true)"
          >
            刷新
          </ElButton>
        </div>
      </ElEmpty>
    </section>
  </main>
</template>

<style scoped>
.inbox-page {
  --inbox-ink: var(--el-text-color-primary);
  --inbox-muted: var(--el-text-color-secondary);
  --inbox-line: var(--el-border-color-lighter);
  --inbox-primary: var(--el-color-primary);
  --inbox-primary-soft: var(--el-color-primary-light-9);

  /* 品牌视觉：深色 hero 的渐变、白色文字与半透明覆盖层集中在这里 */
  --hero-bg:
    radial-gradient(circle at 85% 0, rgb(56 189 248 / 22%), transparent 40%),
    linear-gradient(125deg, #0f1e3d 0%, #173f5f 55%, #2563eb 100%);
  --hero-text: #fff;
  --hero-text-soft: rgb(255 255 255 / 72%);
  --hero-eyebrow: #93c5fd;
  --hero-quiet: #86efac;
  --hero-chip-bg: rgb(255 255 255 / 12%);
  --hero-chip-border: rgb(255 255 255 / 18%);
  --hero-chip-text-soft: rgb(255 255 255 / 68%);
  --hero-cta-bg: #fff;
  --hero-cta-text: #173f5f;
  --hero-cta-hover-bg: #f0f6ff;
  --hero-cta-shadow: 0 10px 24px rgb(8 25 48 / 26%);
  --hero-cta-shadow-hover: 0 12px 26px rgb(8 25 48 / 32%);
  --hero-shadow: 0 18px 45px rgb(16 42 67 / 18%);

  /* 与 hx-layout-container 同机制：绝对定位铺满内容区，滚动收进内容区，避免 document 全屏滚动条。
     页面自身不留 padding（否则 sticky 筛选条贴不到顶），留白由各区块的 width/margin 承担 */
  position: absolute;
  inset: 0;
  overflow-y: auto;
  background:
    radial-gradient(
      circle at 88% 0,
      var(--inbox-primary-soft),
      transparent 28%
    ),
    var(--el-bg-color-page);
}

/* ---------- 头部 ---------- */
.inbox-hero {
  display: flex;
  gap: 28px;
  align-items: center;
  justify-content: space-between;
  width: min(1080px, calc(100% - 40px));
  padding: 30px 32px;
  margin: 20px auto 16px;
  color: var(--hero-text);
  background: var(--hero-bg);
  border-radius: 18px;
  box-shadow: var(--hero-shadow);
}

.eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: var(--hero-eyebrow);
  letter-spacing: 0.2em;
}

.hero-copy h1 {
  margin: 8px 0;
  font-family: 'Noto Serif SC', serif;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.hero-desc {
  max-width: 620px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--hero-text-soft);
}

.hero-side {
  display: flex;
  flex: none;
  gap: 18px;
  align-items: center;
}

.unread-stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 10px 18px;
  background: var(--hero-chip-bg);
  border: 1px solid var(--hero-chip-border);
  border-radius: 12px;
  backdrop-filter: blur(6px);
}

.unread-stat strong {
  font-family: 'Noto Serif SC', serif;
  font-size: 26px;
  line-height: 1;
}

.unread-stat span {
  margin-top: 4px;
  font-size: 11px;
  color: var(--hero-chip-text-soft);
  letter-spacing: 0.08em;
}

.unread-stat.quiet strong {
  color: var(--hero-quiet);
}

.read-all-btn {
  height: 42px;
  padding: 0 22px;
  font-weight: 600;
  color: var(--hero-cta-text) !important;
  background: var(--hero-cta-bg) !important;
  border-color: transparent !important;
  box-shadow: var(--hero-cta-shadow);
  transition:
    transform 0.18s,
    background 0.18s,
    box-shadow 0.18s;
}

.read-all-btn:hover:not(.is-disabled) {
  background: var(--hero-cta-hover-bg) !important;
  box-shadow: var(--hero-cta-shadow-hover);
  transform: translateY(-1px);
}

.read-all-btn.is-disabled,
.read-all-btn.is-disabled:hover {
  color: var(--hero-chip-text-soft) !important;
  background: var(--hero-chip-bg) !important;
  box-shadow: none;
  transform: none;
}

/* ---------- 工具栏 ---------- */
.inbox-toolbar {
  /* 滚动发生在 .inbox-page，固定筛选条不随消息列表滚走；
     top:0 完全贴顶，默认间隙由上方 hero 的自然流位置提供，滚动后随即吸死 */
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  width: min(1080px, calc(100% - 40px));
  padding: 14px 18px;
  margin: 0 auto 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--inbox-line);
  border-radius: 14px;
  box-shadow: var(--el-box-shadow-light);

  /* 玻璃拟态：消息卡片从筛选条下方滚过时呈磨砂效果。
     不支持背景模糊的环境保持上面那行不透明底色，避免文字叠影 */
  @supports (backdrop-filter: blur(1px)) {
    background: color-mix(in srgb, var(--el-bg-color) 65%, transparent);
    backdrop-filter: blur(10px) saturate(1.4);
  }
}

.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-tab {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  padding: 7px 13px;
  font-size: 13px;
  color: var(--inbox-muted);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  transition:
    background 0.18s,
    color 0.18s,
    border-color 0.18s;
}

.filter-tab:hover {
  color: var(--inbox-primary);
  background: var(--inbox-primary-soft);
}

.filter-tab.active {
  font-weight: 600;
  color: var(--inbox-primary);
  background: var(--inbox-primary-soft);
  border-color: var(--el-color-primary-light-7);
}

.filter-tab.empty {
  color: var(--el-text-color-placeholder);
}

.filter-tab:focus-visible {
  outline: 2px solid var(--inbox-primary);
  outline-offset: 2px;
}

.filter-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.filter-dot.order {
  background: var(--el-color-primary);
}

.filter-dot.refund {
  background: var(--el-color-warning);
}

.filter-dot.system {
  background: var(--el-color-info);
}

.filter-dot.vessel {
  background: var(--el-color-success);
}

.filter-count {
  min-width: 18px;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  text-align: center;
  background: var(--el-fill-color-light);
  border-radius: 999px;
}

.filter-tab.active .filter-count {
  color: var(--el-color-white);
  background: var(--inbox-primary);
}

.toolbar-search {
  display: flex;
  flex: 1 1 320px;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
  min-width: 260px;
}

.search-input {
  max-width: 340px;
}

.toolbar-search :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.load-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

/* ---------- 卡片列表 ---------- */
.inbox-list {
  width: min(1080px, calc(100% - 40px));
  margin: 0 auto 20px;
}

.notice-card {
  position: relative;
  display: grid;
  grid-template-areas: 'icon body actions';
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: start;
  padding: 20px 22px 20px 26px;
  margin-bottom: 12px;
  overflow: hidden;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--inbox-line);
  border-radius: 14px;
  box-shadow: var(--el-box-shadow-lighter);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

/* 未读左侧色条 */
.notice-card::before {
  position: absolute;
  top: 14px;
  bottom: 14px;
  left: 0;
  width: 4px;
  content: '';
  background: transparent;
  border-radius: 0 4px 4px 0;
  transition: background 0.2s;
}

.notice-card.is-unread {
  background: linear-gradient(
    180deg,
    var(--el-color-primary-light-9) 0%,
    var(--el-bg-color) 60%
  );
  border-color: var(--el-color-primary-light-7);
}

.notice-card.is-unread::before {
  background: linear-gradient(
    180deg,
    var(--el-color-primary),
    var(--el-color-primary-light-3)
  );
}

.notice-card:not(.is-static):hover,
.notice-card:not(.is-static):focus-visible {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--el-box-shadow-light);
  transform: translateY(-2px);
}

.notice-card.is-static {
  cursor: default;
}

.notice-card:focus-visible {
  outline: 2px solid var(--inbox-primary);
  outline-offset: 2px;
}

.skeleton-card {
  display: block;
  cursor: default;
}

.skeleton-card:hover {
  border-color: var(--inbox-line);
  box-shadow: var(--el-box-shadow-lighter);
  transform: none;
}

.notice-icon {
  display: grid;
  grid-area: icon;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
}

.notice-icon svg {
  width: 20px;
  height: 20px;
}

.notice-body {
  grid-area: body;
  min-width: 0;
}

.notice-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.notice-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--inbox-ink);
  overflow-wrap: anywhere;
}

.is-unread .notice-head h2 {
  font-weight: 700;
}

.unread-dot {
  width: 8px;
  height: 8px;
  background: var(--inbox-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}

.notice-tags {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.tag-type {
  padding: 2px 8px;
  font-size: 11px;
  border-radius: 999px;
}

.tag-priority {
  padding: 2px 8px;
  font-size: 11px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 999px;
}

.summary {
  display: -webkit-box;
  margin: 8px 0 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  font-size: 13px;
  line-height: 1.65;
  color: var(--inbox-muted);
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
}

.card-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.chip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 3px 9px;
  font-size: 11px;
  border-radius: 8px;
}

.chip-title {
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.chip-amount {
  font-weight: 600;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
}

.notice-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.dot-sep {
  color: var(--el-border-color);
}

.notice-actions {
  display: flex;
  flex: none;
  flex-direction: column;
  grid-area: actions;
  gap: 12px;
  align-items: flex-end;
  align-self: stretch;
  justify-content: space-between;
}

.notice-hide {
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.18s;
}

.notice-card:hover .notice-hide,
.notice-card:focus-within .notice-hide,
.notice-card:focus-visible .notice-hide {
  pointer-events: auto;
  opacity: 1;
}

.notice-hide:focus-visible {
  outline: 2px solid var(--inbox-primary);
  outline-offset: 2px;
  border-radius: 4px;
}

.notice-cta {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--inbox-primary);
  white-space: nowrap;
  transition: transform 0.18s;
}

.notice-card:hover .notice-cta {
  transform: translateX(2px);
}

/* ---------- 列表底部 / 空状态 ---------- */
.list-footer {
  display: flex;
  justify-content: center;
  padding: 8px 0 4px;
}

.load-more {
  min-width: 160px;
}

.list-end {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.empty-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 4px;
}

/* ---------- 响应式：窄屏工具栏换行、操作区移到内容下方 ---------- */
/* stylelint-disable-next-line order/order -- 媒体查询必须排在基础规则之后，否则同优先级覆盖会被基础规则反超 */
@media (max-width: 900px) {
  .inbox-page {
    padding: 14px;
  }

  .inbox-hero {
    flex-direction: column;
    gap: 18px;
    align-items: flex-start;
    padding: 24px;
  }

  .hero-side {
    justify-content: space-between;
    width: 100%;
  }

  .inbox-toolbar {
    align-items: stretch;
  }

  .toolbar-search {
    flex: 1 1 100%;
    justify-content: space-between;
    min-width: 0;
  }

  .search-input {
    flex: 1;
    max-width: none;
  }

  .notice-card {
    grid-template-areas:
      'icon body'
      'actions actions';
    grid-template-columns: auto minmax(0, 1fr);
    padding: 18px 18px 18px 22px;
  }

  .notice-actions {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    padding-top: 12px;
    margin-top: 2px;
    border-top: 1px dashed var(--inbox-line);
  }

  /* 触屏没有 hover：隐藏按钮常显 */
  .notice-hide {
    pointer-events: auto;
    opacity: 1;
  }
}

/* stylelint-disable-next-line order/order -- 同上：保持 max-width 覆盖在基础规则之后 */
@media (max-width: 640px) {
  .hero-copy h1 {
    font-size: 24px;
  }

  .hero-side {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .unread-stat {
    flex-direction: row;
    gap: 8px;
    align-items: baseline;
    justify-content: center;
  }

  .filter-tabs {
    width: 100%;
  }
}
</style>
