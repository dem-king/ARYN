<script lang="ts" setup>
import type {
  SalesBindSource,
  SalesClaimStatus,
  SalesCustomerBindLogRecord,
  SalesCustomerClaimRecord,
  SalesCustomerRecord,
  SalesTransferRewardRecord,
} from '#/api/promotion/sales-customer';

import { computed, defineAsyncComponent, reactive, ref } from 'vue';

import { formatDateTime } from '@vben/utils';

import { Edit, RefreshLeft, Tickets } from '@element-plus/icons-vue';
import {
  ElAvatar,
  ElButton,
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
  assign,
  getBindLogPage,
  getClaimPage,
  getPage,
  getTransferRewardPage,
} from '#/api/promotion/sales-customer';
import { getOptions } from '#/api/promotion/sales-staff';
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

interface StaffOption {
  deptName?: string;
  id: string;
  staffName?: string;
}

const { sales_bind_source, sales_claim_status, sales_transfer_reward_status } =
  useDict(
    'sales_bind_source',
    'sales_claim_status',
    'sales_transfer_reward_status',
  );

const loading = ref(false);
const showSearch = ref(true);
const staffOptions = ref<StaffOption[]>([]);
const state = reactive({
  queryParams: {
    customerUserId: '',
    salesStaffId: '',
    bindSource: '' as '' | SalesBindSource,
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [] as SalesCustomerRecord[],
});

const formatTime = (value?: string) => (value ? formatDateTime(value) : '—');
const staffLabel = (item: StaffOption) =>
  `${item.staffName ?? item.id}${item.deptName ? `（${item.deptName}）` : ''}`;

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

// ---------------------------------------------------------------------------
// 改归属
// ---------------------------------------------------------------------------
const assignState = reactive({
  visible: false,
  submitting: false,
  customerUserId: '',
  customerLabel: '',
  salesStaffId: '',
  reason: '',
});

const openAssign = (row: SalesCustomerRecord) => {
  assignState.customerUserId = row.customerUserId;
  assignState.customerLabel = row.nickname
    ? `${row.nickname}（${row.customerUserId}）`
    : row.customerUserId;
  assignState.salesStaffId = row.salesStaffId ?? '';
  assignState.reason = '';
  assignState.visible = true;
};

const submitAssign = async () => {
  if (!assignState.salesStaffId) {
    ElMessage.error('请选择归属销售');
    return;
  }
  if (!assignState.reason.trim()) {
    ElMessage.error('请填写改绑原因');
    return;
  }
  assignState.submitting = true;
  try {
    await assign({
      customerUserId: assignState.customerUserId,
      salesStaffId: assignState.salesStaffId,
      reason: assignState.reason.trim(),
    });
    ElMessage.success('归属已更新');
    assignState.visible = false;
    initPage();
  } finally {
    assignState.submitting = false;
  }
};

// ---------------------------------------------------------------------------
// 换绑日志
// ---------------------------------------------------------------------------
const bindLogState = reactive({
  visible: false,
  loading: false,
  customerUserId: '',
  customerLabel: '',
  page: { current: 1, size: 10, total: 0 },
  rows: [] as SalesCustomerBindLogRecord[],
});

const openBindLog = (row: SalesCustomerRecord) => {
  bindLogState.customerUserId = row.customerUserId;
  bindLogState.customerLabel = row.nickname
    ? `${row.nickname}（${row.customerUserId}）`
    : row.customerUserId;
  bindLogState.page.current = 1;
  bindLogState.visible = true;
  loadBindLog();
};

const loadBindLog = async () => {
  bindLogState.loading = true;
  try {
    const response = await getBindLogPage({
      current: bindLogState.page.current,
      size: bindLogState.page.size,
      customerUserId: bindLogState.customerUserId,
    });
    bindLogState.rows = response.records;
    bindLogState.page.total = response.total;
  } catch {
    bindLogState.rows = [];
    bindLogState.page.total = 0;
  } finally {
    bindLogState.loading = false;
  }
};

const bindSourceOptions = computed<any[]>(() => sales_bind_source?.value ?? []);

const bindSourceText = (source?: string) =>
  bindSourceOptions.value.find((item: any) => item.value === source)?.label ??
  source ??
  '—';

// ---------------------------------------------------------------------------
// 待转交认领
// ---------------------------------------------------------------------------
const claimState = reactive({
  visible: false,
  loading: false,
  queryParams: {
    salesStaffId: '',
    status: '' as '' | SalesClaimStatus,
  },
  page: { current: 1, size: 10, total: 0 },
  rows: [] as SalesCustomerClaimRecord[],
});

const openClaim = () => {
  claimState.queryParams.salesStaffId = '';
  claimState.queryParams.status = '';
  claimState.page.current = 1;
  claimState.visible = true;
  loadClaim();
};

const loadClaim = async () => {
  claimState.loading = true;
  try {
    const response = await getClaimPage({
      current: claimState.page.current,
      size: claimState.page.size,
      ...claimState.queryParams,
    });
    claimState.rows = response.records;
    claimState.page.total = response.total;
  } catch {
    claimState.rows = [];
    claimState.page.total = 0;
  } finally {
    claimState.loading = false;
  }
};

// ---------------------------------------------------------------------------
// 转交奖励
// ---------------------------------------------------------------------------
const rewardState = reactive({
  visible: false,
  loading: false,
  queryParams: {
    customerUserId: '',
    salesStaffId: '',
    status: '' as '0' | '1' | '2' | '',
  },
  page: { current: 1, size: 10, total: 0 },
  rows: [] as SalesTransferRewardRecord[],
});

const openReward = () => {
  rewardState.queryParams.customerUserId = '';
  rewardState.queryParams.salesStaffId = '';
  rewardState.queryParams.status = '';
  rewardState.page.current = 1;
  rewardState.visible = true;
  loadReward();
};

const loadReward = async () => {
  rewardState.loading = true;
  try {
    const response = await getTransferRewardPage({
      current: rewardState.page.current,
      size: rewardState.page.size,
      ...rewardState.queryParams,
    });
    rewardState.rows = response.records;
    rewardState.page.total = response.total;
  } catch {
    rewardState.rows = [];
    rewardState.page.total = 0;
  } finally {
    rewardState.loading = false;
  }
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
        <ElFormItem label="客户ID" prop="customerUserId">
          <ElInput
            v-model="state.queryParams.customerUserId"
            clearable
            placeholder="请输入客户会员ID"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="归属销售" prop="salesStaffId">
          <ElSelect
            v-model="state.queryParams.salesStaffId"
            clearable
            filterable
            placeholder="请选择归属销售"
          >
            <ElOption
              v-for="item in staffOptions"
              :key="item.id"
              :label="staffLabel(item)"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="绑定来源" prop="bindSource">
          <ElSelect
            v-model="state.queryParams.bindSource"
            clearable
            placeholder="请选择绑定来源"
          >
            <ElOption
              v-for="item in bindSourceOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="个客户归属">
        <template #left>
          <ElButton :icon="Tickets" @click="openReward">转交奖励</ElButton>
          <ElButton :icon="RefreshLeft" @click="openClaim">
            待转交认领
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
        empty-text="暂无客户归属"
      >
        <ElTableColumn label="客户" min-width="200">
          <template #default="scope">
            <div class="customer-cell">
              <ElAvatar :size="28" :src="scope.row.avatar">
                {{ (scope.row.nickname ?? '客').slice(0, 1) }}
              </ElAvatar>
              <div class="customer-cell__main">
                <span class="customer-cell__name">
                  {{ scope.row.nickname || '—' }}
                </span>
                <span class="customer-cell__id">
                  {{ scope.row.customerUserId }}
                </span>
              </div>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="salesStaffName"
          label="归属销售"
          min-width="110"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.salesStaffName || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="绑定来源" width="110" align="center">
          <template #default="scope">
            <DictTag
              :options="bindSourceOptions"
              :value="scope.row.bindSource"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="绑定时间" width="170" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.bindTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="pendingClaimStaffName"
          label="待转交销售"
          min-width="110"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.pendingClaimStaffName || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="认领状态" width="100" align="center">
          <template #default="scope">
            <span v-if="!scope.row.claimStatus">—</span>
            <DictTag
              v-else
              :options="sales_claim_status"
              :value="scope.row.claimStatus"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="认领失效时间" width="170" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.claimExpireTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="190" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              v-access:code="'promotion:salescustomer:edit'"
              :icon="Edit"
              link
              type="primary"
              @click="openAssign(scope.row as SalesCustomerRecord)"
            >
              改归属
            </ElButton>
            <ElButton
              v-access:code="'promotion:salescustomer:page'"
              link
              type="primary"
              @click="openBindLog(scope.row as SalesCustomerRecord)"
            >
              换绑日志
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

      <!-- 改归属 -->
      <ElDialog v-model="assignState.visible" title="改归属" width="520px">
        <ElForm label-width="100px">
          <ElFormItem label="客户">
            <ElInput :model-value="assignState.customerLabel" disabled />
          </ElFormItem>
          <ElFormItem label="归属销售" required>
            <ElSelect
              v-model="assignState.salesStaffId"
              filterable
              placeholder="请选择归属销售"
              style="width: 100%"
            >
              <ElOption
                v-for="item in staffOptions"
                :key="item.id"
                :label="staffLabel(item)"
                :value="item.id"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="改绑原因" required>
            <ElInput
              v-model="assignState.reason"
              :maxlength="255"
              placeholder="请填写改绑原因（必填，将写入换绑日志）"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="assignState.visible = false">取消</ElButton>
            <ElButton
              :loading="assignState.submitting"
              type="primary"
              @click="submitAssign"
            >
              确定
            </ElButton>
          </div>
        </template>
      </ElDialog>

      <!-- 换绑日志 -->
      <ElDrawer
        v-model="bindLogState.visible"
        :title="`换绑日志 - ${bindLogState.customerLabel}`"
        size="640px"
      >
        <ElTable
          v-loading="bindLogState.loading"
          :data="bindLogState.rows"
          border
          empty-text="暂无换绑日志"
        >
          <ElTableColumn
            label="原销售"
            min-width="110"
            align="center"
            show-overflow-tooltip
          >
            <template #default="scope">
              {{ scope.row.oldSalesStaffName || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            label="新销售"
            min-width="110"
            align="center"
            show-overflow-tooltip
          >
            <template #default="scope">
              {{ scope.row.newSalesStaffName || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="来源" width="100" align="center">
            <template #default="scope">
              {{ bindSourceText(scope.row.bindSource) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="reason"
            label="原因"
            min-width="140"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="operatorName"
            label="操作人"
            width="100"
            align="center"
          />
          <ElTableColumn label="操作时间" width="170" align="center">
            <template #default="scope">
              {{ formatTime(scope.row.createTime) }}
            </template>
          </ElTableColumn>
        </ElTable>
        <Pagination
          v-model:current="bindLogState.page.current"
          v-model:size="bindLogState.page.size"
          :total="bindLogState.page.total"
          @change="loadBindLog"
        />
      </ElDrawer>

      <!-- 待转交认领 -->
      <ElDrawer v-model="claimState.visible" title="待转交认领" size="720px">
        <PageFilterPanel
          :model="claimState.queryParams"
          @reset="loadClaim"
          @search="loadClaim"
        >
          <ElFormItem label="销售" prop="salesStaffId">
            <ElSelect
              v-model="claimState.queryParams.salesStaffId"
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
          <ElFormItem label="认领状态" prop="status">
            <ElSelect
              v-model="claimState.queryParams.status"
              clearable
              placeholder="请选择状态"
            >
              <ElOption
                v-for="item in sales_claim_status"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </PageFilterPanel>
        <ElTable
          v-loading="claimState.loading"
          :data="claimState.rows"
          border
          empty-text="暂无待转交认领"
        >
          <ElTableColumn
            prop="customerUserId"
            label="客户ID"
            min-width="170"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="nickname"
            label="客户昵称"
            min-width="110"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="staffName"
            label="认领销售"
            min-width="110"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn label="来源" width="100" align="center">
            <template #default="scope">
              {{ bindSourceText(scope.row.source) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="认领状态" width="100" align="center">
            <template #default="scope">
              <DictTag
                :options="sales_claim_status"
                :value="scope.row.status"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn label="认领时间" width="170" align="center">
            <template #default="scope">
              {{ formatTime(scope.row.claimTime) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="失效时间" width="170" align="center">
            <template #default="scope">
              {{ formatTime(scope.row.expireTime) }}
            </template>
          </ElTableColumn>
        </ElTable>
        <Pagination
          v-model:current="claimState.page.current"
          v-model:size="claimState.page.size"
          :total="claimState.page.total"
          @change="loadClaim"
        />
      </ElDrawer>

      <!-- 转交奖励 -->
      <ElDrawer v-model="rewardState.visible" title="转交奖励" size="860px">
        <PageFilterPanel
          :model="rewardState.queryParams"
          @reset="loadReward"
          @search="loadReward"
        >
          <ElFormItem label="客户ID" prop="customerUserId">
            <ElInput
              v-model="rewardState.queryParams.customerUserId"
              clearable
              placeholder="请输入客户会员ID"
              @keyup.enter="loadReward"
            />
          </ElFormItem>
          <ElFormItem label="接收销售" prop="salesStaffId">
            <ElSelect
              v-model="rewardState.queryParams.salesStaffId"
              clearable
              filterable
              placeholder="请选择接收销售"
            >
              <ElOption
                v-for="item in staffOptions"
                :key="item.id"
                :label="staffLabel(item)"
                :value="item.id"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="奖励状态" prop="status">
            <ElSelect
              v-model="rewardState.queryParams.status"
              clearable
              placeholder="请选择状态"
            >
              <ElOption
                v-for="item in sales_transfer_reward_status"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
        </PageFilterPanel>
        <ElTable
          v-loading="rewardState.loading"
          :data="rewardState.rows"
          border
          empty-text="暂无转交奖励"
        >
          <ElTableColumn
            prop="nickname"
            label="客户"
            min-width="100"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="distributorNickname"
            label="原分销员"
            min-width="110"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="salesStaffId"
            label="接收销售"
            min-width="110"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn label="首单实付" width="110" align="center">
            <template #default="scope">
              ¥{{ formatMoney(scope.row.firstOrderAmount) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="奖励比例" width="100" align="center">
            <template #default="scope">
              {{
                scope.row.rewardRate != null
                  ? `${(Number(scope.row.rewardRate) * 100).toFixed(2)}%`
                  : '—'
              }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="奖励金额" width="110" align="center">
            <template #default="scope">
              <span class="hx-money">
                ¥{{ formatMoney(scope.row.rewardAmount) }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="100" align="center">
            <template #default="scope">
              <DictTag
                :options="sales_transfer_reward_status"
                :value="scope.row.status"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn label="结算时间" width="170" align="center">
            <template #default="scope">
              {{ formatTime(scope.row.settleTime) }}
            </template>
          </ElTableColumn>
        </ElTable>
        <Pagination
          v-model:current="rewardState.page.current"
          v-model:size="rewardState.page.size"
          :total="rewardState.page.total"
          @change="loadReward"
        />
      </ElDrawer>
    </div>
  </div>
</template>

<style scoped>
.customer-cell {
  display: flex;
  gap: 8px;
  align-items: center;
}

.customer-cell__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.customer-cell__name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.customer-cell__id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
