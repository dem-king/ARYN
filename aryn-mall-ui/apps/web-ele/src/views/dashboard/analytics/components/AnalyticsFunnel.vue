<script lang="ts" setup>
import type { OrderFunnelVO } from '#/api/order/order-statistics';

import { computed } from 'vue';

import { InfoFilled, TrendCharts } from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElTooltip } from 'element-plus';

import { formatNumber } from '../../shared/chart';

const props = withDefaults(
  defineProps<{
    data: null | OrderFunnelVO;
    loading?: boolean;
  }>(),
  {
    loading: false,
  },
);

const created = computed(() => Number(props.data?.createdCount) || 0);

const rate = (value: number) =>
  created.value > 0 ? Math.min(100, (value / created.value) * 100) : 0;

const steps = computed(() => {
  const data = props.data;
  const paid = Number(data?.paidCount) || 0;
  const delivered = Number(data?.deliveredCount) || 0;
  const completed = Number(data?.completedCount) || 0;
  const refunded = Number(data?.refundedCount) || 0;
  return [
    {
      bar: 'bg-blue-500',
      label: '下单',
      rate: 100,
      value: created.value,
    },
    {
      bar: 'bg-green-500',
      label: '支付',
      rate: rate(paid),
      value: paid,
    },
    {
      bar: 'bg-orange-500',
      label: '发货',
      rate: rate(delivered),
      value: delivered,
    },
    {
      bar: 'bg-purple-500',
      label: '完成',
      rate: rate(completed),
      value: completed,
    },
    {
      bar: 'bg-red-500',
      label: '退款（已到账）',
      rate: rate(refunded),
      value: refunded,
    },
  ];
});
</script>

<template>
  <ElCard v-loading="loading" shadow="hover" class="mb-4">
    <template #header>
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-blue-500"><TrendCharts /></ElIcon>
          <span>交易履约漏斗</span>
        </div>
        <ElTooltip
          content="按下单时间统计；退款为已到账退款的侧向分支，不与主漏斗顺序相减"
          placement="top"
        >
          <ElIcon class="cursor-help text-gray-400"><InfoFilled /></ElIcon>
        </ElTooltip>
      </div>
    </template>

    <div
      v-if="!loading && created === 0"
      class="py-8 text-center text-sm text-gray-400"
    >
      暂无数据
    </div>
    <div v-else class="grid grid-cols-2 gap-3 md:grid-cols-5">
      <div
        v-for="step in steps"
        :key="step.label"
        class="rounded-lg bg-gray-50 p-3 dark:bg-gray-800"
      >
        <div class="text-xs text-gray-500 dark:text-gray-400">
          {{ step.label }}
        </div>
        <div class="mt-1 text-xl font-bold">
          {{ formatNumber(step.value) }}
        </div>
        <div class="mt-1 text-xs text-gray-400">
          {{ step.rate.toFixed(1) }}%
        </div>
        <div
          class="mt-2 h-1 overflow-hidden rounded bg-gray-200 dark:bg-gray-700"
        >
          <div
            class="h-full rounded transition-all duration-500"
            :class="step.bar"
            :style="{ width: `${step.rate}%` }"
          ></div>
        </div>
      </div>
    </div>
  </ElCard>
</template>
