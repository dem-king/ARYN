<script lang="ts" setup>
import { CountTo } from '@vben/common-ui';

import {
  ChatDotSquare,
  CircleCheck,
  Timer,
  Warning,
} from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

defineProps<{
  data: {
    applications: number;
    negativeReviews: number;
    overdueShipment: number;
    successCount: number;
  };
  loading?: boolean;
}>();
</script>

<template>
  <ElCard shadow="hover" class="mb-4 h-full" v-loading="loading">
    <template #header>
      <div class="flex items-center gap-2 font-bold text-red-600">
        <ElIcon><Warning /></ElIcon>
        <span>服务与风控统计</span>
      </div>
    </template>
    <div class="grid grid-cols-2 gap-4">
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-blue-50 p-4 transition-transform hover:scale-105 dark:bg-blue-900/20"
      >
        <div class="text-3xl font-bold text-blue-500 dark:text-blue-400">
          <CountTo :end-val="data.applications" />
        </div>
        <div
          class="mt-1 flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400"
        >
          <ElIcon><Warning /></ElIcon> 售后申请
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-green-50 p-4 transition-transform hover:scale-105 dark:bg-green-900/20"
      >
        <div class="text-3xl font-bold text-green-500 dark:text-green-400">
          <CountTo :end-val="data.successCount" />
        </div>
        <div
          class="mt-1 flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400"
        >
          <ElIcon><CircleCheck /></ElIcon> 退款完成
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-red-50 p-4 transition-transform hover:scale-105 dark:bg-red-900/20"
      >
        <div class="text-3xl font-bold text-red-500 dark:text-red-400">
          <CountTo :end-val="data.overdueShipment" />
        </div>
        <div
          class="mt-1 flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400"
        >
          <ElIcon><Timer /></ElIcon> 超时24小时未发货
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-orange-50 p-4 transition-transform hover:scale-105 dark:bg-orange-900/20"
      >
        <div class="text-3xl font-bold text-orange-500 dark:text-orange-400">
          <CountTo :end-val="data.negativeReviews" />
        </div>
        <div
          class="mt-1 flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400"
        >
          <ElIcon><ChatDotSquare /></ElIcon> 负面评价
        </div>
      </div>
    </div>
  </ElCard>
</template>
