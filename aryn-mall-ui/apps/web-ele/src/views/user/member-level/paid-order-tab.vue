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

import { getPaidOrderPage } from '#/api/user/member-level';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: {
    orderNo: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [] as any[],
});
const showSearch = ref(true);
const loading = ref(false);

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPaidOrderPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

const resetQuery = () => {
  state.queryParams.orderNo = '';
  initPage();
};

const formatPayStatus = (status: string) => {
  const map: Record<string, { label: string; type: string }> = {
    '0': { label: '待支付', type: 'warning' },
    '1': { label: '已支付', type: 'success' },
    '2': { label: '已取消', type: 'info' },
    '3': { label: '已退款', type: 'danger' },
  };
  return map[status] || { label: '未知', type: 'info' };
};

initPage();
</script>
<template>
  <div>
    <!-- 搜索栏 -->
    <ElForm v-show="showSearch" :inline="true" :model="state.queryParams">
      <ElFormItem label="订单号" prop="orderNo">
        <ElInput
          v-model="state.queryParams.orderNo"
          placeholder="请输入订单号"
          clearable
          @keyup.enter="initPage"
        />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :icon="Search" @click="initPage">
          搜索
        </ElButton>
        <ElButton :icon="Refresh" @click="resetQuery">重置</ElButton>
      </ElFormItem>
    </ElForm>
    <!-- 工具栏 -->
    <div class="hx-table-toolbar">
      <RightToolbar
        v-model:show-search="showSearch"
        :refresh-btn="true"
        @refresh="initPage"
      />
    </div>

    <!-- 列表 -->
    <ElTable v-loading="loading" :data="state.tableData" border>
      <ElTableColumn prop="orderNo" label="订单号" width="200" />
      <ElTableColumn prop="nickname" label="用户昵称" width="120" />
      <ElTableColumn prop="phone" label="手机号" width="130" />
      <ElTableColumn prop="levelName" label="会员等级" width="120" />
      <ElTableColumn prop="price" label="支付金额" width="100">
        <template #default="scope">
          ¥{{ scope.row.price?.toFixed(2) ?? '0.00' }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="duration" label="有效期(月)" width="110" />
      <ElTableColumn prop="payStatus" label="支付状态" width="100">
        <template #default="scope">
          <ElTag :type="formatPayStatus(scope.row.payStatus).type">
            {{ formatPayStatus(scope.row.payStatus).label }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="payTime" label="支付时间" width="170" />
      <ElTableColumn prop="expireTime" label="到期时间" width="170" />
      <ElTableColumn prop="createTime" label="创建时间" width="170" />
    </ElTable>
    <!-- 分页 -->
    <Pagination
      :total="state.page.total"
      v-model:current="state.page.currentPage"
      v-model:size="state.page.pageSize"
      @change="initPage"
    />
  </div>
</template>
