<script lang="ts" setup>
import { Medal } from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElRate, ElTable, ElTableColumn } from 'element-plus';

defineProps<{
  data: Array<{
    id: string;
    name: string;
    score: number;
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
    <div class="h-full w-full">
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
        >
          <template #default="{ row }">
            <span class="text-sm">{{ row.name }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="score" width="130" align="right" label="商品评分">
          <template #default="{ row }">
            <div class="flex items-center justify-end">
              <ElRate
                :model-value="row.score"
                disabled
                text-color="#ff9900"
                size="small"
              />
              <span class="ml-2 font-bold text-orange-500">{{
                row.score
              }}</span>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </ElCard>
</template>
