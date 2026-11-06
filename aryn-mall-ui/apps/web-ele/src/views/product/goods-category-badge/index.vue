<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage } from '#/api/product/goods-category-badge';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

import { badgePreviewStyle, buildBadgeStyle } from '../goods-category/logic';

const Form = defineAsyncComponent(() => import('./form.vue'));
const formRef = ref();
const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const query = reactive({ badgeText: '', current: 1, size: 10, status: '' });
const showSearch = ref(true);

/**
 * 列表行追加角标预览样式。
 *
 * 与类目列表共用 buildBadgeStyle 归一规则：图片角标文字可空、颜色非法回退默认色；
 * 无文字且无图、或文字角标颜色非法时返回 null，预览列显示占位横线。
 */
function decorateRow(row: any) {
  return { ...row, badgePreview: buildBadgeStyle(row) };
}

/** 角标文字列：图片角标可以没有文字，空值显示占位横线 */
function displayBadgeText(row: any) {
  return String(row?.badgeText ?? '').trim() || '-';
}

async function load() {
  loading.value = true;
  try {
    const response = await getPage(query);
    rows.value = (response.records ?? []).map((row: any) => decorateRow(row));
    total.value = response.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  Object.assign(query, { current: 1, badgeText: '', status: '' });
  load();
}

async function remove(id: string) {
  await ElMessageBox.confirm(
    '确认删除该角标吗？已被类目引用的角标不可删除。',
    '提示',
    { type: 'warning' },
  );
  await delObj(id);
  ElMessage.success('删除成功');
  load();
}

load();

useKeepAliveRefresh(load);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="query"
        :visible="showSearch"
        @reset="resetQuery"
        @search="load"
      >
        <ElFormItem label="角标文字" prop="badgeText">
          <ElInput
            v-model="query.badgeText"
            clearable
            maxlength="2"
            placeholder="请输入角标文字"
            @keyup.enter="load"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect v-model="query.status" clearable placeholder="请选择状态">
            <ElOption label="启用" value="0" />
            <ElOption label="停用" value="1" />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="total" total-label="个角标">
        <template #left>
          <ElButton
            v-access:code="'product:goodscategorybadge:add'"
            type="primary"
            :icon="Plus"
            @click="formRef.open()"
          >
            新增
          </ElButton>
        </template>
      </PageToolbar>
      <!-- 列表：列宽一律用 min-width（不用固定 width），否则所有列宽之和小于容器时
           Element Plus 不会拉伸，右侧会留一条空白，表格无法自适应宽屏 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="rows"
        border
        empty-text="暂无角标"
      >
        <ElTableColumn label="角标预览" min-width="100" align="center">
          <template #default="scope">
            <!-- 有图按图片角标渲染（宽=高=badgeHeight/2、圆角=borderRadius/2），否则渲染文字角标 -->
            <div v-if="scope.row.badgePreview" class="badge-preview-cell">
              <ElImage
                v-if="scope.row.badgePreview.iconUrl"
                class="badge-preview-image"
                :src="scope.row.badgePreview.iconUrl"
                :style="badgePreviewStyle(scope.row.badgePreview)"
                :preview-src-list="[scope.row.badgePreview.iconUrl]"
                :preview-teleported="true"
                fit="cover"
              />
              <span
                v-else
                class="badge-preview"
                :style="badgePreviewStyle(scope.row.badgePreview)"
              >
                {{ scope.row.badgePreview.text }}
              </span>
              <span class="badge-preview-meta">
                高{{ scope.row.badgePreview.height }} · 圆角{{
                  scope.row.badgePreview.borderRadius
                }}
              </span>
            </div>
            <span v-else class="badge-preview-empty">-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="badgeText" label="角标文字" min-width="110">
          <template #default="scope">
            {{ displayBadgeText(scope.row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="底色" min-width="140" align="center">
          <template #default="scope">
            <span class="badge-color">
              <span
                class="badge-color__swatch"
                :style="{ backgroundColor: scope.row.bgColor }"
              ></span>
              <span class="badge-color__value">{{ scope.row.bgColor }}</span>
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="字色" min-width="140" align="center">
          <template #default="scope">
            <span class="badge-color">
              <span
                class="badge-color__swatch"
                :style="{ backgroundColor: scope.row.textColor }"
              ></span>
              <span class="badge-color__value">{{ scope.row.textColor }}</span>
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="sort" label="排序" min-width="80" align="center" />
        <ElTableColumn prop="status" label="状态" min-width="80" align="center">
          <template #default="scope">
            <ElTag :type="scope.row.status === '0' ? 'success' : 'info'">
              {{ scope.row.status === '0' ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" min-width="170" />
        <ElTableColumn
          label="操作"
          min-width="170"
          fixed="right"
          align="center"
        >
          <template #default="scope">
            <ElButton
              v-access:code="'product:goodscategorybadge:edit'"
              link
              type="primary"
              :icon="Edit"
              @click="formRef.open(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              v-access:code="'product:goodscategorybadge:del'"
              link
              type="danger"
              :icon="Delete"
              @click="remove(scope.row.id)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElPagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-4 justify-end"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @change="load"
      />
      <Form ref="formRef" @refresh="load" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
/* 角标预览单元格：角标在上、尺寸说明在下（说明固定 11px 灰字，不再挤占列宽） */
.badge-preview-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
}

.badge-preview-meta {
  font-size: 11px;
  line-height: 14px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

/* 文字角标：高度/行高/字号/左右内边距/圆角由 badgePreviewStyle 动态下发（rpx/2） */
.badge-preview {
  display: inline-block;
  font-weight: 600;
  white-space: nowrap;
}

.badge-preview-image {
  vertical-align: middle;
}

.badge-preview-empty {
  color: var(--el-text-color-placeholder);
}

.badge-color {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-variant-numeric: tabular-nums;
}

.badge-color__swatch {
  width: 14px;
  height: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 3px;
}

.badge-color__value {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
