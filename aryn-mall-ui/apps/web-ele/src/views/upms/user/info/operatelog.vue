<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/upms/sys-log';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: { ipAddr: '', status: '' },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: ['create_time'],
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

const resetQuery = () => {
  queryRef.value.resetFields();
};

// 初始化页面
initPage();
</script>
<template>
  <div class="hx-layout-container-auto hx-layout-container-view">
    <!-- 搜索 -->
    <ElForm
      :model="state.queryParams"
      ref="queryRef"
      :inline="true"
      v-show="showSearch"
    >
      <ElFormItem label="操作地址">
        <ElInput v-model="state.queryParams.ipAddr" clearable />
      </ElFormItem>
      <ElFormItem label="操作状态">
        <ElSelect v-model="state.queryParams.status" clearable>
          <ElOption label="成功" value="1" />
          <ElOption label="失败" value="0" />
        </ElSelect>
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
      <div></div>
      <RightToolbar
        :search-btn="true"
        :refresh-btn="true"
        @search="showSearch = !showSearch"
        @refresh="initPage"
      />
    </div>
    <!-- 列表 -->
    <ElTable v-loading="loading" :data="state.tableData" border>
      <ElTableColumn prop="userName" label="操作用户" />
      <ElTableColumn prop="title" label="操作标题" />
      <ElTableColumn prop="ipAddr" label="操作地址" />
      <ElTableColumn prop="location" label="操作地点" />
      <ElTableColumn prop="method" label="操作方法" />
      <ElTableColumn prop="status" label="操作状态">
        <template #default="scope">
          <ElTag type="danger" v-if="scope.row.status === '0'">失败</ElTag>
          <ElTag type="success" v-if="scope.row.status === '1'">成功</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="requestTime" label="请求时长">
        <template #default="scope">
          <ElTag type="info">{{ scope.row.requestTime }}ms</ElTag>
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
</template>
