<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type { DistributionOrderRecord } from '#/api/promotion/distribution-order';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Coin, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage, settleOrder } from '#/api/promotion/distribution-order';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

const queryRef = ref<FormInstance>();
const { distribution_order_status } = useDict('distribution_order_status');
const loading = ref(false);
const showSearch = ref(true);
const state = reactive({
  queryParams: {
    bizOrderId: '',
    distributorUserId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [] as DistributionOrderRecord[],
});

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
};

async function doSettle(row: DistributionOrderRecord) {
  await ElMessageBox.confirm('确认释放该笔待结算佣金？', '手动结算', {
    confirmButtonText: '确认结算',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await settleOrder(row.id);
  ElMessage.success('结算成功');
  initPage();
}

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
        <ElFormItem label="业务订单号" prop="bizOrderId">
          <ElInput
            v-model="state.queryParams.bizOrderId"
            clearable
            style="width: 200px"
            placeholder="请输入订单号"
          />
        </ElFormItem>
        <ElFormItem label="分销员ID" prop="distributorUserId">
          <ElInput
            v-model="state.queryParams.distributorUserId"
            clearable
            style="width: 200px"
            placeholder="请输入分销员ID"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery(queryRef)" :icon="Refresh">
            重置
          </ElButton>
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
        <ElTableColumn
          prop="bizOrderId"
          label="业务订单号"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="buyerUserId"
          label="买家用户ID"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="distributorUserId"
          label="分销员ID"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="orderAmount" label="订单金额" align="center" />
        <ElTableColumn prop="freightAmount" label="运费" align="center" />
        <ElTableColumn
          prop="commissionBaseAmount"
          label="佣金基数(不含运费)"
          align="center"
          min-width="150"
        />
        <ElTableColumn prop="commissionLevel" label="佣金层级" align="center" />
        <ElTableColumn
          prop="commissionAmount"
          label="佣金金额"
          align="center"
        />
        <ElTableColumn
          prop="refundedCommissionAmount"
          label="已退佣金"
          align="center"
        />
        <ElTableColumn prop="status" label="结算状态" align="center">
          <template #default="scope">
            <DictTag
              :options="distribution_order_status"
              :value="scope.row.status"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="settleAt" label="计划结算时间" width="180" />
        <ElTableColumn prop="settleTime" label="实际结算时间" width="180" />
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="140" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              v-if="scope.row.status === '0'"
              v-access:code="'promotion:distributionorder:settle'"
              :icon="Coin"
              link
              type="primary"
              @click="doSettle(scope.row as DistributionOrderRecord)"
            >
              触发结算
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
