<script setup lang="ts">
import type { NoticeSavePayload } from '#/api/message/notice';
import type {
  NoticeDispatchStatus,
  NoticeRecipientItem,
  NoticeRecord,
  NoticeStatus,
} from '#/api/message/types';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';

import { Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
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
  ElTag,
  ElTreeSelect,
} from 'element-plus';

import {
  createNotice,
  deleteNotice,
  getNoticePage,
  getNoticeRecipients,
  publishNotice,
  republishNotice,
  retryNoticeDispatch,
  revokeNotice,
  updateNotice,
} from '#/api/message/notice';
import { getTreeList as getDeptTree } from '#/api/upms/dept';
import { getList as getRoleList } from '#/api/upms/sys-role';
import { getList as getMemberLevelList } from '#/api/user/member-level';
import { getList as getMemberTagList } from '#/api/user/member-tag';

interface AudienceSnapshot {
  deptIds?: string[];
  mallUserIds?: string[];
  memberLevelId?: string;
  memberTagId?: string;
  roleIds?: string[];
  staffUserIds?: string[];
}

interface DeptOption {
  children?: DeptOption[];
  id: string;
  name: string;
}

interface MemberLevelOption {
  id: string;
  levelName: string;
}

interface MemberTagOption {
  id: string;
  tagName: string;
}

interface RoleOption {
  id: string;
  roleName: string;
}

const { hasAccessByCodes } = useAccess();
const canViewRecipients = computed(() =>
  hasAccessByCodes(['message:notice:recipient']),
);

const loading = ref(false);
const records = ref<NoticeRecord[]>([]);
const total = ref(0);
const dialogOpen = ref(false);
const drawerOpen = ref(false);
const editingId = ref<string>();
const activeRecord = ref<NoticeRecord>();
const query = reactive({ current: 1, size: 10, keyword: '', status: '' });
const form = reactive<NoticeSavePayload>({
  category: 'SYSTEM',
  content: '',
  deptIds: [],
  jumpType: 'NONE',
  mallUserIds: undefined,
  memberLevelId: undefined,
  memberTagId: undefined,
  priority: 'NORMAL',
  roleIds: [],
  staffUserIds: undefined,
  summary: '',
  targetTypes: ['MALL_USER'],
  title: '',
});

const memberLevels = ref<MemberLevelOption[]>([]);
const memberTags = ref<MemberTagOption[]>([]);
const roles = ref<RoleOption[]>([]);
const depts = ref<DeptOption[]>([]);
const audienceOptionsLoaded = ref(false);

const detailTab = ref('overview');
const recipients = ref<NoticeRecipientItem[]>([]);
const recipientTotal = ref(0);
const recipientLoading = ref(false);
const recipientQuery = reactive({
  current: 1,
  keyword: '',
  readStatus: '',
  recipientType: '',
  size: 20,
});

const STATUS_META: Record<
  NoticeStatus,
  { label: string; tone: 'danger' | 'info' | 'success' | 'warning' }
> = {
  DRAFT: { label: '草稿', tone: 'info' },
  PUBLISHED: { label: '已发布', tone: 'success' },
  REVOKED: { label: '已撤回', tone: 'warning' },
};

const DISPATCH_META: Record<
  NoticeDispatchStatus,
  { label: string; tone: 'danger' | 'info' | 'success' | 'warning' }
> = {
  NONE: { label: '未分发', tone: 'info' },
  RUNNING: { label: '分发中', tone: 'warning' },
  SUCCEEDED: { label: '已送达', tone: 'success' },
  PARTIAL_FAILED: { label: '部分失败', tone: 'danger' },
  FAILED: { label: '分发失败', tone: 'danger' },
};

const TASK_STATUS_META: Record<
  string,
  { label: string; tone: 'danger' | 'info' | 'success' | 'warning' }
