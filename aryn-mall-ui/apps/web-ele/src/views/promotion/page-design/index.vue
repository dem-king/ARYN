<script setup lang="ts">
import type { FormInstance } from 'element-plus';

import type { CanvasThemeVars } from '../page-designer/schema/theme-presets';
import type { PageDesignRowAction } from './list-actions';

import type {
  PageDesignQuery,
  PageDesignRecord,
  PageDesignTheme,
  PageDesignType,
  PublishStatus,
} from '#/api/promotion/page-design';

import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { EditPen, Plus, Refresh, Search, View } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  copyPage,
  createPreviewToken,
  delObj,
  EFFECTIVE_SLOT_PAGE_TYPES,
  getEffectivePages,
  getPage,
  getThemes,
  pageTypeLabel,
  pageTypeToQueryToken,
  setAsHome,
  submitRelease,
  unpublishPage,
} from '#/api/promotion/page-design';

import { buildCanvasThemeVars } from '../page-designer/schema/theme-presets';
import EffectivePageThumb from './components/effective-page-thumb.vue';
import MetricsDialog from './components/metrics-dialog.vue';
import PreviewDialog from './components/preview-dialog.vue';
import { PREVIEW_TTL_MS } from './components/preview-utils';
import ReleaseDialog from './components/release-dialog.vue';
import RowActionDropdown from './components/row-action-dropdown.vue';
import TemplateCreateDialog from './components/template-create-dialog.vue';
import VersionDialog from './components/version-dialog.vue';
import {
  draftLabel,
  draftTagType,
  effectiveLabel,
  pageTypeTagType,
  showEffectiveBadge,
} from './list-actions';

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
const effectivePages = ref<PageDesignRecord[]>([]);
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
const releases = reactive({ pageId: '', pageName: '', visible: false });
const metrics = reactive({ pageId: '', pageName: '', visible: false });
const templateCreateVisible = ref(false);

/** 「当前生效」卡片固定槽位顺序：商城首页 → 分类页 → 个人中心页 → 商品详情页 */
const EFFECTIVE_SLOT_TYPES: PageDesignType[] = EFFECTIVE_SLOT_PAGE_TYPES;

interface EffectiveSlot {
  label: string;
  page?: PageDesignRecord;
  type: PageDesignType;
}

const effectiveSlots = computed<EffectiveSlot[]>(() => {
  const byType = new Map(
    effectivePages.value.map((page) => [page.pageType, page]),
  );
  return EFFECTIVE_SLOT_TYPES.map((type) => ({
    label: pageTypeLabel(type),
    page: byType.get(type),
    type,
  }));
});

/** 商城默认主题：生效卡片缩略图与 C 端实机取同一套主题变量（页面自带主题在画布内优先） */
const themes = ref<PageDesignTheme[]>([]);
const previewThemeVars = computed<CanvasThemeVars>(() =>
  buildCanvasThemeVars(
    themes.value.find((theme) => theme.mallDefaultFlag === '1'),
  ),
);

async function loadThemesQuietly() {
  try {
    themes.value = await getThemes();
  } catch {
    // 主题库加载失败退回内置默认配色，不阻塞列表
    themes.value = [];
  }
}

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
    // 生效汇总失败时降级为空卡片，不拖垮列表本身（兼容未升级的后端）
    const [response, effective] = await Promise.all([
      getPage(params),
      getEffectivePages().catch(() => [] as PageDesignRecord[]),
    ]);
    tableData.value = response.records;
    page.total = response.total;
    effectivePages.value = effective;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  queryRef.value?.resetFields();
  page.currentPage = 1;
  void initPage();
}

function openDesigner(id?: string, pageType?: PageDesignType) {
  const queryToken = !id && pageType ? pageTypeToQueryToken(pageType) : '';
  const target = router.resolve({
    name: 'PageDesigner',
    params: id ? { id } : {},
    query: queryToken ? { type: queryToken } : {},
  });
  window.open(target.href, '_blank', 'noopener,noreferrer');
}

/** 新建页面：微页面 / 商品详情页 / 分类页 / 个人中心页；'template' 走「从模板新建」 */
function handleCreate(command: 'template' | PageDesignType) {
  if (command === 'template') {
    templateCreateVisible.value = true;
    return;
  }
  openDesigner(undefined, command);
}

