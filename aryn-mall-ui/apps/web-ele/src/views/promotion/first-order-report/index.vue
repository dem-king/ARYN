<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getFirstOrderAlerts,
  getFirstOrderReport,
} from '#/api/promotion/first-order-report';
import { downloadBlobFile, formatMoney } from '#/utils/util';

const loading = ref(false);
const alertsLoading = ref(false);
const report = ref<any>({
  rows: [],
  lockedCount: 0,
  confirmedCount: 0,
  releasedCount: 0,
  discountAmount: 0,
});
const alerts = ref<any[]>([]);
const query = reactive<{ range: [] | [string, string] }>({ range: [] });

const SUBJECT_TYPE_TEXT: Record<string, string> = { 1: '船', 2: '采购主体' };
const subjectTypeText = (value?: string) => {
  const mapped = SUBJECT_TYPE_TEXT[value ?? ''];
  return mapped ?? (value || '-');
};

const params = () => ({
  ...(query.range?.[0] ? { beginTime: query.range[0] } : {}),
  ...(query.range?.[1] ? { endTime: query.range[1] } : {}),
});

async function load() {
  loading.value = true;
  try {
    report.value = await getFirstOrderReport(params());
  } finally {
    loading.value = false;
  }
}

async function loadAlerts() {
  alertsLoading.value = true;
  try {
    alerts.value = await getFirstOrderAlerts(params());
  } finally {
    alertsLoading.value = false;
  }
}

function reset() {
  query.range = [];
  load();
  loadAlerts();
}

function exportReport() {
  downloadBlobFile(
    '/promotion/first-order-report/export',
    params(),
    `首单核销报表_${Date.now()}.csv`,
  ).catch(() => ElMessage.error('导出失败'));
}

onMounted(() => {
  load();
  loadAlerts();
});
</script>

<template>
  <div class="first-order-report-page">
    <div class="toolbar">
      <ElDatePicker
        v-model="query.range"
        type="datetimerange"
        value-format="YYYY-MM-DD HH:mm:ss"
        range-separator="-"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
      />
      <ElButton
        type="primary"
        @click="
          load();
          loadAlerts();
        "
      >
        查询
      </ElButton>
      <ElButton @click="reset">重置</ElButton>
      <ElButton type="success" plain @click="exportReport">导出 CSV</ElButton>
    </div>

    <div class="summary">
      <div class="summary-card">
        <div class="summary-label">锁定中</div>
        <div class="summary-value">{{ report.lockedCount ?? 0 }}</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">已核销</div>
        <div class="summary-value">{{ report.confirmedCount ?? 0 }}</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">已释放</div>
        <div class="summary-value">{{ report.releasedCount ?? 0 }}</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">已核销让利</div>
        <div class="summary-value">
          ¥{{ formatMoney(report.discountAmount ?? 0) }}
        </div>
      </div>
    </div>

    <div class="section-title">核销明细</div>
    <ElTable
      v-loading="loading"
      :data="report.rows"
      border
      :row-key="(row) => `${row.activityId}-${row.subjectType}`"
    >
      <ElTableColumn label="活动" min-width="180">
        <template #default="{ row }">
          {{ row.activityName || row.activityId }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="主体类型" width="110">
        <template #default="{ row }">
          {{ subjectTypeText(row.subjectType) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="锁定中" width="90" prop="lockedCount" />
      <ElTableColumn label="已核销" width="90" prop="confirmedCount" />
      <ElTableColumn label="已释放" width="90" prop="releasedCount" />
      <ElTableColumn label="去重主体" width="100" prop="subjectCount" />
      <ElTableColumn label="让利金额" width="120">
        <template #default="{ row }">
          ¥{{ formatMoney(row.discountAmount ?? 0) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="月预算" width="120">
        <template #default="{ row }">
          ¥{{ formatMoney(row.monthlyBudget ?? 0) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="本月已用" width="120">
        <template #default="{ row }">
          ¥{{ formatMoney(row.monthlyUsed ?? 0) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="预算使用率" width="120">
        <template #default="{ row }">
          <ElTag
            v-if="row.usageRate != null"
            :type="row.alert ? 'danger' : 'success'"
            effect="light"
          >
            {{ row.usageRate }}%
          </ElTag>
          <span v-else>未配置</span>
        </template>
      </ElTableColumn>
    </ElTable>

    <div class="section-title" style="margin-top: 24px">预算预警（≥80%）</div>
    <ElTable
      v-loading="alertsLoading"
      :data="alerts"
      border
      :row-key="(row) => `${row.activityId}-${row.subjectType}`"
      empty-text="暂无预警"
    >
      <ElTableColumn label="活动" min-width="220">
        <template #default="{ row }">
          {{ row.activityName || row.activityId }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="主体类型" width="110">
        <template #default="{ row }">
          {{ subjectTypeText(row.subjectType) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="本月已用" width="120">
        <template #default="{ row }">
          ¥{{ formatMoney(row.monthlyUsed ?? 0) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="月预算" width="120">
        <template #default="{ row }">
          ¥{{ formatMoney(row.monthlyBudget ?? 0) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="使用率" width="100">
        <template #default="{ row }">{{ row.usageRate }}%</template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<style scoped>
.first-order-report-page {
  padding: 16px;
  background: var(--el-bg-color);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}

.summary-card {
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.summary-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
}

.section-title {
  margin: 16px 0 8px;
  font-weight: 600;
}
</style>
