<script setup lang="ts">
import type {
  PageDesignAuditLog,
  PageDesignDiff,
  PageDesignRelease,
  PageDesignVersion,
} from '#/api/promotion/page-design';

import { ref, watch } from 'vue';

import dayjs from 'dayjs';
import {
  ElButton,
  ElDialog,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  auditRelease,
  cancelRelease,
  getAuditLogs,
  getReleases,
  getVersionDiff,
  getVersions,
} from '#/api/promotion/page-design';

const props = defineProps<{
  modelValue: boolean;
  pageId: string;
  pageName: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const loading = ref(false);
const releases = ref<PageDesignRelease[]>([]);
const reportVisible = ref(false);
const reportDiff = ref<PageDesignDiff | null>(null);
const auditLoading = ref(false);
const auditLogs = ref<PageDesignAuditLog[]>([]);

const statusLabels: Record<PageDesignRelease['releaseStatus'], string> = {
  0: '待审核',
  1: '已发布',
  2: '已拒绝',
  3: '已取消',
};

const statusTagTypes: Record<PageDesignRelease['releaseStatus'], string> = {
  0: 'warning',
  1: 'success',
  2: 'danger',
  3: 'info',
};

const actionLabels: Record<string, string> = {
  PUBLISH: '发布',
  RELEASE_APPROVE: '审批通过',
  RELEASE_CANCEL: '取消申请',
  RELEASE_REJECT: '拒绝申请',
  RELEASE_SUBMIT: '提交申请',
  ROLLBACK: '回滚',
  SAVE_DRAFT: '保存草稿',
  UNPUBLISH: '下线',
};

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

async function loadReleases() {
  if (!props.modelValue || !props.pageId) return;
  loading.value = true;
  try {
    releases.value = await getReleases(props.pageId);
  } finally {
    loading.value = false;
  }
}

function changeText(change: {
  field: string;
  from?: unknown;
  to?: unknown;
}) {
  const format = (value: unknown) =>
    typeof value === 'object' && value !== null
      ? JSON.stringify(value)
      : String(value ?? '∅');
  return `${change.field}：${format(change.from)} → ${format(change.to)}`;
}

/** 发布报告：展示该申请生成的版本与上一版本的组件级差异 */
async function openReport(release: PageDesignRelease) {
  if (!release.releaseVersionId) return;
  const versions = (await getVersions(props.pageId)) as PageDesignVersion[];
  const current = versions.find(
    (version) => version.id === release.releaseVersionId,
  );
  const previous = current
    ? versions.find(
        (version) => version.versionNo === (current.versionNo ?? 0) - 1,
      )
    : undefined;
  reportDiff.value = await getVersionDiff(
    props.pageId,
    previous?.id ?? current?.id ?? '',
    release.releaseVersionId,
  );
  reportVisible.value = true;
}

async function loadAuditLogs() {
  if (!props.pageId) return;
  auditLoading.value = true;
  try {
    auditLogs.value = await getAuditLogs(props.pageId);
  } finally {
    auditLoading.value = false;
  }
}

async function approve(release: PageDesignRelease, approved: boolean) {
  const action = approved ? '通过并发布该申请快照' : '拒绝该发布申请';
  await ElMessageBox.prompt(
    `将${action}（V 快照内容不受草稿后续修改影响），可填写审批意见。`,
    approved ? '通过发布申请' : '拒绝发布申请',
    {
      confirmButtonText: approved ? '通过' : '拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '审批意见（可选）',
      type: approved ? 'warning' : 'warning',
    },
  ).then(({ value }) =>
    auditRelease(release.id, { approved, auditRemark: value || undefined }),
  );
  ElMessage.success(approved ? '已通过并发布' : '已拒绝');
  await loadReleases();
}

async function cancel(release: PageDesignRelease) {
  await ElMessageBox.confirm('确认取消该待审批的发布申请？', '取消申请', {
    confirmButtonText: '取消申请',
    cancelButtonText: '返回',
    type: 'warning',
  });
  await cancelRelease(props.pageId, release.id);
  ElMessage.success('已取消');
  await loadReleases();
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    void loadReleases();
    void loadAuditLogs();
  },
);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="`${pageName} · 发布中心`"
    width="860px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElTabs>
      <ElTabPane label="发布申请">
        <ElTable v-loading="loading" :data="releases" max-height="420">
          <ElTableColumn label="#" width="60">
            <template #default="{ row }">
              {{ row.releaseNo }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="90" align="center">
            <template #default="{ row }">
              <ElTag
                :type="(statusTagTypes[row.releaseStatus as '0'] as 'warning' | 'success' | 'danger' | 'info')"
                effect="plain"
              >
                {{ statusLabels[row.releaseStatus as '0'] }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="草稿修订" width="90">
            <template #default="{ row }">
              r{{ row.draftRevision }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="publishRemark" label="发布说明" min-width="140" />
          <ElTableColumn prop="submitBy" label="提交人" width="100" />
          <ElTableColumn label="提交时间" width="150">
            <template #default="{ row }">
              {{ formatTime(row.submitAt) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="auditBy" label="审批人" width="100" />
          <ElTableColumn prop="auditRemark" label="审批意见" min-width="120" />
          <ElTableColumn label="操作" width="150" align="center">
            <template #default="{ row }">
              <template v-if="row.releaseStatus === '0'">
                <ElButton
                  v-access:code="'promotion:pagedesign:approve'"
                  link
                  type="primary"
                  @click="approve(row as PageDesignRelease, true)"
                >
                  通过
                </ElButton>
                <ElButton
                  v-access:code="'promotion:pagedesign:approve'"
                  link
                  type="danger"
                  @click="approve(row as PageDesignRelease, false)"
                >
                  拒绝
                </ElButton>
                <ElButton
                  v-access:code="'promotion:pagedesign:submit'"
                  link
                  @click="cancel(row as PageDesignRelease)"
                >
                  撤回
                </ElButton>
              </template>
              <template v-else-if="row.releaseStatus === '1' && row.releaseVersionId">
                <ElButton link type="primary" @click="openReport(row as PageDesignRelease)">
                  查看变更
                </ElButton>
              </template>
              <span v-else class="muted">-</span>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>
      <ElTabPane label="审计日志">
        <ElTable v-loading="auditLoading" :data="auditLogs" max-height="420">
          <ElTableColumn label="操作" width="110">
            <template #default="{ row }">
              {{ actionLabels[row.action as string] ?? row.action }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="operator" label="操作人" width="110" />
          <ElTableColumn prop="operatorIp" label="IP" width="130" />
          <ElTableColumn label="版本变化" min-width="180">
            <template #default="{ row }">
              <span v-if="row.beforeVersionId || row.afterVersionId">
                {{ row.beforeVersionId || '∅' }} →
                {{ row.afterVersionId || '∅' }}
              </span>
              <span v-else class="muted">草稿操作</span>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="remark" label="备注" min-width="140" />
          <ElTableColumn label="时间" width="150">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>
    </ElTabs>

    <ElDialog v-model="reportVisible" append-to-body title="发布报告 · 与上一版本差异" width="640px">
      <template v-if="reportDiff">
        <p class="report-summary">
          V{{ reportDiff.fromVersionNo }} → V{{ reportDiff.toVersionNo }} ·
          新增 {{ reportDiff.added.length }} · 移除 {{ reportDiff.removed.length }} ·
          修改 {{ reportDiff.changed.length }} · 页面设置 {{ reportDiff.pageChanged.length }}
        </p>
        <ul class="report-list">
          <li v-for="change in reportDiff.added" :key="`a-${change.componentId}`">
            新增组件：{{ change.componentType }}（{{ change.componentId }}）
          </li>
          <li v-for="change in reportDiff.removed" :key="`d-${change.componentId}`">
            移除组件：{{ change.componentType }}（{{ change.componentId }}）
          </li>
          <li v-for="update in reportDiff.changed" :key="update.componentId">
            修改组件：{{ update.componentType }}（{{ update.componentId }}）
            <ul>
              <li v-for="change in update.changes" :key="change.field">
                {{ changeText(change) }}
              </li>
            </ul>
          </li>
          <li v-for="change in reportDiff.pageChanged" :key="change.field">
            {{ changeText(change) }}
          </li>
        </ul>
      </template>
    </ElDialog>
  </ElDialog>
</template>

<style scoped>
.report-summary {
  margin: 0 0 10px;
  font-weight: 600;
}

.report-list {
  padding-left: 18px;
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
}
</style>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
}
</style>
