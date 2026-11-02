<script lang="ts" setup>
import type { MenuTreeNode } from './menu-tree';

import { computed, defineAsyncComponent, nextTick, reactive, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { $t } from '@vben/locales';

import { Delete, Edit, Expand, Fold, Plus } from '@element-plus/icons-vue';
import { breakpointsTailwind, useBreakpoints } from '@vueuse/core';
import {
  ElButton,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj as deleteMenu, getList as getMenuList } from '#/api/upms/menu';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';

import { filterMenuTree, flattenMenuTree } from './menu-tree';
import Form from './modules/form.vue';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const state = reactive({
  queryParams: {
    applicationKey: '',
    keyword: '',
    type: '',
  },
  matchedCount: 0,
  tableData: [] as MenuTreeNode[],
});
const showSearch = ref(true);
const loading = ref(false);
const expandAll = ref(false);
const refreshTable = ref(true);

const breakpoints = useBreakpoints(breakpointsTailwind);
// 窄屏放不下全部列时优先保留名称/类型/路由/权限/排序/操作
const showAdvancedColumns = computed(
  () => breakpoints.greaterOrEqual('2xl').value,
);

const { application_key, menu_type } = useDict('application_key', 'menu_type');

let rawMenus: MenuTreeNode[] = [];

function typeLabel(type?: null | string) {
  const label = menu_type?.value?.find(
    (item: any) => String(item.value) === String(type ?? '0'),
  )?.label;
  return label ?? (type === '1' ? '按钮' : '菜单');
}

function appLabel(applicationKey?: null | string) {
  if (!applicationKey) {
    return '未设置';
  }
  return (
    application_key?.value?.find(
      (item: any) => String(item.value) === String(applicationKey),
    )?.label ?? applicationKey
  );
}

const hasActiveFilter = () =>
  Boolean(
    state.queryParams.keyword.trim() ||
    state.queryParams.type ||
    state.queryParams.applicationKey,
  );

function applyFilters() {
  const result = filterMenuTree(rawMenus, state.queryParams);
  state.tableData = result.nodes;
  state.matchedCount = result.matchedCount;
}

function initPage() {
  loading.value = true;
  return getMenuList()
    .then((response) => {
      rawMenus = (response ?? []) as MenuTreeNode[];
      applyFilters();
    })
    .catch(() => {})
    .finally(() => {
      loading.value = false;
    });
}

function onSearch() {
  // 搜索/筛选后自动展开命中分支，否则结果藏在折叠行里看不到
  if (hasActiveFilter()) {
    expandAll.value = true;
  }
  applyFilters();
}

function onReset() {
  state.queryParams.keyword = '';
  state.queryParams.type = '';
  state.queryParams.applicationKey = '';
  applyFilters();
}

function toggleExpandAll() {
  expandAll.value = !expandAll.value;
  refreshTable.value = false;
  nextTick(() => {
    refreshTable.value = true;
  });
}

function onEdit(row: any) {
  formDrawerApi.setData(row).open();
}
function onCreate() {
  formDrawerApi.setData({}).open();
}
function onAppend(row: any) {
  formDrawerApi.setData({ parentId: row.id }).open();
}

function hasChildren(id: string) {
  return (
    (flattenMenuTree(rawMenus).find((node) => node.id === id)?.children
      ?.length ?? 0) > 0
  );
}

function onDelete(row: any) {
  // 过滤后的树可能剪掉了子节点，判断是否存在下级必须回到完整数据里查
  if (hasChildren(row.id)) {
    ElMessage.warning('该菜单存在下级菜单，请先删除下级菜单');
    return;
  }
  ElMessageBox.confirm('此操作将删除该菜单，是否继续?', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确认',
    type: 'warning',
  }).then(() => {
    deleteMenu(row.id)
      .then(() => {
        ElMessage.success($t('ui.actionMessage.deleteSuccess', [row.name]));
        initPage();
      })
      .catch(() => {});
  });
}

initPage();

