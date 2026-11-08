<script lang="ts" setup>
import type { NoticeAction, RowCommand } from './meta';

import type {
  NoticeDispatchStatus,
  NoticeRecord,
  NoticeStatus,
} from '#/api/message/types';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';

import {
  Bell,
  MoreFilled,
  Plus,
  Refresh,
  Search,
  View,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSegmented,
  ElTable,
  ElTableColumn,
  ElTooltip,
} from 'element-plus';

import {
  deleteNotice,
  getNoticePage,
  publishNotice,
  republishNotice,
  retryNoticeDispatch,
  revokeNotice,
} from '#/api/message/notice';
import Pagination from '#/components/pagination/index.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

import {
  categoryMeta,
  deliveryPercent,
  deliveryText,
  dispatchMeta,
  noticeActionPlan,
  ROW_COMMAND_LABELS,
  rowCommands,
  splitDateTime,
  statusMeta,
  targetTypesText,
} from './meta';
import NoticeDetail from './notice-detail.vue';
import NoticeEditor from './notice-editor.vue';

import './notice-ui.css';

/**
 * 「通知管理」页（消息中心 → 通知管理）。
 *
 * 业务语义：纯文本通知先存草稿，发布时冻结受众快照并按目标端（会员 / 工作人员）
 * 拆成若干异步分发任务；发布后正文不可改，只能撤回或重发；分发失败可重试。
 *
 * 页面结构：紧凑页头（标题 + 总数 + 新建）→ 吸顶筛选条（状态分段 + 搜索 + 刷新）
 * → 固定行高表格 → 底部常驻分页；新建/编辑用右侧编辑器抽屉，查看用详情抽屉。
 *
 * 与改造前的主要差异（均来自改动前基线实测）：
 * - 1280 视口下列宽合计 1260 > 可视 970，`fixed=right` 操作列把「创建时间」100% 盖住；
 *   现在列宽合计收敛到 ~930 且不再使用 fixed 列，任何 ≥1280 的视口都不产生横向溢出。
 * - 分类原是原始枚举（`VESSEL_CALL_CHANGE` 换行撑高行），统一走 `meta.ts` 的中文标签。
 * - 行高原来 68–142px 跳动，现在标题/摘要各限一行，行高恒定。
 * - 分页原来落在首屏之外，现在由 `hx-layout-container` 的标准 flex 规则钉在底部。
 */
const { hasAccessByCodes } = useAccess();

/** 状态分段：与后端 page 接口的 `status` 参数一一对应，空值表示全部。 */
type StatusFilter = '' | NoticeStatus;

const STATUS_FILTERS: Array<{ label: string; value: StatusFilter }> = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已撤回', value: 'REVOKED' },
];

/** 行内「更多」菜单的权限口径：与后端 `@SaCheckPermission` 逐项对齐。 */
const COMMAND_PERMISSIONS: Record<RowCommand, string> = {
  delete: 'message:notice:delete',
  edit: 'message:notice:edit',
  publish: 'message:notice:publish',
  republish: 'message:notice:publish',
  retry: 'message:notice:retry',
  revoke: 'message:notice:revoke',
};

const loading = ref(false);
const records = ref<NoticeRecord[]>([]);
const total = ref(0);
const query = reactive({
  current: 1,
  keyword: '',
  size: 10,
  status: '' as StatusFilter,
});

const editorOpen = ref(false);
const editingRecord = ref<NoticeRecord>();
const detailOpen = ref(false);
const activeRecord = ref<NoticeRecord>();

const hasFilters = computed(
  () => query.status !== '' || query.keyword.trim() !== '',
);

const emptyTitle = computed(() =>
  hasFilters.value ? '没有匹配的通知' : '还没有任何通知',
);

const emptyDescription = computed(() =>
  hasFilters.value
    ? '换个关键词或状态再试，也可以清除筛选查看全部通知。'
    : '新建一条通知草稿，发布后会按受众快照异步分发给会员或工作人员。',
);

/** 顶部主指标：列表总数（服务端 total，不是当前页条数）。 */
const totalLabel = computed(() => `${total.value}`);

/** 状态允许的动作 且 当前账号有对应权限码，才渲染进「更多」菜单。 */
function allowedCommands(row: NoticeRecord): RowCommand[] {
  return rowCommands(row).filter((command) =>
    hasAccessByCodes([COMMAND_PERMISSIONS[command]]),
  );
}

