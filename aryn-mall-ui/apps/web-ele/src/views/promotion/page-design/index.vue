<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
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
} from 'element-plus';

import { delObj, editObj, getPage } from '#/api/promotion/page-design';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
// 字典
const { status } = useDict('status');
const state = reactive({
  queryParams: {
    status: '',
    pageName: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const queryRef = ref();
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 重置搜索表单
 */
const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
};
/**
 * 新增按钮
 */
const add = () => {
  window.open(`/#/pagedesign/form`, '_blank');
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  window.open(`/#/pagedesign/form?id=${row.id}`, '_blank');
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该微页面，是否继续?', '提示', {
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
 * 修改首页状态
 */
const updateHomeStatus = (row: any) => {
  ElMessageBox.confirm('此操作将该微页面设置成首页，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    editObj({ id: row.id, homeStatus: '1' })
      .then(() => {
        ElMessage.success('设置成功');
        initPage();
      })
      .catch(() => {});
  });
};
initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="名称" prop="pageName">
          <ElInput
            v-model="state.queryParams.pageName"
            clearable
            placeholder="请输入页面名称"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            style="width: 200px"
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery(queryRef)" :icon="Refresh">
            重置
          </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton type="primary" @click="add" :icon="Plus"> 新增 </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="pageName" label="名称" align="center" />
        <ElTableColumn prop="homeStatus" label="首页状态" align="center">
          <template #default="scope">
            <ElTag v-if="scope.row.homeStatus === '0'" type="danger">
              否
            </ElTag>
            <ElTag v-if="scope.row.homeStatus === '1'" type="success">
              是
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" align="center">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" align="center" />
        <ElTableColumn label="操作" width="200" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'promotion:pagedesign:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-if="scope.row.pageType === '1'"
              v-access:code="'promotion:pagedesign:del'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
            <ElButton
              v-if="scope.row.homeStatus === '0' && scope.row.pageType === '1'"
              link
              type="primary"
              v-access:code="'promotion:pagedesign:edit'"
              @click="updateHomeStatus(scope.row)"
              :icon="Edit"
            >
              设置首页
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>
  </div>
</template>
