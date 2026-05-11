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

import { getRecordPage } from '#/api/promotion/group-buy-activity';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const queryRef = ref<FormInstance>();

const state = reactive({
  queryParams: {
    activityId: '',
    groupStatus: '',
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

const groupStatusOptions = [
  { label: '拼团中', value: '0' },
  { label: '成功', value: '1' },
  { label: '失败', value: '2' },
];

const initPage = async () => {
  if (!state.queryParams.activityId) {
    return;
  }
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    desc: state.page.desc,
    activityId: state.queryParams.activityId,
  };
  await getRecordPage(Object.assign(params, state.queryParams))
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

const statusTagType = (status: string) => {
  if (status === '0') return 'warning';
  if (status === '1') return 'success';
  if (status === '2') return 'danger';
  return 'info';
};

const statusLabel = (status: string) => {
  const found = groupStatusOptions.find((item) => item.value === status);
  return found ? found.label : status;
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
        <ElFormItem label="拼团活动ID" prop="activityId">
          <ElInput
            v-model="state.queryParams.activityId"
            clearable
            style="width: 200px"
            placeholder="请输入拼团活动ID"
          />
        </ElFormItem>
        <ElFormItem label="拼团状态" prop="groupStatus">
          <ElSelect
            v-model="state.queryParams.groupStatus"
            clearable
            style="width: 200px"
            placeholder="请选择拼团状态"
          >
            <ElOption
              v-for="item in groupStatusOptions"
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
        <ElTableColumn
          prop="id"
          label="拼团记录ID"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="leaderUserId"
          label="团长ID"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="groupPrice"
          label="拼团价"
          align="center"
          width="100"
        />
        <ElTableColumn
          prop="groupNum"
          label="成团人数"
          align="center"
          width="90"
        />
        <ElTableColumn
          prop="currentNum"
          label="当前人数"
          align="center"
          width="90"
        />
        <ElTableColumn
          prop="groupStatus"
          label="拼团状态"
          align="center"
          width="90"
        >
          <template #default="scope">
            <ElTag :type="statusTagType(scope.row.groupStatus)">
              {{ statusLabel(scope.row.groupStatus) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="expireAt"
          label="过期时间"
          align="center"
          width="180"
        />
        <ElTableColumn
          prop="successAt"
          label="成团时间"
          align="center"
          width="180"
        />
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
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