/** 危险动作排到分隔线之后，并与普通动作视觉分离。 */
function isDangerCommand(command: RowCommand) {
  return command === 'delete' || command === 'retry';
}

async function load() {
  loading.value = true;
  try {
    const page = await getNoticePage({
      current: query.current,
      keyword: query.keyword.trim() || undefined,
      size: query.size,
      status: query.status || undefined,
    });
    records.value = page.records ?? [];
    total.value = page.total ?? 0;
  } catch {
    // 请求层已统一提示，保留上一次数据避免整页闪空
  } finally {
    loading.value = false;
  }
}

function search() {
  query.current = 1;
  void load();
}

function resetFilters() {
  query.keyword = '';
  query.status = '';
  query.current = 1;
  void load();
}

/** ElSegmented 的 change 参数是 `string | number | boolean`，这里收窄成状态值。 */
function changeStatus(value: boolean | number | string) {
  const next = String(value) as StatusFilter;
  if (query.status === next) {
    return;
  }
  query.status = next;
  search();
}

function openCreate() {
  editingRecord.value = undefined;
  editorOpen.value = true;
}

function openEdit(row: NoticeRecord) {
  editingRecord.value = row;
  editorOpen.value = true;
}

function openDetail(row: NoticeRecord) {
  activeRecord.value = row;
  detailOpen.value = true;
}

async function confirm(action: NoticeAction, row: NoticeRecord) {
  const plan = noticeActionPlan(action, row.targetTypes);
  try {
    await ElMessageBox.confirm(plan.message, plan.title, {
      cancelButtonText: '取消',
      confirmButtonText: plan.ok,
      type: 'warning',
    });
  } catch {
    return undefined;
  }
  return plan;
}

/** 行内动作：确认 → 请求 → 提示 → 刷新。文案与详情抽屉同源（meta.ts）。 */
const ACTION_RUNNERS: Record<
  NoticeAction,
  (row: NoticeRecord) => Promise<unknown>
> = {
  delete: (row) => deleteNotice(row.id),
  publish: (row) => publishNotice(row.id),
  republish: (row) => republishNotice(row.id),
  retry: (row) => retryNoticeDispatch(row.id),
  revoke: (row) => revokeNotice(row.id),
};

async function runAction(action: NoticeAction, row: NoticeRecord) {
  const plan = await confirm(action, row);
  if (!plan) {
    return;
  }
  try {
    await ACTION_RUNNERS[action](row);
    ElMessage.success(plan.done);
    await afterMutation();
  } catch {
    // 请求层已统一提示，列表保持原样
  }
}

const COMMAND_HANDLERS: Record<
  RowCommand,
  (row: NoticeRecord) => Promise<void>
> = {
  delete: (row) => runAction('delete', row),
  edit: async (row) => openEdit(row),
  publish: (row) => runAction('publish', row),
  republish: (row) => runAction('republish', row),
  retry: (row) => runAction('retry', row),
  revoke: (row) => runAction('revoke', row),
};

async function handleCommand(command: RowCommand, row: NoticeRecord) {
  await COMMAND_HANDLERS[command](row);
}

/** 变更后刷新列表；详情抽屉打开时同步刷新其数据来源。 */
async function afterMutation() {
  detailOpen.value = false;
  await load();
}

function handleSaved() {
  editorOpen.value = false;
  void load();
}

function handleDetailRefresh() {
  void load();
}

/** 详情里点「编辑」：关详情、开编辑器，并沿用同一条记录。 */
function editFromDetail() {
  const record = activeRecord.value;
  detailOpen.value = false;
  if (record) {
    openEdit(record);
  }
}

function categoryLabel(row: NoticeRecord) {
  return categoryMeta(row.category).label;
}

function categoryTone(row: NoticeRecord) {
  return categoryMeta(row.category).tone;
}

function statusLabel(status: NoticeStatus) {
  return statusMeta(status).label;
}

function statusTone(status: NoticeStatus) {
  return statusMeta(status).tone;
}

function dispatchLabel(row: NoticeRecord) {
  return dispatchMeta(row).label;
}

function dispatchTone(row: NoticeRecord) {
  return dispatchMeta(row).tone;
}

