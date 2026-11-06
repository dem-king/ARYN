<script lang="ts" setup>
import type {
  SalesOrderRecord,
  SalesOrderStatistics,
  SalesOrderStatus,
} from '#/api/promotion/sales-order';

import { computed, defineAsyncComponent, reactive, ref } from 'vue';

import { formatDateTime } from '@vben/utils';

import { Download, View } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTreeSelect,
} from 'element-plus';

import { getPage, getStatistics } from '#/api/promotion/sales-order';
import { getOptions } from '#/api/promotion/sales-staff';
import { getTreeList } from '#/api/upms/dept';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';
import { downloadBlobFile, formatMoney } from '#/utils/util';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

interface DeptNode {
  children?: DeptNode[];
  id: string;
  name: string;
}

interface StaffOption {
  deptName?: string;
  id: string;
  staffName?: string;
}

const { sales_order_status } = useDict('sales_order_status');

const loading = ref(false);
const loadingStatistics = ref(false);
const exporting = ref(false);
const showSearch = ref(true);
const deptData = ref<DeptNode[]>([]);
const staffOptions = ref<StaffOption[]>([]);
const statistics = ref<null | SalesOrderStatistics>(null);
const state = reactive({
  queryParams: {
    salesStaffId: '',
    deptId: '',
    status: '' as '' | SalesOrderStatus,
    orderNo: '',
    payTimeRange: [] as string[],
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [] as SalesOrderRecord[],
});

const formatTime = (value?: string) => (value ? formatDateTime(value) : '—');
const formatRate = (value?: null | number) =>
  value === null || value === undefined
    ? '—'
    : `${(Number(value) * 100).toFixed(2)}%`;
const staffLabel = (item: StaffOption) =>
  `${item.staffName ?? item.id}${item.deptName ? `（${item.deptName}）` : ''}`;

/** 过滤空值，避免把 '' 传给后端当等值条件 */
const buildParams = () => {
  const { payTimeRange, ...rest } = state.queryParams;
  const params: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(rest)) {
    if (value !== '' && value !== null && value !== undefined) {
      params[key] = value;
    }
  }
  if (payTimeRange?.length === 2) {
    params.payTimeStart = payTimeRange[0];
    params.payTimeEnd = payTimeRange[1];
  }
  return params;
};

const statCards = computed(() => [
  {
    label: '业绩单数',
    value: String(statistics.value?.orderCount ?? 0),
  },
  {
    label: '成交额',
    value: `¥${formatMoney(statistics.value?.tradeAmount)}`,
  },
  {
    label: '提成金额',
    value: `¥${formatMoney(statistics.value?.commissionAmount)}`,
  },
  {
    label: '退款金额',
    value: `¥${formatMoney(statistics.value?.refundAmount)}`,
  },
]);

const loadStaffOptions = () => {
  getOptions()
    .then((response) => {
      staffOptions.value = response ?? [];
    })
    .catch(() => {
      staffOptions.value = [];
    });
};

const loadDeptTree = () => {
  getTreeList()
    .then((response) => {
      deptData.value = response as DeptNode[];
    })
    .catch(() => {
      deptData.value = [];
    });
};

const loadStatistics = async () => {
  loadingStatistics.value = true;
  try {
    statistics.value = await getStatistics(buildParams());
  } catch {
    statistics.value = null;
  } finally {
    loadingStatistics.value = false;
  }
};

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    ...buildParams(),
  };
  await getPage(params)
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
  loadStatistics();
};

// ---------------------------------------------------------------------------
// 详情
// ---------------------------------------------------------------------------
const detailState = reactive({
  visible: false,
  row: null as null | SalesOrderRecord,
});

const openDetail = (row: SalesOrderRecord) => {
  detailState.row = row;
  detailState.visible = true;
};

// ---------------------------------------------------------------------------
// 导出（与列表筛选条件一致）
// ---------------------------------------------------------------------------
const localTimestamp = () => {
  const now = new Date();
  const pad = (input: number) => String(input).padStart(2, '0');
  return (
    `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
    `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  );
};

const handleExport = () => {
  exporting.value = true;
  downloadBlobFile(
    '/promotion/sales/order/export',
    buildParams(),
    `销售业绩单_${localTimestamp()}.xlsx`,
  ).finally(() => {
    exporting.value = false;
  });
  ElMessage.success('导出已开始，请稍候');
};

