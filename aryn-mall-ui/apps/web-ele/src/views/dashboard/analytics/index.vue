<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';

import {
  getOrderOverview,
  getOrderStatusOverview,
  getOrderTradeStatistics,
  getOrderTradeTrend,
  getProductSalesTop10,
  getRefundRateTop10,
  getUserStatistics,
} from '#/api/order/order-statistics';
import { getUserSourceStatistics } from '#/api/user/user-statistics';

import AnalyticsCards from './components/AnalyticsCards.vue';
import AnalyticsHeader from './components/AnalyticsHeader.vue';
import AnalyticsOrderStatus from './components/AnalyticsOrderStatus.vue';
import AnalyticsProductRanking from './components/AnalyticsProductRanking.vue';
import AnalyticsService from './components/AnalyticsService.vue';
import AnalyticsTraffic from './components/AnalyticsTraffic.vue';
import AnalyticsTrends from './components/AnalyticsTrends.vue';
import AnalyticsUser from './components/AnalyticsUser.vue';

interface RankingItem {
  name: string;
  value: number;
}

interface TrendsData {
  dates: string[];
  gmv: number[];
  orders: number[];
  paidBuyers: number[];
}

interface UserSourceItem {
  count?: number;
  title: string;
}

const timeRange = ref<string>('30d');
const loading = ref(false);

// Reactive data objects for each component
const cardsData = ref({
  gmv: 0,
  paidOrders: 0,
  paidBuyers: 0,
  aov: 0,
  afterSalesOrders: 0,
  refundAmount: 0,
});

const trendsData = ref<TrendsData>({
  dates: [],
  gmv: [],
  orders: [],
  paidBuyers: [],
});

const orderStatusData = ref({
  pendingPayment: 0,
  pendingShipment: 0,
  shipped: 0,
  completed: 0,
  afterSales: 0,
  refunded: 0,
});

const productRankingData = ref<{
  refundTop10: RankingItem[];
  salesTop10: RankingItem[];
  zeroSales: number;
}>({
  salesTop10: [],
  refundTop10: [],
  zeroSales: 0,
});

const userData = ref({
  transactingUsers: 0,
  newUsers: 0,
  repurchaseRate: 0,
  returningCustomerShare: 0,
});

const riskData = ref({
  applications: 0,
  successCount: 0,
  overdueShipment: 0,
  negativeReviews: 0,
});

const trafficData = ref<Array<{ name: string; value: number }>>([]);

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
      tradeStats,
      trendStats,
      refundStats,
      userStats,
      overviewStats,
      salesTop10Stats,
      orderStatusStats,
      userSourceStats,
    ] = await Promise.all([
      getOrderTradeStatistics(commonParams),
      getOrderTradeTrend(commonParams),
      getRefundRateTop10(commonParams),
      getUserStatistics(commonParams),
      getOrderOverview(commonParams),
      getProductSalesTop10(commonParams),
      getOrderStatusOverview(commonParams),
      getUserSourceStatistics(commonParams),
    ]);

    // 更新交易数据卡片
    cardsData.value = tradeStats
      ? {
          gmv: tradeStats.gmv || 0,
          paidOrders: tradeStats.payOrderCount || 0,
          paidBuyers: tradeStats.payBuyerCount || 0,
          aov: tradeStats.averageTicketSize || 0,
          afterSalesOrders: tradeStats.afterSalesOrderCount || 0,
          refundAmount: tradeStats.refundAmount || 0,
        }
      : {
          gmv: 0,
          paidOrders: 0,
          paidBuyers: 0,
          aov: 0,
          afterSalesOrders: 0,
          refundAmount: 0,
        };

    // 更新趋势图表
    trendsData.value =
      trendStats && Array.isArray(trendStats)
        ? {
            dates: trendStats.map((item) => item.timePoint),
            gmv: trendStats.map((item) => item.gmv),
            orders: trendStats.map((item) => item.payOrderCount),
            paidBuyers: trendStats.map((item) => item.payBuyerCount),
          }
        : {
            dates: [],
            gmv: [],
            orders: [],
            paidBuyers: [],
          };

    // 更新退款率 Top10
    productRankingData.value = {
      salesTop10:
        salesTop10Stats && Array.isArray(salesTop10Stats)
          ? salesTop10Stats.map((item) => ({
              name: item.spuName,
              value: item.salesCount,
            }))
          : [],
      refundTop10:
        refundStats && Array.isArray(refundStats)
          ? refundStats.map((item) => ({
              name: item.spuName,
              value: item.refundRate,
            }))
          : [],
      zeroSales: 0,
    };

    // 更新用户统计
    userData.value = userStats
      ? {
          transactingUsers: userStats.payBuyerCount || 0,
          newUsers: userStats.newUserCount || 0,
          repurchaseRate: userStats.repurchaseRate || 0,
          returningCustomerShare: userStats.oldCustomerGmvRate || 0,
        }
      : {
          transactingUsers: 0,
          newUsers: 0,
          repurchaseRate: 0,
          returningCustomerShare: 0,
        };

    // 更新服务与风控统计
    riskData.value = overviewStats
      ? {
          applications: overviewStats.afterSalesCount || 0,
          successCount: overviewStats.refundCompletedCount || 0,
          overdueShipment: overviewStats.unshippedTimeoutCount || 0,
          negativeReviews: overviewStats.negativeAppraisalCount || 0,
        }
      : {
          applications: 0,
          successCount: 0,
          overdueShipment: 0,
          negativeReviews: 0,
        };

    // 更新订单状态总览
    orderStatusData.value = orderStatusStats
      ? {
          pendingPayment: orderStatusStats.waitingForPaymentCount || 0,
          pendingShipment: orderStatusStats.waitingForDeliveryCount || 0,
          shipped: orderStatusStats.shippedCount || 0,
          completed: orderStatusStats.completedCount || 0,
          afterSales: orderStatusStats.afterSalesCount || 0,
          refunded: orderStatusStats.refundedCount || 0,
        }
      : {
          pendingPayment: 0,
          pendingShipment: 0,
          shipped: 0,
          completed: 0,
          afterSales: 0,
          refunded: 0,
        };

    trafficData.value = userSourceStats
      ? (userSourceStats as UserSourceItem[]).map((item) => ({
          name: item.title,
          value: item.count || 0,
        }))
      : [];
  } catch (error) {
    console.error('Failed to fetch analytics data', error);
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

    <AnalyticsCards :data="cardsData" :loading="loading" />

    <AnalyticsTrends :data="trendsData" :loading="loading" />

    <div class="mb-4 grid grid-cols-1 gap-4 md:grid-cols-2">
      <AnalyticsOrderStatus :data="orderStatusData" :loading="loading" />
      <AnalyticsService :data="riskData" :loading="loading" />

      <AnalyticsProductRanking
        :rankings="productRankingData"
        :loading="loading"
      />
      <div class="flex flex-col gap-4">
        <AnalyticsUser :data="userData" :loading="loading" />
        <AnalyticsTraffic :data="trafficData" :loading="loading" />
      </div>
    </div>
  </div>
</template>
