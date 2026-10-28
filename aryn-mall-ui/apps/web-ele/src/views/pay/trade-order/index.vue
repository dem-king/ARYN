<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElFormItem,
  ElInput,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/pay/trade-order';
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
const getPayTypeDesc = (extra: any): string => {
  if (!extra) {
    return '';
  }
  try {
    const { payType } = JSON.parse(extra);
    const payTypeMap: Record<string, string> = {
      '1': '微信',
      '2': '支付宝',
    };
    return payTypeMap[payType] || '未知';
  } catch (error) {
    console.warn('extra 解析失败:', error);
    return '未知';
  }
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
        <ElFormItem label="订单编号" prop="outTradeNo">
          <ElInput
            v-model="state.queryParams.outTradeNo"
            clearable
            placeholder="请输入订单编号"
            @keyup.enter="initPage"
          />
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="笔交易">
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
        empty-text="暂无交易订单"
      >
        <ElTableColumn prop="outTradeNo" label="订单编号" />
        <ElTableColumn prop="description" label="订单描述" />
        <ElTableColumn prop="amount" label="支付金额（元）" width="150">
          <template #default="scope">
            <span class="trade-amount">
              ¥{{ formatMoney(scope.row.amount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="payStatus" label="支付状态">
          <template #default="scope">
            <ElTag v-if="scope.row.payStatus === '0'" type="danger">
              待支付
            </ElTag>
            <ElTag v-if="scope.row.payStatus === '1'" type="success">
              支付成功
            </ElTag>
          </template>
        </ElTableColumn>

        <ElTableColumn prop="notifyUrl" label="通知地址" />
        <ElTableColumn prop="paySuccessTime" label="支付成功时间" />
        <ElTableColumn prop="extra" label="额外参数">
          <template #default="scope">
            <div>支付类型：{{ getPayTypeDesc(scope.row.extra) }}</div>
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

<style scoped lang="scss">
.trade-amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>