function handleCreatedFromTemplate(pageId: string) {
  openDesigner(pageId);
  void initPage();
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
    `提交后按发布流程更新“${row.pageName}”的线上版本（未开启审批时立即发布），是否继续？`,
    '发布页面',
    {
      confirmButtonText: '提交发布',
      cancelButtonText: '取消',
      type: 'warning',
    },
  );
  const release = await submitRelease(row.id, {
    draftRevision: row.draftRevision,
  });
  ElMessage.success(
    release.releaseStatus === '1' ? '发布成功' : '发布申请已提交，等待审批',
  );
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

async function handleSetAsHome(row: PageDesignRecord) {
  await ElMessageBox.confirm(
    `移动端首页将切换为“${row.pageName}”，原首页保留但不再作为首页展示，是否继续？`,
    '设为首页',
    {
      confirmButtonText: '设为首页',
      cancelButtonText: '取消',
      type: 'warning',
    },
  );
  await setAsHome(row.id);
  ElMessage.success(`已将“${row.pageName}”设为移动端首页`);
  await initPage();
}

function openVersions(row: PageDesignRecord) {
  versions.pageId = row.id;
  versions.pageName = row.pageName;
  versions.visible = true;
}

function openReleases(row: PageDesignRecord) {
  releases.pageId = row.id;
  releases.pageName = row.pageName;
  releases.visible = true;
}

