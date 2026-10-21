<script lang="ts" setup>
import { CountTo } from '@vben/common-ui';

import { Medal } from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElTable, ElTableColumn } from 'element-plus';

import { formatPercent } from '#/utils/percent';

defineProps<{
  data: Array<{
    count: number;
    id: string;
    name: string;
    rate: number;
  }>;
  loading?: boolean;
}>();
</script>

<template>
  <ElCard v-loading="loading" class="h-full" shadow="hover">
    <template #header>
      <div class="flex items-center gap-2 font-bold">
        <ElIcon class="text-yellow-500"><Medal /></ElIcon>
        <span>商品好评榜 TOP 10</span>
        <span class="ml-2 text-xs font-normal text-gray-400">
          (统计周期内数据)
        </span>
      </div>
    </template>
    <ElTable :data="data" style="width: 100%">
      <ElTableColumn label="排名" width="60">
        <template #default="{ $index }">
          <span
            class="inline-block size-5 rounded-full text-center text-xs leading-5"
            :class="{
              'bg-red-500 text-white': $index === 0,
              'bg-orange-500 text-white': $index === 1,
              'bg-yellow-500 text-white': $index === 2,
              'bg-gray-100 text-gray-500': $index > 2,
            }"
          >
            {{ $index + 1 }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="name"
        show-overflow-tooltip
        min-width="150"
        label="商品名称"
      />
      <ElTableColumn prop="rate" width="100" align="right" label="好评率">
        <template #default="{ row }">
          <span class="font-bold text-orange-500">
            {{ formatPercent(row.rate, 1) }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="count" width="90" align="right" label="好评数">
        <template #default="{ row }">
          <CountTo :end-val="row.count" />
        </template>
      </ElTableColumn>
    </ElTable>
  </ElCard>
</template>
