<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, onMounted, ref, watch } from 'vue';

import { CountTo } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { DataLine, Money, ShoppingCart, User } from '@element-plus/icons-vue';
import { ElCard, ElIcon } from 'element-plus';

import { CHART_PALETTE, formatNumber } from '../../shared/chart';
import { calculateDelta } from '../../shared/compare';
import DeltaBadge from '../../shared/DeltaBadge.vue';

interface ConsumptionOverview {
  aov: number;
  perCapitaConsumption: number;
  totalAmount: number;
  totalOrders: number;
  transactionUsers: number;
}

const props = withDefaults(
  defineProps<{
    amountData: Array<{ name: string; value: number }>;
    deltaLabel?: string;
    frequencyData: {
      categories: string[];
      values: number[];
    };
    loading?: boolean;
    overview: ConsumptionOverview;
    previous?: ConsumptionOverview | null;
  }>(),
  {
    deltaLabel: '较上期',
    loading: false,
    previous: null,
  },
);

const delta = (key: keyof ConsumptionOverview) =>
  calculateDelta(props.overview[key], props.previous?.[key]);

const hasFrequencyData = computed(
  () =>
    props.frequencyData.categories.length > 0 &&
    props.frequencyData.values.some((value) => value > 0),
);

const hasAmountData = computed(() =>
  props.amountData.some((item) => item.value > 0),
);

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
        valueFormatter: (value: any) => formatNumber(Number(value) || 0),
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
        axisLabel: {
          formatter: (value: number) => formatNumber(value),
        },
      },
      series: [
        {
          name: '用户数',
          type: 'bar',
          barWidth: '40%',
          data: props.frequencyData.values,
          itemStyle: { color: CHART_PALETTE.primary },
        },
      ],
    });
  }

  if (props.amountData) {
    renderAmountChart({
      tooltip: {
        trigger: 'item',
        valueFormatter: (value: any) => formatNumber(Number(value) || 0),
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
            borderColor: 'transparent',
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
        <div
          class="mt-1 flex items-center gap-1 text-xs text-[var(--el-text-color-secondary)]"
        >
          <ElIcon><User /></ElIcon> 成交用户数
        </div>
        <DeltaBadge :delta="delta('transactionUsers')" :label="deltaLabel" />
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-red-50 p-4 dark:bg-red-900/20"
      >
        <div class="text-xl font-bold text-red-500">
          <CountTo :end-val="overview.totalAmount" prefix="¥" :decimals="2" />
        </div>
        <div
          class="mt-1 flex items-center gap-1 text-xs text-[var(--el-text-color-secondary)]"
        >
          <ElIcon><Money /></ElIcon> 总消费金额
        </div>
        <DeltaBadge :delta="delta('totalAmount')" :label="deltaLabel" />
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
        <div
          class="mt-1 flex items-center gap-1 text-xs text-[var(--el-text-color-secondary)]"
        >
          <ElIcon><Money /></ElIcon> 人均消费
        </div>
        <DeltaBadge
          :delta="delta('perCapitaConsumption')"
          :label="deltaLabel"
        />
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-indigo-50 p-4 dark:bg-indigo-900/20"
      >
        <div class="text-xl font-bold text-indigo-500">
          <CountTo :end-val="overview.aov" prefix="¥" :decimals="2" />
        </div>
        <div
          class="mt-1 flex items-center gap-1 text-xs text-[var(--el-text-color-secondary)]"
        >
          <ElIcon><Money /></ElIcon> 客单价
        </div>
        <DeltaBadge :delta="delta('aov')" :label="deltaLabel" />
      </div>
      <div
        class="flex flex-col items-center justify-center rounded-lg bg-blue-50 p-4 dark:bg-blue-900/20"
      >
        <div class="text-xl font-bold text-blue-500">
          <CountTo :end-val="overview.totalOrders" />
        </div>
        <div
          class="mt-1 flex items-center gap-1 text-xs text-[var(--el-text-color-secondary)]"
        >
          <ElIcon><ShoppingCart /></ElIcon> 总订单数
        </div>
        <DeltaBadge :delta="delta('totalOrders')" :label="deltaLabel" />
      </div>
    </div>

    <!-- Charts -->
    <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <div class="flex h-96 flex-col">
        <div
          class="mb-2 text-center text-sm font-bold text-[var(--el-text-color-regular)]"
        >
          用户消费次数分布
        </div>
        <div class="relative min-h-0 w-full flex-1">
          <EchartsUI ref="freqChartRef" />
          <div
            v-if="!loading && !hasFrequencyData"
            class="absolute inset-0 flex items-center justify-center text-sm text-[var(--el-text-color-secondary)]"
          >
            暂无数据
          </div>
        </div>
      </div>
      <div class="flex h-96 flex-col">
        <div
          class="mb-2 text-center text-sm font-bold text-[var(--el-text-color-regular)]"
        >
          用户消费金额分层
        </div>
        <div class="relative min-h-0 w-full flex-1">
          <EchartsUI ref="amountChartRef" />
          <div
            v-if="!loading && !hasAmountData"
            class="absolute inset-0 flex items-center justify-center text-sm text-[var(--el-text-color-secondary)]"
          >
            暂无数据
          </div>
        </div>
      </div>
    </div>
  </ElCard>
</template>
