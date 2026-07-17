<script setup lang="ts">
import type { FormInstance } from 'element-plus';

import type {
  PageDesignQuery,
  PageDesignRecord,
  PageDesignType,
  PublishStatus,
} from '#/api/promotion/page-design';

import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  Clock,
  CopyDocument,
  Delete,
  EditPen,
  Plus,
  Refresh,
  Search,
  Upload,
  VideoPause,
  View,
} from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSpace,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  copyPage,
  createPreviewToken,
  delObj,
  getPage,
  publishPage,
  unpublishPage,
} from '#/api/promotion/page-design';

import PreviewDialog from './components/preview-dialog.vue';
import { PREVIEW_TTL_MS } from './components/preview-utils';
import VersionDialog from './components/version-dialog.vue';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const router = useRouter();
const queryRef = ref<FormInstance>();
const loading = ref(false);
const showSearch = ref(true);
const tableData = ref<PageDesignRecord[]>([]);
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 });
const query = reactive<{
  pageName: string;
  pageType: '' | PageDesignType;
  publishedStatus: '' | PublishStatus;
}>({ pageName: '', pageType: '', publishedStatus: '' });

const preview = reactive({
  expiresAt: 0,
  pageName: '',
  token: '',
  visible: false,
});
const versions = reactive({ pageId: '', pageName: '', visible: false });

