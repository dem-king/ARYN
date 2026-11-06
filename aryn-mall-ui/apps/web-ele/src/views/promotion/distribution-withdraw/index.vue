<script lang="ts" setup>
import type {
  DistributionWithdrawRecord,
  DistributionWithdrawStatus,
} from '#/api/promotion/distribution-withdraw';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { View } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getById, getPage } from '#/api/promotion/distribution-withdraw';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
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

/**
 * 提现功能已下线（佣金改为兑换优惠券），本页只保留历史查询与详情。
 * 审核/打款接口在后端返回“提现功能已下线，仅保留历史查询”，前端不再提供操作入口。
 */
const detailState = reactive({
  visible: false,
  loading: false,
  record: null as DistributionWithdrawRecord | null,
});

const openDetail = async (row: DistributionWithdrawRecord) => {
  detailState.record = row;
  detailState.visible = true;
  detailState.loading = true;
  try {
    const detail = await getById(row.id);
    if (detail) {
      detailState.record = detail;
    }
  } catch {
    // 详情失败时保留列表行数据
  } finally {
    detailState.loading = false;
  }
};

/** 金额统一 2 位展示 */
const formatAmount = (value?: number) => formatMoney(value);

initPage();

useKeepAliveRefresh(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElAlert
        class="hx-withdraw-offline"
        :closable="false"
        show-icon
        title="提现已下线，仅保留历史查询"
        type="warning"
      />
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
        <ElTableColumn label="操作" width="100" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              :icon="View"
              link
              type="primary"
              @click="openDetail(scope.row as DistributionWithdrawRecord)"
            >
              详情
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

      <!-- 历史提现单详情（只读） -->
      <ElDialog v-model="detailState.visible" title="提现单详情" width="560px">
        <ElDescriptions
          v-if="detailState.record"
          v-loading="detailState.loading"
          :column="1"
          border
          label-width="120px"
        >
          <ElDescriptionsItem label="提现单号">
            {{ detailState.record.withdrawNo }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="分销员ID">
            {{ detailState.record.userId }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="提现金额">
            ¥{{ formatAmount(detailState.record.amount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="收款方式">
            {{ detailState.record.accountType || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="收款人">
            {{ detailState.record.accountName || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="收款账号">
            {{ detailState.record.accountNo || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <DictTag
              :options="distribution_withdraw_status"
              :value="detailState.record.status"
            />
          </ElDescriptionsItem>
          <ElDescriptionsItem label="审核人">
            {{ detailState.record.auditBy || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="打款流水号">
            {{ detailState.record.payoutNo || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="打款确认人">
            {{ detailState.record.payoutBy || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="打款时间">
            {{ detailState.record.payoutTime || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="申请时间">
            {{ detailState.record.createTime || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="驳回原因">
            {{ detailState.record.rejectReason || '—' }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped>
.hx-withdraw-offline {
  margin-bottom: 14px;
}
</style>
