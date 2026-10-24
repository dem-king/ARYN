<script lang="ts" setup>
import type { PointsLiabilityReport } from '#/api/user/points-config';

import { onMounted, reactive, ref } from 'vue';

import { Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getLiabilityReport } from '#/api/user/points-config';

const loading = ref(false);
const report = ref<null | PointsLiabilityReport>(null);

function daysAgo(days: number): string {
  const date = new Date(Date.now() - days * 24 * 60 * 60 * 1000);
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

const query = reactive({
  // 默认近 30 天（含今日）
  beginDate: daysAgo(29),
  endDate: daysAgo(0),
});

const summaryCards = ref<
  Array<{ hint?: string; label: string; value: string }>
>([]);

const fmtAmount = (value: null | number | undefined): string =>
  value === null || value === undefined ? '—' : `¥${value}`;

const fmtPoints = (value: null | number | undefined): string =>
  value === null || value === undefined ? '—' : `${value}`;

const buildSummary = (data: PointsLiabilityReport) => {
  const rateHint =
    data.pointsPerYuan > 0
      ? `按 ${data.pointsPerYuan} 积分=1 元面值折算`
      : '抵现规则未配置，金额不折算';
  summaryCards.value = [
    {
      label: '期末未消耗余额（负债）',
      value: `${fmtPoints(data.balancePoints)} 分 / ${fmtAmount(data.balanceAmount)}`,
      hint: rateHint,
    },
    {
      label: '累计发放',
      value: `${fmtPoints(data.totalGrantPoints)} 分`,
      hint: `退款返还 ${fmtPoints(data.totalRefundReturnPoints)} 分（不重复确认负债）`,
    },
    {
      label: '累计消耗（结转营销费用）',
      value: `${fmtPoints(
        (data.totalDeductPoints ?? 0) + (data.totalExchangePoints ?? 0),
      )} 分`,
      hint: `抵现 ${fmtPoints(data.totalDeductPoints)} / 兑券 ${fmtPoints(
        data.totalExchangePoints,
      )}`,
    },
    {
      label: '累计过期核销（负债转销）',
      value: `${fmtPoints(data.totalExpirePoints)} 分`,
      hint: `区间内核销 ${fmtPoints(data.rangeExpirePoints)} 分`,
    },
    {
      label: '区间发放',
      value: `${fmtPoints(data.rangeGrantPoints)} 分`,
    },
    {
      label: '区间消耗',
      value: `${fmtPoints(data.rangeConsumePoints)} 分`,
      hint: '抵现 + 兑券 + 后台扣减',
    },
  ];
};

const loadReport = async () => {
  loading.value = true;
  try {
    const data = (await getLiabilityReport({
      beginDate: query.beginDate || undefined,
      endDate: query.endDate || undefined,
    })) as PointsLiabilityReport;
    report.value = data;
    buildSummary(data);
  } finally {
    loading.value = false;
  }
};

const handleSearch = async () => {
  if (query.beginDate && query.endDate && query.beginDate > query.endDate) {
    ElMessage.warning('开始日期不能晚于结束日期');
    return;
  }
  await loadReport();
};

onMounted(() => {
  loadReport();
});
</script>
<template>
  <div>
    <!-- 筛选 -->
    <div class="mb-2 flex items-center gap-2">
      <ElDatePicker
        v-model="query.beginDate"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="开始日期"
        style="width: 160px"
      />
      <span>至</span>
      <ElDatePicker
        v-model="query.endDate"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="结束日期"
        style="width: 160px"
      />
      <ElButton
        type="primary"
        :icon="Search"
        :loading="loading"
        @click="handleSearch"
      >
        查询
      </ElButton>
      <ElInput
        v-if="report"
        :model-value="`期末负债 ${report.balancePoints} 分（约 ¥${
          report.balanceAmount ?? '—'
        }）`"
        disabled
        style="width: 260px"
      />
    </div>
    <!-- 汇总卡 -->
    <div class="mb-2 grid grid-cols-2 gap-2 md:grid-cols-3">
      <div
        v-for="card in summaryCards"
        :key="card.label"
        style="padding: 8px 12px; border: 1px solid #e4e7ed; border-radius: 6px"
      >
        <div style="font-size: 12px; color: #909399">{{ card.label }}</div>
        <div style="margin-top: 2px; font-size: 16px; font-weight: 600">
          {{ card.value }}
        </div>
        <div
          v-if="card.hint"
          style="margin-top: 2px; font-size: 11px; color: #b0b3b8"
        >
          {{ card.hint }}
        </div>
      </div>
    </div>
    <!-- 日报表 -->
    <ElTable
      v-loading="loading"
      class="hx-data-table"
      :data="report?.days ?? []"
      border
      max-height="480"
      empty-text="区间内暂无积分流水"
    >
      <ElTableColumn prop="statDate" label="日期" width="110" fixed />
      <ElTableColumn
        prop="grantPoints"
        label="发放(分)"
        width="90"
        align="right"
      />
      <ElTableColumn
        prop="grantAmount"
        label="发放折算"
        width="100"
        align="right"
      >
        <template #default="scope">
          {{ fmtAmount(scope.row.grantAmount) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="refundReturnPoints"
        label="退款返还(分)"
        width="110"
        align="right"
      />
      <ElTableColumn
        prop="deductPoints"
        label="抵现(分)"
        width="90"
        align="right"
      />
      <ElTableColumn
        prop="deductAmount"
        label="抵现结转"
        width="100"
        align="right"
      >
        <template #default="scope">
          {{ fmtAmount(scope.row.deductAmount) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="exchangePoints"
        label="兑券(分)"
        width="90"
        align="right"
      />
      <ElTableColumn
        prop="exchangeAmount"
        label="兑券结转"
        width="100"
        align="right"
      >
        <template #default="scope">
          {{ fmtAmount(scope.row.exchangeAmount) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="expirePoints"
        label="过期核销(分)"
        width="110"
        align="right"
      />
      <ElTableColumn
        prop="expireAmount"
        label="核销转销"
        width="100"
        align="right"
      >
        <template #default="scope">
          {{ fmtAmount(scope.row.expireAmount) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="adminDeductPoints"
        label="后台扣减(分)"
        width="110"
        align="right"
      />
    </ElTable>
    <div class="mt-1" style="font-size: 12px; color: #909399">
      财务口径：发放确认负债（面值）、抵现/兑券消耗结转营销费用、过期核销转销；预计消耗率不做硬编码折现，请按财务口径表外调整。
    </div>
  </div>
</template>
