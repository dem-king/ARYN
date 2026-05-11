<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Star } from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

const props = defineProps<{
  data: Array<{ name: string; value: number }>;
  loading?: boolean;
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const updateChart = () => {
  if (!props.data) return;

  renderEcharts({
    tooltip: {
      trigger: 'item',
    },
    legend: {
      top: '5%',
      left: 'center',
    },
    series: [
      {
        name: '用户价值分层',
        type: 'pie',
        radius: '50%',
        data: props.data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)',
          },
        },
      },
    ],
  });
};

watch(() => props.data, updateChart, { deep: true });

onMounted(() => {
  updateChart();
});
</script>

<template>
  <ElCard shadow="hover" class="h-full" v-loading="loading">
    <template #header>
      <div class="flex items-center gap-2 font-bold">
        <ElIcon class="text-yellow-500"><Star /></ElIcon>
        <span>用户价值分层</span>
      </div>
    </template>
    <div class="h-64 w-full">
      <EchartsUI ref="chartRef" />
    </div>
  </ElCard>
</template>
