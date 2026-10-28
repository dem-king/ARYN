<script lang="ts" setup>
import type {
  OrderFunnelVO,
  TrafficFunnelVO,
} from '#/api/order/order-statistics';

import { computed, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';
import { ElAlert } from 'element-plus';

import { getOrderDashboardStatistics } from '#/api/order/order-statistics';
import { getUserDashboardStatistics } from '#/api/user/user-statistics';
import { formatPercent } from '#/utils/percent';

import { formatNumber } from '../shared/chart';
import { calculateDelta } from '../shared/compare';
import { downloadCsv } from '../shared/csv';
import DashboardHeader from '../shared/DashboardHeader.vue';
import DashboardInsights from '../shared/DashboardInsights.vue';
import DashboardSkeleton from '../shared/DashboardSkeleton.vue';
import { settle } from '../shared/settle';
import { useDashboardRange } from '../shared/use-dashboard-range';
import AnalyticsCards from './components/AnalyticsCards.vue';
import AnalyticsFunnel from './components/AnalyticsFunnel.vue';
import AnalyticsOrderStatus from './components/AnalyticsOrderStatus.vue';
import AnalyticsProductRanking from './components/AnalyticsProductRanking.vue';
import AnalyticsService from './components/AnalyticsService.vue';
import AnalyticsTraffic from './components/AnalyticsTraffic.vue';
import AnalyticsTrends from './components/AnalyticsTrends.vue';
import AnalyticsUser from './components/AnalyticsUser.vue';
import ChannelContribution from './components/ChannelContribution.vue';
import DashboardTarget from './components/DashboardTarget.vue';
import TrafficFunnel from './components/TrafficFunnel.vue';

interface CardsData {
  afterSalesOrders: number;
  aov: number;
  gmv: number;
  paidBuyers: number;
  paidOrders: number;
  refundAmount: number;
}

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
const previousCardsData = ref<CardsData | null>(null);

const cardsData = ref<CardsData>({
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
}>({
  salesTop10: [],
  refundTop10: [],
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
const funnelData = ref<null | OrderFunnelVO>(null);
const trafficFunnelData = ref<null | TrafficFunnelVO>(null);

const rangeLabel = computed(
  () =>
    ({
      '30d': '近30天',
      '7d': '近7天',
      custom: '自定义',
      today: '今日',
    })[rangeKey.value] ?? '经营概览',
);

const deltaLabel = computed(() =>
  compareMode.value === 'year' ? '较去年同期' : '较上期',
);

const gmvDelta = computed(() =>
  previousCardsData.value
    ? calculateDelta(cardsData.value.gmv, previousCardsData.value.gmv)
    : null,
);
const refundDelta = computed(() =>
  previousCardsData.value
    ? calculateDelta(
        cardsData.value.refundAmount,
        previousCardsData.value.refundAmount,
      )
    : null,
);

const insights = computed(() => {
  const items: Array<{
    description: string;
    title: string;
    to?: string;
    tone: 'danger' | 'info' | 'success' | 'warning';
  }> = [];

  if (orderStatusData.value.pendingShipment > 0) {
    items.push({
      description: `${formatNumber(orderStatusData.value.pendingShipment)} 单待发货，建议及时处理避免超时。`,
      title: '待发货积压',
      tone: 'warning',
      to: '/order/order-info?status=2',
    });
  }
  if (riskData.value.overdueShipment > 0) {
    items.push({
      description: `${formatNumber(riskData.value.overdueShipment)} 单已超过 24 小时未发货。`,
      title: '超时未发货',
      tone: 'danger',
      to: '/order/order-info?status=2',
    });
  }
  if (orderStatusData.value.afterSales > 0) {
    items.push({
      description: `${formatNumber(orderStatusData.value.afterSales)} 单售后待审核。`,
      title: '售后待审核',
      tone: 'warning',
      to: '/order/order-refund?status=1',
    });
  }
  if (gmvDelta.value !== null && gmvDelta.value < -10) {
    items.push({
      description: `较对比期下降 ${Math.abs(gmvDelta.value).toFixed(1)}%，建议检查流量与退款。`,
      title: '成交额下滑',
      tone: 'danger',
    });
  }
  if (refundDelta.value !== null && refundDelta.value > 20) {
    items.push({
      description: `较对比期上升 ${refundDelta.value.toFixed(1)}%，建议关注退款原因。`,
      title: '退款金额上升',
      tone: 'warning',
    });
  }
  if (riskData.value.negativeReviews > 0) {
    items.push({
      description: `${formatNumber(riskData.value.negativeReviews)} 条负面评价待关注。`,
      title: '负面评价',
      tone: 'info',
    });
  }

  return items.length > 0
    ? items
    : [
        {
          description: '当前没有需要立即处理的异常。',
          title: '经营指标平稳',
          tone: 'success' as const,
        },
      ];
});

const fetchData = useDebounceFn(async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const orderParams = {
      ...currentRange.value,
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
      failed.push('经营指标');
    } else if (orderRes.data) {
      const dashboard = orderRes.data;
      const trade = dashboard.trade;
      if (trade) {
        cardsData.value = {
          gmv: trade.gmv || 0,
          paidOrders: trade.payOrderCount || 0,
          paidBuyers: trade.payBuyerCount || 0,
          aov: trade.averageTicketSize || 0,
          afterSalesOrders: trade.afterSalesOrderCount || 0,
          refundAmount: trade.refundAmount || 0,
        };
      }

      const compareTrade = dashboard.compareTrade;
      previousCardsData.value =
        trade && compareTrade
          ? {
              gmv: compareTrade.gmv || 0,
              paidOrders: compareTrade.payOrderCount || 0,
              paidBuyers: compareTrade.payBuyerCount || 0,
              aov: compareTrade.averageTicketSize || 0,
              afterSalesOrders: compareTrade.afterSalesOrderCount || 0,
              refundAmount: compareTrade.refundAmount || 0,
            }
          : null;

      if (Array.isArray(dashboard.tradeTrend)) {
        trendsData.value = {
          dates: dashboard.tradeTrend.map((item) => item.timePoint),
          gmv: dashboard.tradeTrend.map((item) => item.gmv),
          orders: dashboard.tradeTrend.map((item) => item.payOrderCount),
          paidBuyers: dashboard.tradeTrend.map((item) => item.payBuyerCount),
        };
      }

      productRankingData.value = {
        refundTop10: Array.isArray(dashboard.refundTop10)
          ? dashboard.refundTop10.map((item) => ({
              name: item.spuName,
              value: item.refundRate,
            }))
          : [],
        salesTop10: Array.isArray(dashboard.salesTop10)
          ? dashboard.salesTop10.map((item) => ({
              name: item.spuName,
              value: item.salesCount,
            }))
          : [],
      };

      if (dashboard.user) {
        userData.value = {
          transactingUsers: dashboard.user.payBuyerCount || 0,
          newUsers: dashboard.user.newUserCount || 0,
          repurchaseRate: dashboard.user.repurchaseRate || 0,
          returningCustomerShare: dashboard.user.oldCustomerGmvRate || 0,
        };
      }

      if (dashboard.overview) {
        riskData.value = {
          applications: dashboard.overview.afterSalesCount || 0,
          successCount: dashboard.overview.refundCompletedCount || 0,
          overdueShipment: dashboard.overview.unshippedTimeoutCount || 0,
          negativeReviews: dashboard.overview.negativeAppraisalCount || 0,
        };
      }

      if (dashboard.status) {
        orderStatusData.value = {
          pendingPayment: dashboard.status.waitingForPaymentCount || 0,
          pendingShipment: dashboard.status.waitingForDeliveryCount || 0,
          shipped: dashboard.status.shippedCount || 0,
          completed: dashboard.status.completedCount || 0,
          afterSales: dashboard.status.afterSalesCount || 0,
          refunded: dashboard.status.refundedCount || 0,
        };
      }

      funnelData.value = dashboard.funnel || null;
      trafficFunnelData.value = dashboard.trafficFunnel || null;
    }

    if (userRes.error) {
      failed.push('用户来源');
    } else if (userRes.data) {
      trafficData.value = Array.isArray(userRes.data.sourceStatistics)
        ? userRes.data.sourceStatistics.map((item) => ({
            name: item.title,
            value: item.count || 0,
          }))
        : [];
    }

    loadError.value =
      failed.length > 0 ? `部分数据加载失败：${failed.join('、')}` : '';
    lastUpdated.value = dayjs().format('HH:mm:ss');
    hasLoaded.value = true;
  } catch (error) {
    console.error('Failed to fetch analytics data', error);
    loadError.value = '概览数据加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}, 100);

const handleExport = () => {
  const rows: unknown[][] = [
    ['指标', '数值'],
    ['成交额', cardsData.value.gmv],
    ['支付订单数', cardsData.value.paidOrders],
    ['支付买家数', cardsData.value.paidBuyers],
    ['客单价', cardsData.value.aov],
    ['售后订单数', cardsData.value.afterSalesOrders],
    ['退款金额', cardsData.value.refundAmount],
    [],
    ['日期', '成交额', '支付订单数', '支付买家数'],
    ...trendsData.value.dates.map((date, index) => [
      date,
      trendsData.value.gmv[index] ?? 0,
      trendsData.value.orders[index] ?? 0,
      trendsData.value.paidBuyers[index] ?? 0,
    ]),
    [],
    ['订单状态', '数量'],
    ['待付款', orderStatusData.value.pendingPayment],
    ['待发货', orderStatusData.value.pendingShipment],
    ['已发货', orderStatusData.value.shipped],
    ['已完成', orderStatusData.value.completed],
    ['售后待审核', orderStatusData.value.afterSales],
    ['已退款', orderStatusData.value.refunded],
    [],
    ['交易履约漏斗', '订单数'],
    ['下单', funnelData.value?.createdCount || 0],
    ['支付', funnelData.value?.paidCount || 0],
    ['发货', funnelData.value?.deliveredCount || 0],
    ['完成', funnelData.value?.completedCount || 0],
    ['退款（已到账）', funnelData.value?.refundedCount || 0],
    [],
    ['流量转化漏斗', '用户数'],
    ['访问用户', trafficFunnelData.value?.visitorCount || 0],
    ['下单用户', trafficFunnelData.value?.createdBuyerCount || 0],
    ['支付用户', trafficFunnelData.value?.paidBuyerCount || 0],
    [],
    ['客户数据', '数值'],
    ['成交用户数', userData.value.transactingUsers],
    ['新增用户', userData.value.newUsers],
    ['复购率', formatPercent(userData.value.repurchaseRate)],
    ['老客成交占比', formatPercent(userData.value.returningCustomerShare)],
    [],
    ['服务与风控', '数值'],
    ['售后申请', riskData.value.applications],
    ['退款完成', riskData.value.successCount],
    ['超时未发货', riskData.value.overdueShipment],
    ['负面评价', riskData.value.negativeReviews],
    [],
    ['流量来源', '用户数'],
    ...trafficData.value.map((item) => [item.name, item.value]),
    [],
    ['商品销量 TOP10', '销量'],
    ...productRankingData.value.salesTop10.map((item) => [
      item.name,
      item.value,
    ]),
    [],
    ['退款率 TOP10', '退款率'],
    ...productRankingData.value.refundTop10.map((item) => [
      item.name,
      formatPercent(item.value),
    ]),
  ];
  downloadCsv(
    `经营概览_${rangeLabel.value}_${dayjs().format('YYYYMMDD_HHmm')}.csv`,
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
        <DashboardInsights v-if="hasLoaded" :insights="insights" />

        <AnalyticsCards
          :data="cardsData"
          :delta-label="deltaLabel"
          :previous="previousCardsData"
          :loading="loading"
        />

        <DashboardTarget />

        <AnalyticsTrends :data="trendsData" :loading="loading" />

        <AnalyticsFunnel :data="funnelData" :loading="loading" />

        <TrafficFunnel :data="trafficFunnelData" :loading="loading" />

        <ChannelContribution
          :end-time="currentRange.endTime"
          :start-time="currentRange.startTime"
        />

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
      </template>
    </div>
  </div>
</template>
