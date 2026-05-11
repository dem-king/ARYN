<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/pay/refund-order';

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
const queryRef = ref();
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
/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
};
initPage();
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
        <ElFormItem label="退款单号" prop="refundTradeNo">
          <ElInput
            v-model="state.queryParams.refundTradeNo"
            clearable
            placeholder="请输入退款单号"
          />
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
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="refundTradeNo" label="退款单号" />
        <ElTableColumn prop="outTradeNo" label="支付单号" />
        <ElTableColumn prop="payAmount" label="支付金额" />
        <ElTableColumn prop="refundAmount" label="退款金额" />
        <ElTableColumn prop="extra" label="额外参数" />
        <ElTableColumn prop="refundStatus" label="退款状态">
          <template #default="scope">
            <ElTag v-if="scope.row.refundStatus === '0'" type="info">
              待退款
            </ElTag>
            <ElTag v-if="scope.row.refundStatus === '1'" type="success">
              退款中
            </ElTag>
            <ElTag v-if="scope.row.refundStatus === '2'" type="success">
              已退款
            </ElTag>
            <ElTag v-if="scope.row.refundStatus === '3'" type="danger">
              退款失败
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
