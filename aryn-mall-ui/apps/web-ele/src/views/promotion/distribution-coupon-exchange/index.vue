<script lang="ts" setup>
import type {
  DistributionCouponCost,
  DistributionCouponExchangeRecord,
  DistributionCouponExchangeStatus,
} from '#/api/promotion/distribution-coupon-exchange';

import { computed, defineAsyncComponent, reactive, ref } from 'vue';

import { formatDateTime } from '@vben/utils';

import {
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  getPage,
  getStatistics,
} from '#/api/promotion/distribution-coupon-exchange';
import { getList as getPackList } from '#/api/promotion/distribution-coupon-pack';
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

interface PackOption {
  id: string;
  packName?: string;
}

const { distribution_coupon_exchange_status } = useDict(
  'distribution_coupon_exchange_status',
);

const loading = ref(false);
const loadingCost = ref(false);
const showSearch = ref(true);
const packOptions = ref<PackOption[]>([]);
const cost = ref<DistributionCouponCost | null>(null);
const state = reactive({
  queryParams: {
    userId: '',
    packId: '',
    status: '' as '' | DistributionCouponExchangeStatus,
    createTimeRange: [] as string[],
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [] as DistributionCouponExchangeRecord[],
});

const formatTime = (value?: string) => (value ? formatDateTime(value) : '—');

const buildParams = () => {
  const { createTimeRange, ...rest } = state.queryParams;
  const params: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(rest)) {
    if (value !== '' && value !== null && value !== undefined) {
      params[key] = value;
    }
  }
  if (createTimeRange?.length === 2) {
    params.createTimeStart = createTimeRange[0];
    params.createTimeEnd = createTimeRange[1];
  }
  return params;
};

/** 券成本汇总卡片：兑换笔数 / 消耗佣金 / 券面额（营销费用口径） */
const costCards = computed(() => [
  {
    label: '兑换笔数',
    value: String(cost.value?.exchangeCount ?? 0),
  },
  {
    label: '消耗佣金',
    value: `¥${formatMoney(cost.value?.commissionAmount)}`,
  },
  {
    label: '券面额合计（营销费用）',
    value: `¥${formatMoney(cost.value?.faceAmount)}`,
  },
]);

const loadPackOptions = () => {
  getPackList()
    .then((response) => {
      packOptions.value = response ?? [];
    })
    .catch(() => {
      packOptions.value = [];
    });
};

const loadCost = async () => {
  loadingCost.value = true;
  try {
    cost.value = await getStatistics(buildParams());
  } catch {
    cost.value = null;
  } finally {
    loadingCost.value = false;
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
  loadCost();
};

loadPackOptions();
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
        <ElFormItem label="分销员ID" prop="userId">
          <ElInput
            v-model="state.queryParams.userId"
            clearable
            placeholder="请输入分销员会员ID"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="券包" prop="packId">
          <ElSelect
            v-model="state.queryParams.packId"
            clearable
            filterable
            placeholder="请选择券包"
          >
            <ElOption
              v-for="item in packOptions"
              :key="item.id"
              :label="item.packName ?? item.id"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="兑换状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in distribution_coupon_exchange_status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="兑换时间" prop="createTimeRange">
          <ElDatePicker
            v-model="state.queryParams.createTimeRange"
            end-placeholder="结束时间"
            range-separator="-"
            start-placeholder="开始时间"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </ElFormItem>
      </PageFilterPanel>

      <!-- 券成本汇总 -->
      <div v-loading="loadingCost" class="hx-stat-grid">
        <div v-for="item in costCards" :key="item.label" class="hx-stat-card">
          <span class="hx-stat-card__label">{{ item.label }}</span>
          <span class="hx-stat-card__value">{{ item.value }}</span>
        </div>
      </div>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="条兑换记录">
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
        empty-text="暂无兑换记录"
      >
        <ElTableColumn label="分销员" min-width="170" align="center">
          <template #default="scope">
            <div class="user-cell">
              <span>
                {{ scope.row.userNickname || scope.row.nickname || '—' }}
              </span>
              <span class="user-cell__id">{{ scope.row.userId }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="packName"
          label="券包"
          min-width="150"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.packName || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="券面额" width="110" align="center">
          <template #default="scope">
            ¥{{ formatMoney(scope.row.faceAmount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="消耗佣金" width="120" align="center">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.commissionAmount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100" align="center">
          <template #default="scope">
            <DictTag
              :options="distribution_coupon_exchange_status"
              :value="scope.row.status"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="兑换时间" width="170" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.exchangeTime ?? scope.row.createTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="couponUserIds"
          label="发放优惠券ID"
          min-width="180"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.couponUserIds || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="remark"
          label="备注"
          min-width="140"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.remark || '—' }}
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
    </div>
  </div>
</template>

<style scoped>
.hx-stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
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

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-cell__id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
