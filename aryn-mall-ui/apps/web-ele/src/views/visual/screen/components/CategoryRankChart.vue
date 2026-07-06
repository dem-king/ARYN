<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { VisualCategoryRankVO } from '#/api/visual/screen';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

interface Props {
  data: VisualCategoryRankVO[];
  loading?: boolean;
}

const props = defineProps<Props>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const formatValue = (val: number) => {
  if (val >= 10_000) {
    return `${(val / 10_000).toFixed(1)}万`;
  }
  return String(val);
};

const updateChart = () => {
  if (!props.data || props.data.length === 0) return;

  // 取 Top10，按销售额降序排列后反转（ECharts 横向柱状图从下到上）
  const sorted = [...props.data]
    .sort((a, b) => b.salesAmount - a.salesAmount)
    .slice(0, 10)
    .reverse();

  const names = sorted.map((item) => item.categoryName);
  const values = sorted.map((item) => item.salesAmount);

  renderEcharts({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgb(10 40 80 / 90%)',
      borderColor: 'rgb(0 180 255 / 30%)',
      textStyle: { color: '#fff' },
    },
    grid: {
      bottom: 20,
      containLabel: true,
      left: '3%',
      right: '8%',
      top: 20,
    },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: {
        color: 'rgb(126 200 255 / 60%)',
        fontSize: 11,
        formatter: (val: number) => formatValue(val),
      },
      splitLine: {
        lineStyle: { color: 'rgb(0 180 255 / 10%)' },
      },
    },
    yAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: 'rgb(0 180 255 / 30%)' } },
      axisLabel: {
        color: 'rgb(126 200 255 / 80%)',
        fontSize: 12,
        width: 80,
        overflow: 'truncate',
      },
    },
    series: [
      {
        type: 'bar',
        data: values,
        barWidth: 14,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: 'rgb(0 180 255 / 30%)' },
              { offset: 1, color: 'rgb(0 180 255 / 80%)' },
            ],
          },
        },
        label: {
          show: true,
          position: 'right',
          color: '#7ec8ff',
          fontSize: 11,
          formatter: (params: any) => formatValue(params.value as number),
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
  <div class="chart-panel">
    <div class="panel-header">
      <div class="panel-title">
        <span class="title-dot"></span>
        品类销售排行 Top10
      </div>
    </div>
    <div class="chart-container">
      <EchartsUI ref="chartRef" />
    </div>
  </div>
</template>

<style scoped>
.chart-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: linear-gradient(
    135deg,
    rgb(10 40 80 / 60%) 0%,
    rgb(5 20 50 / 80%) 100%
  );
  border: 1px solid rgb(0 180 255 / 15%);
  border-radius: 8px;
}

.panel-header {
  padding: 12px 16px 0;
}

.panel-title {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: #7ec8ff;
  letter-spacing: 1px;
}

.title-dot {
  width: 8px;
  height: 8px;
  background: #00e396;
  border-radius: 50%;
  box-shadow: 0 0 8px rgb(0 227 150 / 60%);
}

.chart-container {
  flex: 1;
  min-height: 0;
  padding: 8px;
}
</style>
