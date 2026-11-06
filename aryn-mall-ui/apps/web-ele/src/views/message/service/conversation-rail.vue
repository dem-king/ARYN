<script setup lang="ts">
import type {
  ConversationFilterState,
  ServiceTab,
  ServiceTabOption,
} from './service-state';

import type {
  Conversation,
  ConversationStatus,
  QueueStats,
} from '#/api/message/types';
import type { PresenceStatus } from '#/store/agent-presence';

import { onBeforeUnmount, onMounted, ref } from 'vue';

import {
  ElButton,
  ElEmpty,
  ElInput,
  ElOption,
  ElPopover,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import ConversationItem from './conversation-item.vue';
import {
  conversationStatusLabel,
  formatWaitingDuration,
} from './service-state';

/** 接待状态提示条（结构与 useDeskPresence.presenceNotice 对齐）。 */
interface PresenceNotice {
  action?: 'config' | 'online' | 'retry';
  text: string;
  tone: 'muted' | 'warning';
}

/**
 * 会话侧栏（展示组件 + 两处轻量本地 UI 状态）：
 * 品牌区/接待状态/队列统计/搜索/精细筛选/页签/列表三态与会话行。
 *
 * <p>所有业务状态由页面传入、用户动作以事件回传；搜索框聚焦通过
 * `defineExpose({ focusSearch })` 暴露给页面快捷键（`/` 或 `Alt+K`）。
 * 等待时长每 60s 刷新一次的 `now` 也由本组件持有并下发给会话行。</p>
 */
const props = defineProps<{
  /** 当前打开的会话 ID（会话行高亮）。 */
  activeId?: string;
  activeTab: ServiceTab;
  /** 主管筛选负责人时的候选（懒加载，可能为空）。 */
  assigneeLoading: boolean;
  assigneeOptions: Array<{ label: string; value: string }>;
  autoAccept: boolean;
  /** 是否展示「负责人」筛选（仅主管）。 */
  canFilterAssignee: boolean;
  configured: boolean;
  conversationKeyword: string;
  /** 全量会话（用于区分「暂无会话」与「无匹配」）。 */
  conversations: Conversation[];
  enabled: boolean;
  filteredConversations: Conversation[];
  filters: ConversationFilterState;
  filterTags: Array<{ key: string; label: string }>;
  hasActiveFilters: boolean;
  /** 是否处于搜索/筛选态：决定空态文案与清除入口。 */
  hasActiveQuery: boolean;
  hasMore: boolean;
  isWindowMode: boolean;
  listFailed: boolean;
  listLoading: boolean;
  presence: PresenceStatus;
  presenceCapacity: string;
  presenceNotice?: PresenceNotice;
  presenceOptions: Array<{ label: string; value: PresenceStatus }>;
  /** 队列统计（无权限/失败时为空，静默隐藏）。 */
  queueStats?: QueueStats;
  /** Redis/presence 疑似降级：展示「在线状态暂不可用」而不是 0。 */
  queueStatsDegraded: boolean;
  /** 窄屏单栏且已打开会话时隐藏侧栏（页面布局推导）。 */
  railHidden: boolean;
  syncing: boolean;
  tabCounts: Record<ServiceTab, number>;
  tabs: ServiceTabOption[];
}>();

const emit = defineEmits<{
  changeTab: [tab: ServiceTab];
  clearFilters: [];
  clearQuery: [];
  configAgent: [];
  goOnline: [];
  initiateConversation: [];
  loadMore: [];
  openFilters: [];
  openWindow: [];
  presenceChange: [value: PresenceStatus];
  reload: [];
  retryPresence: [];
  selectConversation: [conversation: Conversation];
  'update:conversationKeyword': [value: string];
  'update:filter': [patch: Partial<ConversationFilterState>];
}>();

/** 精细筛选的状态候选（顺序与契约 §2.2 一致）。 */
const STATUS_FILTER_OPTIONS: Array<{
  label: string;
  value: ConversationStatus;
}> = (['WAITING', 'ASSIGNED', 'ACTIVE', 'CLOSED'] as ConversationStatus[]).map(
  (value) => ({ label: conversationStatusLabel(value), value }),
);

const searchInputRef = ref<InstanceType<typeof ElInput>>();
/** 当前时间：每 60s 刷新一次，驱动等待时长（契约 §3.1 展示口径）。 */
const now = ref(Date.now());
let nowTimer: ReturnType<typeof setInterval> | undefined;

onMounted(() => {
  nowTimer = setInterval(() => {
    now.value = Date.now();
  }, 60_000);
});
onBeforeUnmount(() => {
  if (nowTimer) clearInterval(nowTimer);
});

/** 快捷键聚焦会话搜索框。 */
function focusSearch() {
  searchInputRef.value?.focus();
}

defineExpose({ focusSearch });

function onPresenceChange(value: PresenceStatus) {
  emit('presenceChange', value);
}

function toggleStatus(status: ConversationStatus) {
  const current = props.filters.statuses;
  const next = current.includes(status)
    ? current.filter((item) => item !== status)
    : [...current, status];
  emit('update:filter', { statuses: next });
}

function onQueueCodeChange(value: unknown) {
  const queueCode = value === null || value === undefined ? '' : String(value);
  emit('update:filter', { queueCode: queueCode.trim() || undefined });
}

function onAssigneeChange(value: unknown) {
  const assignedStaffId =
    value === null || value === undefined ? '' : String(value);
  if (!assignedStaffId) {
    emit('update:filter', {
      assignedStaffId: undefined,
      assignedStaffName: undefined,
    });
    return;
  }
  const matched = props.assigneeOptions.find(
    (option) => option.value === assignedStaffId,
  );
  emit('update:filter', {
    assignedStaffId,
    assignedStaffName: matched?.label,
  });
}
</script>

<template>
  <aside class="conversation-rail" :class="{ 'rail-hidden': props.railHidden }">
    <div class="desk-brand">
      <div class="desk-brand-copy">
        <span class="pulse"></span>
        <div><strong>客服调度台</strong><small>SHARED SERVICE POOL</small></div>
      </div>
      <div class="brand-actions">
        <ElButton
          v-if="!props.isWindowMode"
          class="ghost-trigger window-trigger"
          size="small"
          @click="emit('openWindow')"
        >
          独立窗口
        </ElButton>
        <ElButton
          v-access:code="'message:conversation:initiate'"
          class="ghost-trigger"
          size="small"
          @click="emit('initiateConversation')"
        >
          主动联系
        </ElButton>
        <ElButton
          v-access:code="'message:service:agent'"
          class="config-trigger"
          size="small"
          @click="emit('configAgent')"
        >
          坐席设置
        </ElButton>
      </div>
    </div>

    <div class="presence-row">
      <span>
        接待状态
        <em v-if="props.presenceCapacity" class="presence-capacity">
          {{ props.presenceCapacity }}
        </em>
        <em v-if="props.autoAccept" class="presence-auto"> 自动接待 </em>
      </span>
      <ElSelect
        class="presence-select"
        size="small"
        :disabled="!props.configured || !props.enabled"
        :loading="props.syncing"
        :model-value="props.presence"
        @update:model-value="onPresenceChange"
      >
        <ElOption
          v-for="option in props.presenceOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </ElSelect>
    </div>

    <div
      v-if="props.presenceNotice"
      class="presence-tip"
      :class="props.presenceNotice.tone"
    >
      <span>{{ props.presenceNotice.text }}</span>
      <ElButton
        v-if="props.presenceNotice.action === 'online'"
        size="small"
        type="primary"
        @click="emit('goOnline')"
      >
        一键上线
      </ElButton>
      <ElButton
        v-else-if="props.presenceNotice.action === 'retry'"
        size="small"
        @click="emit('retryPresence')"
      >
        重试
      </ElButton>
      <ElButton
        v-else-if="props.presenceNotice.action === 'config'"
        v-access:code="'message:service:agent'"
        size="small"
        @click="emit('configAgent')"
      >
        去配置
      </ElButton>
    </div>

    <!-- 队列统计（独立于 attention.waitingTotal：后者只统计单队列，契约 §5.4） -->
    <div
      v-if="props.queueStats"
      class="queue-stats"
      role="status"
      aria-label="队列统计"
    >
      <span class="queue-stats-title">队列</span>
      <span>等待 {{ props.queueStats.total.waitingCount }}</span>
      <span v-if="props.queueStats.total.oldestWaitingSince">
        最久
        {{
          formatWaitingDuration(props.queueStats.total.oldestWaitingSince, now)
        }}
      </span>
      <span v-if="props.queueStatsDegraded" class="queue-stats-degraded">
        在线状态暂不可用
      </span>
      <template v-else>
        <span>在线 {{ props.queueStats.total.onlineAgentCount }}</span>
        <span>容量 {{ props.queueStats.total.availableCapacity }}</span>
      </template>
    </div>

    <div class="rail-search-row">
      <ElInput
        ref="searchInputRef"
        :model-value="props.conversationKeyword"
        class="rail-search"
        clearable
        placeholder="搜索会员 / 摘要 / ID"
        size="small"
        @update:model-value="emit('update:conversationKeyword', $event)"
      />
      <ElPopover
        placement="bottom-end"
        :width="300"
        trigger="click"
        popper-class="desk-filter-popper"
      >
        <template #reference>
          <ElButton
            class="filter-trigger"
            :class="{ active: props.hasActiveFilters }"
            size="small"
            title="精细筛选"
            @click="emit('openFilters')"
          >
            筛选<template v-if="props.filterTags.length > 0">
              · {{ props.filterTags.length }}
            </template>
          </ElButton>
        </template>
        <div class="filter-panel">
          <p v-if="props.activeTab === 'waiting'" class="filter-hint">
            待领取页签固定走共享池，精细筛选在其它页签生效
          </p>
          <div class="filter-row">
            <span class="filter-label">仅未读</span>
            <ElSwitch
              :model-value="props.filters.unreadOnly"
              @update:model-value="
                (value) => emit('update:filter', { unreadOnly: Boolean(value) })
              "
            />
          </div>
          <div class="filter-row">
            <span class="filter-label">队列</span>
            <ElSelect
              class="filter-control"
              :model-value="props.filters.queueCode"
              allow-create
              clearable
              default-first-option
              filterable
              placeholder="全部队列"
              size="small"
              @update:model-value="onQueueCodeChange"
            >
              <ElOption label="DEFAULT" value="DEFAULT" />
            </ElSelect>
          </div>
          <div class="filter-row filter-row--status">
            <span class="filter-label">状态</span>
            <div class="filter-chips">
              <button
                v-for="option in STATUS_FILTER_OPTIONS"
                :key="option.value"
                type="button"
                class="filter-chip"
                :class="{
                  active: props.filters.statuses.includes(option.value),
                }"
                :aria-pressed="props.filters.statuses.includes(option.value)"
                @click="toggleStatus(option.value)"
              >
                {{ option.label }}
              </button>
            </div>
          </div>
          <div v-if="props.canFilterAssignee" class="filter-row">
            <span class="filter-label">负责人</span>
            <ElSelect
              class="filter-control"
              :model-value="props.filters.assignedStaffId"
              clearable
              filterable
              :loading="props.assigneeLoading"
              placeholder="全部客服"
              size="small"
              @update:model-value="onAssigneeChange"
            >
              <ElOption
                v-for="option in props.assigneeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>
          </div>
          <div class="filter-footer">
            <ElButton
              size="small"
              :disabled="!props.hasActiveFilters"
              @click="emit('clearFilters')"
            >
              清除筛选
            </ElButton>
          </div>
        </div>
      </ElPopover>
    </div>

    <div v-if="props.filterTags.length > 0" class="filter-tags">
      <span v-for="tag in props.filterTags" :key="tag.key" class="filter-tag">
        {{ tag.label }}
      </span>
      <button type="button" class="filter-clear" @click="emit('clearFilters')">
        清除
      </button>
    </div>

    <div
      class="segment"
      role="tablist"
      :style="{ gridTemplateColumns: `repeat(${props.tabs.length}, 1fr)` }"
    >
      <button
        v-for="tab in props.tabs"
        :key="tab.key"
        type="button"
        role="tab"
        :aria-selected="props.activeTab === tab.key"
        :class="{ active: props.activeTab === tab.key }"
        @click="emit('changeTab', tab.key)"
      >
        {{ tab.label }}
        <span v-if="props.tabCounts[tab.key]" class="tab-count">
          {{ props.tabCounts[tab.key] > 99 ? '99+' : props.tabCounts[tab.key] }}
        </span>
      </button>
    </div>

    <div v-loading="props.listLoading" class="conversation-list">
      <div
        v-if="props.listLoading && props.conversations.length === 0"
        class="rail-skeleton"
      >
        <span v-for="index in 6" :key="index" class="skeleton-row"></span>
      </div>
      <div
        v-if="
          !props.listLoading &&
          props.listFailed &&
          props.conversations.length === 0
        "
        class="rail-error"
      >
        <p>会话列表加载失败，请检查网络后重试</p>
        <ElButton size="small" @click="emit('reload')"> 重新加载 </ElButton>
      </div>
      <template v-if="!props.listLoading && !props.listFailed">
        <div
          v-if="props.conversations.length === 0 && !props.hasActiveQuery"
          class="rail-empty"
        >
          <ElEmpty description="暂无会话" :image-size="60" />
        </div>
        <div
          v-else-if="props.conversations.length === 0"
          class="rail-empty rail-empty--actions"
        >
          <p class="rail-empty-text">没有匹配当前搜索/筛选条件的会话</p>
          <ElButton size="small" @click="emit('clearQuery')">
            清除条件
          </ElButton>
        </div>
        <div
          v-else-if="props.filteredConversations.length === 0"
          class="rail-empty"
        >
          <p class="rail-empty-text">
            没有匹配「{{ props.conversationKeyword }}」的会话
          </p>
        </div>
      </template>
      <ConversationItem
        v-for="item in props.filteredConversations"
        :key="item.id"
        :active="props.activeId === item.id"
        :item="item"
        :now="now"
        :show-tags="
          props.activeTab === 'waiting' || props.activeTab === 'supervisor'
        "
        @select="emit('selectConversation', $event)"
      />
      <ElButton
        v-if="props.hasMore && !props.listLoading"
        class="load-more"
        size="small"
        @click="emit('loadMore')"
      >
        加载更多
      </ElButton>
    </div>
  </aside>
