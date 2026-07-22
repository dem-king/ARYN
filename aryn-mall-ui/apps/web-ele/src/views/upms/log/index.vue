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

import { getById, getPage } from '#/api/upms/sys-log';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const Detail = defineAsyncComponent(() => import('./detail.vue'));

const showSearch = ref(true);
const loading = ref(false);
const detailRef = ref();
const queryRef = ref();
const state = reactive({
  queryParams: { ipAddr: '', status: '' },
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
    .then((response: any) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

/**
 * 详情按钮
 */
const detail = (row: any) => {
  detailRef.value.dialog = true;
  getById(row.id).then((response) => {
    detailRef.value.form = response;
  });
};

initPage();

/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
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
        <ElFormItem label="操作地址" prop="ipAddr">
          <ElInput
            v-model="state.queryParams.ipAddr"
            clearable
            placeholder="请输入操作地址"
          />
        </ElFormItem>
        <ElFormItem label="操作状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择操作状态"
            style="width: 200px"
          >
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
      <!-- 详情 -->
      <Detail ref="detailRef" />
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
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="180" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              icon="view"
              @click="detail(scope.row)"
            >
              详情
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
