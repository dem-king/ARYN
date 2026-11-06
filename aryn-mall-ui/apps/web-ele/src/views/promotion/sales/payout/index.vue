<script lang="ts" setup>
import type { SalesOrderRecord } from '#/api/promotion/sales-order';
import type {
  SalesPayoutRecord,
  SalesPayoutStatus,
} from '#/api/promotion/sales-payout';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { formatDateTime } from '@vben/utils';

import { Check, Download, Refresh, View } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  confirm,
  generate,
  getById,
  getOrderPage,
  getPage,
} from '#/api/promotion/sales-payout';
import { getOptions } from '#/api/promotion/sales-staff';
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

interface StaffOption {
  deptName?: string;
  id: string;
  staffName?: string;
}

const { sales_order_status, sales_payout_status } = useDict(
  'sales_payout_status',
  'sales_order_status',
);

const loading = ref(false);
const exporting = ref(false);
const showSearch = ref(true);
const staffOptions = ref<StaffOption[]>([]);
const state = reactive({
  queryParams: {
    payoutMonth: '',
    salesStaffId: '',
    status: '' as '' | SalesPayoutStatus,
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [] as SalesPayoutRecord[],
});

const formatTime = (value?: string) => (value ? formatDateTime(value) : '—');
const staffLabel = (item: StaffOption) =>
  `${item.staffName ?? item.id}${item.deptName ? `（${item.deptName}）` : ''}`;

/** 上月（yyyy-MM），生成台账时的默认月份 */
const lastMonth = () => {
  const date = new Date();
  date.setDate(1);
  date.setMonth(date.getMonth() - 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
};

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
};

// ---------------------------------------------------------------------------
// 生成台账
// ---------------------------------------------------------------------------
const generateState = reactive({
  visible: false,
  submitting: false,
  payoutMonth: '',
});

const openGenerate = () => {
  generateState.payoutMonth = lastMonth();
  generateState.visible = true;
};

const submitGenerate = async () => {
  generateState.submitting = true;
  try {
    const count = await generate({
      payoutMonth: generateState.payoutMonth || undefined,
    });
    ElMessage.success(`台账生成完成，共生成 ${count} 个批次`);
    generateState.visible = false;
    initPage();
  } finally {
    generateState.submitting = false;
  }
};

// ---------------------------------------------------------------------------
// 确认发放
// ---------------------------------------------------------------------------
const confirmState = reactive({
  visible: false,
  submitting: false,
  row: null as null | SalesPayoutRecord,
  remark: '',
});

const openConfirm = (row: SalesPayoutRecord) => {
  confirmState.row = row;
  confirmState.remark = '';
  confirmState.visible = true;
};

const submitConfirm = async () => {
  if (!confirmState.row) {
    return;
  }
  confirmState.submitting = true;
  try {
    await confirm({
      id: confirmState.row.id,
      remark: confirmState.remark || undefined,
    });
    ElMessage.success('已确认发放');
    confirmState.visible = false;
    initPage();
  } finally {
    confirmState.submitting = false;
  }
};

// ---------------------------------------------------------------------------
// 台账详情（批次内业绩单）
// ---------------------------------------------------------------------------
const detailState = reactive({
  visible: false,
  loading: false,
  row: null as null | SalesPayoutRecord,
  orderPage: { current: 1, size: 10, total: 0 },
  orders: [] as SalesOrderRecord[],
});

const loadDetailOrders = async () => {
  if (!detailState.row) {
    return;
  }
  detailState.loading = true;
  try {
    const response = await getOrderPage(detailState.row.id, {
      current: detailState.orderPage.current,
      size: detailState.orderPage.size,
    });
    detailState.orders = response.records;
    detailState.orderPage.total = response.total;
  } catch {
    detailState.orders = [];
    detailState.orderPage.total = 0;
  } finally {
    detailState.loading = false;
  }
};

const openDetail = async (row: SalesPayoutRecord) => {
  detailState.row = row;
  detailState.orderPage.current = 1;
  detailState.orders = [];
  detailState.visible = true;
  // 用详情接口补齐批次汇总字段（分页列表可能裁剪）
  try {
    const detail = await getById(row.id);
    if (detail) {
      detailState.row = detail;
    }
  } catch {
    // 详情失败时保留列表行数据
  }
  loadDetailOrders();
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
    '/promotion/sales/payout/export',
    buildParams(),
    `销售发放台账_${localTimestamp()}.xlsx`,
  ).finally(() => {
    exporting.value = false;
  });
  ElMessage.success('导出已开始，请稍候');
};