</template>

<style scoped>
@keyframes desk-skeleton {
  0% {
    background-position: 0 0;
  }

  100% {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-row {
    animation: none;
  }
}

.conversation-rail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--desk-rail-bg);
  border-right: 1px solid var(--desk-line-soft);
}

.conversation-rail.rail-hidden {
  display: none;
}

/*
 * 品牌区：品牌文案独占一行，操作按钮另起一行右对齐。
 *
 * 会话栏宽度由布局固定（330/300/280），不是弹性列：「品牌 + 3 个按钮」放不进同一行
 * （按钮约 228px + 品牌约 110px > 内容宽 240~290px）。此前 `.brand-actions` 是
 * `flex: none`，收缩压力全部落在 `.desk-brand-copy` 上，17px 的「客服调度台」被压成
 * 一行一个字并纵向溢出，与右侧「独立窗口」按钮叠在一起（截图缺陷）。
 * 改为 flex-wrap + 品牌占满首行，按钮永远有完整宽度，窄栏下也不会互相遮挡。
 */
.desk-brand {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
  align-items: center;
  padding: 20px 20px 16px;
  border-bottom: 1px solid var(--desk-line-faint);
}

.desk-brand-copy {
  display: flex;
  flex: 1 1 100%;
  gap: 13px;
  align-items: center;
  min-width: 0;
}

