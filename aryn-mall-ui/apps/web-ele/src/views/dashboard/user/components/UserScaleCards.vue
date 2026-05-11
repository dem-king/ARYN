<script lang="ts" setup>
import { CountTo } from '@vben/common-ui';

import {
  Calendar,
  DataAnalysis,
  TrendCharts,
  User,
  UserFilled,
} from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

defineProps<{
  data: {
    monthNewUserCount: number;
    sevenDayNewUserCount: number;
    thirtyDayNewUserCount: number;
    todayNewUserCount: number;
    totalTransactionUsers: number;
    totalUserCount: number;
  };
  loading?: boolean;
}>();
</script>

<template>
  <ElCard shadow="hover" class="mb-4" v-loading="loading">
    <template #header>
      <div class="flex items-center gap-2 font-bold">
        <ElIcon class="text-blue-500"><User /></ElIcon>
        <span>用户规模概览</span>
      </div>
    </template>
    <div class="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-blue-50 p-4 dark:bg-blue-900/20"
      >
        <div class="text-2xl font-bold text-blue-500">
          <CountTo :end-val="data.totalUserCount" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><UserFilled /></ElIcon> 用户总数
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-green-50 p-4 dark:bg-green-900/20"
      >
        <div class="text-2xl font-bold text-green-500">
          <CountTo :end-val="data.todayNewUserCount" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><Calendar /></ElIcon> 今日新增
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-teal-50 p-4 dark:bg-teal-900/20"
      >
        <div class="text-2xl font-bold text-teal-500">
          <CountTo :end-val="data.sevenDayNewUserCount" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><TrendCharts /></ElIcon> 7日新增
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-cyan-50 p-4 dark:bg-cyan-900/20"
      >
        <div class="text-2xl font-bold text-cyan-500">
          <CountTo :end-val="data.thirtyDayNewUserCount" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><TrendCharts /></ElIcon> 30日新增
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-indigo-50 p-4 dark:bg-indigo-900/20"
      >
        <div class="text-2xl font-bold text-indigo-500">
          <CountTo :end-val="data.monthNewUserCount" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><Calendar /></ElIcon> 本月新增
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-purple-50 p-4 dark:bg-purple-900/20"
      >
        <div class="text-2xl font-bold text-purple-500">
          <CountTo :end-val="data.totalTransactionUsers" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><DataAnalysis /></ElIcon> 累计成交用户
        </div>
      </div>
    </div>
  </ElCard>
</template>
