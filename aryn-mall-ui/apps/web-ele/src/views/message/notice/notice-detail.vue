<script lang="ts" setup>
import type { AudienceSnapshot, NoticeAction } from './meta';

import type { NoticeRecipientItem, NoticeRecord } from '#/api/message/types';

import { computed, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';

import {
  ElButton,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import {
  deleteNotice,
  getNoticeRecipients,
  publishNotice,
  republishNotice,
  retryNoticeDispatch,
  revokeNotice,
} from '#/api/message/notice';

import {
  audienceSummary,
  audienceText,
  canRetry,
  categoryMeta,
  deliveryPercent,
  deliveryText,
  dispatchMeta,
  noticeActionPlan,
  parseAudienceSnapshot,
  splitDateTime,
  statusMeta,
  targetTypesText,
  taskStatusMeta,
} from './meta';

import './notice-ui.css';

/**
 * 通知详情抽屉。
 *
 * 结构：状态头（标题 + 状态胶囊 + 受众摘要）→ 概览（关键指标 + 正文 + 分发任务）
 * → 收件人明细（有 `message:notice:recipient` 权限才出页签）。
 *
 * 关键取舍：
 * - 概览先给「送达 / 已读 / 隐藏」三个数与投递完成度，再看正文；
 *   运营打开详情的第一诉求是「这条通知到底送到没有」，不是重读正文。
 * - 分发任务表用中文状态与人群口径，错误摘要单独高亮在表下，避免挤进单元格。
 */
const props = defineProps<{
  modelValue: boolean;
  record?: NoticeRecord;
}>();

const emit = defineEmits<{
  edit: [];
  refresh: [];
  'update:modelValue': [value: boolean];
}>();

const { hasAccessByCodes } = useAccess();
const canViewRecipients = computed(() =>
  hasAccessByCodes(['message:notice:recipient']),
);

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const activeTab = ref('overview');
const acting = ref(false);

const recipients = ref<NoticeRecipientItem[]>([]);
const recipientTotal = ref(0);
const recipientLoading = ref(false);
const recipientQuery = reactive({
  current: 1,
  keyword: '',
  readStatus: '',
  recipientType: '',
  size: 10,
});

const record = computed(() => props.record);

const audience = computed<AudienceSnapshot>(() =>
  parseAudienceSnapshot(record.value?.audienceSnapshot),
);

const status = computed(() => statusMeta(record.value?.status));
const dispatch = computed(() =>
  dispatchMeta(record.value ?? { dispatchStatus: 'NONE', status: 'DRAFT' }),
);
const category = computed(() => categoryMeta(record.value?.category));

const createdParts = computed(() => splitDateTime(record.value?.createTime));
const publishedParts = computed(() =>
  record.value?.publishTime
    ? splitDateTime(record.value.publishTime)
    : { date: '—', time: '' },
);
const expireParts = computed(() =>
  record.value?.expireTime
    ? splitDateTime(record.value.expireTime)
    : { date: '—', time: '' },
);

const tasks = computed(() => record.value?.dispatchTasks ?? []);

const dispatchError = computed(
  () => tasks.value.find((task) => task.errorSummary)?.errorSummary,
);

const canRetryCurrent = computed(() =>
  record.value ? canRetry(record.value) : false,
);

const readPercent = computed(() =>
  deliveryPercent(record.value ?? { readCount: 0, recipientCount: 0 }),
);

const audienceTextOf = computed(() =>
  audienceSummary(audience.value, record.value?.targetTypes),
);

function recipientTypeText(type: string) {
  return type === 'MALL_USER' ? '会员' : '工作人员';
}

async function loadRecipients() {
  if (!record.value) {
    return;
  }
  recipientLoading.value = true;
  try {
    const page = await getNoticeRecipients(record.value.id, {
      ...recipientQuery,
      keyword: recipientQuery.keyword.trim() || undefined,
      readStatus: recipientQuery.readStatus || undefined,
      recipientType: recipientQuery.recipientType || undefined,
    });
    recipients.value = page.records ?? [];
    recipientTotal.value = page.total ?? 0;
  } catch {
    // 请求层已统一提示
  } finally {
    recipientLoading.value = false;
  }
}

function searchRecipients() {
  recipientQuery.current = 1;
  void loadRecipients();
}

function handleTabChange(name: number | string) {
  if (name === 'recipients' && canViewRecipients.value) {
    void loadRecipients();
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return;
    }
    activeTab.value = 'overview';
    recipients.value = [];
    recipientTotal.value = 0;
    Object.assign(recipientQuery, {
      current: 1,
      keyword: '',
      readStatus: '',
      recipientType: '',
    });
  },
);

/** 详情内动作：确认 → 请求 → 关闭抽屉 → 通知父页面刷新。文案与列表同源（meta.ts）。 */
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

async function runAction(action: NoticeAction) {
  if (!record.value || acting.value) {
    return;
  }
  const current = record.value;
  const plan = noticeActionPlan(action, current.targetTypes);
  try {
    await ElMessageBox.confirm(plan.message, plan.title, {
      cancelButtonText: '取消',
      confirmButtonText: plan.ok,
      type: 'warning',
    });
  } catch {
    return;
  }
  acting.value = true;
  try {
    await ACTION_RUNNERS[action](current);
    ElMessage.success(plan.done);
    visible.value = false;
    emit('refresh');
  } catch {
    // 请求层已统一提示
  } finally {
    acting.value = false;
  }
}
</script>

<template>
  <ElDrawer
    v-model="visible"
    class="nt-detail"
    size="min(760px, 94vw)"
    :title="record?.title || '通知详情'"
  >
    <div v-if="record" class="nt-detail__body">
      <header class="nt-detail__head">
        <div class="nt-detail__pills">
          <span class="nt-pill" :class="`nt-pill--${status.tone}`">
            <i class="nt-dot" :class="`nt-dot--${status.tone}`"></i>
            {{ status.label }}
          </span>
          <span class="nt-pill" :class="`nt-pill--${category.tone}`">
            {{ category.label }}
          </span>
          <span class="nt-pill nt-pill--info">
            {{ targetTypesText(record.targetTypes) }}
          </span>
          <span
            v-if="record.priority === 'HIGH'"
            class="nt-pill nt-pill--warning"
          >
            重要
          </span>
          <span
            v-if="record.senderType === 'SYSTEM'"
            class="nt-pill nt-pill--info"
          >
            系统生成
          </span>
        </div>
        <p class="nt-detail__audience">受众：{{ audienceTextOf }}</p>
      </header>

      <ElTabs
        v-model="activeTab"
        class="nt-detail__tabs"
        @tab-change="handleTabChange"
      >
        <ElTabPane label="概览" name="overview">
          <div class="nt-metrics">
            <div class="nt-metric">
              <span class="nt-metric__value nt-num">
                {{ record.recipientCount ?? 0 }}
              </span>
              <span class="nt-metric__label">送达</span>
            </div>
            <div class="nt-metric">
              <span class="nt-metric__value nt-num">
                {{ record.readCount ?? 0 }}
              </span>
              <span class="nt-metric__label">已读</span>
            </div>
            <div class="nt-metric">
              <span class="nt-metric__value nt-num">
                {{ record.hiddenCount ?? 0 }}
              </span>
              <span class="nt-metric__label">隐藏</span>
            </div>
            <div class="nt-metric nt-metric--wide">
              <div class="nt-metric__head">
                <span class="nt-pill" :class="`nt-pill--${dispatch.tone}`">
                  {{ dispatch.label }}
                </span>
                <span class="nt-metric__rate">{{ deliveryText(record) }}</span>
              </div>
              <div
                class="nt-progress"
                role="progressbar"
                aria-label="已读占比"
                :aria-valuemax="100"
                :aria-valuemin="0"
                :aria-valuenow="readPercent"
              >
                <i
                  class="nt-progress__bar"
                  :style="{ width: `${readPercent}%` }"
                ></i>
              </div>
            </div>
          </div>

          <dl class="nt-facts">
            <div class="nt-fact">
              <dt>发送人</dt>
              <dd>{{ record.senderName || '系统' }}</dd>
            </div>
            <div class="nt-fact">
              <dt>创建时间</dt>
              <dd class="nt-num">
                {{ createdParts.date }} {{ createdParts.time }}
              </dd>
            </div>
            <div class="nt-fact">
              <dt>发布时间</dt>
              <dd class="nt-num">
                {{ publishedParts.date }} {{ publishedParts.time }}
              </dd>
            </div>
            <div class="nt-fact">
              <dt>过期时间</dt>
              <dd class="nt-num">
                {{ expireParts.date }} {{ expireParts.time }}
              </dd>
            </div>
            <div class="nt-fact">
              <dt>受众范围</dt>
              <dd>{{ audienceTextOf }}</dd>
            </div>
            <div class="nt-fact">
              <dt>摘要</dt>
              <dd>{{ record.summary || '—' }}</dd>
            </div>
          </dl>

          <section class="nt-block">
            <h4 class="nt-block__title">正文</h4>
            <pre class="nt-content">{{ record.content }}</pre>
          </section>

          <section v-if="tasks.length > 0" class="nt-block">
            <h4 class="nt-block__title">分发任务</h4>
            <ElTable :data="tasks" size="small" class="nt-task-table">
              <ElTableColumn label="目标端" width="96">
                <template #default="{ row }">
                  {{ targetTypesText(row.targetType) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="受众" width="96">
                <template #default="{ row }">
                  {{ audienceText(row.audienceType) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="状态" width="104">
                <template #default="{ row }">
                  <span
                    class="nt-pill"
                    :class="`nt-pill--${taskStatusMeta(row.status).tone}`"
                  >
                    {{ taskStatusMeta(row.status).label }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="成功 / 失败" width="110">
                <template #default="{ row }">
                  <span class="nt-num">
                    {{ row.successCount ?? 0 }} / {{ row.failureCount ?? 0 }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="重试" width="72">
                <template #default="{ row }">
                  <span class="nt-num">{{ row.retryCount ?? 0 }}</span>
                </template>
              </ElTableColumn>
            </ElTable>
            <p v-if="dispatchError" class="nt-error">
              最近错误：{{ dispatchError }}
            </p>
          </section>

          <p v-if="record.status === 'DRAFT'" class="nt-hint">
            草稿不会进入任何收件箱；发布后正文不可修改，只能撤回。
          </p>
        </ElTabPane>

        <ElTabPane
          v-if="canViewRecipients"
          label="收件人明细"
          name="recipients"
        >
          <div class="nt-recipient-toolbar">
            <ElSelect
              v-model="recipientQuery.readStatus"
              class="nt-recipient-filter"
              clearable
              placeholder="全部阅读状态"
              @change="searchRecipients"
            >
              <ElOption label="未读" value="0" />
              <ElOption label="已读" value="1" />
            </ElSelect>
            <ElSelect
              v-model="recipientQuery.recipientType"
              class="nt-recipient-filter"
              clearable
              placeholder="全部类型"
              @change="searchRecipients"
            >
              <ElOption label="会员" value="MALL_USER" />
              <ElOption label="工作人员" value="SYS_USER" />
            </ElSelect>
            <ElInput
              v-model="recipientQuery.keyword"
              class="nt-recipient-search"
              clearable
              placeholder="搜索姓名或 ID"
              @clear="searchRecipients"
              @keyup.enter="searchRecipients"
            />
            <span class="nt-recipient-count">{{ recipientTotal }} 人</span>
          </div>
          <ElTable
            v-loading="recipientLoading"
            :data="recipients"
            size="small"
            class="nt-recipient-table"
          >
            <template #empty>
              <ElEmpty description="暂无收件人" :image-size="72" />
            </template>
            <ElTableColumn label="收件人" min-width="150">
              <template #default="{ row }">
                <div class="nt-recipient-name">
                  {{ row.recipientName || row.recipientId }}
                </div>
                <div class="nt-recipient-id">{{ row.recipientId }}</div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="类型" width="84">
              <template #default="{ row }">
                {{ recipientTypeText(row.recipientType) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="阅读" width="132">
              <template #default="{ row }">
                <span
                  class="nt-pill"
                  :class="
                    row.readStatus === '1'
                      ? 'nt-pill--success'
                      : 'nt-pill--info'
                  "
                >
                  {{ row.readStatus === '1' ? '已读' : '未读' }}
                </span>
                <div v-if="row.readTime" class="nt-recipient-id nt-num">
                  {{ row.readTime }}
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="接收时间" min-width="150">
              <template #default="{ row }">
                <span class="nt-num">{{ row.receivedTime }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="收件箱" width="82">
              <template #default="{ row }">
                {{ row.inboxStatus === 'HIDDEN' ? '隐藏' : '显示' }}
              </template>
            </ElTableColumn>
          </ElTable>
          <ElPagination
            v-model:current-page="recipientQuery.current"
            v-model:page-size="recipientQuery.size"
            class="nt-recipient-pager"
            layout="total, prev, pager, next"
            :total="recipientTotal"
            @current-change="loadRecipients"
          />
        </ElTabPane>
      </ElTabs>
    </div>

    <template #footer>
      <div class="nt-footer">
        <ElButton @click="visible = false">关闭</ElButton>
        <template v-if="record">
          <ElButton
            v-if="record.status === 'DRAFT'"
            v-access:code="'message:notice:edit'"
            :disabled="acting"
            @click="emit('edit')"
          >
            编辑
          </ElButton>
          <ElButton
            v-if="record.status === 'DRAFT'"
            v-access:code="'message:notice:publish'"
            type="primary"
            :loading="acting"
            @click="runAction('publish')"
          >
            发布
          </ElButton>
          <ElButton
            v-if="record.status === 'DRAFT'"
            v-access:code="'message:notice:delete'"
            type="danger"
            plain
            :disabled="acting"
            @click="runAction('delete')"
          >
            删除
          </ElButton>
          <ElButton
            v-if="record.status === 'PUBLISHED'"
            v-access:code="'message:notice:revoke'"
            type="warning"
            plain
            :disabled="acting"
            @click="runAction('revoke')"
          >
            撤回
          </ElButton>
          <ElButton
            v-if="record.status === 'REVOKED'"
            v-access:code="'message:notice:publish'"
            type="primary"
            :loading="acting"
            @click="runAction('republish')"
          >
            重新发布
          </ElButton>
          <ElButton
            v-if="canRetryCurrent"
            v-access:code="'message:notice:retry'"
            type="danger"
            plain
            :disabled="acting"
            @click="runAction('retry')"
          >
            重试失败任务
          </ElButton>
        </template>
      </div>
    </template>
  </ElDrawer>
</template>

<style scoped>
.nt-detail__body {
  color: var(--el-text-color-primary);
}

.nt-detail__head {
  padding: 12px 14px;
  margin-bottom: 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.nt-detail__pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.nt-detail__audience {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--nt-text-muted);
}

.nt-detail__tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

/* ---------- 指标 ---------- */
.nt-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.nt-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.nt-metric--wide {
  grid-column: 1 / -1;
  gap: 8px;
}

.nt-metric__value {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.nt-metric__label {
  font-size: 12px;
  color: var(--nt-text-muted);
}

.nt-metric__head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.nt-metric__rate {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
}

/* ---------- 事实表 ---------- */
.nt-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
  margin: 0 0 4px;
}

.nt-fact dt {
  font-size: 12px;
  color: var(--nt-text-muted);
}

.nt-fact dd {
  margin: 3px 0 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}

/* ---------- 正文 / 任务 ---------- */
.nt-block {
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.nt-block__title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.nt-content {
  padding: 12px 14px;
  margin: 0;
  overflow: auto;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.nt-task-table {
  border-radius: 8px;
}

.nt-error {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-color-danger);
  overflow-wrap: anywhere;
}

.nt-hint {
  margin: 14px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--nt-text-muted);
}

/* ---------- 收件人 ---------- */
.nt-recipient-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.nt-recipient-filter {
  width: 132px;
}

.nt-recipient-search {
  width: 200px;
}

.nt-recipient-count {
  margin-left: auto;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--nt-text-muted);
}

.nt-recipient-table {
  border-radius: 8px;
}

.nt-recipient-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.nt-recipient-id {
  margin-top: 2px;
  font-size: 11px;
  color: var(--nt-text-muted);
  overflow-wrap: anywhere;
}

.nt-recipient-pager {
  justify-content: flex-end;
  margin-top: 12px;
}

/* stylelint-disable-next-line order/order -- 媒体查询必须排在基础规则之后，否则同优先级覆盖会被基础规则反超 */
@media (width <= 640px) {
  .nt-facts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
