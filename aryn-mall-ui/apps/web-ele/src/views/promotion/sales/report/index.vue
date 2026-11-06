<script lang="ts" setup>
import type {
  SalesReportQuery,
  SalesReportRecord,
} from '#/api/promotion/sales-report';

import { reactive, ref } from 'vue';

import { Download, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTreeSelect,
} from 'element-plus';

import { getSummary } from '#/api/promotion/sales-report';
import { getOptions } from '#/api/promotion/sales-staff';
import { getTreeList } from '#/api/upms/dept';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { downloadBlobFile, formatMoney } from '#/utils/util';

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

const loading = ref(false);
const exporting = ref(false);
const showSearch = ref(true);
const deptData = ref<DeptNode[]>([]);
const staffOptions = ref<StaffOption[]>([]);
const state = reactive({
  queryParams: {
    payoutMonth: '',
    salesStaffId: '',
    deptId: '',
  } as SalesReportQuery,
  tableData: [] as SalesReportRecord[],
});

const staffLabel = (item: StaffOption) =>
  `${item.staffName ?? item.id}${item.deptName ? `（${item.deptName}）` : ''}`;

const buildParams = () => {
  const params: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(state.queryParams)) {
    if (value !== '' && value !== null && value !== undefined) {
      params[key] = value;
    }
  }
  return params;
};

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

const initPage = async () => {
  loading.value = true;
  try {
    state.tableData = (await getSummary(buildParams())) ?? [];
  } catch {
    state.tableData = [];
  } finally {
    loading.value = false;
  }
};

/** 汇总行：单数、成交额、退款额、应发与已发放提成合计 */
const getSummaries = (param: {
  columns: Array<Record<string, any>>;
  data: SalesReportRecord[];
}) => {
  const { columns, data } = param;
  const sums: string[] = [];
  columns.forEach((column, index) => {
    if (index === 0) {
      sums[index] = '合计';
      return;
    }
    if (column.property === 'salesStaffId') {
      sums[index] = `${data.length} 人`;
      return;
    }
    if (column.property === 'staffName' || column.property === 'deptName') {
      sums[index] = '—';
      return;
    }
    let total = 0;
    for (const item of data) {
      const value = Number(item[column.property as keyof SalesReportRecord]);
      if (Number.isFinite(value)) {
        total += value;
      }
    }
    sums[index] =
      column.property === 'orderCount'
        ? String(total)
        : `¥${formatMoney(total)}`;
  });
  return sums;
};

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
    '/promotion/sales/report/export',
    buildParams(),
    `销售业绩报表_${localTimestamp()}.xlsx`,
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
        <ElFormItem label="统计月份" prop="payoutMonth">
          <ElDatePicker
            v-model="state.queryParams.payoutMonth"
            placeholder="请选择月份"
            type="month"
            value-format="YYYY-MM"
          />
        </ElFormItem>
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
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.tableData.length" total-label="行业绩汇总">
        <template #left>
          <ElButton
            v-access:code="'promotion:salesreport:export'"
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
          <ElButton :icon="Search" @click="initPage">刷新</ElButton>
        </template>
      </PageToolbar>

      <!-- 汇总表格 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        border
        empty-text="暂无业绩数据"
        show-summary
        :summary-method="getSummaries"
      >
        <ElTableColumn
          prop="salesStaffId"
          label="销售ID"
          min-width="170"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="staffName"
          label="销售姓名"
          min-width="110"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.staffName || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="deptName"
          label="部门"
          min-width="120"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.deptName || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="orderCount"
          label="业绩单数"
          width="110"
          align="center"
        />
        <ElTableColumn
          prop="tradeAmount"
          label="成交额"
          width="130"
          align="center"
        >
          <template #default="scope">
            ¥{{ formatMoney(scope.row.tradeAmount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="refundAmount"
          label="退款额"
          width="130"
          align="center"
        >
          <template #default="scope">
            ¥{{ formatMoney(scope.row.refundAmount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="commissionAmount"
          label="应发提成"
          width="130"
          align="center"
        >
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.commissionAmount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="paidAmount"
          label="已发放提成"
          width="130"
          align="center"
        >
          <template #default="scope">
            ¥{{ formatMoney(scope.row.paidAmount) }}
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </div>
</template>
