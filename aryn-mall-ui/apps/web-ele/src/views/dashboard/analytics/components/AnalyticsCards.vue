<script lang="ts" setup>
import { CountTo } from '@vben/common-ui';

import {
  Headset,
  List,
  Money,
  RefreshLeft,
  User,
} from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

defineProps<{
  data: {
    afterSalesOrders: number;
    aov: number;
    gmv: number;
    paidBuyers: number;
    paidOrders: number;
    refundAmount: number;
  };
  loading?: boolean;
}>();
</script>

<template>
  <div class="mb-4 grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
    <!-- GMV -->
    <ElCard
      shadow="hover"
      v-loading="loading"
      class="enter-y transform transition-all duration-300 hover:-translate-y-1 hover:shadow-lg"
    >
      <div class="flex items-center justify-between">
        <div>
          <div class="text-sm text-gray-500">成交额 (GMV)</div>
          <div class="mt-2 text-2xl font-bold">
            <CountTo :end-val="data.gmv" :decimals="2" prefix="¥" />
          </div>
        </div>
        <div class="rounded-full bg-blue-50 p-3 text-blue-500">
          <ElIcon :size="24"><Money /></ElIcon>
        </div>
      </div>
    </ElCard>

    <!-- Paid Orders -->
    <ElCard
      shadow="hover"
      v-loading="loading"
      class="enter-y !animation-delay-100 transform transition-all duration-300 hover:-translate-y-1 hover:shadow-lg"
    >
      <div class="flex items-center justify-between">
        <div>
          <div class="text-sm text-gray-500">支付订单数</div>
          <div class="mt-2 text-2xl font-bold">
            <CountTo :end-val="data.paidOrders" />
          </div>
        </div>
        <div class="rounded-full bg-green-50 p-3 text-green-500">
          <ElIcon :size="24"><List /></ElIcon>
        </div>
      </div>
    </ElCard>

    <!-- Paid Buyers -->
    <ElCard
      shadow="hover"
      v-loading="loading"
      class="enter-y !animation-delay-200 transform transition-all duration-300 hover:-translate-y-1 hover:shadow-lg"
    >
      <div class="flex items-center justify-between">
        <div>
          <div class="text-sm text-gray-500">支付买家数</div>
          <div class="mt-2 text-2xl font-bold">
            <CountTo :end-val="data.paidBuyers" />
          </div>
        </div>
        <div class="rounded-full bg-orange-50 p-3 text-orange-500">
          <ElIcon :size="24"><User /></ElIcon>
        </div>
      </div>
    </ElCard>

    <!-- AOV -->
    <ElCard
      shadow="hover"
      v-loading="loading"
      class="enter-y !animation-delay-300 transform transition-all duration-300 hover:-translate-y-1 hover:shadow-lg"
    >
      <div class="flex items-center justify-between">
        <div>
          <div class="text-sm text-gray-500">客单价</div>
          <div class="mt-2 text-2xl font-bold">
            <CountTo :end-val="data.aov" :decimals="2" prefix="¥" />
          </div>
        </div>
        <div class="rounded-full bg-purple-50 p-3 text-purple-500">
          <ElIcon :size="24"><Money /></ElIcon>
        </div>
      </div>
    </ElCard>

    <!-- After Sales -->
    <ElCard
      shadow="hover"
      v-loading="loading"
      class="enter-y !animation-delay-400 transform transition-all duration-300 hover:-translate-y-1 hover:shadow-lg"
    >
      <div class="flex items-center justify-between">
        <div>
          <div class="text-sm text-gray-500">售后订单数</div>
          <div class="mt-2 text-2xl font-bold text-red-500">
            <CountTo :end-val="data.afterSalesOrders" />
          </div>
        </div>
        <div class="rounded-full bg-red-50 p-3 text-red-500">
          <ElIcon :size="24"><Headset /></ElIcon>
        </div>
      </div>
    </ElCard>

    <!-- Refund Amount -->
    <ElCard
      shadow="hover"
      v-loading="loading"
      class="enter-y !animation-delay-500 transform transition-all duration-300 hover:-translate-y-1 hover:shadow-lg"
    >
      <div class="flex items-center justify-between">
        <div>
          <div class="text-sm text-gray-500">退款金额</div>
          <div class="mt-2 text-2xl font-bold text-red-500">
            <CountTo :end-val="data.refundAmount" :decimals="2" prefix="¥" />
          </div>
        </div>
        <div class="rounded-full bg-red-50 p-3 text-red-500">
          <ElIcon :size="24"><RefreshLeft /></ElIcon>
        </div>
      </div>
    </ElCard>
  </div>
</template>