function dispatchStatusOf(row: NoticeRecord): NoticeDispatchStatus {
  return (row.dispatchStatus ?? 'NONE') as NoticeDispatchStatus;
}

function createdParts(row: NoticeRecord) {
  return splitDateTime(row.createTime);
}

onMounted(load);

// 菜单开启 keepAlive 时，切回本页静默对齐一次（未开启时 onActivated 不触发，无副作用）
useKeepAliveRefresh(load);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view nt-page">
      <!-- 页头：紧凑、单行信息层级，不再占用半屏高度 -->
      <header class="nt-hero">
        <div class="nt-hero__main">
          <span class="nt-hero__badge" aria-hidden="true">
            <ElIcon :size="20"><Bell /></ElIcon>
          </span>
          <div class="nt-hero__copy">
            <nav class="nt-hero__eyebrow" aria-label="面包屑">
              消息中心 <span aria-hidden="true">/</span> 通知编排
            </nav>
            <h1 class="nt-hero__title">通知管理</h1>
            <p class="nt-hero__desc">
              纯文本发布 · 受众冻结 · 异步分发与撤回审计
            </p>
          </div>
        </div>
        <div class="nt-hero__side">
          <p class="nt-hero__metric">
            <strong class="nt-num">{{ totalLabel }}</strong>
            <span>条通知</span>
          </p>
          <ElButton
            v-access:code="'message:notice:add'"
            type="primary"
            :icon="Plus"
            @click="openCreate"
          >
            新建通知
          </ElButton>
        </div>
      </header>

      <!-- 吸顶筛选条：状态分段 + 关键词 + 刷新 -->
      <div class="nt-toolbar">
        <div class="nt-toolbar__left">
          <ElSegmented
            class="nt-seg"
            :model-value="query.status"
            :options="STATUS_FILTERS"
            @change="changeStatus"
          />
          <ElInput
            v-model="query.keyword"
            class="nt-search"
            clearable
            :prefix-icon="Search"
            placeholder="搜索标题、摘要或正文"
            aria-label="搜索通知"
            @clear="search"
            @keyup.enter="search"
          />
          <ElButton :icon="Search" @click="search">查询</ElButton>
          <ElButton v-if="hasFilters" link type="primary" @click="resetFilters">
            清除筛选
          </ElButton>
        </div>
        <div class="nt-toolbar__right">
          <ElTooltip content="刷新列表" placement="top">
            <ElButton
              circle
              :icon="Refresh"
              aria-label="刷新列表"
              :disabled="loading"
              @click="load"
            />
          </ElTooltip>
        </div>
      </div>

      <!-- 列表：行高恒定，标题可点开详情，操作收进「更多」 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table nt-table"
        :data="records"
        row-key="id"
        :show-overflow-tooltip="false"
        @row-dblclick="openDetail"
      >
        <template #empty>
          <div class="nt-empty">
            <span class="nt-empty__badge" aria-hidden="true">
              <ElIcon :size="24"><Bell /></ElIcon>
            </span>
            <p class="nt-empty__title">{{ emptyTitle }}</p>
            <p class="nt-empty__desc">{{ emptyDescription }}</p>
            <div class="nt-empty__actions">
              <ElButton v-if="hasFilters" @click="resetFilters">
                清除筛选
              </ElButton>
              <ElButton
                v-access:code="'message:notice:add'"
                type="primary"
                :icon="Plus"
                @click="openCreate"
              >
                新建通知
              </ElButton>
            </div>
          </div>
        </template>

        <ElTableColumn label="通知" min-width="264">
          <template #default="{ row }">
            <div class="nt-cell-main">
              <div class="nt-title-row">
                <button
                  type="button"
                  class="nt-title"
                  @click="openDetail(row as NoticeRecord)"
                >
                  {{ row.title }}
                </button>
                <span
                  v-if="row.priority === 'HIGH'"
                  class="nt-pill nt-pill--warning"
                >
                  重要
                </span>
                <span
                  v-if="row.senderType === 'SYSTEM'"
                  class="nt-pill nt-pill--info"
                >
                  系统生成
                </span>
              </div>
              <p class="nt-summary" :title="row.summary || row.content">
                {{ row.summary || row.content }}
              </p>
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn label="分类 / 目标端" width="128">
          <template #default="{ row }">
            <div class="nt-cell-stack">
              <span
                class="nt-pill"
                :class="`nt-pill--${categoryTone(row as NoticeRecord)}`"
              >
                {{ categoryLabel(row as NoticeRecord) }}
              </span>
              <span class="nt-cell-sub">
                {{ targetTypesText(row.targetTypes) }}
              </span>
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn align="center" label="状态" width="88">
          <template #default="{ row }">
            <span
              class="nt-pill"
              :class="`nt-pill--${statusTone(row.status as NoticeStatus)}`"
            >
              <i
                class="nt-dot"
                :class="`nt-dot--${statusTone(row.status)}`"
                aria-hidden="true"
              ></i>
              {{ statusLabel(row.status as NoticeStatus) }}
            </span>
          </template>
        </ElTableColumn>

        <ElTableColumn label="投递" width="186">
          <template #default="{ row }">
            <div class="nt-delivery">
              <div class="nt-delivery__head">
                <span
                  class="nt-pill"
                  :class="`nt-pill--${dispatchTone(row as NoticeRecord)}`"
                >
                  {{ dispatchLabel(row as NoticeRecord) }}
                </span>
                <span class="nt-num nt-delivery__count">
                  {{ deliveryText(row as NoticeRecord) }}
                </span>
              </div>
              <div
                class="nt-progress"
                role="progressbar"
                aria-label="已读占比"
                :aria-valuemax="100"
                :aria-valuemin="0"
                :aria-valuenow="deliveryPercent(row as NoticeRecord)"
              >
                <i
                  class="nt-progress__bar"
                  :class="{
                    'is-failed':
                      dispatchStatusOf(row as NoticeRecord) === 'FAILED',
                  }"
                  :style="{ width: `${deliveryPercent(row as NoticeRecord)}%` }"
                ></i>
              </div>
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn label="创建时间" width="122">
          <template #default="{ row }">
            <div class="nt-cell-stack nt-cell-stack--time">
              <span class="nt-num">{{
                createdParts(row as NoticeRecord).date
              }}</span>
              <span class="nt-cell-sub nt-num">{{
                createdParts(row as NoticeRecord).time
              }}</span>
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn align="right" label="操作" width="124">
          <template #default="{ row }">
            <div class="nt-actions">
              <ElButton
                class="nt-action"
                link
                type="primary"
                :icon="View"
                @click="openDetail(row as NoticeRecord)"
              >
                查看
              </ElButton>
              <ElDropdown
                v-if="allowedCommands(row as NoticeRecord).length > 0"
                trigger="click"
                placement="bottom-end"
                @command="
                  (cmd: any) =>
                    handleCommand(cmd as RowCommand, row as NoticeRecord)
                "
              >
                <ElButton
                  class="nt-action nt-action--more"
                  link
                  :icon="MoreFilled"
                  aria-label="更多操作"
                />
                <template #dropdown>
                  <ElDropdownMenu>
                    <ElDropdownItem
                      v-for="command in allowedCommands(row as NoticeRecord)"
                      :key="command"
                      :command="command"
                      :divided="isDangerCommand(command)"
                      :class="{ 'nt-danger': command === 'delete' }"
                    >
                      {{ ROW_COMMAND_LABELS[command] }}
                    </ElDropdownItem>
                  </ElDropdownMenu>
                </template>
              </ElDropdown>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>

      <!-- 分页：由 hx-layout-container 的 flex 规则钉在卡片底部 -->
      <Pagination
        v-model:current="query.current"
        v-model:size="query.size"
        :total="total"
        @change="load"
      />
    </div>

    <NoticeEditor
      v-model="editorOpen"
      :record="editingRecord"
      @saved="handleSaved"
    />

    <NoticeDetail
      v-model="detailOpen"
      :record="activeRecord"
      @edit="editFromDetail"
      @refresh="handleDetailRefresh"
    />
  </div>