function openMetrics(row: PageDesignRecord) {
  metrics.pageId = row.id;
  metrics.pageName = row.pageName;
  metrics.visible = true;
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

/** 「⋯」菜单分发：表格行与生效卡片共用 */
function handleRowAction(action: PageDesignRowAction, row: PageDesignRecord) {
  switch (action) {
    case 'copy': {
      void handleCopy(row);
      break;
    }
    case 'delete': {
      void handleDelete(row);
      break;
    }
    case 'metrics': {
      openMetrics(row);
      break;
    }
    case 'publish': {
      void handlePublish(row);
      break;
    }
    case 'releases': {
      openReleases(row);
      break;
    }
    case 'set-home': {
      void handleSetAsHome(row);
      break;
    }
    case 'unpublish': {
      void handleUnpublish(row);
      break;
    }
    case 'versions': {
      openVersions(row);
      break;
    }
  }
}

function openSlotEditor(slot: EffectiveSlot) {
  if (slot.page) openDesigner(slot.page.id);
}

function previewSlot(slot: EffectiveSlot) {
  if (slot.page) void handlePreview(slot.page);
}

function handleSlotAction(action: PageDesignRowAction, slot: EffectiveSlot) {
  if (!slot.page) return;
  handleRowAction(action, slot.page);
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

onMounted(() => {
  void initPage();
  void loadThemesQuietly();
});
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
            <ElOption label="商品详情页" value="2" />
            <ElOption label="分类页" value="3" />
            <ElOption label="个人中心页" value="4" />
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
        <ElDropdown trigger="click" @command="handleCreate">
          <ElButton
            v-access:code="'promotion:pagedesign:add'"
            :icon="Plus"
            type="primary"
          >
            新建页面
          </ElButton>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem command="template">从模板新建</ElDropdownItem>
              <ElDropdownItem divided command="0">微页面</ElDropdownItem>
              <ElDropdownItem command="2">商品详情页</ElDropdownItem>
              <ElDropdownItem command="3">分类页</ElDropdownItem>
              <ElDropdownItem command="4">个人中心页</ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
        <RightToolbar
          :refresh-btn="true"
          :search-btn="true"
          @refresh="initPage"
          @search="showSearch = !showSearch"
        />
      </div>

      <div class="effective-strip">
        <div class="effective-head">
          <span class="effective-title">当前生效</span>
          <span class="muted">
            C 端实际渲染的装修页；同类型多条已发布时按最近发布生效
          </span>
        </div>
        <div class="effective-cards">
          <div
            v-for="slot in effectiveSlots"
            :key="slot.type"
            class="effective-card"
            :class="{ 'is-empty': !slot.page }"
          >
            <div class="card-head">
              <ElTag
                :type="pageTypeTagType(slot.type)"
                effect="plain"
                size="small"
              >
                {{ slot.label }}
              </ElTag>
              <ElTag
                v-if="slot.page"
                effect="light"
                size="small"
                type="success"
              >
                {{ effectiveLabel(slot.page) }}
              </ElTag>
            </div>
            <template v-if="slot.page">
              <EffectivePageThumb
                :page="slot.page"
                :theme-vars="previewThemeVars"
              />
              <div class="card-name" :title="slot.page.pageName">
                {{ slot.page.pageName }}
              </div>
              <div class="card-meta">
                最近发布 {{ formatTime(slot.page.publishedAt) }}
              </div>
              <div class="card-actions">
                <ElTooltip content="编辑">
                  <ElButton
                    v-access:code="'promotion:pagedesign:edit'"
                    :icon="EditPen"
                    aria-label="编辑"
                    circle
                    text
                    type="primary"
                    @click="openSlotEditor(slot)"
                  />
                </ElTooltip>
                <ElTooltip content="预览">
                  <ElButton
                    v-access:code="'promotion:pagedesign:edit'"
                    :icon="View"
                    aria-label="预览"
                    circle
                    text
                    @click="previewSlot(slot)"
                  />
                </ElTooltip>
                <RowActionDropdown
                  :page="slot.page"
                  @action="(action) => handleSlotAction(action, slot)"
                />
              </div>
            </template>
            <template v-else>
              <div class="card-name is-placeholder">未配置</div>
              <div class="card-meta">
                {{
                  slot.type === '1'
                    ? '发布微页面后可「设为首页」'
                    : `发布${slot.label}装修后自动生效`
                }}
              </div>
              <div v-if="slot.type !== '1'" class="card-actions">
                <ElButton
                  v-access:code="'promotion:pagedesign:add'"
                  plain
                  size="small"
                  type="primary"
                  @click="handleCreate(slot.type)"
                >
                  新建{{ slot.label }}
                </ElButton>
              </div>
            </template>
          </div>
        </div>
      </div>

      <ElTable v-loading="loading" :data="tableData" border row-key="id">
        <ElTableColumn label="页面" min-width="260">
          <template #default="{ row }">
            <div class="page-cell">
              <strong>{{ row.pageName }}</strong>
              <ElTag
                :type="pageTypeTagType(row.pageType as PageDesignType)"
                effect="plain"
                size="small"
              >
                {{ pageTypeLabel(row.pageType as PageDesignType) }}
              </ElTag>
              <ElTag
                v-if="showEffectiveBadge(row as PageDesignRecord)"
                effect="light"
                size="small"
                type="success"
              >
                {{ effectiveLabel(row as PageDesignRecord) }}
              </ElTag>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="草稿状态" width="130" align="center">
          <template #default="{ row }">
            <ElTag :type="draftTagType(row as PageDesignRecord)" effect="plain">
              {{ draftLabel(row as PageDesignRecord) }}
            </ElTag>
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
        <ElTableColumn fixed="right" label="操作" width="180" align="center">
          <template #default="{ row }">
            <div class="row-actions">
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
              <ElTooltip content="预览">
                <ElButton
                  v-access:code="'promotion:pagedesign:edit'"
                  :icon="View"
                  aria-label="预览"
                  circle
                  text
                  @click="handlePreview(row as PageDesignRecord)"
                />
              </ElTooltip>
              <RowActionDropdown
                :page="row as PageDesignRecord"
                @action="
                  (action) => handleRowAction(action, row as PageDesignRecord)
                "
              />
            </div>
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
    <TemplateCreateDialog
      v-model="templateCreateVisible"
      @created="handleCreatedFromTemplate"
    />
    <ReleaseDialog
      v-model="releases.visible"
      :page-id="releases.pageId"
      :page-name="releases.pageName"
    />
    <MetricsDialog
      v-model="metrics.visible"
      :page-id="metrics.pageId"
      :page-name="metrics.pageName"
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

.effective-strip {
  margin-bottom: 12px;
}

.effective-head {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin-bottom: 8px;
}

.effective-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.effective-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.effective-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.effective-card.is-empty {
  border-style: dashed;
}

.card-head {
  display: flex;
  gap: 6px;
  align-items: center;
}

.card-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.card-name.is-placeholder {
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.card-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.card-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.page-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  align-items: center;
}

.page-cell strong {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.row-actions {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
}
</style>
