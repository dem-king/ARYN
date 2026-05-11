<script lang="ts" setup>
import { CountTo } from '@vben/common-ui';

import {
  InfoFilled,
  Refresh,
  Star,
  User,
  UserFilled,
} from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElTooltip } from 'element-plus';

defineProps<{
  data: {
    newUsers: number;
    repurchaseRate: number;
    returningCustomerShare: number;
    transactingUsers: number;
  };
  loading?: boolean;
}>();
</script>

<template>
  <ElCard shadow="hover" v-loading="loading">
    <template #header>
      <div class="flex items-center gap-2 font-bold">
        <ElIcon><User /></ElIcon>
        <span>客户数据</span>
      </div>
    </template>
    <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
      <div
        class="flex flex-col justify-between rounded-lg bg-gray-50 p-4 transition-all hover:-translate-y-1 hover:shadow-md dark:bg-gray-800"
      >
        <div class="flex items-center gap-4">
          <div
            class="rounded-full bg-blue-100 p-2 text-blue-500 dark:bg-blue-900/30 dark:text-blue-400"
          >
            <ElIcon :size="20"><UserFilled /></ElIcon>
          </div>
          <div>
            <div
              class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400"
            >
              成交用户数
              <ElTooltip
                content="统计周期内有支付行为的用户总数"
                placement="top"
              >
                <ElIcon class="cursor-help"><InfoFilled /></ElIcon>
              </ElTooltip>
            </div>
            <div class="text-xl font-bold dark:text-gray-100">
              <CountTo :end-val="data.transactingUsers" />
            </div>
          </div>
        </div>
      </div>

      <div
        class="flex flex-col justify-between rounded-lg bg-gray-50 p-4 transition-all hover:-translate-y-1 hover:shadow-md dark:bg-gray-800"
      >
        <div class="flex items-center gap-4">
          <div
            class="rounded-full bg-green-100 p-2 text-green-500 dark:bg-green-900/30 dark:text-green-400"
          >
            <ElIcon :size="20"><User /></ElIcon>
          </div>
          <div>
            <div
              class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400"
            >
              新增用户
              <ElTooltip content="统计周期内首次访问店铺的用户" placement="top">
                <ElIcon class="cursor-help"><InfoFilled /></ElIcon>
              </ElTooltip>
            </div>
            <div class="text-xl font-bold text-green-500 dark:text-green-400">
              <CountTo :end-val="data.newUsers" />
            </div>
          </div>
        </div>
      </div>

      <div
        class="flex flex-col justify-between rounded-lg bg-gray-50 p-4 transition-all hover:-translate-y-1 hover:shadow-md dark:bg-gray-800"
      >
        <div class="flex items-center gap-4">
          <div
            class="rounded-full bg-purple-100 p-2 text-purple-500 dark:bg-purple-900/30 dark:text-purple-400"
          >
            <ElIcon :size="20"><Refresh /></ElIcon>
          </div>
          <div>
            <div
              class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400"
            >
              复购率
              <ElTooltip
                content="统计周期内产生两次及以上购买行为的用户占比"
                placement="top"
              >
                <ElIcon class="cursor-help"><InfoFilled /></ElIcon>
              </ElTooltip>
            </div>
            <div class="text-xl font-bold dark:text-gray-100">
              <CountTo
                :end-val="data.repurchaseRate"
                :decimals="2"
                suffix="%"
              />
            </div>
          </div>
        </div>
      </div>

      <div
        class="flex flex-col justify-between rounded-lg bg-gray-50 p-4 transition-all hover:-translate-y-1 hover:shadow-md dark:bg-gray-800"
      >
        <div class="flex items-center gap-4">
          <div
            class="rounded-full bg-orange-100 p-2 text-orange-500 dark:bg-orange-900/30 dark:text-orange-400"
          >
            <ElIcon :size="20"><Star /></ElIcon>
          </div>
          <div>
            <div
              class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400"
            >
              老客成交占比
              <ElTooltip
                content="统计周期内老客户成交金额占总成交额比例"
                placement="top"
              >
                <ElIcon class="cursor-help"><InfoFilled /></ElIcon>
              </ElTooltip>
            </div>
            <div class="text-xl font-bold dark:text-gray-100">
              <CountTo
                :end-val="data.returningCustomerShare"
                :decimals="2"
                suffix="%"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </ElCard>
</template>