</template>

<style scoped>
.nt-page {
  --nt-line: var(--el-border-color-lighter);

  gap: 10px;
}

/* ---------- 页头 ---------- */
.nt-hero {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background:
    radial-gradient(
      120% 220% at 0% 0%,
      var(--el-color-primary-light-9),
      transparent 58%
    ),
    var(--el-bg-color);
  border: 1px solid var(--nt-line);
  border-radius: 12px;
}

.nt-hero__main {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.nt-hero__badge {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: 11px;
}

.nt-hero__copy {
  min-width: 0;
}

.nt-hero__eyebrow {
  font-size: 12px;
  font-weight: 600;
  color: var(--nt-text-muted);
  letter-spacing: 0.04em;
}

.nt-hero__title {
  margin: 2px 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
  color: var(--el-text-color-primary);
}

.nt-hero__desc {
  font-size: 13px;
  line-height: 20px;
  color: var(--nt-text-muted);
}

.nt-hero__side {
  display: flex;
  flex: none;
  gap: 16px;
  align-items: center;
}

.nt-hero__metric {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}

.nt-hero__metric strong {
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.nt-hero__metric span {
  font-size: 11px;
  color: var(--nt-text-muted);
}

/* ---------- 筛选条 ---------- */
.nt-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 10px 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--nt-line);
  border-radius: 12px;

  @supports (backdrop-filter: blur(1px)) {
    background: color-mix(in srgb, var(--el-bg-color) 72%, transparent);
    box-shadow: var(--el-box-shadow-lighter);
    backdrop-filter: blur(10px) saturate(1.4);
  }
}

