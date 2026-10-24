<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { cancelObj, getPage } from '#/api/user/recharge-order';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { formatMoney } from '#/utils/util';

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
    const endDate = state.queryParams.dateRange[1];
    if (endDate) params.endTime = `${endDate.slice(0, 10)} 23:59:59`;
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

const handleCancel = (row: any) => {
  ElMessageBox.confirm(`确定关闭充值订单「${row.orderNo}」吗？`, '提示', {
    confirmButtonText: '确认关闭',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    await cancelObj(row.id);
    ElMessage.success('订单已关闭');
    initPage();
  });
};

initPage();
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
        <ElFormItem label="订单号" prop="orderNo">
          <ElInput
            v-model="state.queryParams.orderNo"
            placeholder="请输入订单号"
            clearable
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="支付状态" prop="payStatus">
          <ElSelect
            v-model="state.queryParams.payStatus"
            placeholder="请选择"
            clearable
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
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="笔充值订单">
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
        empty-text="暂无充值订单"
      >
        <ElTableColumn prop="orderNo" label="订单号" />
        <ElTableColumn prop="nickname" label="用户昵称" />
        <ElTableColumn prop="rechargeAmount" label="充值金额" width="110">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.rechargeAmount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="giftAmount" label="赠送金额" width="110">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.giftAmount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="giftPoint" label="赠送积分" width="100" />
        <ElTableColumn prop="payStatus" label="支付状态" width="100">
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
        <ElTableColumn label="操作" width="100" align="center">
          <template #default="scope">
            <ElButton
              v-if="scope.row.payStatus === '0'"
              link
              type="warning"
              v-access:code="'user:rechargeorder:edit'"
              @click="handleCancel(scope.row)"
            >
              关闭
            </ElButton>
          </template>
        </ElTableColumn>
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
