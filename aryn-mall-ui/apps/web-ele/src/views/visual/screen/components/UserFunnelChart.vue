<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { VisualUserFunnelVO } from '#/api/visual/screen';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

interface Props {
  data: null | VisualUserFunnelVO;
  loading?: boolean;
}

const props = defineProps<Props>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const updateChart = () => {
  if (!props.data) return;

  const { registered, ordered, repurchased } = props.data;

  renderEcharts({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgb(10 40 80 / 90%)',
      borderColor: 'rgb(0 180 255 / 30%)',
      textStyle: { color: '#fff' },
    },
    legend: {
      data: ['注册用户', '下单用户', '复购用户'],
      textStyle: { color: 'rgb(126 200 255 / 80%)' },
      bottom: 10,
    },
    series: [
      {
        type: 'funnel',
        left: '10%',
        top: 20,
        bottom: 60,
        width: '80%',
        min: 0,
        max: registered,
        minSize: '20%',
        maxSize: '100%',
        sort: 'descending',
        gap: 4,
        label: {
          show: true,
          position: 'inside',
          formatter: (params: any) => {
            return `${params.name}\n${params.value.toLocaleString()}`;
          },
          color: '#fff',
          fontSize: 14,
          lineHeight: 20,
        },
        labelLine: {
          show: false,
        },
        itemStyle: {
          borderColor: 'rgb(10 40 80 / 80%)',
          borderWidth: 1,
        },
        data: [
          {
            name: '注册用户',
            value: registered,
            itemStyle: {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 1,
                y2: 0,
                colorStops: [
                  { offset: 0, color: 'rgb(0 180 255 / 60%)' },
                  { offset: 1, color: 'rgb(0 180 255 / 90%)' },
                ],
              },
            },
          },
          {
            name: '下单用户',
            value: ordered,
            itemStyle: {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 1,
                y2: 0,
                colorStops: [
                  { offset: 0, color: 'rgb(0 227 150 / 60%)' },
                  { offset: 1, color: 'rgb(0 227 150 / 90%)' },
                ],
              },
            },
          },
          {
            name: '复购用户',
            value: repurchased,
            itemStyle: {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 1,
                y2: 0,
                colorStops: [
                  { offset: 0, color: 'rgb(254 176 25 / 60%)' },
                  { offset: 1, color: 'rgb(254 176 25 / 90%)' },
                ],
              },
            },
          },
        ],
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
        用户转化漏斗
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
  background: #feb019;
  border-radius: 50%;
  box-shadow: 0 0 8px rgb(254 176 25 / 60%);
}

.chart-container {
  flex: 1;
  min-height: 0;
  padding: 8px;
}
</style>