/* 品牌文案不允许逐字折行：溢出时省略，而不是被压成一列字并盖住按钮 */
.desk-brand-copy > div {
  min-width: 0;
}

.desk-brand strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 17px;
  white-space: nowrap;
}

.desk-brand small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 9px;
  color: var(--desk-dim);
  letter-spacing: 0.18em;
  white-space: nowrap;
}

/* 按钮组占一行、靠右；宽度不够时允许换行，绝不与品牌文案重叠 */
.brand-actions {
  display: flex;
  flex: 1 1 100%;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  justify-content: flex-end;
}

.config-trigger,
.ghost-trigger {
  flex: none;
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-color: var(--desk-accent-line);
}

.ghost-trigger {
  color: var(--desk-muted);
  background: transparent;
  border-color: var(--desk-line);
}

.config-trigger:hover,
.ghost-trigger:hover {
  color: var(--desk-accent-contrast);
  background: var(--desk-accent-soft-hover);
  border-color: var(--desk-accent-line-hover);
}

.pulse {
  flex: none;
  width: 12px;
  height: 12px;
  background: var(--desk-accent);
  border-radius: 50%;
  box-shadow:
    0 0 0 6px var(--desk-accent-soft),
    0 0 22px var(--desk-accent);
}

/*
 * 接待状态行：标签整体不拆字，窄栏时整块换行而不是把「自动接待」折断成「自动接/待」。
 * 状态选择框用 `margin-left: auto`：换行后仍贴右，保持与首行一致的视觉对齐。
 */
