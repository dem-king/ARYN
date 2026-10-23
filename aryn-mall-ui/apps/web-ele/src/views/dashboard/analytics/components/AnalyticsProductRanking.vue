<script lang="ts" setup>
import { CountTo } from '@vben/common-ui';

import { Trophy } from '@element-plus/icons-vue';
import { ElCard, ElEmpty, ElIcon } from 'element-plus';

import { formatPercent } from '#/utils/percent';

defineProps<{
  loading?: boolean;
  rankings: {
    refundTop10: Array<{ name: string; value: number }>;
    salesTop10: Array<{ name: string; value: number }>;
  };
}>();
const getRankBg = (index: number) => {
  if (index === 0) {
    return 'bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400';
  }
  if (index === 1) {
    return 'bg-[var(--el-fill-color)] text-[var(--el-text-color-regular)]';
  }
  if (index === 2) {
    return 'bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400';
  }
  return 'bg-[var(--el-fill-color-light)] text-[var(--el-text-color-secondary)]';
};
</script>

<template>
  <ElCard
    :body-style="{ height: '100%' }"
    class="flex h-full flex-col"
    shadow="hover"
    v-loading="loading"
  >
    <template #header>
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-yellow-500"><Trophy /></ElIcon>
          <span>商品排行</span>
        </div>
      </div>
    </template>
    <div class="flex h-full flex-col gap-8 md:flex-row">
      <!-- Sales Ranking -->
      <div class="flex min-w-0 flex-1 flex-col">
        <div class="mb-4 flex items-center gap-2 text-sm font-bold">
          <div class="h-4 w-1 bg-blue-500"></div>
          销量 TOP 10
        </div>
        <template v-if="rankings.salesTop10 && rankings.salesTop10.length > 0">
          <div class="flex flex-col gap-2">
            <div
              v-for="(item, index) in rankings.salesTop10"
              :key="index"
              class="flex items-center justify-between gap-4 rounded p-2 transition-colors hover:bg-[var(--el-fill-color-light)]"
            >
              <div
                class="flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-bold"
                :class="getRankBg(index)"
              >
                {{ index + 1 }}
              </div>
              <span class="min-w-0 flex-1 truncate text-sm" :title="item.name">
                {{ item.name }}
              </span>
              <span
                class="shrink-0 font-mono font-bold text-[var(--el-text-color-primary)]"
              >
                <CountTo :end-val="item.value" />
              </span>
            </div>
          </div>
        </template>
        <ElEmpty
          v-else
          :image-size="60"
          class="flex-1"
          description="暂无排名"
        />
      </div>

      <!-- Refund Ranking -->
      <div class="flex min-w-0 flex-1 flex-col">
        <div class="mb-4 flex items-center gap-2 text-sm font-bold">
          <div class="h-4 w-1 bg-red-500"></div>
          退款率 TOP 10
        </div>
        <template
          v-if="rankings.refundTop10 && rankings.refundTop10.length > 0"
        >
          <div class="flex flex-col gap-2">
            <div
              v-for="(item, index) in rankings.refundTop10"
              :key="index"
              class="flex items-center justify-between gap-4 rounded p-2 transition-colors hover:bg-[var(--el-fill-color-light)]"
            >
              <div
                class="flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-bold"
                :class="getRankBg(index)"
              >
                {{ index + 1 }}
              </div>
              <span class="min-w-0 flex-1 truncate text-sm" :title="item.name">
                {{ item.name }}
              </span>
              <span class="shrink-0 font-mono font-bold text-red-500">
                {{ formatPercent(item.value) }}
              </span>
            </div>
          </div>
        </template>
        <ElEmpty
          v-else
          :image-size="60"
          class="flex-1"
          description="暂无排名"
        />
      </div>
    </div>
  </ElCard>
</template>
