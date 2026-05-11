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

import { getPage } from '#/api/pay/trade-order';

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
        <ElFormItem label="订单编号" prop="outTradeNo">
          <ElInput
            v-model="state.queryParams.outTradeNo"
            clearable
            placeholder="请输入订单编号"
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
        <ElTableColumn prop="outTradeNo" label="订单编号" />
        <ElTableColumn prop="description" label="订单描述" />
        <ElTableColumn prop="amount" label="支付金额" />
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