.presence-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  font-size: 12px;
  color: var(--desk-muted);
}

.presence-row > span {
  white-space: nowrap;
}

.presence-select {
  width: 118px;
  margin-left: auto;
}

.presence-tip {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  margin: 0 16px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--desk-warning);
  background: var(--desk-warning-soft);
  border: 1px solid var(--desk-warning-line);
  border-radius: 10px;
}

.presence-tip.muted {
  color: var(--desk-muted);
  background: var(--desk-hover);
  border-color: var(--desk-line-soft);
}

.presence-capacity,
.presence-auto {
  padding: 0 5px;
  margin-left: 4px;
  font-size: 10px;
  font-style: normal;
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-radius: 6px;
}

.presence-auto {
  color: var(--desk-dim);
  background: var(--desk-hover);
}

/* 队列统计条：一行小字，失败/无权限时整条隐藏 */
.queue-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: center;
  padding: 8px 12px;
  margin: 0 16px 10px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--desk-muted);
  background: var(--desk-accent-soft);
  border: 1px solid var(--desk-line-soft);
  border-radius: 10px;
}

.queue-stats-title {
  padding: 0 5px;
  font-size: 10px;
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft-hover);
  border-radius: 6px;
}

.queue-stats-degraded {
  color: var(--desk-warning);
}

