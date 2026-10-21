<script lang="ts" setup>
import type { UserCohortRetentionVO } from '#/api/order/order-statistics';

import { DataAnalysis, InfoFilled } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import { ElCard, ElIcon, ElTooltip } from 'element-plus';

import { formatNumber } from '../../shared/chart';

const props = withDefaults(
  defineProps<{
    data: UserCohortRetentionVO[];
    endMonth: string;
    loading?: boolean;
  }>(),
  {
    loading: false,
  },
);

const MONTH_KEYS = [
  'month0',
  'month1',
  'month2',
  'month3',
  'month4',
  'month5',
] as const;
const COLUMN_LABELS = ['当月', '+1月', '+2月', '+3月', '+4月', '+5月'];

const targetMonth = (cohortMonth: string, offset: number) =>
  dayjs(`${cohortMonth}-01`).add(offset, 'month').format('YYYY-MM');

const isAvailable = (cohortMonth: string, offset: number) =>
  Boolean(props.endMonth) && targetMonth(cohortMonth, offset) <= props.endMonth;

const cellValue = (row: UserCohortRetentionVO, offset: number) => {
  const key = MONTH_KEYS[offset];
  return key ? Number(row[key]) || 0 : 0;
};

const rate = (row: UserCohortRetentionVO, offset: number) =>
  row.cohortSize > 0 ? (cellValue(row, offset) / row.cohortSize) * 100 : 0;

const cellStyle = (row: UserCohortRetentionVO, offset: number) => {
  const ratio = rate(row, offset) / 100;
  return {
    backgroundColor: `rgba(64, 158, 255, ${0.08 + ratio * 0.72})`,
    color: ratio > 0.55 ? '#fff' : '#303133',
  };
};
</script>

<template>
  <ElCard v-loading="loading" shadow="hover" class="mb-4">
    <template #header>
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-cyan-500"><DataAnalysis /></ElIcon>
          <span>用户留存（按首购月份）</span>
        </div>
        <ElTooltip
          content="按首笔支付月份分组；留存=该 Cohort 在对应月份有支付行为的用户占比"
          placement="top"
        >
          <ElIcon class="cursor-help text-gray-400"><InfoFilled /></ElIcon>
        </ElTooltip>
      </div>
    </template>

    <div
      v-if="!loading && data.length === 0"
      class="py-8 text-center text-sm text-gray-400"
    >
      暂无数据
    </div>
    <div v-else class="overflow-x-auto">
      <table class="w-full min-w-[640px] border-collapse text-center text-sm">
        <thead>
          <tr class="text-xs text-gray-500">
            <th class="p-2 text-left">首购月份</th>
            <th class="p-2">用户数</th>
            <th v-for="label in COLUMN_LABELS" :key="label" class="p-2">
              {{ label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in data"
            :key="row.cohortMonth"
            class="border-t border-gray-100 dark:border-gray-800"
          >
            <td class="p-2 text-left font-medium">{{ row.cohortMonth }}</td>
            <td class="p-2">{{ formatNumber(row.cohortSize) }}</td>
            <td
              v-for="(label, offset) in COLUMN_LABELS"
              :key="label"
              class="p-1"
            >
              <div
                v-if="isAvailable(row.cohortMonth, offset)"
                class="rounded px-2 py-2"
                :style="cellStyle(row, offset)"
              >
                <div class="font-bold">
                  {{ cellValue(row, offset) }}
                </div>
                <div class="text-xs opacity-80">
                  {{ rate(row, offset).toFixed(0) }}%
                </div>
              </div>
              <span v-else class="text-gray-300">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </ElCard>
</template>
