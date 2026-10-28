<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/upms/sys-login-log';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const showSearch = ref(true);
const loading = ref(false);

const state = reactive({
  queryParams: {
    ipAddr: '',
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

initPage();

useKeepAliveRefresh(initPage);
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
        <ElFormItem label="登录地址" prop="ipAddr">
          <ElInput
            v-model="state.queryParams.ipAddr"
            clearable
            placeholder="请输入操作地址"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="操作状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择操作状态"
          >
            <ElOption label="成功" value="1" />
            <ElOption label="失败" value="0" />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="条日志">
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
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        border
        empty-text="暂无登录日志"
      >
        <ElTableColumn prop="userName" label="登录用户" />
        <ElTableColumn prop="ipAddr" label="登录地址" />
        <ElTableColumn prop="location" label="登录地点" />
        <ElTableColumn prop="createTime" label="登录时间" />
        <ElTableColumn prop="browser" label="浏览器" />
        <ElTableColumn prop="os" label="操作系统" />
        <ElTableColumn prop="status" label="操作状态">
          <template #default="scope">
            <ElTag type="danger" v-if="scope.row.status === '0'">失败</ElTag>
            <ElTag type="success" v-if="scope.row.status === '1'">成功</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="msg" label="操作描述" />
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
