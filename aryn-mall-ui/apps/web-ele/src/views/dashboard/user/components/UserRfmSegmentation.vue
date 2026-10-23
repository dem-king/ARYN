<script lang="ts" setup>
import type { UserRfmSegmentVO } from '#/api/order/order-statistics';

import { computed } from 'vue';

import { InfoFilled, PieChart } from '@element-plus/icons-vue';
import { ElCard, ElEmpty, ElIcon, ElProgress, ElTooltip } from 'element-plus';

import { formatMoney } from '#/utils/util';

import { CHART_SERIES_COLORS, formatNumber } from '../../shared/chart';

const props = withDefaults(
  defineProps<{
    data: UserRfmSegmentVO[];
    loading?: boolean;
  }>(),
  {
    loading: false,
  },
);

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
          <span
            class="text-xs font-normal text-[var(--el-text-color-secondary)]"
          >
            共 {{ formatNumber(totalUsers) }} 位成交用户
          </span>
        </div>
        <ElTooltip
          content="以周期结束时间为基准，按最近购买、购买频次、累计消费打分分层；阈值见数据口径字典"
          placement="top"
        >
          <ElIcon class="cursor-help text-[var(--el-text-color-placeholder)]">
            <InfoFilled />
          </ElIcon>
        </ElTooltip>
      </div>
    </template>

    <ElEmpty
      v-if="!loading && data.length === 0"
      :image-size="60"
      description="暂无数据"
    />
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
          :color="CHART_SERIES_COLORS[index % CHART_SERIES_COLORS.length]"
          class="min-w-32 flex-1"
        />
        <span class="w-20 text-right text-sm font-bold">
          {{ formatNumber(item.userCount) }} 人
        </span>
        <span
          class="hx-money w-28 text-right text-xs text-[var(--el-text-color-secondary)]"
        >
          ¥{{ formatMoney(item.gmv) }}
        </span>
      </div>
    </div>
  </ElCard>
</template>
