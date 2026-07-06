<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { DataAnalysis, InfoFilled } from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElTooltip } from 'element-plus';

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
      bottom: '8%',
      left: 'center',
    },
    series: [
      {
        avoidLabelOverlap: false,
        center: ['50%', '40%'],
        data: props.data,
        emphasis: {
          label: {
            fontSize: 20,
            fontWeight: 'bold',
            show: true,
          },
        },
        itemStyle: {
          borderColor: 'var(--el-bg-color)',
          borderRadius: 10,
          borderWidth: 2,
        },
        label: {
          position: 'center',
          show: false,
        },
        labelLine: {
          show: false,
        },
        name: '流量来源',
        radius: ['40%', '70%'],
        type: 'pie',
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
  <ElCard v-loading="loading" class="flex-1" shadow="hover">
    <template #header>
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-purple-500"><DataAnalysis /></ElIcon>
          <span>流量来源占比</span>
        </div>
        <ElTooltip
          content="该统计数据为全局数据，不随店铺和时间筛选变化"
          placement="top"
        >
          <ElIcon class="cursor-help text-gray-400"><InfoFilled /></ElIcon>
        </ElTooltip>
      </div>
    </template>
    <div class="h-64 w-full">
      <EchartsUI ref="chartRef" />
    </div>
  </ElCard>
</template>
