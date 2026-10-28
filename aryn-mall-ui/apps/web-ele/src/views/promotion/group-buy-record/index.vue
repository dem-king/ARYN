<script lang="ts" setup>
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

import { getRecordPage } from '#/api/promotion/group-buy-activity';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { formatMoney } from '#/utils/util';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

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
        <ElFormItem label="拼团活动ID" prop="activityId">
          <ElInput
            v-model="state.queryParams.activityId"
            clearable
            placeholder="请输入拼团活动ID"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="拼团状态" prop="groupStatus">
          <ElSelect
            v-model="state.queryParams.groupStatus"
            clearable
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
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="条拼团记录">
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
        empty-text="暂无拼团记录"
      >
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
        >
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.groupPrice) }}
            </span>
          </template>
        </ElTableColumn>
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