> = {
  PENDING: { label: '待分发', tone: 'info' },
  RUNNING: { label: '分发中', tone: 'warning' },
  SUCCEEDED: { label: '成功', tone: 'success' },
  PARTIAL_FAILED: { label: '部分失败', tone: 'danger' },
  FAILED: { label: '失败', tone: 'danger' },
  CANCELED: { label: '已取消', tone: 'info' },
};

const AUDIENCE_LABELS: Record<string, string> = {
  ALL: '全部',
  FILTERED: '条件筛选',
  SPECIFIED: '指定',
};

const activeTasks = computed(() => activeRecord.value?.dispatchTasks ?? []);
const dispatchError = computed(
  () => activeTasks.value.find((task) => task.errorSummary)?.errorSummary,
);

function statusInfo(row: NoticeRecord) {
  return (
    STATUS_META[row.status] ?? { label: row.status, tone: 'info' as const }
  );
}

function dispatchInfo(row: NoticeRecord) {
  if (row.status !== 'PUBLISHED') {
    return { label: '—', tone: 'info' as const };
  }
  return DISPATCH_META[row.dispatchStatus ?? 'NONE'] ?? DISPATCH_META.NONE;
}

function taskStatusInfo(status: string) {
  return TASK_STATUS_META[status] ?? { label: status, tone: 'info' as const };
}

function targetText(targetTypes: string | undefined) {
  if (!targetTypes) {
    return '—';
  }
  const labels: Record<string, string> = {
    MALL_USER: '会员',
    SYS_USER: '工作人员',
  };
  return targetTypes
    .split(',')
    .map((type) => labels[type] ?? type)
    .join('、');
}

function audienceText(audienceType: string | undefined) {
  return audienceType ? (AUDIENCE_LABELS[audienceType] ?? audienceType) : '—';
}

function deliveryText(row: NoticeRecord) {
  if ((row.recipientCount ?? 0) === 0) {
    return '—';
  }
  return `${row.readCount ?? 0} / ${row.recipientCount}`;
}

function canRetry(row: NoticeRecord) {
  return (
    row.status === 'PUBLISHED' &&
    (row.dispatchStatus === 'FAILED' || row.dispatchStatus === 'PARTIAL_FAILED')
  );
}

function parseAudienceSnapshot(snapshot?: string): AudienceSnapshot {
  if (!snapshot) {
    return {};
  }
  try {
    return JSON.parse(snapshot) as AudienceSnapshot;
  } catch {
    return {};
  }
}

async function loadAudienceOptions() {
  if (audienceOptionsLoaded.value) {
    return;
  }
  audienceOptionsLoaded.value = true;
  const [levelResult, tagResult, roleResult, deptResult] =
    await Promise.allSettled([
      getMemberLevelList(),
      getMemberTagList(),
      getRoleList({}),
      getDeptTree(),
    ]);
  if (levelResult.status === 'fulfilled') {
    memberLevels.value = levelResult.value ?? [];
  }
  if (tagResult.status === 'fulfilled') {
    memberTags.value = tagResult.value ?? [];
  }
  if (roleResult.status === 'fulfilled') {
    roles.value = roleResult.value ?? [];
  }
  if (deptResult.status === 'fulfilled') {
    depts.value = deptResult.value ?? [];
  }
}

function syncAudienceFields() {
  if (!form.targetTypes.includes('MALL_USER')) {
    form.memberLevelId = undefined;
    form.memberTagId = undefined;
    form.mallUserIds = undefined;
  }
  if (!form.targetTypes.includes('SYS_USER')) {
    form.roleIds = [];
    form.deptIds = [];
    form.staffUserIds = undefined;
  }
}

function buildPayload(): NoticeSavePayload {
  const mall = form.targetTypes.includes('MALL_USER');
  const staff = form.targetTypes.includes('SYS_USER');
  return {
    ...form,
    deptIds: staff ? (form.deptIds ?? []) : [],
    mallUserIds: mall ? form.mallUserIds : undefined,
    memberLevelId: mall ? form.memberLevelId || undefined : undefined,
    memberTagId: mall ? form.memberTagId || undefined : undefined,
    roleIds: staff ? (form.roleIds ?? []) : [],
    staffUserIds: staff ? form.staffUserIds : undefined,
  };
}