.nt-toolbar__left,
.nt-toolbar__right {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.nt-search {
  width: 260px;
}

/* 分段控件与查询控件统一圆角语言 */

/* 分段控件：class 挂在组件根上，直接选中即可（点选块用卡片色浮起） */
.nt-seg.el-segmented {
  --el-segmented-bg-color: var(--el-fill-color-light);
  --el-segmented-item-selected-bg-color: var(--el-bg-color);
  --el-segmented-item-selected-color: var(--el-color-primary);
  --el-segmented-color: var(--el-text-color-regular);

  padding: 3px;
  border-radius: 10px;
}

.nt-seg :deep(.el-segmented__item-selected) {
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-lighter);
}

.nt-toolbar :deep(.el-input__wrapper) {
  border-radius: 9px;
}

/* ---------- 表格 ---------- */
.nt-table {
  flex: 1 1 0;
  min-height: 240px;
  border-radius: 10px;
}

.nt-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.nt-table :deep(.el-table__row) {
  height: 62px;
}

.nt-table :deep(.el-table__cell .cell) {
  padding: 0 10px;
  line-height: 1.45;
}

.nt-cell-main {
  min-width: 0;
}

.nt-title-row {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.nt-title {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  min-height: 24px;
  padding: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 4px;
}

.nt-title:hover {
  color: var(--el-color-primary);
}

.nt-title:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.nt-summary {
  margin: 2px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 18px;
  color: var(--nt-text-muted);
  white-space: nowrap;
}

.nt-cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.nt-cell-stack--time {
  gap: 2px;
}

.nt-cell-sub {
  font-size: 12px;
  color: var(--nt-text-muted);
  white-space: nowrap;
}

/* ---------- 投递 ---------- */
.nt-delivery {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nt-delivery__head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.nt-delivery__head .nt-pill {
  overflow: hidden;
  text-overflow: ellipsis;
}

.nt-delivery__count {
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

/* ---------- 操作 ---------- */
.nt-actions {
  display: flex;
  gap: 2px;
  align-items: center;
  justify-content: flex-end;
}

.nt-action {
  height: 28px;
  padding: 0 6px;
  font-size: 13px;
}

.nt-action--more {
  min-width: 28px;
}

/* ---------- 空态 ---------- */
.nt-empty {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  padding: 28px 16px;
}

.nt-empty__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  color: var(--nt-text-muted);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--nt-line);
  border-radius: 14px;
}

.nt-empty__title {
  margin: 4px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.nt-empty__desc {
  max-width: 420px;
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--nt-text-muted);
  text-align: center;
}

.nt-empty__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 8px;
}

/* stylelint-disable-next-line order/order -- 媒体查询必须排在基础规则之后 */
@media (width <= 900px) {
  .nt-search {
    width: 100%;
    min-width: 180px;
  }

  .nt-toolbar__left {
    flex: 1 1 100%;
  }

  .nt-hero__side {
    justify-content: space-between;
    width: 100%;
  }
}
</style>