useKeepAliveRefresh(initPage);
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <FormDrawer @success="initPage" />
      <!-- 搜索 -->
      <PageFilterPanel
        :model="state.queryParams"
        :visible="showSearch"
        @reset="onReset"
        @search="onSearch"
      >
        <ElFormItem label="关键字" prop="keyword">
          <ElInput
            v-model="state.queryParams.keyword"
            clearable
            placeholder="名称 / 权限标识 / 路径"
            @keyup.enter="onSearch"
          />
        </ElFormItem>
        <ElFormItem label="菜单类型" prop="type">
          <ElSelect
            v-model="state.queryParams.type"
            clearable
            placeholder="全部类型"
          >
            <ElOption
              v-for="item in menu_type"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="应用系统" prop="applicationKey">
          <ElSelect
            v-model="state.queryParams.applicationKey"
            clearable
            placeholder="全部应用"
          >
            <ElOption
              v-for="item in application_key"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.matchedCount" total-label="个菜单项">
        <template #left>
          <ElButton
            v-access:code="'upms:sysmenu:add'"
            type="primary"
            :icon="Plus"
            @click="onCreate"
          >
            新增菜单
          </ElButton>
          <ElButton :icon="expandAll ? Fold : Expand" @click="toggleExpandAll">
            {{ expandAll ? '收起全部' : '展开全部' }}
          </ElButton>
        </template>
        <template #right>
          <RightToolbar
            :search-btn="true"
            :refresh-btn="true"
            @search="showSearch = !showSearch"
            @refresh="initPage"
          />
        </template>
      </PageToolbar>
      <!-- 列表 -->
      <ElTable
        v-if="refreshTable"
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        :default-expand-all="expandAll"
        border
        empty-text="暂无菜单项"
      >
        <ElTableColumn label="菜单名称" fixed="left" min-width="240">
          <template #default="{ row }">
            <div class="menu-name">
              <span class="menu-name__icon">
                <IconifyIcon
                  v-if="row.type === '1'"
                  icon="carbon:security"
                  class="size-full"
                />
                <IconifyIcon
                  v-else
                  :icon="row.icon || 'carbon:circle-dash'"
                  class="size-full"
                />
              </span>
              <span class="menu-name__text">{{ row.name }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="类型" width="90" align="center">
          <template #default="{ row }">
            <ElTag
              size="small"
              effect="plain"
              :type="row.type === '1' ? 'info' : 'primary'"
            >
              {{ typeLabel(row.type) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="路由地址" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="row.path ? 'menu-code' : 'menu-empty'">
              {{ row.path || '—' }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="showAdvancedColumns"
          label="组件路径"
          min-width="200"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span :class="row.component ? 'menu-code' : 'menu-empty'">
              {{ row.component || '—' }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="权限标识" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="row.permission ? 'menu-code' : 'menu-empty'">
              {{ row.permission || '—' }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="排序" width="70" align="center">
          <template #default="{ row }">
            <span
              :class="
                row.sort === null || row.sort === undefined ? 'menu-empty' : ''
              "
            >
              {{ row.sort ?? '—' }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="showAdvancedColumns" label="应用系统" width="110">
          <template #default="{ row }">
            <span :class="row.applicationKey ? '' : 'menu-empty'">
              {{ appLabel(row.applicationKey) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="250" align="center" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              :icon="Edit"
              v-access:code="'upms:sysmenu:edit'"
              @click="onEdit(row)"
            >
              修改
            </ElButton>
            <ElButton
              v-if="row.type !== '1'"
              link
              type="primary"
              :icon="Plus"
              v-access:code="'upms:sysmenu:add'"
              @click="onAppend(row)"
            >
              新增下级
            </ElButton>
            <ElButton
              link
              type="danger"
              :icon="Delete"
              v-access:code="'upms:sysmenu:del'"
              @click="onDelete(row)"
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
.menu-name {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.menu-name__icon {
  display: inline-flex;
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  color: var(--el-text-color-secondary);
}

.menu-name__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.menu-empty {
  color: var(--el-text-color-placeholder);
}
</style>
