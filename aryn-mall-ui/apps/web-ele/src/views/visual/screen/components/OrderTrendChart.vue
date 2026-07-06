<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { VisualOrderTrendVO } from '#/api/visual/screen';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

interface Props {
  data: VisualOrderTrendVO[];
  loading?: boolean;
}

const props = defineProps<Props>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const updateChart = () => {
  if (!props.data || props.data.length === 0) return;

  const dates = props.data.map((item) => item.date);
  const orders = props.data.map((item) => item.orderCount);
  const gmv = props.data.map((item) => item.gmv);

  renderEcharts({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgb(10 40 80 / 90%)',
      borderColor: 'rgb(0 180 255 / 30%)',
      textStyle: { color: '#fff' },
    },
    legend: {
      data: ['成交额', '订单数'],
      textStyle: { color: 'rgb(126 200 255 / 80%)' },
      top: 10,
    },
    grid: {
      bottom: 30,
      containLabel: true,
      left: '3%',
      right: '3%',
      top: 50,
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: 'rgb(0 180 255 / 30%)' } },
      axisLabel: { color: 'rgb(126 200 255 / 60%)', fontSize: 11 },
      splitLine: { show: false },
    },
    yAxis: [
      {
        type: 'value',
        name: '金额',
        nameTextStyle: { color: 'rgb(126 200 255 / 60%)' },
        axisLine: { show: false },
        axisLabel: { color: 'rgb(126 200 255 / 60%)', fontSize: 11 },
        splitLine: {
          lineStyle: { color: 'rgb(0 180 255 / 10%)' },
        },
      },
      {
        type: 'value',
        name: '数量',
        nameTextStyle: { color: 'rgb(126 200 255 / 60%)' },
        axisLine: { show: false },
        axisLabel: { color: 'rgb(126 200 255 / 60%)', fontSize: 11 },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '成交额',
        type: 'line',
        smooth: true,
        data: gmv,
        yAxisIndex: 0,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#00b4ff', width: 2 },
        itemStyle: { color: '#00b4ff' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgb(0 180 255 / 35%)' },
              { offset: 1, color: 'rgb(0 180 255 / 0%)' },
            ],
          },
        },
      },
      {
        name: '订单数',
        type: 'line',
        smooth: true,
        data: orders,
        yAxisIndex: 1,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#00e396', width: 2 },
        itemStyle: { color: '#00e396' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgb(0 227 150 / 25%)' },
              { offset: 1, color: 'rgb(0 227 150 / 0%)' },
            ],
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
  <div class="chart-panel">
    <div class="panel-header">
      <div class="panel-title">
        <span class="title-dot"></span>
        订单趋势
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
  background: #00b4ff;
  border-radius: 50%;
  box-shadow: 0 0 8px rgb(0 180 255 / 60%);
}

.chart-container {
  flex: 1;
  min-height: 0;
  padding: 8px;
}
</style>
