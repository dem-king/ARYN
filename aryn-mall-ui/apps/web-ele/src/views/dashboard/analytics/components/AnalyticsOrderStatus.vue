<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { CountTo } from '@vben/common-ui';

import {
  Box,
  CircleCheck,
  CreditCard,
  Headset,
  Money,
  Money as Truck,
} from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

defineProps<{
  data: {
    afterSales: number;
    completed: number;
    pendingPayment: number;
    pendingShipment: number;
    refunded: number;
    shipped: number;
  };
  loading?: boolean;
  shopId?: string;
}>();

const router = useRouter();

const handleNavigate = (status: string) => {
  // TODO: Add shopId to query
  router.push({ path: '/order/order-info', query: { status } });
};
const handleRefund = (status: string) => {
  router.push({ path: '/order/order-refund', query: { status } });
};
</script>

<template>
  <ElCard shadow="hover" class="mb-4 h-full" v-loading="loading">
    <template #header>
      <div class="font-bold">订单状态总览</div>
    </template>
    <div class="grid grid-cols-3 gap-4 text-center md:grid-cols-3">
      <div
        class="hover:text-primary group flex cursor-pointer flex-col items-center justify-center rounded-lg p-2 transition-all hover:bg-[var(--el-fill-color-light)]"
        @click="handleNavigate('1')"
      >
        <div
          class="mb-2 flex size-10 items-center justify-center rounded-full bg-blue-50 text-blue-500 transition-transform group-hover:scale-110 dark:bg-blue-900/20 dark:text-blue-400"
        >
          <ElIcon :size="20">
            <CreditCard />
          </ElIcon>
        </div>
        <div class="text-2xl font-bold">
          <CountTo :end-val="data.pendingPayment" />
        </div>
        <div class="text-xs text-[var(--el-text-color-secondary)]">待付款</div>
      </div>

      <div
        class="hover:text-primary group flex cursor-pointer flex-col items-center justify-center rounded-lg p-2 transition-all hover:bg-[var(--el-fill-color-light)]"
        @click="handleNavigate('2')"
      >
        <div
          class="mb-2 flex size-10 items-center justify-center rounded-full bg-red-50 text-red-500 transition-transform group-hover:scale-110 dark:bg-red-900/20 dark:text-red-400"
        >
          <ElIcon :size="20">
            <Box />
          </ElIcon>
        </div>
        <div class="text-2xl font-bold text-red-500">
          <CountTo :end-val="data.pendingShipment" />
        </div>
        <div class="text-xs text-[var(--el-text-color-secondary)]">待发货</div>
      </div>

      <div
        class="hover:text-primary group flex cursor-pointer flex-col items-center justify-center rounded-lg p-2 transition-all hover:bg-[var(--el-fill-color-light)]"
        @click="handleNavigate('3')"
      >
        <div
          class="mb-2 flex size-10 items-center justify-center rounded-full bg-orange-50 text-orange-500 transition-transform group-hover:scale-110 dark:bg-orange-900/20 dark:text-orange-400"
        >
          <ElIcon :size="20">
            <Truck />
          </ElIcon>
        </div>
        <div class="text-2xl font-bold">
          <CountTo :end-val="data.shipped" />
        </div>
        <div class="text-xs text-[var(--el-text-color-secondary)]">已发货</div>
      </div>

      <div
        class="hover:text-primary group flex cursor-pointer flex-col items-center justify-center rounded-lg p-2 transition-all hover:bg-[var(--el-fill-color-light)]"
        @click="handleNavigate('4')"
      >
        <div
          class="mb-2 flex size-10 items-center justify-center rounded-full bg-green-50 text-green-500 transition-transform group-hover:scale-110 dark:bg-green-900/20 dark:text-green-400"
        >
          <ElIcon :size="20">
            <CircleCheck />
          </ElIcon>
        </div>
        <div class="text-2xl font-bold">
          <CountTo :end-val="data.completed" />
        </div>
        <div class="text-xs text-[var(--el-text-color-secondary)]">已完成</div>
      </div>

      <div
        class="hover:text-primary group flex cursor-pointer flex-col items-center justify-center rounded-lg p-2 transition-all hover:bg-[var(--el-fill-color-light)]"
        @click="handleRefund('1')"
      >
        <div
          class="mb-2 flex size-10 items-center justify-center rounded-full bg-purple-50 text-purple-500 transition-transform group-hover:scale-110 dark:bg-purple-900/20 dark:text-purple-400"
        >
          <ElIcon :size="20">
            <Headset />
          </ElIcon>
        </div>
        <div class="text-2xl font-bold">
          <CountTo :end-val="data.afterSales" />
        </div>
        <div class="text-xs text-[var(--el-text-color-secondary)]">
          售后待审核
        </div>
      </div>

      <div
        class="hover:text-primary group flex cursor-pointer flex-col items-center justify-center rounded-lg p-2 transition-all hover:bg-[var(--el-fill-color-light)]"
        @click="handleRefund('6')"
      >
        <div
          class="mb-2 flex size-10 items-center justify-center rounded-full bg-[var(--el-fill-color-light)] text-[var(--el-text-color-secondary)] transition-transform group-hover:scale-110"
        >
          <ElIcon :size="20">
            <Money />
          </ElIcon>
        </div>
        <div class="text-2xl font-bold">
          <CountTo :end-val="data.refunded" />
        </div>
        <div class="text-xs text-[var(--el-text-color-secondary)]">已退款</div>
      </div>
    </div>
  </ElCard>
</template>
