<script setup lang="ts">
import type {
  PageDesignDiff,
  PageDesignVersion,
} from '#/api/promotion/page-design';

import { ref, watch } from 'vue';

import { RefreshLeft } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getVersions, getVersionDiff, rollbackVersion } from '#/api/promotion/page-design';

const props = defineProps<{
  modelValue: boolean;
  pageId: string;
  pageName: string;
}>();

const emit = defineEmits<{
  restored: [];
  'update:modelValue': [value: boolean];
}>();

const loading = ref(false);
const versions = ref<PageDesignVersion[]>([]);
const diffLoading = ref(false);
const diffFromId = ref('');
const diffToId = ref('');
const diff = ref<PageDesignDiff | null>(null);

async function loadVersions() {
  if (!props.modelValue || !props.pageId) return;
  loading.value = true;
  diff.value = null;
  diffFromId.value = '';
  diffToId.value = '';
  try {
    versions.value = await getVersions(props.pageId);
  } finally {
    loading.value = false;
  }
}

async function restore(version: PageDesignVersion) {
  await ElMessageBox.confirm(
    `将版本 V${version.versionNo} 重新发布为最新版本，历史记录不会被覆盖。`,
    '确认回滚',
    {
      confirmButtonText: '回滚并发布',
      cancelButtonText: '取消',
      type: 'warning',
    },
  );
  await rollbackVersion(
    props.pageId,
    version.id,
    `回滚至 V${version.versionNo}`,
  );
  ElMessage.success('已创建新的回滚版本');
  await loadVersions();
  emit('restored');
}

async function loadDiff() {
  if (!diffFromId.value || !diffToId.value) {
    ElMessage.warning('请选择要对比的两个版本');
    return;
  }
  diffLoading.value = true;
  try {
    diff.value = await getVersionDiff(
      props.pageId,
      diffFromId.value,
      diffToId.value,
    );
  } finally {
    diffLoading.value = false;
  }
}

function diffSummary() {
  if (!diff.value) return '';
  const total =
    diff.value.added.length +
    diff.value.removed.length +
    diff.value.changed.length +
    diff.value.pageChanged.length;
  return total === 0
    ? '两个版本内容一致'
    : `新增 ${diff.value.added.length} · 移除 ${diff.value.removed.length} · 修改 ${diff.value.changed.length} · 页面设置 ${diff.value.pageChanged.length}`;
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

watch(() => [props.modelValue, props.pageId], loadVersions);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="`${pageName} · 版本历史`"
    width="820px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="diff-toolbar">
      <ElSelect
        v-model="diffFromId"
        clearable
        filterable
        placeholder="起始版本"
        size="small"
        style="width: 220px"
      >
        <ElOption
          v-for="version in versions"
          :key="version.id"
          :label="`V${version.versionNo} · ${version.publishRemark || '未填写说明'}`"
          :value="version.id"
        />
      </ElSelect>
      <span class="diff-arrow">→</span>
      <ElSelect
        v-model="diffToId"
        clearable
        filterable
        placeholder="目标版本"
        size="small"
        style="width: 220px"
      >
        <ElOption
          v-for="version in versions"
          :key="version.id"
          :label="`V${version.versionNo} · ${version.publishRemark || '未填写说明'}`"
          :value="version.id"
        />
      </ElSelect>
      <ElButton
        :loading="diffLoading"
        size="small"
        type="primary"
        @click="loadDiff"
      >
        对比
      </ElButton>
    </div>

    <ElAlert
      v-if="diff"
      :closable="false"
      class="diff-summary"
      :title="`V${diff.fromVersionNo} → V${diff.toVersionNo}：${diffSummary()}`"
      :type="diff.added.length + diff.removed.length + diff.changed.length + diff.pageChanged.length === 0 ? 'success' : 'info'"
    />
    <div v-if="diff" class="diff-detail">
      <div v-if="diff.pageChanged.length > 0">
        <strong>页面设置</strong>
        <ul>
          <li v-for="change in diff.pageChanged" :key="change.field">
            {{ changeText(change) }}
          </li>
        </ul>
      </div>
      <div v-if="diff.changed.length > 0">
        <strong>修改的组件</strong>
        <ul>
          <li v-for="update in diff.changed" :key="update.componentId">
            {{ update.componentType }}（{{ update.componentId }}）
            <ul>
              <li v-for="change in update.changes" :key="change.field">
                {{ changeText(change) }}
              </li>
            </ul>
          </li>
        </ul>
      </div>
      <div v-if="diff.added.length > 0">
        <strong>新增组件</strong>
        <ul>
          <li v-for="change in diff.added" :key="`add-${change.componentId}`">
            {{ change.componentType }}（{{ change.componentId }}）
          </li>
        </ul>
      </div>
      <div v-if="diff.removed.length > 0">
        <strong>移除组件</strong>
        <ul>
          <li v-for="change in diff.removed" :key="`del-${change.componentId}`">
            {{ change.componentType }}（{{ change.componentId }}）
          </li>
        </ul>
      </div>
    </div>

    <ElTable v-loading="loading" :data="versions" max-height="360">
      <ElTableColumn label="版本" width="90">
        <template #default="{ row }">
          <ElTag effect="plain">V{{ row.versionNo }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="publishRemark" label="发布说明" min-width="180" />
      <ElTableColumn prop="publishBy" label="发布人" width="120" />
      <ElTableColumn label="发布时间" width="170">
        <template #default="{ row }">
          {{
            row.publishedAt
              ? dayjs(row.publishedAt).format('YYYY-MM-DD HH:mm')
              : '-'
          }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="100" align="center">
        <template #default="{ row }">
          <ElButton
            v-access:code="'promotion:pagedesign:rollback'"
            :icon="RefreshLeft"
            link
            type="primary"
            @click="restore(row as PageDesignVersion)"
          >
            回滚
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </ElDialog>
</template>

<style scoped>
.diff-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.diff-arrow {
  color: var(--el-text-color-secondary);
}

.diff-summary {
  margin-bottom: 12px;
}

.diff-detail {
  max-height: 220px;
  margin-bottom: 12px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.7;
}

.diff-detail ul {
  padding-left: 18px;
  margin: 4px 0 8px;
}

.diff-detail strong {
  color: var(--el-text-color-regular);
}
</style>
