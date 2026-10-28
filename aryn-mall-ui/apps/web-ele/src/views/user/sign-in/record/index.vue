<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage } from '#/api/user/sign-in-record';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: { nickname: '', dateRange: [] as string[] },
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

const initPage = async () => {
  loading.value = true;
  const params: any = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  if (state.queryParams.nickname) params.nickname = state.queryParams.nickname;
  if (state.queryParams.dateRange?.length === 2) {
    params.beginDate = state.queryParams.dateRange[0];
    params.endDate = state.queryParams.dateRange[1];
  }
  await getPage(params)
    .then((res) => {
      state.tableData = res.records;
      state.page.total = res.total;
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
        <ElFormItem label="会员昵称" prop="nickname">
          <ElInput
            v-model="state.queryParams.nickname"
            placeholder="请输入会员昵称"
            clearable
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="签到日期" prop="dateRange">
          <ElDatePicker
            v-model="state.queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
          />
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="条签到记录">
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
        row-key="id"
        border
        empty-text="暂无签到记录"
      >
        <ElTableColumn prop="nickname" label="会员昵称" />
        <ElTableColumn prop="signDate" label="签到日期" width="120" />
        <ElTableColumn prop="consecutiveDay" label="连续签到天数" width="130" />
        <ElTableColumn prop="rewardPoint" label="获得积分" width="100" />
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