loadStaffOptions();
loadDeptTree();
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
        <ElFormItem label="归属销售" prop="salesStaffId">
          <ElSelect
            v-model="state.queryParams.salesStaffId"
            clearable
            filterable
            placeholder="请选择销售"
          >
            <ElOption
              v-for="item in staffOptions"
              :key="item.id"
              :label="staffLabel(item)"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="所属部门" prop="deptId">
          <ElTreeSelect
            v-model="state.queryParams.deptId"
            :data="deptData"
            :props="{ children: 'children', label: 'name' }"
            node-key="id"
            check-strictly
            clearable
            filterable
            placeholder="请选择部门"
          />
        </ElFormItem>
        <ElFormItem label="业绩单状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in sales_order_status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="订单号" prop="orderNo">
          <ElInput
            v-model="state.queryParams.orderNo"
            clearable
            placeholder="请输入订单号"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="支付时间" prop="payTimeRange">
          <ElDatePicker
            v-model="state.queryParams.payTimeRange"
            end-placeholder="结束时间"
            range-separator="-"
            start-placeholder="开始时间"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </ElFormItem>
      </PageFilterPanel>

      <!-- 汇总卡片 -->
      <div v-loading="loadingStatistics" class="hx-stat-grid">
        <div v-for="item in statCards" :key="item.label" class="hx-stat-card">
          <span class="hx-stat-card__label">{{ item.label }}</span>
          <span class="hx-stat-card__value">{{ item.value }}</span>
        </div>
      </div>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="笔业绩单">
        <template #left>
          <ElButton
            v-access:code="'promotion:salesorder:export'"
            :icon="Download"
            :loading="exporting"
            type="warning"
            plain
            @click="handleExport"
          >
            导出
          </ElButton>
        </template>
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
        empty-text="暂无业绩单"
      >
        <ElTableColumn
          prop="orderNo"
          label="订单号"
          min-width="180"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn label="买家" min-width="150" align="center">
          <template #default="scope">
            <div class="buyer-cell">
              <span>{{ scope.row.buyerNickname || '—' }}</span>
              <span class="buyer-cell__id">{{ scope.row.buyerUserId }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="staffName"
          label="归属销售"
          min-width="110"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.staffName || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="实付金额" width="120" align="center">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.paymentAmount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="提成基数" width="120" align="center">
          <template #default="scope">
            ¥{{ formatMoney(scope.row.commissionBase) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="提成比例" width="100" align="center">
          <template #default="scope">
            {{ formatRate(scope.row.commissionRate) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="提成金额" width="120" align="center">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.commissionAmount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="退款扣回" width="120" align="center">
          <template #default="scope">
            ¥{{ formatMoney(scope.row.refundedCommission) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100" align="center">
          <template #default="scope">
            <DictTag :options="sales_order_status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="支付时间" width="170" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.payTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="计划可发放" width="170" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.settleAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="90" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              :icon="View"
              link
              type="primary"
              @click="openDetail(scope.row as SalesOrderRecord)"
            >
              详情
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <!-- 分页 -->
      <Pagination
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
      />

      <!-- 业绩单详情 -->
      <ElDrawer v-model="detailState.visible" title="业绩单详情" size="560px">
        <ElDescriptions
          v-if="detailState.row"
          :column="1"
          border
          label-width="120px"
        >
          <ElDescriptionsItem label="业绩单ID">
            {{ detailState.row.id }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="订单号">
            {{ detailState.row.orderNo || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="买家会员ID">
            {{ detailState.row.buyerUserId }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="归属销售">
            {{ detailState.row.staffName || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="实付金额">
            ¥{{ formatMoney(detailState.row.paymentAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="运费">
            ¥{{ formatMoney(detailState.row.freightAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="提成基数">
            ¥{{ formatMoney(detailState.row.commissionBase) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="提成比例">
            {{ formatRate(detailState.row.commissionRate) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="提成金额">
            ¥{{ formatMoney(detailState.row.commissionAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="退款基数">
            ¥{{ formatMoney(detailState.row.refundedBase) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="退款扣回">
            ¥{{ formatMoney(detailState.row.refundedCommission) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <DictTag
              :options="sales_order_status"
              :value="detailState.row.status"
            />
          </ElDescriptionsItem>
          <ElDescriptionsItem label="支付时间">
            {{ formatTime(detailState.row.payTime) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="计划可发放">
            {{ formatTime(detailState.row.settleAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="实际可发放">
            {{ formatTime(detailState.row.settleTime) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="发放批次">
            {{ detailState.row.payoutId || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="备注">
            {{ detailState.row.remark || '—' }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElDrawer>
    </div>
  </div>
</template>

<style scoped>
.hx-stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.hx-stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.hx-stat-card__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hx-stat-card__value {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.buyer-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.buyer-cell__id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
