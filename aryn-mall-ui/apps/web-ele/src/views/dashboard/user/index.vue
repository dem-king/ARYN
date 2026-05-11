<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';

import {
  getAccumulatedTransactionUserCount,
  getUserConsumptionAmount,
  getUserConsumptionAnalysis,
  getUserConsumptionFrequency,
  getUserGrowthTrend,
} from '#/api/order/order-statistics';
import { getUserOverview } from '#/api/user/user-statistics';

import AnalyticsHeader from '../analytics/components/AnalyticsHeader.vue';
import UserConsumptionAnalysis from './components/UserConsumptionAnalysis.vue';
import UserGrowthTrend from './components/UserGrowthTrend.vue';
import UserScaleCards from './components/UserScaleCards.vue';

const timeRange = ref<string>('30d');
const loading = ref(false);

// Reactive data
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

const consumptionData = ref({
  overview: {
    transactionUsers: 0,
    totalAmount: 0,
    perCapitaConsumption: 0,
    aov: 0,
    totalOrders: 0,
  },
  frequencyData: {
    categories: [] as string[],
    values: [] as number[],
  },
  amountData: [] as Array<{ name: string; value: number }>,
});

const fetchData = useDebounceFn(async () => {
  loading.value = true;
  try {
    const format = 'YYYY-MM-DD HH:mm:ss';
    let startTime = '';
    let endTime = dayjs().format(format);

    switch (timeRange.value) {
      case '7d': {
        startTime = dayjs().subtract(7, 'day').startOf('day').format(format);
        break;
      }
      case '30d': {
        startTime = dayjs().subtract(30, 'day').startOf('day').format(format);
        break;
      }
      case 'today': {
        startTime = dayjs().startOf('day').format(format);
        break;
      }
      // 保留兼容性，如果还有其他地方用到 yesterday
      case 'yesterday': {
        startTime = dayjs().subtract(1, 'day').startOf('day').format(format);
        endTime = dayjs().subtract(1, 'day').endOf('day').format(format);
        break;
      }
    }

    const commonParams = {
      startTime,
      endTime,
    };

    const [
      trendRes,
      overviewRes,
      consumptionRes,
      consumptionFrequencyRes,
      consumptionAmountRes,
      accumulatedTransactionUserCount,
    ] = await Promise.all([
      getUserGrowthTrend(commonParams),
      getUserOverview(commonParams),
      getUserConsumptionAnalysis(commonParams),
      getUserConsumptionFrequency(commonParams),
      getUserConsumptionAmount(commonParams),
      getAccumulatedTransactionUserCount(commonParams),
    ]);

    const trendList = trendRes || [];
    const overview = overviewRes || {};

    scaleData.value = {
      totalUserCount: overview.totalUserCount || 0,
      todayNewUserCount: overview.todayNewUserCount || 0,
      sevenDayNewUserCount: overview.sevenDayNewUserCount || 0,
      thirtyDayNewUserCount: overview.thirtyDayNewUserCount || 0,
      monthNewUserCount: overview.monthNewUserCount || 0,
      totalTransactionUsers: accumulatedTransactionUserCount || 0,
    };

    trendData.value = {
      dates: trendList.map((item: any) => item.timePoint),
      newUsers: trendList.map((item: any) => item.newUserCount),
      transactionUsers: trendList.map(
        (item: any) => item.transactingUserCount || 0,
      ),
    };

    consumptionData.value.overview = consumptionRes
      ? {
          transactionUsers: consumptionRes.transactingUserCount || 0,
          totalAmount: consumptionRes.totalConsumptionAmount || 0,
          perCapitaConsumption: consumptionRes.averageConsumptionPerUser || 0,
          aov: consumptionRes.averageOrderValue || 0,
          totalOrders: consumptionRes.totalOrderCount || 0,
        }
      : {
          transactionUsers: 0,
          totalAmount: 0,
          perCapitaConsumption: 0,
          aov: 0,
          totalOrders: 0,
        };

    consumptionData.value.frequencyData = consumptionFrequencyRes
      ? {
          categories: ['1次', '2-3次', '4-5次', '6次以上'],
          values: [
            consumptionFrequencyRes.oneTimeCount || 0,
            consumptionFrequencyRes.twoToThreeTimesCount || 0,
            consumptionFrequencyRes.fourToFiveTimesCount || 0,
            consumptionFrequencyRes.sixPlusTimesCount || 0,
          ],
        }
      : {
          categories: ['1次', '2-3次', '4-5次', '6次以上'],
          values: [0, 0, 0, 0],
        };

    consumptionData.value.amountData = consumptionAmountRes
      ? [
          {
            name: '0-100元',
            value: consumptionAmountRes.amount0To100Count || 0,
          },
          {
            name: '100-500元',
            value: consumptionAmountRes.amount100To500Count || 0,
          },
          {
            name: '500-2000元',
            value: consumptionAmountRes.amount500To2000Count || 0,
          },
          {
            name: '2000元+',
            value: consumptionAmountRes.amount2000PlusCount || 0,
          },
        ]
      : [
          { name: '0-100元', value: 0 },
          { name: '100-500元', value: 0 },
          { name: '500-2000元', value: 0 },
          { name: '2000元+', value: 0 },
        ];
  } catch (error) {
    console.error('Failed to fetch user analytics data', error);
    loading.value = false;
  } finally {
    loading.value = false;
  }
}, 100);

watch([timeRange], () => {
  fetchData();
});

onMounted(() => {
  fetchData();
});
</script>

<template>
  <div class="p-4">
    <AnalyticsHeader
      v-model:time-range="timeRange"
      @change="fetchData"
      @init="fetchData"
    />

    <!-- 一、用户规模 -->
    <UserScaleCards :data="scaleData" :loading="loading" />
    <UserGrowthTrend :data="trendData" :loading="loading" />

    <!-- 二、用户消费 -->
    <UserConsumptionAnalysis
      :overview="consumptionData.overview"
      :frequency-data="consumptionData.frequencyData"
      :amount-data="consumptionData.amountData"
      :loading="loading"
    />
  </div>
</template>
