<script lang="ts" setup>
import type { UserRfmSegmentVO } from '#/api/order/order-statistics';

import { computed } from 'vue';

import { InfoFilled, PieChart } from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElProgress, ElTooltip } from 'element-plus';

import { formatNumber } from '../../shared/chart';

const props = withDefaults(
  defineProps<{
    data: UserRfmSegmentVO[];
    loading?: boolean;
  }>(),
  {
    loading: false,
  },
);

const COLORS = [
  '#409EFF',
  '#67C23A',
  '#E6A23C',
  '#F56C6C',
  '#909399',
  '#8E44AD',
  '#16A085',
  '#7F8C8D',
];

const totalUsers = computed(() =>
  props.data.reduce((sum, item) => sum + (Number(item.userCount) || 0), 0),
);

const percent = (value: number) =>
  totalUsers.value > 0
    ? Math.min(100, (Number(value || 0) / totalUsers.value) * 100)
    : 0;
</script>

<template>
  <ElCard v-loading="loading" shadow="hover" class="mb-4">
    <template #header>
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-purple-500"><PieChart /></ElIcon>
          <span>用户价值分层（RFM）</span>
          <span class="text-xs font-normal text-gray-400">
            共 {{ formatNumber(totalUsers) }} 位成交用户
          </span>
        </div>
        <ElTooltip
          content="以周期结束时间为基准，按最近购买、购买频次、累计消费打分分层；阈值见数据口径字典"
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
    <div v-else class="space-y-3">
      <div
        v-for="(item, index) in data"
        :key="item.segment"
        class="flex flex-wrap items-center gap-3"
      >
        <span class="w-32 truncate text-sm font-medium">
          {{ item.segment }}
        </span>
        <ElProgress
          :percentage="percent(item.userCount)"
          :show-text="false"
          :color="COLORS[index % COLORS.length]"
          class="min-w-32 flex-1"
        />
        <span class="w-20 text-right text-sm font-bold">
          {{ formatNumber(item.userCount) }} 人
        </span>
        <span class="w-28 text-right text-xs text-gray-500">
          ¥{{ formatNumber(item.gmv) }}
        </span>
      </div>
    </div>
  </ElCard>
</template>
