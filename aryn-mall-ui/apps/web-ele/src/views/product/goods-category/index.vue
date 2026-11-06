<script lang="ts" setup>
import type { CategoryBadgeStyle } from './logic';

import { defineAsyncComponent, nextTick, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Sort } from '@element-plus/icons-vue';
import {
  ElButton,
  ElImage,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage } from '#/api/product/goods-category';
import { getList as getBadgeList } from '#/api/product/goods-category-badge';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useDict } from '#/utils/dict';

import {
  badgePreviewStyle,
  buildBadgeStyleMap,
  resolveBadgeStyle,
} from './logic';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);

const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
// 字典
const { status } = useDict('status');
const state = reactive({
  tableData: [],
  queryParams: {},
});
const refreshTable = ref(true);
const isDefaultExpand = ref(false);
const loading = ref(false);
const formRef = ref();

/**
 * 类目角标样式映射：key 为角标配置的 badgeKey，value 为接口下发的文字与双色。
 *
 * 角标已配置化，不再在前端写死「荐绿 / 热红」；接口失败或未知 key 时列表显示
 * 占位横线，绝不凭空兜底旧配色（避免管理端看到与 C 端不一致的角标）。
 */
const badgeStyleMap = ref<Record<string, CategoryBadgeStyle>>({});
const loadBadgeStyleMap = async () => {
  try {
    badgeStyleMap.value = buildBadgeStyleMap(await getBadgeList());
  } catch {
    badgeStyleMap.value = {};
  }
};
/** 列表单元格解析：badgeType → 配置下发样式；无角标/未知 key 返回 null */
const resolveRowBadge = (row: any) =>
  resolveBadgeStyle(badgeStyleMap.value, row?.badgeType);

const initPage = async () => {
  loading.value = true;
  void loadBadgeStyleMap();
  await getPage()
    .then((response) => {
      state.tableData = response;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 新增按钮
 */
const add = () => {
  formRef.value.initForm();
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  formRef.value.initForm(row);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该类目，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};
/**
 * 展开/折叠菜单
 */
const onExpand = () => {
  isDefaultExpand.value = !isDefaultExpand.value;
  refreshTable.value = false;
  nextTick(() => {
    refreshTable.value = true;
  });
};
initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 工具栏 -->
      <PageToolbar>
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'product:goodscategory:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
          <ElButton @click="onExpand" :icon="Sort">展开/折叠</ElButton>
        </template>
        <template #right>
          <RightToolbar
            :search-btn="false"
            :refresh-btn="true"
            @refresh="initPage"
          />
        </template>
      </PageToolbar>
      <Form ref="formRef" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable
        v-if="refreshTable"
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        style="width: 100%"
        :default-expand-all="isDefaultExpand"
        border
        empty-text="暂无类目"
      >
        <ElTableColumn prop="name" label="类目名称" width="200" />
        <ElTableColumn prop="description" label="类目描述" />
        <ElTableColumn prop="categoryPic" label="类目图片">
          <template #default="scope">
            <ElImage
              style="width: 50px; height: 50px"
              v-if="scope.row.categoryPic"
              :src="scope.row.categoryPic"
              :preview-src-list="[scope.row.categoryPic]"
              fit="cover"
              :preview-teleported="true"
            />
          </template>
        </ElTableColumn>

        <ElTableColumn prop="status" label="状态">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="sort" label="排序" align="center" />
        <ElTableColumn label="类目角标" align="center" width="100">
          <template #default="scope">
            <!-- 样式取「类目角标」配置：有图渲染图片角标（宽高/圆角按 rpx/2 缩放），
                 否则渲染文字角标；无角标 / 未知 key 显示占位横线 -->
            <ElImage
              v-if="resolveRowBadge(scope.row)?.iconUrl"
              class="category-badge-image"
              :src="resolveRowBadge(scope.row)!.iconUrl"
              fit="contain"
              :style="badgePreviewStyle(resolveRowBadge(scope.row)!)"
            />
            <span
              v-else-if="resolveRowBadge(scope.row)"
              class="category-badge"
              :style="badgePreviewStyle(resolveRowBadge(scope.row)!)"
            >
              {{ resolveRowBadge(scope.row)!.text }}
            </span>
            <span v-else class="category-badge-empty">-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" align="center" width="200">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'product:goodscategory:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'product:goodscategory:del'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </div>
</template>

<style lang="scss" scoped>
/* 与 C 端分类页左栏的角标同尺寸（那边是 19rpx ≈ 13px 字号、4rpx 圆角） */
.category-badge {
  display: inline-block;
  font-weight: 600;
  text-align: center;
}

/* 图片角标：与文字角标同列宽高由配置的 rpx/2 预览换算给出 */
.category-badge-image {
  display: inline-block;
  vertical-align: middle;
}

.category-badge-empty {
  color: var(--el-text-color-placeholder);
}
</style>
