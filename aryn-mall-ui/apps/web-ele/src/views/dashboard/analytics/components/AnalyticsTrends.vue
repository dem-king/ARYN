<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { TrendCharts } from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

import {
  areaGradient,
  CHART_PALETTE,
  formatAxisCurrency,
  formatCurrency,
  formatNumber,
} from '../../shared/chart';

const props = defineProps<{
  data: {
    dates: string[];
    gmv: number[];
    orders: number[];
    paidBuyers: number[];
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
      formatter: (params: any) => {
        const items = Array.isArray(params) ? params : [params];
        const title = items[0]?.axisValueLabel ?? '';
        const lines = items.map((item: any) => {
          const value =
            item.seriesName === '成交额'
              ? formatCurrency(Number(item.value))
              : formatNumber(Number(item.value));
          return `${item.marker}${item.seriesName}: ${value}`;
        });
        return [title, ...lines].join('<br/>');
      },
    },
    legend: {
      data: ['成交额', '订单数', '支付买家数'],
    },
    xAxis: {
      type: 'category',
      data: props.data.dates,
      boundaryGap: false,
    },
    yAxis: [
      {
        type: 'value',
        name: '金额',
        position: 'left',
        axisLabel: {
          formatter: (value: number) => formatAxisCurrency(value),
        },
      },
      {
        type: 'value',
        name: '数量',
        position: 'right',
        axisLabel: {
          formatter: (value: number) => formatNumber(value),
        },
      },
    ],
    series: [
      {
        name: '成交额',
        type: 'line',
        smooth: true,
        data: props.data.gmv,
        yAxisIndex: 0,
        itemStyle: { color: CHART_PALETTE.primary },
        areaStyle: {
          color: areaGradient(CHART_PALETTE.primary),
        },
      },
      {
        name: '订单数',
        type: 'line',
        smooth: true,
        data: props.data.orders,
        yAxisIndex: 1,
        itemStyle: { color: CHART_PALETTE.success },
      },
      {
        name: '支付买家数',
        type: 'line',
        smooth: true,
        data: props.data.paidBuyers,
        yAxisIndex: 1,
        itemStyle: { color: CHART_PALETTE.warning },
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
        <span>交易趋势</span>
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
