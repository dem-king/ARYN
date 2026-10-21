<script lang="ts" setup>
import type { ProductLifecycleVO } from '#/api/product/goods-statistics';

import { computed } from 'vue';

import { CountTo } from '@vben/common-ui';

import {
  Goods,
  InfoFilled,
  SoldOut,
  TrendCharts,
  Warning,
} from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElTooltip } from 'element-plus';

const props = withDefaults(
  defineProps<{
    data: null | ProductLifecycleVO;
    loading?: boolean;
  }>(),
  {
    loading: false,
  },
);

const metrics = computed(() => [
  {
    label: '近30天新品',
    tone: 'bg-blue-50 text-blue-500 dark:bg-blue-900/20 dark:text-blue-400',
    icon: Goods,
    value: props.data?.newCount || 0,
  },
  {
    label: '动销商品',
    tone: 'bg-green-50 text-green-500 dark:bg-green-900/20 dark:text-green-400',
    icon: TrendCharts,
    value: props.data?.activeCount || 0,
  },
  {
    label: '滞销商品',
    tone: 'bg-orange-50 text-orange-500 dark:bg-orange-900/20 dark:text-orange-400',
    icon: Warning,
    value: props.data?.slowMovingCount || 0,
  },
  {
    label: '缺货商品',
    tone: 'bg-red-50 text-red-500 dark:bg-red-900/20 dark:text-red-400',
    icon: SoldOut,
    value: props.data?.outOfStockCount || 0,
  },
]);
</script>

<template>
  <ElCard v-loading="loading" shadow="hover" class="mb-4">
    <template #header>
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-cyan-500"><TrendCharts /></ElIcon>
          <span>商品生命周期</span>
        </div>
        <ElTooltip
          content="基于商品累计销量与当前库存；滞销=30 天前创建且累计销量为 0"
          placement="top"
        >
          <ElIcon class="cursor-help text-gray-400"><InfoFilled /></ElIcon>
        </ElTooltip>
      </div>
    </template>
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div
        v-for="item in metrics"
        :key="item.label"
        class="flex flex-col items-center justify-center rounded-lg p-4"
        :class="item.tone"
      >
        <div class="text-2xl font-bold">
          <CountTo :end-val="item.value" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><component :is="item.icon" /></ElIcon>
          {{ item.label }}
        </div>
      </div>
    </div>
  </ElCard>
</template>