loadStaffOptions();
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
        <ElFormItem label="台账月份" prop="payoutMonth">
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
        <ElFormItem label="台账状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in sales_payout_status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="个发放批次">
        <template #left>
          <ElButton
            v-access:code="'promotion:salespayout:generate'"
            :icon="Refresh"
            type="primary"
            @click="openGenerate"
          >
            生成台账
          </ElButton>
          <ElButton
            v-access:code="'promotion:salespayout:export'"
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
        empty-text="暂无发放台账"
      >
        <ElTableColumn
          prop="payoutMonth"
          label="台账月份"
          width="100"
          align="center"
        />
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
        <ElTableColumn
          prop="deptId"
          label="部门ID"
          min-width="150"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.deptId || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="orderCount"
          label="业绩单数"
          width="100"
          align="center"
        />
        <ElTableColumn label="成交额" width="120" align="center">
          <template #default="scope">
            ¥{{ formatMoney(scope.row.tradeAmount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="应发提成" width="120" align="center">
          <template #default="scope">
            ¥{{ formatMoney(scope.row.commissionAmount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="退款冲减" width="120" align="center">
          <template #default="scope">
            ¥{{ formatMoney(scope.row.refundDeductAmount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="实发金额" width="120" align="center">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.payableAmount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100" align="center">
          <template #default="scope">
            <DictTag :options="sales_payout_status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="confirmBy"
          label="确认人"
          width="100"
          align="center"
        />
        <ElTableColumn label="确认时间" width="170" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.confirmTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="170" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              :icon="View"
              link
              type="primary"
              @click="openDetail(scope.row as SalesPayoutRecord)"
            >
              详情
            </ElButton>
            <ElButton
              v-if="scope.row.status === '0'"
              v-access:code="'promotion:salespayout:confirm'"
              :icon="Check"
              link
              type="success"
              @click="openConfirm(scope.row as SalesPayoutRecord)"
            >
              确认发放
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

      <!-- 生成台账 -->
      <ElDialog
        v-model="generateState.visible"
        title="生成月度台账"
        width="480px"
      >
        <ElForm label-width="100px">
          <ElFormItem label="台账月份">
            <ElDatePicker
              v-model="generateState.payoutMonth"
              placeholder="默认上月"
              style="width: 100%"
              type="month"
              value-format="YYYY-MM"
            />
          </ElFormItem>
          <div class="dialog-tip">
            按月汇总已确认的业绩单与退款负数调整；已存在的批次会幂等跳过，不会重复生成。
          </div>
        </ElForm>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="generateState.visible = false">取消</ElButton>
            <ElButton
              :loading="generateState.submitting"
              type="primary"
              @click="submitGenerate"
            >
              生成
            </ElButton>
          </div>
        </template>
      </ElDialog>

      <!-- 确认发放 -->
      <ElDialog v-model="confirmState.visible" title="确认发放" width="480px">
        <ElForm label-width="110px">
          <ElFormItem label="归属销售">
            <ElInput
              :model-value="confirmState.row?.staffName ?? ''"
              disabled
            />
          </ElFormItem>
          <ElFormItem label="实发金额">
            <span class="confirm-amount">
              ¥{{ formatMoney(confirmState.row?.payableAmount) }}
            </span>
          </ElFormItem>
          <ElFormItem label="备注/凭证号">
            <ElInput
              v-model="confirmState.remark"
              :maxlength="500"
              placeholder="可填写发放备注或银行凭证号"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="confirmState.visible = false">取消</ElButton>
            <ElButton
              :loading="confirmState.submitting"
              type="primary"
              @click="submitConfirm"
            >
              确认发放
            </ElButton>
          </div>
        </template>
      </ElDialog>

      <!-- 台账详情 -->
      <ElDrawer
        v-model="detailState.visible"
        :title="`发放台账 - ${detailState.row?.payoutMonth ?? ''}`"
        size="860px"
      >
        <ElDescriptions
          v-if="detailState.row"
          :column="2"
          border
          label-width="100px"
        >
          <ElDescriptionsItem label="归属销售">
            {{ detailState.row.staffName || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="业绩单数">
            {{ detailState.row.orderCount ?? 0 }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="成交额">
            ¥{{ formatMoney(detailState.row.tradeAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="应发提成">
            ¥{{ formatMoney(detailState.row.commissionAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="退款冲减">
            ¥{{ formatMoney(detailState.row.refundDeductAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="实发金额">
            ¥{{ formatMoney(detailState.row.payableAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <DictTag
              :options="sales_payout_status"
              :value="detailState.row.status"
            />
          </ElDescriptionsItem>
          <ElDescriptionsItem label="确认时间">
            {{ formatTime(detailState.row.confirmTime) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="备注" :span="2">
            {{ detailState.row.remark || '—' }}
          </ElDescriptionsItem>
        </ElDescriptions>

        <div class="detail-orders-title">批次内业绩单</div>
        <ElTable
          v-loading="detailState.loading"
          :data="detailState.orders"
          border
          empty-text="暂无业绩单"
        >
          <ElTableColumn
            prop="orderNo"
            label="订单号"
            min-width="170"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="staffName"
            label="归属销售"
            width="100"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn label="提成基数" width="110" align="center">
            <template #default="scope">
              ¥{{ formatMoney(scope.row.commissionBase) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="提成金额" width="110" align="center">
            <template #default="scope">
              <span class="hx-money">
                ¥{{ formatMoney(scope.row.commissionAmount) }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="退款扣回" width="110" align="center">
            <template #default="scope">
              ¥{{ formatMoney(scope.row.refundedCommission) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="100" align="center">
            <template #default="scope">
              <DictTag
                :options="sales_order_status"
                :value="scope.row.status"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn label="支付时间" width="170" align="center">
            <template #default="scope">
              {{ formatTime(scope.row.payTime) }}
            </template>
          </ElTableColumn>
        </ElTable>
        <Pagination
          v-model:current="detailState.orderPage.current"
          v-model:size="detailState.orderPage.size"
          :total="detailState.orderPage.total"
          @change="loadDetailOrders"
        />
      </ElDrawer>
    </div>
  </div>
</template>

<style scoped>
.dialog-tip {
  padding-left: 100px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.confirm-amount {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-color-danger);
}

.detail-orders-title {
  margin: 18px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
