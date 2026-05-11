<script setup lang="ts">
import { reactive, ref } from 'vue';

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

import { getPage } from '#/api/upms/sys-login-log';

const props = defineProps({
  userId: {
    type: String,
    default: '',
  },
});

const state = reactive({
  queryParams: {
    createId: props.userId,
    ipAddr: '',
    status: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [],
});

const showSearch = ref(false);
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

// 解构响应式数据供模板使用
</script>

<template>
  <div class="layout-padding-auto layout-padding-view">
    <!-- 搜索 -->
    <ElForm
      :model="state.queryParams"
      ref="queryRef"
      :inline="true"
      v-show="state.showSearch"
    >
      <ElFormItem label="操作地址" prop="ipAddr">
        <ElInput v-model="state.queryParams.ipAddr" clearable />
      </ElFormItem>
      <ElFormItem label="操作状态" prop="status">
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
      <right-toolbar
        :search-btn="true"
        :refresh-btn="true"
        @search="showSearch = !showSearch"
        @refresh="initPage"
      />
    </div>
    <!-- 列表 -->
    <ElTable v-loading="loading" :data="state.tableData">
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
    <pagination
      v-model:total="state.page.total"
      v-model:current-page="state.page.currentPage"
      v-model:page-size="state.page.pageSize"
      @change-page="initPage"
    />
  </div>
</template>
