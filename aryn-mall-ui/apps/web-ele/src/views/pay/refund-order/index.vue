<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElFormItem,
  ElInput,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/pay/refund-order';
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
    refundTradeNo: '',
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
        <ElFormItem label="退款单号" prop="refundTradeNo">
          <ElInput
            v-model="state.queryParams.refundTradeNo"
            clearable
            placeholder="请输入退款单号"
            @keyup.enter="initPage"
          />
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="笔退单">
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
        empty-text="暂无退款单"
      >
        <ElTableColumn prop="refundTradeNo" label="退款单号" />
        <ElTableColumn prop="outTradeNo" label="支付单号" />
        <ElTableColumn prop="payAmount" label="支付金额" width="120">
          <template #default="scope">
            <span class="hx-money"
              >¥{{ formatMoney(scope.row.payAmount) }}</span
            >
          </template>
        </ElTableColumn>
        <ElTableColumn prop="refundAmount" label="退款金额" width="120">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.refundAmount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="extra" label="额外参数" />
        <ElTableColumn prop="refundStatus" label="退款状态">
          <template #default="scope">
            <ElTag v-if="scope.row.refundStatus === '0'" type="info">
              待退款
            </ElTag>
            <ElTag v-else-if="scope.row.refundStatus === '1'" type="primary">
              退款中
            </ElTag>
            <ElTag v-else-if="scope.row.refundStatus === '2'" type="success">
              已退款
            </ElTag>
            <ElTag v-else-if="scope.row.refundStatus === '3'" type="danger">
              退款失败
            </ElTag>
            <ElTag v-else-if="scope.row.refundStatus === '4'" type="warning">
              待确认
            </ElTag>
            <ElTag v-else type="info">
              未知({{ scope.row.refundStatus }})
            </ElTag>
          </template>
        </ElTableColumn>

        <ElTableColumn prop="notifyUrl" label="通知地址" />
        <ElTableColumn prop="paySuccessTime" label="支付成功时间" />
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
