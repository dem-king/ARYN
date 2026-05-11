<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/user/recharge-order';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: {
    orderNo: '',
    payStatus: '',
    dateRange: [] as string[],
  },
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
const queryRef = ref();

const initPage = async () => {
  loading.value = true;
  const params: any = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  if (state.queryParams.orderNo) params.orderNo = state.queryParams.orderNo;
  if (state.queryParams.payStatus)
    params.payStatus = state.queryParams.payStatus;
  if (state.queryParams.dateRange?.length === 2) {
    params.beginTime = state.queryParams.dateRange[0];
    params.endTime = state.queryParams.dateRange[1];
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

const resetQuery = () => {
  queryRef.value.resetFields();
  initPage();
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
        <ElFormItem label="订单号" prop="orderNo">
          <ElInput
            v-model="state.queryParams.orderNo"
            placeholder="请输入订单号"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="支付状态" prop="payStatus">
          <ElSelect
            v-model="state.queryParams.payStatus"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <ElOption label="待支付" value="0" />
            <ElOption label="已支付" value="1" />
            <ElOption label="已取消" value="2" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="创建时间" prop="dateRange">
          <ElDatePicker
            v-model="state.queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh">重置</ElButton>
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
        <ElTableColumn prop="orderNo" label="订单号" />
        <ElTableColumn prop="nickname" label="用户昵称" />
        <ElTableColumn prop="rechargeAmount" label="充值金额" width="100" />
        <ElTableColumn prop="giftAmount" label="赠送金额" width="100" />
        <ElTableColumn prop="giftPoint" label="赠送积分" width="100" />
        <ElTableColumn prop="payStatus" label="支付状态" width="80">
          <template #default="scope">
            <ElTag v-if="scope.row.payStatus === '0'" type="info">
              待支付
            </ElTag>
            <ElTag v-else-if="scope.row.payStatus === '1'" type="success">
              已支付
            </ElTag>
            <ElTag v-else-if="scope.row.payStatus === '2'" type="danger">
              已取消
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="payTime" label="支付时间" width="170" />
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
  </div>
</template>