function recipientTypeText(type: string) {
  return type === 'MALL_USER' ? '会员' : '工作人员';
}

async function loadRecipients() {
  if (!activeRecord.value) {
    return;
  }
  recipientLoading.value = true;
  try {
    const page = await getNoticeRecipients(
      activeRecord.value.id,
      recipientQuery,
    );
    recipients.value = page.records;
    recipientTotal.value = page.total;
  } catch {
    // 请求层已统一提示
  } finally {
    recipientLoading.value = false;
  }
}

function searchRecipients() {
  recipientQuery.current = 1;
  loadRecipients();
}

function handleDetailTab(name: number | string) {
  if (name === 'recipients' && canViewRecipients.value) {
    searchRecipients();
  }
}

async function load() {
  loading.value = true;
  try {
    const page = await getNoticePage(query);
    records.value = page.records;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

function search() {
  query.current = 1;
  load();
}

function resetForm() {
  editingId.value = undefined;
  Object.assign(form, {
    category: 'SYSTEM',
    content: '',
    deptIds: [],
    jumpPayload: undefined,
    jumpType: 'NONE',
    mallUserIds: undefined,
    memberLevelId: undefined,
    memberTagId: undefined,
    priority: 'NORMAL',
    roleIds: [],
    staffUserIds: undefined,
    summary: '',
    targetTypes: ['MALL_USER'],
    title: '',
  });
}

function openCreate() {
  resetForm();
  void loadAudienceOptions();
  dialogOpen.value = true;
}

function openEdit(row: NoticeRecord) {
  drawerOpen.value = false;
  editingId.value = row.id;
  const audience = parseAudienceSnapshot(row.audienceSnapshot);
  Object.assign(form, {
    category: row.category,
    content: row.content,
    deptIds: audience.deptIds ?? [],
    jumpPayload: row.jumpPayload,
    jumpType: row.jumpType || 'NONE',
    mallUserIds: audience.mallUserIds,
    memberLevelId: audience.memberLevelId,
    memberTagId: audience.memberTagId,
    priority: row.priority,
    roleIds: audience.roleIds ?? [],
    staffUserIds: audience.staffUserIds,
    summary: row.summary,
    targetTypes: row.targetTypes.split(',') as Array<'MALL_USER' | 'SYS_USER'>,
    title: row.title,
  });
  void loadAudienceOptions();
  dialogOpen.value = true;
}

function openDetail(row: NoticeRecord) {
  activeRecord.value = row;
  detailTab.value = 'overview';
  recipients.value = [];
  recipientTotal.value = 0;
  recipientQuery.current = 1;
  recipientQuery.keyword = '';
  recipientQuery.readStatus = '';
  recipientQuery.recipientType = '';
  drawerOpen.value = true;
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题和正文不能为空');
    return;
  }
  if (form.targetTypes.length === 0) {
    ElMessage.warning('请选择至少一个目标端');
    return;
  }
  const payload = buildPayload();
  await (editingId.value
    ? updateNotice(editingId.value, payload)
    : createNotice(payload));
  ElMessage.success('草稿已保存');
  dialogOpen.value = false;
  await load();
}

async function publish(row: NoticeRecord) {
  await ElMessageBox.confirm(
    `发布后将按受众快照面向 ${targetText(row.targetTypes)} 异步分发，正文不可再修改。`,
    '确认发布',
    { type: 'warning' },
  );
  await publishNotice(row.id);
  drawerOpen.value = false;
  ElMessage.success('已提交分发任务');
  await load();
}

async function revoke(row: NoticeRecord) {
  await ElMessageBox.confirm(
    '撤回后收件箱立即隐藏，未开始的分发任务会取消；可在“已撤回”列表中重新发布。',
    '确认撤回',
    {
      type: 'warning',
    },
  );
  await revokeNotice(row.id);
  drawerOpen.value = false;
  ElMessage.success('通知已撤回');
  await load();
}

async function republish(row: NoticeRecord) {
  await ElMessageBox.confirm(
    '重新发布后通知会恢复到收件箱；已送达的收件人保留已读状态，未完成的分发会重新提交。',
    '重新发布',
    { confirmButtonText: '重新发布', type: 'warning' },
  );
  await republishNotice(row.id);
  drawerOpen.value = false;
  ElMessage.success('通知已重新发布');
  await load();
}

async function removeDraft(row: NoticeRecord) {
  await ElMessageBox.confirm(
    '删除后草稿不可恢复，未发送的通知不会进入任何收件箱。',
    '删除草稿',
    { confirmButtonText: '删除', type: 'warning' },
  );
  await deleteNotice(row.id);
  drawerOpen.value = false;
  ElMessage.success('草稿已删除');
  await load();
}

async function retry(row: NoticeRecord) {
  await ElMessageBox.confirm(
    '将重新提交失败的分发任务，已送达的收件人不会重复收到。',
    '重试分发',
    { confirmButtonText: '重试', type: 'warning' },
  );
  await retryNoticeDispatch(row.id);
  drawerOpen.value = false;
  ElMessage.success('已重新提交分发');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="notice-command min-h-full p-5">
    <section class="hero-panel">
      <div>
        <p class="eyebrow">MESSAGE CONTROL / 通知编排</p>
        <h1>让每一条通知都有明确的受众、状态和去向</h1>
        <p class="hero-copy">
          纯文本发布、受众冻结、异步分发与撤回审计集中在同一工作面板。
        </p>
      </div>
      <ElButton
        v-access:code="'message:notice:add'"
        type="primary"
        size="large"
        @click="openCreate"
      >
        新建通知草稿
      </ElButton>
    </section>

    <section class="data-panel">
      <div class="toolbar">
        <div class="toolbar-filters">
          <ElSelect
            v-model="query.status"
            clearable
            placeholder="全部状态"
            class="w-40"
            @change="search"
          >
            <ElOption label="草稿" value="DRAFT" />
            <ElOption label="已发布" value="PUBLISHED" />
            <ElOption label="已撤回" value="REVOKED" />
          </ElSelect>
          <ElInput
            v-model="query.keyword"
            clearable
            class="w-64"
            :prefix-icon="Search"
            placeholder="搜索标题、摘要或正文"
            @clear="search"
            @keyup.enter="search"
          />
          <ElButton @click="search">查询</ElButton>
        </div>
        <span class="record-count">{{ total }} 条通知</span>
      </div>
      <ElTable v-loading="loading" :data="records" class="message-table">
        <ElTableColumn label="通知" min-width="320">
          <template #default="{ row }">
            <div class="notice-title-row">
              <button
                type="button"
                class="notice-title"
                @click="openDetail(row as NoticeRecord)"
              >
                {{ row.title }}
              </button>
              <ElTag
                v-if="row.senderType === 'SYSTEM'"
                size="small"
                type="info"
                effect="plain"
              >
                系统生成
              </ElTag>
              <ElTag
                v-if="row.priority === 'HIGH'"
                size="small"
                type="warning"
                effect="plain"
              >
                重要
              </ElTag>
            </div>
            <div class="notice-summary">{{ row.summary || row.content }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="category" label="分类" width="120" />
        <ElTableColumn label="目标端" width="140">
          <template #default="{ row }">
            {{ targetText(row.targetTypes) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="statusInfo(row as NoticeRecord).tone">
              {{ statusInfo(row as NoticeRecord).label }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="已读 / 送达" width="140">
          <template #default="{ row }">
            <div class="dispatch-cell">
              <ElTag
                :type="dispatchInfo(row as NoticeRecord).tone"
                size="small"
                effect="plain"
              >
                {{ dispatchInfo(row as NoticeRecord).label }}
              </ElTag>
              <span class="read-count">
                {{ deliveryText(row as NoticeRecord) }}
              </span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              @click="openDetail(row as NoticeRecord)"
            >
              查看
            </ElButton>
            <ElButton
              v-if="row.status === 'DRAFT'"
              v-access:code="'message:notice:edit'"
              link
              type="primary"
              @click="openEdit(row as NoticeRecord)"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="row.status === 'DRAFT'"
              v-access:code="'message:notice:publish'"
              link
              type="success"
              @click="publish(row as NoticeRecord)"
            >
              发布
            </ElButton>
            <ElButton
              v-if="row.status === 'DRAFT'"
              v-access:code="'message:notice:delete'"
              link
              type="danger"
              @click="removeDraft(row as NoticeRecord)"
            >
              删除
            </ElButton>
            <ElButton
              v-if="row.status === 'PUBLISHED'"
              v-access:code="'message:notice:revoke'"
              link
              type="warning"
              @click="revoke(row as NoticeRecord)"
            >
              撤回
            </ElButton>
            <ElButton
              v-if="row.status === 'REVOKED'"
              v-access:code="'message:notice:publish'"
              link
              type="success"
              @click="republish(row as NoticeRecord)"
            >
              重新发布
            </ElButton>
            <ElButton
              v-if="canRetry(row as NoticeRecord)"
              v-access:code="'message:notice:retry'"
              link
              type="danger"
              @click="retry(row as NoticeRecord)"
            >
              重试
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElPagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-5 justify-end"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </section>

    <ElDialog
      v-model="dialogOpen"
      :title="editingId ? '编辑通知草稿' : '新建通知草稿'"
      width="680px"
    >
      <ElForm label-position="top">
        <div class="grid grid-cols-2 gap-x-5">
          <ElFormItem label="标题" class="col-span-2">
            <ElInput v-model="form.title" maxlength="200" show-word-limit />
          </ElFormItem>
          <ElFormItem label="分类">
            <ElInput v-model="form.category" />
          </ElFormItem>
          <ElFormItem label="优先级">
            <ElSelect v-model="form.priority" class="w-full">
              <ElOption label="普通" value="NORMAL" /><ElOption
                label="重要"
                value="HIGH"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="目标端" class="col-span-2">
            <ElCheckboxGroup
              v-model="form.targetTypes"
              @change="syncAudienceFields"
            >
              <ElCheckbox value="MALL_USER">会员</ElCheckbox
              ><ElCheckbox value="SYS_USER"> 工作人员 </ElCheckbox>
            </ElCheckboxGroup>
          </ElFormItem>
          <template v-if="form.targetTypes.includes('MALL_USER')">
            <ElFormItem label="会员等级">
              <ElSelect
                v-model="form.memberLevelId"
                clearable
                class="w-full"
                placeholder="全部会员"
              >
                <ElOption
                  v-for="level in memberLevels"
                  :key="level.id"
                  :label="level.levelName"
                  :value="level.id"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="会员标签">
              <ElSelect
                v-model="form.memberTagId"
                clearable
                class="w-full"
                placeholder="全部会员"
              >
                <ElOption
                  v-for="tag in memberTags"
                  :key="tag.id"
                  :label="tag.tagName"
                  :value="tag.id"
                />
              </ElSelect>
            </ElFormItem>
            <div
              v-if="form.mallUserIds?.length"
              class="audience-hint col-span-2"
            >
              已指定 {{ form.mallUserIds.length }}
              名会员，优先于等级/标签条件。
              <ElButton
                link
                type="primary"
                size="small"
                @click="form.mallUserIds = undefined"
              >
                清除指定范围
              </ElButton>
            </div>
          </template>
          <template v-if="form.targetTypes.includes('SYS_USER')">
            <ElFormItem label="角色" class="col-span-2">
              <ElSelect
                v-model="form.roleIds"
                multiple
                filterable
                collapse-tags
                clearable
                class="w-full"
                placeholder="全部在职工作人员"
              >
                <ElOption
                  v-for="role in roles"
                  :key="role.id"
                  :label="role.roleName"
                  :value="role.id"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="部门" class="col-span-2">
              <ElTreeSelect
                v-model="form.deptIds"
                :data="depts"
                multiple
                show-checkbox
                check-strictly
                node-key="id"
                :props="{ children: 'children', label: 'name' }"
                class="w-full"
                placeholder="全部在职工作人员"
              />
            </ElFormItem>
            <div
              v-if="form.staffUserIds?.length"
              class="audience-hint col-span-2"
            >
              已指定 {{ form.staffUserIds.length }}
              名工作人员，优先于角色/部门条件。
              <ElButton
                link
                type="primary"
                size="small"
                @click="form.staffUserIds = undefined"
              >
                清除指定范围
              </ElButton>
            </div>
          </template>
          <div
            v-if="
              form.targetTypes.includes('MALL_USER') ||
              form.targetTypes.includes('SYS_USER')
            "
            class="audience-hint col-span-2"
          >
            不选等级/标签/角色/部门时发给全部目标端；同时选择多个条件时取交集。
          </div>
          <ElFormItem label="摘要" class="col-span-2">
            <ElInput v-model="form.summary" maxlength="500" />
          </ElFormItem>
          <ElFormItem label="纯文本正文" class="col-span-2">
            <ElInput
              v-model="form.content"
              type="textarea"
              :rows="8"
              maxlength="10000"
              show-word-limit
            />
          </ElFormItem>
        </div>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogOpen = false">取消</ElButton
        ><ElButton type="primary" @click="save">保存草稿</ElButton>
      </template>
    </ElDialog>

    <ElDrawer
      v-model="drawerOpen"
      size="720px"
      :title="activeRecord?.title || '通知详情'"
    >
      <div v-if="activeRecord" class="notice-detail">
        <ElTabs v-model="detailTab" @tab-change="handleDetailTab">
          <ElTabPane label="概览" name="overview">
            <div class="detail-tags">
              <ElTag :type="statusInfo(activeRecord).tone">
                {{ statusInfo(activeRecord).label }}
              </ElTag>
              <ElTag
                v-if="activeRecord.senderType === 'SYSTEM'"
                type="info"
                effect="plain"
              >
                系统生成
              </ElTag>
              <ElTag
                v-if="activeRecord.priority === 'HIGH'"
                type="warning"
                effect="plain"
              >
                重要
              </ElTag>
            </div>

            <dl class="detail-grid">
              <div>
                <dt>分类</dt>
                <dd>{{ activeRecord.category }}</dd>
              </div>
              <div>
                <dt>目标端</dt>
                <dd>{{ targetText(activeRecord.targetTypes) }}</dd>
              </div>
              <div>
                <dt>发送人</dt>
                <dd>{{ activeRecord.senderName || '系统' }}</dd>
              </div>
              <div>
                <dt>创建时间</dt>
                <dd>{{ activeRecord.createTime }}</dd>
              </div>
              <div>
                <dt>发布时间</dt>
                <dd>{{ activeRecord.publishTime || '—' }}</dd>
              </div>
              <div>
                <dt>过期时间</dt>
                <dd>{{ activeRecord.expireTime || '—' }}</dd>
              </div>
            </dl>

            <section class="detail-section">
              <h3>正文</h3>
              <pre class="detail-content">{{ activeRecord.content }}</pre>
            </section>

            <section class="detail-section">
              <h3>送达与阅读</h3>
              <div class="detail-stats">
                <span>
                  <strong>{{ activeRecord.recipientCount ?? 0 }}</strong>
                  送达
                </span>
                <span>
                  <strong>{{ activeRecord.readCount ?? 0 }}</strong>
                  已读
                </span>
                <span>
                  <strong>{{ activeRecord.hiddenCount ?? 0 }}</strong>
                  隐藏
                </span>
              </div>
            </section>

            <section v-if="activeTasks.length > 0" class="detail-section">
              <h3>分发任务</h3>
              <ElTable :data="activeTasks" size="small" class="dispatch-table">
                <ElTableColumn label="目标端" width="90">
                  <template #default="{ row }">
                    {{ targetText(row.targetType) }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="受众" width="90">
                  <template #default="{ row }">
                    {{ audienceText(row.audienceType) }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="状态" width="90">
                  <template #default="{ row }">
                    <ElTag :type="taskStatusInfo(row.status).tone" size="small">
                      {{ taskStatusInfo(row.status).label }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="成功 / 失败" width="100">
                  <template #default="{ row }">
                    {{ row.successCount ?? 0 }} / {{ row.failureCount ?? 0 }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="重试" width="70">
                  <template #default="{ row }">
                    {{ row.retryCount ?? 0 }}
                  </template>
                </ElTableColumn>
              </ElTable>
              <p v-if="dispatchError" class="dispatch-error">
                最近错误：{{ dispatchError }}
              </p>
            </section>

            <section
              v-if="activeRecord.status === 'DRAFT'"
              class="detail-section"
            >
              <h3>草稿提示</h3>
              <p class="detail-hint">
                草稿不会进入任何收件箱；发布后正文不可修改，只能撤回。
              </p>
            </section>
          </ElTabPane>
          <ElTabPane
            v-if="canViewRecipients"
            label="收件人明细"
            name="recipients"
          >
            <div class="recipient-toolbar">
              <ElSelect
                v-model="recipientQuery.readStatus"
                clearable
                placeholder="全部阅读状态"
                class="w-36"
                @change="searchRecipients"
              >
                <ElOption label="未读" value="0" />
                <ElOption label="已读" value="1" />
              </ElSelect>
              <ElSelect
                v-model="recipientQuery.recipientType"
                clearable
                placeholder="全部类型"
                class="w-36"
                @change="searchRecipients"
              >
                <ElOption label="会员" value="MALL_USER" />
                <ElOption label="工作人员" value="SYS_USER" />
              </ElSelect>
              <ElInput
                v-model="recipientQuery.keyword"
                clearable
                class="w-48"
                placeholder="搜索姓名或ID"
                @clear="searchRecipients"
                @keyup.enter="searchRecipients"
              />
              <span class="recipient-count">{{ recipientTotal }} 人</span>
            </div>
            <ElTable
              v-loading="recipientLoading"
              :data="recipients"
              size="small"
              class="recipient-table"
            >
              <ElTableColumn label="收件人" min-width="160">
                <template #default="{ row }">
                  <div class="recipient-name">
                    {{ row.recipientName || row.recipientId }}
                  </div>
                  <div class="recipient-id">{{ row.recipientId }}</div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="类型" width="80">
                <template #default="{ row }">
                  {{ recipientTypeText(row.recipientType) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="阅读" width="120">
                <template #default="{ row }">
                  <ElTag
                    :type="row.readStatus === '1' ? 'success' : 'info'"
                    size="small"
                  >
                    {{ row.readStatus === '1' ? '已读' : '未读' }}
                  </ElTag>
                  <div v-if="row.readTime" class="recipient-id">
                    {{ row.readTime }}
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="receivedTime" label="接收时间" width="160" />
              <ElTableColumn label="收件箱" width="80">
                <template #default="{ row }">
                  {{ row.inboxStatus === 'HIDDEN' ? '隐藏' : '显示' }}
                </template>
              </ElTableColumn>
            </ElTable>
            <ElPagination
              v-model:current-page="recipientQuery.current"
              v-model:page-size="recipientQuery.size"
              class="mt-4 justify-end"
              layout="total, prev, pager, next"
              :total="recipientTotal"
              @current-change="loadRecipients"
            />
          </ElTabPane>
        </ElTabs>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <ElButton @click="drawerOpen = false">关闭</ElButton>
          <ElButton
            v-if="activeRecord?.status === 'DRAFT'"
            v-access:code="'message:notice:edit'"
            @click="activeRecord && openEdit(activeRecord)"
          >
            编辑
          </ElButton>
          <ElButton
            v-if="activeRecord?.status === 'DRAFT'"
            v-access:code="'message:notice:publish'"
            type="success"
            @click="activeRecord && publish(activeRecord)"
          >
            发布
          </ElButton>
          <ElButton
            v-if="activeRecord?.status === 'DRAFT'"
            v-access:code="'message:notice:delete'"
            type="danger"
            plain
            @click="activeRecord && removeDraft(activeRecord)"
          >
            删除
          </ElButton>
          <ElButton
            v-if="activeRecord?.status === 'PUBLISHED'"
            v-access:code="'message:notice:revoke'"
            type="warning"
            plain
            @click="activeRecord && revoke(activeRecord)"
          >
            撤回
          </ElButton>
          <ElButton
            v-if="activeRecord?.status === 'REVOKED'"
            v-access:code="'message:notice:publish'"
            type="success"
            @click="activeRecord && republish(activeRecord)"
          >
            重新发布
          </ElButton>
          <ElButton
            v-if="activeRecord && canRetry(activeRecord)"
            v-access:code="'message:notice:retry'"
            type="danger"
            plain
            @click="activeRecord && retry(activeRecord)"
          >
            重试失败任务
          </ElButton>
        </div>
      </template>
    </ElDrawer>
  </div>
</template>

<style scoped>
.notice-command {
  --ink: #102a43;
  --signal: #0f766e;

  background:
    radial-gradient(circle at 88% 4%, rgb(15 118 110 / 12%), transparent 26%),
    #f4f7f8;
}

.hero-panel {
  display: flex;
  gap: 32px;
  align-items: end;
  justify-content: space-between;
  padding: 34px;
  color: white;
  background: linear-gradient(125deg, #102a43 0%, #173f5f 62%, #0f766e 100%);
  border-radius: 18px;
  box-shadow: 0 18px 55px rgb(16 42 67 / 20%);
}

.hero-panel h1 {
  max-width: 720px;
  margin: 7px 0 10px;
  font-family: 'Noto Serif SC', serif;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: #5eead4;
  letter-spacing: 0.18em;
}

.hero-copy {
  color: rgb(255 255 255 / 68%);
}

.data-panel {
  padding: 22px;
  margin-top: 18px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid rgb(15 118 110 / 12%);
  border-radius: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.record-count {
  font-size: 13px;
  color: #627d98;
}

.notice-title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.notice-title {
  padding: 0;
  font: inherit;
  font-weight: 700;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
}

.notice-title:hover {
  color: var(--signal);
  text-decoration: underline;
}

.notice-summary {
  max-width: 520px;
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: #829ab1;
  white-space: nowrap;
}

.dispatch-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.read-count {
  font-size: 12px;
  color: #627d98;
}

.notice-detail {
  color: #102a43;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
  margin: 0;
}

.detail-grid dt {
  font-size: 12px;
  color: #829ab1;
}

.detail-grid dd {
  margin: 4px 0 0;
  font-size: 13px;
  color: #102a43;
}

.detail-section {
  padding-top: 16px;
  margin-top: 18px;
  border-top: 1px solid #e3ebf3;
}

.detail-section h3 {
  margin: 0 0 10px;
  font-size: 14px;
  color: #102a43;
}

.detail-content {
  padding: 12px 14px;
  margin: 0;
  overflow: auto;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  white-space: pre-wrap;
  background: #f7f9fb;
  border-radius: 10px;
}

.detail-stats {
  display: flex;
  gap: 28px;
  font-size: 13px;
  color: #627d98;
}

.detail-stats strong {
  margin-right: 4px;
  font-size: 20px;
  color: #102a43;
}

.dispatch-error {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #b91c1c;
  overflow-wrap: anywhere;
}

.detail-hint {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #627d98;
}

.audience-hint {
  font-size: 12px;
  line-height: 1.6;
  color: #829ab1;
}

.recipient-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.recipient-count {
  font-size: 12px;
  color: #829ab1;
}

.recipient-name {
  font-weight: 600;
  color: #102a43;
}

.recipient-id {
  margin-top: 2px;
  font-size: 11px;
  color: #9aabb8;
  overflow-wrap: anywhere;
}

.drawer-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
</style>
