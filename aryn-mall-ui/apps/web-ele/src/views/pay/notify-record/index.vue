<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElFormItem,
  ElInput,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/pay/notify-record';
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
  queryParams: {
    outTradeNo: '',
  },
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 10, // 每页显示多少条
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
    desc: 'create_time',
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
        <ElFormItem label="商户订单号" prop="outTradeNo">
          <ElInput
            v-model="state.queryParams.outTradeNo"
            clearable
            placeholder="请输入商户订单号"
            @keyup.enter="initPage"
          />
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="条回调记录">
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
        empty-text="暂无回调记录"
      >
        <ElTableColumn
          prop="outTradeNo"
          label="商户订单号"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="channelOrderNo"
          label="渠道订单号"
          show-overflow-tooltip
        />
        <ElTableColumn prop="request" label="请求报文" show-overflow-tooltip />
        <ElTableColumn prop="response" label="响应报文" show-overflow-tooltip />
        <ElTableColumn prop="type" label="回调类型">
          <template #default="scope">
            <ElTag v-if="scope.row.type === '1'" type="primary">支付回调</ElTag>
            <ElTag v-if="scope.row.type === '2'" type="success">退款回调</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" />
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