async function initPage() {
  loading.value = true;
  const params: PageDesignQuery = {
    current: page.currentPage,
    pageName: query.pageName || undefined,
    pageType: query.pageType,
    publishedStatus: query.publishedStatus,
    size: page.pageSize,
  };
  try {
    const response = await getPage(params);
    tableData.value = response.records;
    page.total = response.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  queryRef.value?.resetFields();
  page.currentPage = 1;
  void initPage();
}

function openDesigner(id?: string) {
  const target = router.resolve({
    name: 'PageDesigner',
    params: id ? { id } : {},
  });
  window.open(target.href, '_blank', 'noopener,noreferrer');
}

async function handleCopy(row: PageDesignRecord) {
  const copied = await copyPage(row.id);
  ElMessage.success(`已复制为“${copied.pageName}”`);
  await initPage();
}

async function handlePreview(row: PageDesignRecord) {
  preview.token = await createPreviewToken(row.id, row.draftRevision);
  preview.expiresAt = Date.now() + PREVIEW_TTL_MS;
  preview.pageName = row.pageName;
  preview.visible = true;
}

async function handlePublish(row: PageDesignRecord) {
  await ElMessageBox.confirm(
    `发布后将更新“${row.pageName}”的线上版本，是否继续？`,
    '发布页面',
    { confirmButtonText: '发布', cancelButtonText: '取消', type: 'warning' },
  );
  await publishPage(row.id, { draftRevision: row.draftRevision });
  ElMessage.success('发布成功');
  await initPage();
}

async function handleUnpublish(row: PageDesignRecord) {
  await ElMessageBox.confirm(
    `下线后用户将无法访问“${row.pageName}”，历史版本仍会保留。`,
    '下线页面',
    { confirmButtonText: '下线', cancelButtonText: '取消', type: 'warning' },
  );
  await unpublishPage(row.id);
  ElMessage.success('页面已下线');
  await initPage();
}

function openVersions(row: PageDesignRecord) {
  versions.pageId = row.id;
  versions.pageName = row.pageName;
  versions.visible = true;
}

async function handleDelete(row: PageDesignRecord) {
  await ElMessageBox.confirm(`确认删除微页面“${row.pageName}”？`, '删除页面', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await delObj(row.id);
  ElMessage.success('删除成功');
  await initPage();
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

function draftLabel(row: PageDesignRecord) {
  if (row.publishedStatus !== '1') return '待发布';
  if (!row.publishedAt || !row.updateTime) return '已发布';
  return dayjs(row.updateTime).isAfter(dayjs(row.publishedAt))
    ? '有未发布修改'
    : '已同步';
}

function draftTagType(row: PageDesignRecord) {
  if (row.publishedStatus !== '1') return 'info';
  return draftLabel(row) === '有未发布修改' ? 'warning' : 'success';
}

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container page-design-list">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm
        v-show="showSearch"
        ref="queryRef"
        :inline="true"
        :model="query"
        class="filter-bar"
      >
        <ElFormItem label="页面名称" prop="pageName">
          <ElInput
            v-model="query.pageName"
            clearable
            placeholder="输入页面名称"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="页面类型" prop="pageType">
          <ElSelect v-model="query.pageType" clearable placeholder="全部类型">
            <ElOption label="首页" value="1" />
            <ElOption label="微页面" value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="发布状态" prop="publishedStatus">
          <ElSelect
            v-model="query.publishedStatus"
            clearable
            placeholder="全部状态"
          >
            <ElOption label="已发布" value="1" />
            <ElOption label="未发布" value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton :icon="Search" type="primary" @click="initPage">
            查询
          </ElButton>
          <ElButton :icon="Refresh" @click="resetQuery">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <div class="hx-table-toolbar">
        <ElButton
          v-access:code="'promotion:pagedesign:add'"
          :icon="Plus"
          type="primary"
          @click="openDesigner()"
        >
          新建页面
        </ElButton>
        <RightToolbar
          :refresh-btn="true"
          :search-btn="true"
          @refresh="initPage"
          @search="showSearch = !showSearch"
        />
      </div>

      <ElTable v-loading="loading" :data="tableData" border row-key="id">
        <ElTableColumn label="页面" min-width="220">
          <template #default="{ row }">
            <div class="page-cell">
              <strong>{{ row.pageName }}</strong>
              <span>{{ row.pageType === '1' ? '商城首页' : '微页面' }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="草稿状态" width="130" align="center">
          <template #default="{ row }">
            <ElTag :type="draftTagType(row)" effect="plain">
              {{ draftLabel(row) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="线上版本" min-width="160">
          <template #default="{ row }">
            <ElTooltip
              v-if="row.publishedVersionId"
              :content="row.publishedVersionId"
            >
              <span class="version-id">{{ row.publishedVersionId }}</span>
            </ElTooltip>
            <span v-else class="muted">尚未发布</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="最近编辑" width="160">
          <template #default="{ row }">
            {{ formatTime(row.updateTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="最近发布" width="160">
          <template #default="{ row }">
            {{ formatTime(row.publishedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn fixed="right" label="操作" width="300" align="center">
          <template #default="{ row }">
            <ElSpace :size="4">
              <ElTooltip content="编辑">
                <ElButton
                  v-access:code="'promotion:pagedesign:edit'"
                  :icon="EditPen"
                  aria-label="编辑"
                  circle
                  text
                  type="primary"
                  @click="openDesigner(row.id)"
                />
              </ElTooltip>
              <ElTooltip content="复制">
                <ElButton
                  v-access:code="'promotion:pagedesign:add'"
                  :icon="CopyDocument"
                  aria-label="复制"
                  circle
                  text
                  @click="handleCopy(row)"
                />
              </ElTooltip>
              <ElTooltip content="预览">
                <ElButton
                  v-access:code="'promotion:pagedesign:edit'"
                  :icon="View"
                  aria-label="预览"
                  circle
                  text
                  @click="handlePreview(row)"
                />
              </ElTooltip>
              <ElTooltip content="发布">
                <ElButton
                  v-if="
                    row.publishedStatus !== '1' ||
                    draftLabel(row) === '有未发布修改'
                  "
                  v-access:code="'promotion:pagedesign:publish'"
                  :icon="Upload"
                  aria-label="发布"
                  circle
                  text
                  type="success"
                  @click="handlePublish(row)"
                />
              </ElTooltip>
              <ElTooltip content="下线">
                <ElButton
                  v-if="row.publishedStatus === '1'"
                  v-access:code="'promotion:pagedesign:publish'"
                  :icon="VideoPause"
                  aria-label="下线"
                  circle
                  text
                  type="warning"
                  @click="handleUnpublish(row)"
                />
              </ElTooltip>
              <ElTooltip content="版本历史">
                <ElButton
                  v-access:code="'promotion:pagedesign:get'"
                  :icon="Clock"
                  aria-label="版本历史"
                  circle
                  text
                  @click="openVersions(row)"
                />
              </ElTooltip>
              <ElTooltip v-if="row.pageType === '0'" content="删除">
                <ElButton
                  v-access:code="'promotion:pagedesign:del'"
                  :icon="Delete"
                  aria-label="删除"
                  circle
                  text
                  type="danger"
                  @click="handleDelete(row)"
                />
              </ElTooltip>
            </ElSpace>
          </template>
        </ElTableColumn>
      </ElTable>

      <Pagination
        v-model:current="page.currentPage"
        v-model:size="page.pageSize"
        :total="page.total"
        @change="initPage"
      />
    </div>

    <VersionDialog
      v-model="versions.visible"
      :page-id="versions.pageId"
      :page-name="versions.pageName"
      @restored="initPage"
    />
    <PreviewDialog
      v-model="preview.visible"
      :expires-at="preview.expiresAt"
      :page-name="preview.pageName"
      :token="preview.token"
    />
  </div>
</template>

<style scoped>
.filter-bar :deep(.el-select) {
  width: 160px;
}

.page-cell {
  display: grid;
  gap: 4px;
}

.page-cell strong {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.page-cell span,
.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.version-id {
  display: block;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}
</style>
