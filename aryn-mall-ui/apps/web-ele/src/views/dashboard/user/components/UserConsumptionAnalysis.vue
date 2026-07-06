<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref, watch } from 'vue';

import { CountTo } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { DataLine, Money, ShoppingCart, User } from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

const props = defineProps<{
  amountData: Array<{ name: string; value: number }>;
  frequencyData: {
    categories: string[];
    values: number[];
  };
  loading?: boolean;
  overview: {
    aov: number;
    perCapitaConsumption: number;
    totalAmount: number;
    totalOrders: number;
    transactionUsers: number;
  };
}>();

const freqChartRef = ref<EchartsUIType>();
const amountChartRef = ref<EchartsUIType>();
const { renderEcharts: renderFreqChart } = useEcharts(freqChartRef);
const { renderEcharts: renderAmountChart } = useEcharts(amountChartRef);

const updateCharts = () => {
  if (props.frequencyData && props.frequencyData.categories) {
    renderFreqChart({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        data: props.frequencyData.categories,
      },
      yAxis: {
        type: 'value',
        name: '用户数',
      },
      series: [
        {
          name: '用户数',
          type: 'bar',
          barWidth: '40%',
          data: props.frequencyData.values,
          itemStyle: { color: '#409EFF' },
        },
      ],
    });
  }

  if (props.amountData) {
    renderAmountChart({
      tooltip: {
        trigger: 'item',
      },
      legend: {
        bottom: '1%',
        left: 'center',
      },
      series: [
        {
          name: '消费金额区间',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 10,
            borderColor: 'var(--el-bg-color)',
            borderWidth: 2,
          },
          label: {
            show: false,
            position: 'center',
          },
          emphasis: {
            label: {
              show: true,
              fontSize: 20,
              fontWeight: 'bold',
            },
          },
          labelLine: {
            show: false,
          },
          data: props.amountData,
        },
      ],
    });
  }
};

watch(() => [props.frequencyData, props.amountData], updateCharts, {
  deep: true,
});

onMounted(() => {
  updateCharts();
});
</script>

<template>
  <ElCard shadow="hover" class="mb-4" v-loading="loading">
    <template #header>
      <div class="flex items-center gap-2 font-bold">
        <ElIcon class="text-orange-500"><DataLine /></ElIcon>
        <span>用户消费分析</span>
      </div>
    </template>

    <!-- Overview Metrics -->
    <div class="mb-6 grid grid-cols-2 gap-4 md:grid-cols-5">
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-orange-50 p-4 dark:bg-orange-900/20"
      >
        <div class="text-xl font-bold text-orange-500">
          <CountTo :end-val="overview.transactionUsers" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><User /></ElIcon> 成交用户数
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-red-50 p-4 dark:bg-red-900/20"
      >
        <div class="text-xl font-bold text-red-500">
          <CountTo :end-val="overview.totalAmount" prefix="¥" :decimals="2" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><Money /></ElIcon> 总消费金额
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-purple-50 p-4 dark:bg-purple-900/20"
      >
        <div class="text-xl font-bold text-purple-500">
          <CountTo
            :end-val="overview.perCapitaConsumption"
            prefix="¥"
            :decimals="2"
          />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><Money /></ElIcon> 人均消费
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-indigo-50 p-4 dark:bg-indigo-900/20"
      >
        <div class="text-xl font-bold text-indigo-500">
          <CountTo :end-val="overview.aov" prefix="¥" :decimals="2" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><Money /></ElIcon> 客单价
        </div>
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-blue-50 p-4 dark:bg-blue-900/20"
      >
        <div class="text-xl font-bold text-blue-500">
          <CountTo :end-val="overview.totalOrders" />
        </div>
        <div class="mt-1 flex items-center gap-1 text-xs text-gray-500">
          <ElIcon><ShoppingCart /></ElIcon> 总订单数
        </div>
      </div>
    </div>

    <!-- Charts -->
    <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <div class="flex h-96 flex-col">
        <div class="mb-2 text-center text-sm font-bold text-gray-600">
          用户消费次数分布
        </div>
        <div class="min-h-0 w-full flex-1">
          <EchartsUI ref="freqChartRef" />
        </div>
      </div>
      <div class="flex h-96 flex-col">
        <div class="mb-2 text-center text-sm font-bold text-gray-600">
          用户消费金额分层
        </div>
        <div class="min-h-0 w-full flex-1">
          <EchartsUI ref="amountChartRef" />
        </div>
      </div>
    </div>
  </ElCard>
</template>
