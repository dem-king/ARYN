<script lang="ts" setup>
import type { ZoneReportVO } from '#/api/order/order-statistics';

import { ref, watch } from 'vue';

import { ElCard, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getZoneReport } from '#/api/order/order-statistics';

const props = defineProps<{
  endTime?: string;
  startTime?: string;
}>();

const loading = ref(false);
const data = ref<null | ZoneReportVO>(null);

const zoneLabel: Record<string, string> = {
  member: '会员专区',
  clearance: '低价捡漏',
};

const zoneTagType: Record<string, 'danger' | 'warning'> = {
  clearance: 'danger',
  member: 'warning',
};

async function load() {
  if (!props.startTime || !props.endTime) return;
  loading.value = true;
  try {
    data.value = await getZoneReport({
      endTime: props.endTime,
      startTime: props.startTime,
    });
  } catch {
    data.value = null;
  } finally {
    loading.value = false;
  }
}

watch(() => [props.startTime, props.endTime], load, { immediate: true });
</script>

<template>
  <ElCard shadow="hover" class="mb-4" v-loading="loading">
    <template #header>
      <div class="flex items-center justify-between">
        <div class="font-bold">专区让利</div>
        <div class="text-xs text-[var(--el-text-color-secondary)]">
          让利 = 活动前价差 + 会员折扣（不含券/积分）
        </div>
      </div>
    </template>

    <div class="mb-4 grid grid-cols-2 gap-4 md:grid-cols-4">
      <div class="rounded-lg bg-[var(--el-fill-color-light)] p-3">
        <div class="text-xs text-[var(--el-text-color-secondary)]">
          专区订单
        </div>
        <div class="text-xl font-bold">{{ data?.orderCount ?? 0 }}</div>
      </div>
      <div class="rounded-lg bg-[var(--el-fill-color-light)] p-3">
        <div class="text-xs text-[var(--el-text-color-secondary)]">
          商品件数
        </div>
        <div class="text-xl font-bold">{{ data?.itemCount ?? 0 }}</div>
      </div>
      <div class="rounded-lg bg-[var(--el-fill-color-light)] p-3">
        <div class="text-xs text-[var(--el-text-color-secondary)]">
          商品实付（不含运费）
        </div>
        <div class="text-xl font-bold">
          ¥{{ Number(data?.gmv ?? 0).toFixed(2) }}
        </div>
      </div>
      <div class="rounded-lg bg-[var(--el-fill-color-light)] p-3">
        <div class="text-xs text-[var(--el-text-color-secondary)]">
          让利金额
        </div>
        <div class="text-xl font-bold text-red-500">
          ¥{{ Number(data?.discount ?? 0).toFixed(2) }}
        </div>
      </div>
    </div>

    <ElTable :data="data?.items ?? []" border empty-text="所选周期暂无专区成交">
      <ElTableColumn label="专区" width="140">
        <template #default="scope">
          <ElTag
            :type="zoneTagType[scope.row.zoneSource] ?? 'info'"
            size="small"
          >
            {{ zoneLabel[scope.row.zoneSource] ?? scope.row.zoneSource }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="orderCount" label="订单数" align="right" />
      <ElTableColumn prop="itemCount" label="件数" align="right" />
      <ElTableColumn label="商品实付" align="right" width="140">
        <template #default="scope">
          ¥{{ Number(scope.row.gmv ?? 0).toFixed(2) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="让利" align="right" width="140">
        <template #default="scope">
          <span class="text-red-500">
            ¥{{ Number(scope.row.discount ?? 0).toFixed(2) }}
          </span>
        </template>
      </ElTableColumn>
    </ElTable>
  </ElCard>
</template>
