<script lang="ts" setup>
import type { TrafficFunnelVO } from '#/api/order/order-statistics';

import { computed } from 'vue';

import { InfoFilled, TrendCharts } from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElTooltip } from 'element-plus';

import { formatNumber } from '../../shared/chart';

const props = withDefaults(
  defineProps<{
    data: null | TrafficFunnelVO;
    loading?: boolean;
  }>(),
  {
    loading: false,
  },
);

const visitors = computed(() => Number(props.data?.visitorCount) || 0);
const created = computed(() => Number(props.data?.createdBuyerCount) || 0);
const paid = computed(() => Number(props.data?.paidBuyerCount) || 0);

const rate = (value: number, base: number) =>
  base > 0 ? Math.min(100, (value / base) * 100) : 0;

const steps = computed(() => [
  {
    bar: 'bg-blue-500',
    hint: '商品足迹去重用户',
    label: '访问用户',
    rate: visitors.value > 0 ? 100 : 0,
    value: visitors.value,
  },
  {
    bar: 'bg-green-500',
    hint: '下单用户 / 访问用户',
    label: '下单用户',
    rate: rate(created.value, visitors.value),
    value: created.value,
  },
  {
    bar: 'bg-orange-500',
    hint: '支付用户 / 访问用户',
    label: '支付用户',
    rate: rate(paid.value, visitors.value),
    value: paid.value,
  },
]);

const hasData = computed(
  () => visitors.value > 0 || created.value > 0 || paid.value > 0,
);
</script>

<template>
  <ElCard v-loading="loading" shadow="hover" class="mb-4">
    <template #header>
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-green-500"><TrendCharts /></ElIcon>
          <span>流量转化漏斗</span>
        </div>
        <ElTooltip
          content="按去重用户统计；访问来自商品足迹，加购环节尚未埋点"
          placement="top"
        >
          <ElIcon class="cursor-help text-gray-400"><InfoFilled /></ElIcon>
        </ElTooltip>
      </div>
    </template>

    <div
      v-if="!loading && !hasData"
      class="py-8 text-center text-sm text-gray-400"
    >
      暂无数据
    </div>
    <div v-else class="grid grid-cols-1 gap-3 md:grid-cols-3">
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
        <div class="mt-1 text-xs text-gray-400">{{ step.hint }}</div>
        <div class="mt-2 flex items-center gap-2">
          <div
            class="h-1 flex-1 overflow-hidden rounded bg-gray-200 dark:bg-gray-700"
          >
            <div
              class="h-full rounded transition-all duration-500"
              :class="step.bar"
              :style="{ width: `${step.rate}%` }"
            ></div>
          </div>
          <span class="text-xs text-gray-400">
            {{ step.rate.toFixed(1) }}%
          </span>
        </div>
      </div>
    </div>
  </ElCard>
</template>
