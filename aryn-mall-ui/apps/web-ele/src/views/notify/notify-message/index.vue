<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

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

import { getPage } from '#/api/notify/notify-message';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const notifyTypeOptions = [
  { value: 1, label: '订单消息' },
  { value: 2, label: '支付消息' },
  { value: 3, label: '物流消息' },
  { value: 4, label: '营销消息' },
  { value: 5, label: '系统消息' },
  { value: 6, label: '社交消息' },
];

const readStatusOptions = [
  { value: '0', label: '未读' },
  { value: '1', label: '已读' },
];

const queryRef = ref<FormInstance>();
const state = reactive({
  queryParams: {
    userId: '',
    title: '',
    notifyType: '' as number | string,
    readStatus: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);

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

const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
};

const notifyTypeText = (type: number) => {
  return notifyTypeOptions.find((t) => t.value === type)?.label || '-';
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="用户ID" prop="userId">
          <ElInput
            v-model="state.queryParams.userId"
            clearable
            style="width: 200px"
            placeholder="请输入用户ID"
          />
        </ElFormItem>
        <ElFormItem label="标题" prop="title">
          <ElInput
            v-model="state.queryParams.title"
            clearable
            style="width: 200px"
            placeholder="请输入标题"
          />
        </ElFormItem>
        <ElFormItem label="消息类型" prop="notifyType">
          <ElSelect
            v-model="state.queryParams.notifyType"
            clearable
            style="width: 200px"
            placeholder="请选择消息类型"
          >
            <ElOption
              v-for="item in notifyTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="已读状态" prop="readStatus">
          <ElSelect
            v-model="state.queryParams.readStatus"
            clearable
            style="width: 200px"
            placeholder="请选择已读状态"
          >
            <ElOption
              v-for="item in readStatusOptions"
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

      <div class="hx-table-toolbar">
        <div></div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="userId"
          label="用户ID"
          align="center"
          width="180"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="notifyType"
          label="消息类型"
          align="center"
          width="100"
        >
          <template #default="scope">
            {{ notifyTypeText(scope.row.notifyType) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="title"
          label="标题"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="content"
          label="内容"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="bizType"
          label="业务类型"
          align="center"
          width="100"
        />
        <ElTableColumn prop="readStatus" label="已读" align="center" width="80">
          <template #default="scope">
            <ElTag v-if="scope.row.readStatus === '1'" type="success">
              已读
            </ElTag>
            <ElTag v-else type="danger">未读</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
      </ElTable>
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>
  </div>
</template>
