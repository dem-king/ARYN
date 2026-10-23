<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { TrendCharts } from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

import { areaGradient, CHART_PALETTE, formatNumber } from '../../shared/chart';

const props = defineProps<{
  data: {
    dates: string[];
    newUsers: number[];
    transactionUsers: number[];
  };
  loading?: boolean;
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const hasData = computed(() => props.data.dates.length > 0);

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
      valueFormatter: (value: any) => formatNumber(Number(value) || 0),
    },
    legend: {
      data: ['新增用户', '成交用户'],
    },
    xAxis: {
      type: 'category',
      data: props.data.dates,
      boundaryGap: false,
    },
    yAxis: {
      type: 'value',
      name: '人数',
      axisLabel: {
        formatter: (value: number) => formatNumber(value),
      },
    },
    series: [
      {
        name: '新增用户',
        type: 'line',
        smooth: true,
        data: props.data.newUsers,
        itemStyle: { color: CHART_PALETTE.primary },
        areaStyle: {
          color: areaGradient(CHART_PALETTE.primary),
        },
      },
      {
        name: '成交用户',
        type: 'line',
        smooth: true,
        data: props.data.transactionUsers,
        itemStyle: { color: CHART_PALETTE.success },
        areaStyle: {
          color: areaGradient(CHART_PALETTE.success),
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
  <ElCard shadow="hover" class="mb-4" v-loading="loading">
    <template #header>
      <div class="flex items-center gap-2 font-bold">
        <ElIcon class="text-blue-500"><TrendCharts /></ElIcon>
        <span>用户增长趋势</span>
      </div>
    </template>
    <div class="relative h-80 w-full">
      <EchartsUI ref="chartRef" />
      <div
        v-if="!loading && !hasData"
        class="absolute inset-0 flex items-center justify-center text-sm text-[var(--el-text-color-secondary)]"
      >
        暂无数据
      </div>
    </div>
  </ElCard>
</template>
