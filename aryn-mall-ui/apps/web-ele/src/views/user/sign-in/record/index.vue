<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import { ElButton, ElDatePicker, ElForm, ElFormItem, ElInput, ElTable, ElTableColumn } from 'element-plus';

import { getPage } from '#/api/user/sign-in-record';

const RightToolbar = defineAsyncComponent(() => import('#/components/right-toolbar/index.vue'));
const Pagination = defineAsyncComponent(() => import('#/components/pagination/index.vue'));

const state = reactive({
  queryParams: { nickname: '', dateRange: [] as string[] },
  page: { total: 0, currentPage: 1, pageSize: 10, asc: '', desc: 'create_time' },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const queryRef = ref();

const initPage = async () => {
  loading.value = true;
  const params: any = { current: state.page.currentPage, size: state.page.pageSize, asc: state.page.asc, desc: state.page.desc };
  if (state.queryParams.nickname) params.nickname = state.queryParams.nickname;
  if (state.queryParams.dateRange?.length === 2) {
    params.beginDate = state.queryParams.dateRange[0];
    params.endDate = state.queryParams.dateRange[1];
  }
  await getPage(params).then((res) => { state.tableData = res.records; state.page.total = res.total; loading.value = false; }).catch(() => { loading.value = false; });
};

const resetQuery = () => { queryRef.value.resetFields(); initPage(); };

initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :model="state.queryParams" ref="queryRef" :inline="true" v-show="showSearch">
        <ElFormItem label="会员昵称" prop="nickname">
          <ElInput v-model="state.queryParams.nickname" placeholder="请输入会员昵称" clearable />
        </ElFormItem>
        <ElFormItem label="签到日期" prop="dateRange">
          <ElDatePicker v-model="state.queryParams.dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">搜索</ElButton>
          <ElButton @click="resetQuery" :icon="Refresh">重置</ElButton>
        </ElFormItem>
      </ElForm>
      <div class="hx-table-toolbar">
        <div></div>
        <RightToolbar :search-btn="true" :refresh-btn="true" @search="showSearch = !showSearch" @refresh="initPage" />
      </div>
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="nickname" label="会员昵称" />
        <ElTableColumn prop="signDate" label="签到日期" width="120" />
        <ElTableColumn prop="consecutiveDay" label="连续签到天数" width="130" />
        <ElTableColumn prop="rewardPoint" label="获得积分" width="100" />
      </ElTable>
      <Pagination :total="state.page.total" v-model:current="state.page.currentPage" v-model:size="state.page.pageSize" @change="initPage" />
    </div>
  </div>
</template>
