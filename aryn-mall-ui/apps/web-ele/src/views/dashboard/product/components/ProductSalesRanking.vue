<script lang="ts" setup>
import { CountTo } from '@vben/common-ui';

import { Rank, Warning } from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElTable, ElTableColumn, ElTag } from 'element-plus';

defineProps<{
  loading?: boolean;
  stockWarning: Array<{
    cover: string;
    id: string;
    name: string;
    status: string;
    stock: number;
  }>;
  visitRank: Array<{
    cover: string;
    id: string;
    name: string;
    pv: number;
    uv: number;
  }>;
}>();
</script>

<template>
  <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
    <!-- 浏览排行 -->
    <ElCard shadow="hover" class="h-full" v-loading="loading">
      <template #header>
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-red-500"><Rank /></ElIcon>
          <span>商品浏览排行 TOP 10</span>
          <span class="ml-2 text-xs font-normal text-gray-400">
            (统计周期内数据)
          </span>
        </div>
      </template>
      <ElTable
        :data="visitRank"
        style="width: 100%"
        size="small"
        :show-header="true"
      >
        <ElTableColumn type="index" label="排名" width="60" align="center">
          <template #default="{ $index }">
            <div
              class="mx-auto flex size-5 items-center justify-center rounded-full text-xs text-white"
              :class="{
                'bg-red-500': $index === 0,
                'bg-orange-500': $index === 1,
                'bg-yellow-500': $index === 2,
                'bg-gray-300': $index > 2,
              }"
            >
              {{ $index + 1 }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="name"
          label="商品名称"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <img
                :src="row.cover"
                class="size-8 rounded bg-gray-100 object-cover"
                alt=""
                v-if="row.cover"
              />
              <div
                class="flex size-8 items-center justify-center rounded bg-gray-100 text-xs text-gray-400"
                v-else
              >
                无图
              </div>
              <span class="truncate">{{ row.name }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="pv" label="浏览量(PV)" width="100" align="center">
          <template #default="{ row }">
            <CountTo :end-val="row.pv" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="uv" label="访客数(UV)" width="100" align="center">
          <template #default="{ row }">
            <CountTo :end-val="row.uv" />
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <!-- 库存预警 -->
    <ElCard shadow="hover" class="h-full" v-loading="loading">
      <template #header>
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-orange-500"><Warning /></ElIcon>
          <span>商品库存预警</span>
        </div>
      </template>
      <ElTable :data="stockWarning" style="width: 100%">
        <ElTableColumn
          prop="name"
          label="商品名称"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <span class="truncate">{{ row.name }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="stock" label="剩余库存" width="100" align="center">
          <template #default="{ row }">
            <span class="font-bold text-red-500">{{ row.stock }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <ElTag type="danger" size="small">{{ row.status }}</ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
  </div>
</template>
