<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage } from '#/api/upms/tenant-package';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
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

const Form = defineAsyncComponent(() => import('./form.vue'));

const state = reactive({
  queryParams: {
    name: '',
    status: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
// 字典
const { application_key, status } = useDict('application_key', 'status');
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
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
initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="state.queryParams"
        :visible="showSearch"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="套餐名称" prop="name">
          <ElInput
            v-model="state.queryParams.name"
            clearable
            placeholder="请输入套餐名称"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="套餐状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择套餐状态"
          >
            <ElOption
              v-for="(item, index) in status"
              :label="item.label"
              :value="item.value"
              :key="index"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="个套餐">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'upms:tenantpackage:add'"
            @click="add"
            :icon="Plus"
          >
            新增
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
      <Form ref="formRef" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        :data="state.tableData"
        class="hx-data-table"
        border
        empty-text="暂无租户套餐"
      >
        <ElTableColumn prop="name" label="套餐名称" />
        <ElTableColumn prop="originalPrice" label="原价（元）" width="150" />
        <ElTableColumn prop="salesPrice" label="销售价格（元）" width="150" />
        <ElTableColumn prop="status" label="状态" width="150" align="center">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="appKey" label="应用系统">
          <template #default="scope">
            <DictTag :options="application_key" :value="scope.row.appKey" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="220" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'upms:tenantpackage:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current-page="state.page.currentPage"
        v-model:page-size="state.page.pageSize"
        @change-page="initPage"
      />
    </div>
  </div>
</template>
