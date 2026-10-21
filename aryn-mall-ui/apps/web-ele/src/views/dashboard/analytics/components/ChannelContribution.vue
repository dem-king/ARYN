<script lang="ts" setup>
import type { ChannelContributionVO } from '#/api/order/order-statistics';

import { computed, onMounted, ref, watch } from 'vue';

import { InfoFilled, PieChart } from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElProgress, ElTooltip } from 'element-plus';

import { getChannelContribution } from '#/api/order/order-statistics';

import { formatCurrency, formatNumber } from '../../shared/chart';

const props = defineProps<{
  endTime: string;
  startTime: string;
}>();

const loading = ref(false);
const loadError = ref(false);
const data = ref<ChannelContributionVO[]>([]);

const totalGmv = computed(() =>
  data.value.reduce((sum, item) => sum + (Number(item.gmv) || 0), 0),
);

const share = (gmv: number) =>
  totalGmv.value > 0
    ? Math.min(100, (Number(gmv || 0) / totalGmv.value) * 100)
    : 0;

const load = async () => {
  loading.value = true;
  loadError.value = false;
  try {
    data.value = await getChannelContribution({
      endTime: props.endTime,
      startTime: props.startTime,
    });
  } catch {
    data.value = [];
    loadError.value = true;
  } finally {
    loading.value = false;
  }
};

watch(() => [props.startTime, props.endTime], load);

onMounted(load);
</script>

<template>
  <ElCard v-loading="loading" shadow="hover" class="mb-4">
    <template #header>
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-purple-500"><PieChart /></ElIcon>
          <span>渠道贡献</span>
          <span v-if="loadError" class="text-xs font-normal text-orange-500">
            渠道数据暂不可用
          </span>
        </div>
        <ElTooltip
          content="按用户来源聚合；来源缺失归为未知。当前来源为微信小程序/H5/APP，不是广告投放归因"
          placement="top"
        >
          <ElIcon class="cursor-help text-gray-400"><InfoFilled /></ElIcon>
        </ElTooltip>
      </div>
    </template>

    <div
      v-if="!loading && data.length === 0"
      class="py-8 text-center text-sm text-gray-400"
    >
      暂无渠道数据
    </div>
    <div v-else class="space-y-3">
      <div
        v-for="item in data"
        :key="item.source"
        class="flex flex-wrap items-center gap-3"
      >
        <span class="w-24 truncate text-sm font-medium">
          {{ item.sourceName }}
        </span>
        <ElProgress
          :percentage="share(item.gmv)"
          :show-text="false"
          color="#8E44AD"
          class="min-w-32 flex-1"
        />
        <span class="w-16 text-right text-sm font-bold">
          {{ formatCurrency(item.gmv) }}
        </span>
        <span class="w-20 text-right text-xs text-gray-500">
          {{ formatNumber(item.buyerCount) }} 人
        </span>
        <span class="w-20 text-right text-xs text-gray-500">
          {{ formatNumber(item.orderCount) }} 单
        </span>
        <span class="w-14 text-right text-xs text-gray-400">
          {{ share(item.gmv).toFixed(1) }}%
        </span>
      </div>
    </div>
  </ElCard>
</template>