.rail-search-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0 16px 10px;
}

.rail-search-row .rail-search {
  flex: 1;
  margin: 0;
}

.filter-trigger {
  flex: none;
  color: var(--desk-muted);
  background: transparent;
  border-color: var(--desk-line);
}

.filter-trigger.active,
.filter-trigger:hover {
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-color: var(--desk-accent-line);
}

.filter-panel {
  /* 弹层 teleport 到 body，拿不到 .service-desk 作用域的 --el-* 映射，这里显式给控件深色令牌 */
  --el-bg-color: var(--desk-surface);
  --el-bg-color-overlay: var(--desk-surface);
  --el-border-color: var(--desk-line);
  --el-color-primary: var(--desk-accent);
  --el-fill-color-blank: var(--desk-canvas);
  --el-text-color-placeholder: var(--desk-dim);
  --el-text-color-primary: var(--desk-text);
  --el-text-color-regular: var(--desk-muted);

  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-control :deep(.el-select__wrapper) {
  color: var(--desk-text);
  background-color: var(--desk-canvas);
  box-shadow: 0 0 0 1px var(--desk-line) inset;
}

.filter-control :deep(.el-select__placeholder) {
  color: var(--desk-dim);
}

.filter-control :deep(.el-select__caret) {
  color: var(--desk-dim);
}

.filter-hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--desk-warning);
}

.filter-row {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.filter-label {
  flex: none;
  font-size: 12px;
  color: var(--desk-muted);
}

.filter-control {
  width: 180px;
}

.filter-row--status {
  align-items: flex-start;
}

.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.filter-chip {
  padding: 3px 9px;
  font-size: 11px;
  color: var(--desk-muted);
  background: transparent;
  border: 1px solid var(--desk-line);
  border-radius: 999px;
}

.filter-chip.active {
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-color: var(--desk-accent-line);
}

.filter-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
  border-top: 1px solid var(--desk-line-faint);
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  padding: 0 16px;
  margin-bottom: 10px;
}

.filter-tag {
  padding: 2px 8px;
  font-size: 10px;
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border: 1px solid var(--desk-accent-line);
  border-radius: 999px;
}

.filter-clear {
  padding: 2px 4px;
  font-size: 10px;
  color: var(--desk-dim);
  background: transparent;
}

.filter-clear:hover {
  color: var(--desk-accent-strong);
}

.segment {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 4px;
  margin: 4px 16px 12px;
  background: var(--desk-segment-bg);
  border-radius: 10px;
}

.segment button {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
  padding: 9px 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--desk-segment-text);
  white-space: nowrap;
  border-radius: 8px;
}

.segment button.active {
  color: var(--desk-segment-active-text);
  background: var(--desk-segment-active-bg);
}

.tab-count {
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  color: var(--desk-segment-active-text);
  text-align: center;
  background: rgb(45 212 191 / 22%);
  border-radius: 8px;
}

.conversation-list {
  flex: 1;
  min-height: 0;
  padding: 0 10px 20px;
  overflow: auto;
}

.rail-empty {
  padding: 8px 0 14px;
}

.rail-empty--actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.rail-empty-text {
  padding: 18px 12px;
  font-size: 12px;
  color: var(--desk-dim);
  text-align: center;
}

.rail-error {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  padding: 24px 12px;
  text-align: center;
}

.rail-error p {
  font-size: 12px;
  line-height: 1.6;
  color: var(--desk-warning);
}

.window-trigger {
  color: var(--desk-accent-strong);
  border-color: var(--desk-accent-line);
}

.load-more {
  width: 100%;
  margin-top: 8px;
  color: var(--desk-muted);
  background: transparent;
  border-color: var(--desk-line);
}

/* 骨架屏：首屏没有数据时用占位行，避免整块 loading 遮罩 */
.rail-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 6px;
}

.skeleton-row {
  height: 52px;
  background: linear-gradient(
    90deg,
    var(--desk-hover),
    var(--desk-accent-soft),
    var(--desk-hover)
  );
  background-size: 200% 100%;
  border-radius: 10px;
  animation: desk-skeleton 1.4s ease infinite;
}

/* 深色工作台里的空态文字跟随工作台配色，避免浅色模式默认灰字对比不足 */
.rail-empty :deep(.el-empty__description p) {
  color: var(--desk-muted);
}
</style>
