<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage } from '#/api/upms/tenant';
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
const TenantMenu = defineAsyncComponent(() => import('./tenantmenu.vue'));
// 字典
const { status } = useDict('status');
const queryRef = ref();
const state = reactive({
  queryParams: {
    name: '',
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
const showSearch = ref(true);
const loading = ref(false);
const refForm = ref();
const tenantMenuRef = ref();

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
  refForm.value.initForm();
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  refForm.value.initForm(row);
};
initPage();
/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
};
const upMenu = (id: string) => {
  tenantMenuRef.value.initMenu(id);
};
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
        <ElFormItem label="租户名称" prop="name">
          <ElInput
            v-model="state.queryParams.name"
            clearable
            placeholder="请输入租户名称"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            v-access:code="'upms:systenant:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <Form ref="refForm" @init-page="initPage" />
      <TenantMenu ref="tenantMenuRef" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="id" label="租户ID" />
        <ElTableColumn prop="name" label="租户名" />
        <ElTableColumn prop="siteUrl" label="官网" />
        <ElTableColumn prop="phone" label="手机号" />
        <ElTableColumn prop="authBeginTime" label="授权开始时间" />
        <ElTableColumn prop="authEndTime" label="授权结束时间" />
        <ElTableColumn prop="status" label="状态">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="220" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'upms:systenant:edit'"
              :icon="Edit"
              @click="edit(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="primary"
              v-if="scope.row.id !== '1881232176465358849'"
              v-access:code="'upms:systenant:add'"
              :icon="Edit"
              @click="upMenu(scope.row.id)"
            >
              配置菜单
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
