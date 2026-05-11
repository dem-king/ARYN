<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { TrendCharts } from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

const props = defineProps<{
  data: {
    dates: string[];
    pv: number[];
    uv: number[];
  };
  loading?: boolean;
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const updateChart = () => {
  if (!props.data || !props.data.dates) return;

  renderEcharts({
    grid: {
      bottom: 0,
      containLabel: true,
      left: '1%',
      right: '1%',
      top: '10%',
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
      },
    },
    legend: {
      data: ['浏览量(PV)', '访客数(UV)'],
    },
    xAxis: {
      type: 'category',
      data: props.data.dates,
      boundaryGap: false,
    },
    yAxis: [
      {
        type: 'value',
        name: '浏览量',
        position: 'left',
      },
      {
        type: 'value',
        name: '访客数',
        position: 'right',
      },
    ],
    series: [
      {
        name: '浏览量(PV)',
        type: 'line',
        smooth: true,
        data: props.data.pv,
        yAxisIndex: 0,
        itemStyle: { color: '#409EFF' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64,158,255, 0.5)' },
              { offset: 1, color: 'rgba(64,158,255, 0)' },
            ],
          },
        },
      },
      {
        name: '访客数(UV)',
        type: 'line',
        smooth: true,
        data: props.data.uv,
        yAxisIndex: 1,
        itemStyle: { color: '#67C23A' },
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
  <ElCard shadow="hover" class="mb-4" v-loading="loading">
    <template #header>
      <div class="flex items-center gap-2 font-bold">
        <ElIcon class="text-blue-500"><TrendCharts /></ElIcon>
        <span>商品访问趋势</span>
        <span class="ml-2 text-xs font-normal text-gray-400">
          (统计周期内数据)
        </span>
      </div>
    </template>
    <div class="h-80 w-full">
      <EchartsUI ref="chartRef" />
    </div>
  </ElCard>
</template>
