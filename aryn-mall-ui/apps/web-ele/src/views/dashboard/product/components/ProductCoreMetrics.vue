<script lang="ts" setup>
import { CountTo } from '@vben/common-ui';

import {
  Box,
  DataLine,
  Goods,
  Money,
  SoldOut,
  TrendCharts,
  Warning,
} from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

defineProps<{
  loading?: boolean;
  sales: {
    avgPrice: number;
    salesAmount: number;
    salesItemCount: number;
    salesProductCount: number;
    sellThroughRate: number;
  };
  scale: {
    lowStock: number;
    offShelf: number;
    onSale: number;
    pendingReview: number;
    total: number;
    totalStock: number;
  };
}>();
</script>

<template>
  <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
    <!-- 商品规模类 -->
    <ElCard shadow="hover" class="h-full" v-loading="loading">
      <template #header>
        <div class="flex items-center gap-2 font-bold">
          <ElIcon><Box /></ElIcon>
          <span>商品规模</span>
        </div>
      </template>
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <div
          class="flex flex-col items-center justify-center rounded-lg bg-gray-50 p-4 dark:bg-gray-800"
        >
          <div class="text-2xl font-bold text-gray-700 dark:text-gray-300">
            <CountTo :end-val="scale.total" />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><Goods /></ElIcon> 商品总数
          </div>
        </div>
        <div
          class="flex flex-col items-center justify-center rounded-lg bg-green-50 p-4 dark:bg-green-900/20"
        >
          <div class="text-2xl font-bold text-green-500">
            <CountTo :end-val="scale.onSale" />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><Goods /></ElIcon> 上架商品
          </div>
        </div>
        <div
          class="flex flex-col items-center justify-center rounded-lg bg-gray-50 p-4 dark:bg-gray-800"
        >
          <div class="text-2xl font-bold text-gray-400">
            <CountTo :end-val="scale.offShelf" />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><SoldOut /></ElIcon> 下架商品
          </div>
        </div>
        <div
          class="flex flex-col items-center justify-center rounded-lg bg-indigo-50 p-4 dark:bg-indigo-900/20"
        >
          <div class="text-2xl font-bold text-indigo-500">
            <CountTo :end-val="scale.totalStock" />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><Box /></ElIcon> 库存总量
          </div>
        </div>
        <div
          class="flex flex-col items-center justify-center rounded-lg bg-red-50 p-4 dark:bg-red-900/20"
        >
          <div class="text-2xl font-bold text-red-500">
            <CountTo :end-val="scale.lowStock" />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><Warning /></ElIcon> 库存预警
          </div>
        </div>
      </div>
    </ElCard>

    <!-- 销售能力类 -->
    <ElCard shadow="hover" class="h-full" v-loading="loading">
      <template #header>
        <div class="flex items-center gap-2 font-bold">
          <ElIcon><DataLine /></ElIcon>
          <span>销售能力</span>
          <span class="ml-2 text-xs font-normal text-gray-400">
            (统计周期内数据)
          </span>
        </div>
      </template>
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <div
          class="flex flex-col items-center justify-center rounded-lg bg-purple-50 p-4 dark:bg-purple-900/20"
        >
          <div class="text-2xl font-bold text-purple-500">
            <CountTo :end-val="sales.salesProductCount" />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><Goods /></ElIcon> 动销商品数
          </div>
        </div>
        <div
          class="flex flex-col items-center justify-center rounded-lg bg-orange-50 p-4 dark:bg-orange-900/20"
        >
          <div class="text-2xl font-bold text-orange-500">
            <CountTo :end-val="sales.salesItemCount" />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><Box /></ElIcon> 销售件数
          </div>
        </div>
        <div
          class="col-span-2 flex flex-col items-center justify-center rounded-lg bg-red-50 p-4 sm:col-span-1 dark:bg-red-900/20"
        >
          <div class="text-2xl font-bold text-red-500">
            <CountTo :end-val="sales.salesAmount" prefix="¥" :decimals="2" />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><Money /></ElIcon> 销售额
          </div>
        </div>
        <div
          class="flex flex-col items-center justify-center rounded-lg bg-teal-50 p-4 dark:bg-teal-900/20"
        >
          <div class="text-2xl font-bold text-teal-500">
            <CountTo :end-val="sales.avgPrice" prefix="¥" :decimals="2" />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><Money /></ElIcon> 客单价
          </div>
        </div>
        <div
          class="flex flex-col items-center justify-center rounded-lg bg-cyan-50 p-4 dark:bg-cyan-900/20"
        >
          <div class="text-2xl font-bold text-cyan-500">
            <CountTo
              :end-val="sales.sellThroughRate"
              suffix="%"
              :decimals="1"
            />
          </div>
          <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
            <ElIcon><TrendCharts /></ElIcon> 动销率
          </div>
        </div>
      </div>
    </ElCard>
  </div>
</template>
