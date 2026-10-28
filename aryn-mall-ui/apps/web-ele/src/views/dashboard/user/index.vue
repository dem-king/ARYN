<script lang="ts" setup>
import type {
  UserCohortRetentionVO,
  UserRfmSegmentVO,
} from '#/api/order/order-statistics';

import { computed, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';
import { ElAlert } from 'element-plus';

import { getOrderDashboardStatistics } from '#/api/order/order-statistics';
import { getUserDashboardStatistics } from '#/api/user/user-statistics';

import { downloadCsv } from '../shared/csv';
import DashboardHeader from '../shared/DashboardHeader.vue';
import DashboardSkeleton from '../shared/DashboardSkeleton.vue';
import { settle } from '../shared/settle';
import { useDashboardRange } from '../shared/use-dashboard-range';
import UserCohortRetention from './components/UserCohortRetention.vue';
import UserConsumptionAnalysis from './components/UserConsumptionAnalysis.vue';
import UserGrowthTrend from './components/UserGrowthTrend.vue';
import UserRfmSegmentation from './components/UserRfmSegmentation.vue';
import UserScaleCards from './components/UserScaleCards.vue';

interface ConsumptionOverview {
  aov: number;
  perCapitaConsumption: number;
  totalAmount: number;
  totalOrders: number;
  transactionUsers: number;
}

const {
  compare,
  compareMode,
  currentRange,
  customEnd,
  customStart,
  previousRange,
  rangeKey,
} = useDashboardRange();

const loading = ref(false);
const hasLoaded = ref(false);
const lastUpdated = ref('');
const loadError = ref('');
const previousConsumptionOverview = ref<ConsumptionOverview | null>(null);
const rfmSegments = ref<UserRfmSegmentVO[]>([]);
const cohortRetention = ref<UserCohortRetentionVO[]>([]);

const scaleData = ref({
  totalUserCount: 0,
  todayNewUserCount: 0,
  sevenDayNewUserCount: 0,
  thirtyDayNewUserCount: 0,
  monthNewUserCount: 0,
  totalTransactionUsers: 0,
});

const trendData = ref({
  dates: [] as string[],
  newUsers: [] as number[],
  transactionUsers: [] as number[],
});

const consumptionData = ref<{
  amountData: Array<{ name: string; value: number }>;
  frequencyData: {
    categories: string[];
    values: number[];
  };
  overview: ConsumptionOverview;
}>({
  overview: {
    transactionUsers: 0,
    totalAmount: 0,
    perCapitaConsumption: 0,
    aov: 0,
    totalOrders: 0,
  },
  frequencyData: {
    categories: [],
    values: [],
  },
  amountData: [],
});

const rangeLabel = computed(
  () =>
    ({
      '30d': '近30天',
      '7d': '近7天',
      custom: '自定义',
      today: '今日',
    })[rangeKey.value] ?? '用户分析',
);

const deltaLabel = computed(() =>
  compareMode.value === 'year' ? '较去年同期' : '较上期',
);

const fetchData = useDebounceFn(async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const orderParams = {
      ...currentRange.value,
      includeUserAnalytics: true,
      ...(compare.value
        ? {
            compareEndTime: previousRange.value.endTime,
            compareStartTime: previousRange.value.startTime,
          }
        : {}),
    };
    const [orderRes, userRes] = await Promise.all([
      settle(getOrderDashboardStatistics(orderParams)),
      settle(getUserDashboardStatistics()),
    ]);

    const failed: string[] = [];

    if (orderRes.error) {
      failed.push('用户消费与增长');
    } else if (orderRes.data) {
      const dashboard = orderRes.data;

      if (Array.isArray(dashboard.userGrowthTrend)) {
        trendData.value = {
          dates: dashboard.userGrowthTrend.map((item) => item.timePoint),
          newUsers: dashboard.userGrowthTrend.map((item) => item.newUserCount),
          transactionUsers: dashboard.userGrowthTrend.map(
            (item) => item.transactingUserCount || 0,
          ),
        };
      }

      if (dashboard.consumption) {
        consumptionData.value.overview = {
          transactionUsers: dashboard.consumption.transactingUserCount || 0,
          totalAmount: dashboard.consumption.totalConsumptionAmount || 0,
          perCapitaConsumption:
            dashboard.consumption.averageConsumptionPerUser || 0,
          aov: dashboard.consumption.averageOrderValue || 0,
          totalOrders: dashboard.consumption.totalOrderCount || 0,
        };
      }

      if (dashboard.consumptionFrequency) {
        consumptionData.value.frequencyData = {
          categories: ['1次', '2-3次', '4-5次', '6次以上'],
          values: [
            dashboard.consumptionFrequency.oneTimeCount || 0,
            dashboard.consumptionFrequency.twoToThreeTimesCount || 0,
            dashboard.consumptionFrequency.fourToFiveTimesCount || 0,
            dashboard.consumptionFrequency.sixPlusTimesCount || 0,
          ],
        };
      }

      if (dashboard.consumptionAmount) {
        consumptionData.value.amountData = [
          {
            name: '0-100元',
            value: dashboard.consumptionAmount.amount0To100Count || 0,
          },
          {
            name: '100-500元',
            value: dashboard.consumptionAmount.amount100To500Count || 0,
          },
          {
            name: '500-2000元',
            value: dashboard.consumptionAmount.amount500To2000Count || 0,
          },
          {
            name: '2000元+',
            value: dashboard.consumptionAmount.amount2000PlusCount || 0,
          },
        ];
      }

      previousConsumptionOverview.value =
        dashboard.consumption && dashboard.compareConsumption
          ? {
              transactionUsers:
                dashboard.compareConsumption.transactingUserCount || 0,
              totalAmount:
                dashboard.compareConsumption.totalConsumptionAmount || 0,
              perCapitaConsumption:
                dashboard.compareConsumption.averageConsumptionPerUser || 0,
              aov: dashboard.compareConsumption.averageOrderValue || 0,
              totalOrders: dashboard.compareConsumption.totalOrderCount || 0,
            }
          : null;

      rfmSegments.value = Array.isArray(dashboard.rfmSegments)
        ? dashboard.rfmSegments
        : [];
      cohortRetention.value = Array.isArray(dashboard.cohortRetention)
        ? dashboard.cohortRetention
        : [];

      if (dashboard.accumulatedTransactionUserCount !== undefined) {
        scaleData.value.totalTransactionUsers =
          dashboard.accumulatedTransactionUserCount || 0;
      }
    }

    if (userRes.error) {
      failed.push('用户规模');
    } else if (userRes.data?.overview) {
      const overview = userRes.data.overview;
      scaleData.value = {
        totalUserCount: overview.totalUserCount || 0,
        todayNewUserCount: overview.todayNewUserCount || 0,
        sevenDayNewUserCount: overview.sevenDayNewUserCount || 0,
        thirtyDayNewUserCount: overview.thirtyDayNewUserCount || 0,
        monthNewUserCount: overview.monthNewUserCount || 0,
        totalTransactionUsers: scaleData.value.totalTransactionUsers,
      };
    }

    loadError.value =
      failed.length > 0 ? `部分数据加载失败：${failed.join('、')}` : '';
    lastUpdated.value = dayjs().format('HH:mm:ss');
    hasLoaded.value = true;
  } catch (error) {
    console.error('Failed to fetch user analytics data', error);
    loadError.value = '用户分析数据加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}, 100);

const handleExport = () => {
  const rows: unknown[][] = [
    ['用户规模', '数值'],
    ['用户总数', scaleData.value.totalUserCount],
    ['今日新增', scaleData.value.todayNewUserCount],
    ['7日新增', scaleData.value.sevenDayNewUserCount],
    ['30日新增', scaleData.value.thirtyDayNewUserCount],
    ['本月新增', scaleData.value.monthNewUserCount],
    ['累计成交用户', scaleData.value.totalTransactionUsers],
    [],
    ['消费概览', '数值'],
    ['成交用户数', consumptionData.value.overview.transactionUsers],
    ['总消费金额', consumptionData.value.overview.totalAmount],
    ['人均消费', consumptionData.value.overview.perCapitaConsumption],
    ['客单价', consumptionData.value.overview.aov],
    ['总订单数', consumptionData.value.overview.totalOrders],
    [],
    ['日期', '新增用户', '成交用户'],
    ...trendData.value.dates.map((date, index) => [
      date,
      trendData.value.newUsers[index] ?? 0,
      trendData.value.transactionUsers[index] ?? 0,
    ]),
    [],
    ['消费次数分布', '用户数'],
    ...consumptionData.value.frequencyData.categories.map((category, index) => [
      category,
      consumptionData.value.frequencyData.values[index] ?? 0,
    ]),
    [],
    ['消费金额分层', '用户数'],
    ...consumptionData.value.amountData.map((item) => [item.name, item.value]),
    [],
    ['用户价值分层（RFM）', '用户数', '累计成交金额'],
    ...rfmSegments.value.map((item) => [
      item.segment,
      item.userCount,
      item.gmv,
    ]),
    [],
    ['首购月份', '用户数', '当月', '+1月', '+2月', '+3月', '+4月', '+5月'],
    ...cohortRetention.value.map((item) => [
      item.cohortMonth,
      item.cohortSize,
      item.month0,
      item.month1,
      item.month2,
      item.month3,
      item.month4,
      item.month5,
    ]),
  ];
  downloadCsv(
    `用户分析_${rangeLabel.value}_${dayjs().format('YYYYMMDD_HHmm')}.csv`,
    rows,
  );
};

watch([rangeKey, customStart, customEnd, compare], () => {
  fetchData();
});

onMounted(() => {
  fetchData();
});
</script>

<template>
  <div class="absolute inset-0 overflow-y-auto">
    <DashboardHeader
      v-model:range-key="rangeKey"
      v-model:custom-start="customStart"
      v-model:custom-end="customEnd"
      v-model:compare="compare"
      v-model:compare-mode="compareMode"
      :loading="loading"
      :last-updated="lastUpdated"
      exportable
      @refresh="fetchData"
      @export="handleExport"
    />

    <!-- 页面留白收在这层，滚动根不留 padding，时间范围条才能完全贴顶吸住 -->
    <div class="px-4 pb-4">
      <ElAlert
        v-if="loadError"
        :title="loadError"
        type="warning"
        show-icon
        :closable="false"
        class="mb-4"
      />

      <DashboardSkeleton v-if="loading && !hasLoaded" />

      <template v-else>
        <UserScaleCards :data="scaleData" :loading="loading" />
        <UserGrowthTrend :data="trendData" :loading="loading" />

        <UserConsumptionAnalysis
          :overview="consumptionData.overview"
          :previous="previousConsumptionOverview"
          :delta-label="deltaLabel"
          :frequency-data="consumptionData.frequencyData"
          :amount-data="consumptionData.amountData"
          :loading="loading"
        />

        <UserRfmSegmentation :data="rfmSegments" :loading="loading" />

        <UserCohortRetention
          :data="cohortRetention"
          :end-month="currentRange.endTime.slice(0, 7)"
          :loading="loading"
        />
      </template>
    </div>
  </div>
</template>
