<script lang="ts" setup>
import type {
  DistributionWithdrawRecord,
  DistributionWithdrawStatus,
} from '#/api/promotion/distribution-withdraw';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { CircleCheck, CircleClose } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { audit, getPage } from '#/api/promotion/distribution-withdraw';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useDict } from '#/utils/dict';
import { formatMoney } from '#/utils/util';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

const { distribution_withdraw_status } = useDict(
  'distribution_withdraw_status',
);
const loading = ref(false);
const showSearch = ref(true);
const state = reactive({
  queryParams: {
    status: '' as '' | DistributionWithdrawStatus,
    userId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [] as DistributionWithdrawRecord[],
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

async function doApprove(row: DistributionWithdrawRecord) {
  const payout = await ElMessageBox.prompt(
    '请输入线下打款流水号',
    '确认打款并通过',
    {
      confirmButtonText: '确认已打款',
      cancelButtonText: '取消',
      inputPattern: /^\S{1,64}$/,
      inputErrorMessage: '流水号不能为空且不能超过64字符',
      type: 'warning',
    },
  ).catch(() => null);
  if (!payout?.value) return;
  await audit({ id: row.id, payoutNo: payout.value.trim(), status: '1' });
  ElMessage.success('审核通过');
  initPage();
}

async function doReject(row: DistributionWithdrawRecord) {
  const reason = await ElMessageBox.prompt('请输入驳回原因', '审核驳回', {
    inputPattern: /^.{2,100}$/,
    inputErrorMessage: '驳回原因长度需在2-100字符',
  }).catch(() => null);
  if (!reason?.value) {
    return;
  }
  await audit({ id: row.id, status: '2', rejectReason: reason.value });
  ElMessage.success('已驳回');
  initPage();
}

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
        <ElFormItem label="分销员ID" prop="userId">
          <ElInput
            v-model="state.queryParams.userId"
            clearable
            placeholder="请输入用户ID"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="提现状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in distribution_withdraw_status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="笔提现单">
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
        border
        empty-text="暂无提现记录"
      >
        <ElTableColumn
          prop="withdrawNo"
          label="提现单号"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="userId"
          label="分销员ID"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="amount" label="提现金额" align="center">
          <template #default="scope">
            <span class="hx-money"> ¥{{ formatMoney(scope.row.amount) }} </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="accountType" label="收款方式" align="center" />
        <ElTableColumn prop="accountName" label="收款人" align="center" />
        <ElTableColumn
          prop="accountNo"
          label="收款账号"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="status" label="状态" align="center">
          <template #default="scope">
            <DictTag
              :options="distribution_withdraw_status"
              :value="scope.row.status"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="auditBy" label="审核人" align="center" />
        <ElTableColumn
          prop="payoutNo"
          label="打款流水号"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="payoutBy" label="打款确认人" align="center" />
        <ElTableColumn prop="payoutTime" label="打款时间" width="180" />
        <ElTableColumn prop="createTime" label="申请时间" width="180" />
        <ElTableColumn label="操作" width="160" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              v-if="scope.row.status === '0'"
              v-access:code="'promotion:distributionwithdraw:audit'"
              :icon="CircleCheck"
              link
              type="primary"
              @click="doApprove(scope.row as DistributionWithdrawRecord)"
            >
              通过
            </ElButton>
            <ElButton
              v-if="scope.row.status === '0'"
              v-access:code="'promotion:distributionwithdraw:audit'"
              :icon="CircleClose"
              link
              type="danger"
              @click="doReject(scope.row as DistributionWithdrawRecord)"
            >
              驳回
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
