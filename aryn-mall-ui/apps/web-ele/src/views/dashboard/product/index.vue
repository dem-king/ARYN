<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';

import { getProductSalesAnalysis } from '#/api/order/order-statistics';
import {
  getLowStockTop10,
  getProductOverview,
  getProductPraiseTop10,
  getProductVisitTop10,
  getProductVisitTrend,
} from '#/api/product/goods-statistics';

import AnalyticsHeader from '../analytics/components/AnalyticsHeader.vue';
import ProductCategoryAnalysis from './components/ProductCategoryAnalysis.vue';
import ProductCoreMetrics from './components/ProductCoreMetrics.vue';
import ProductSalesRanking from './components/ProductSalesRanking.vue';
import ProductTrend from './components/ProductTrend.vue';

const timeRange = ref<string>('30d');
const loading = ref(false);

// Reactive data objects
const scaleData = ref({
  total: 0,
  onSale: 0,
  offShelf: 0,
  pendingReview: 0,
  totalStock: 0,
  lowStock: 0,
});

const salesData = ref({
  salesProductCount: 0,
  salesItemCount: 0,
  salesAmount: 0,
  avgPrice: 0,
  sellThroughRate: 0,
});

const trendData = ref({
  dates: [] as string[],
  pv: [] as number[],
  uv: [] as number[],
});

const visitRankData = ref<any[]>([]);
const stockWarningData = ref<any[]>([]);
const reviewRankData = ref<any[]>([]);

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
      overviewRes,
      trendRes,
      visitTop10Res,
      praiseTop10Res,
      lowStockRes,
      salesAnalysisRes,
    ] = await Promise.all([
      getProductOverview({}),
      getProductVisitTrend(commonParams),
      getProductVisitTop10(commonParams),
      getProductPraiseTop10(commonParams),
      getLowStockTop10(),
      getProductSalesAnalysis(commonParams),
    ]);

    // 1. 商品概览
    if (overviewRes) {
      scaleData.value = {
        total:
          (overviewRes.onSaleCount || 0) +
          (overviewRes.offShelfCount || 0) +
          (overviewRes.pendingReviewCount || 0),
        onSale: overviewRes.onSaleCount || 0,
        offShelf: overviewRes.offShelfCount || 0,
        pendingReview: overviewRes.pendingReviewCount || 0,
        totalStock: overviewRes.totalStock || 0, // 接口暂无
        lowStock: overviewRes.lowStockCount || 0,
      };
    }

    // 2. 访问趋势
    const trendList = trendRes || [];
    trendData.value = {
      dates: trendList.map((item) => item.timePoint),
      pv: trendList.map((item) => item.pv),
      uv: trendList.map((item) => item.uv),
    };

    // 3. 浏览排行 Top10
    const visitList = visitTop10Res || [];
    visitRankData.value = visitList.map((item, index) => ({
      id: String(index),
      name: item.spuName,
      cover: item.picUrl,
      pv: item.pv,
      uv: item.uv,
    }));

    // 4. 好评榜 Top10
    const praiseList = praiseTop10Res || [];
    reviewRankData.value = praiseList.map((item, index) => ({
      id: String(index),
      name: item.spuName,
      score: item.goodAppraiseRate ? Number(item.goodAppraiseRate) : 0,
    }));

    // 5. 库存预警
    const lowStockList = lowStockRes || [];
    stockWarningData.value = lowStockList.map((item, index) => ({
      id: item.id || String(index),
      name: item.name,
      cover: item.picUrl,
      stock: item.stock,
      status: '库存紧张',
    }));

    // Simulate API calls with mock data
    salesData.value = {
      salesProductCount: salesAnalysisRes?.activeProductCount || 0,
      salesItemCount: salesAnalysisRes?.salesCount || 0,
      salesAmount: salesAnalysisRes?.salesAmount || 0,
      avgPrice: salesAnalysisRes?.averageTicketSize || 0,
      sellThroughRate: salesAnalysisRes?.activeProductRate || 0,
    };
  } catch (error) {
    console.error('Failed to fetch product analytics data', error);
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

    <!-- 核心指标卡片 -->
    <div class="mb-4">
      <ProductCoreMetrics
        :scale="scaleData"
        :sales="salesData"
        :loading="loading"
      />
    </div>

    <!-- 商品访问趋势图 -->
    <ProductTrend :data="trendData" :loading="loading" />

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
      <!-- 销量排行 & 滞销商品 (占 2/3 宽度) -->
      <div class="lg:col-span-2">
        <ProductSalesRanking
          :visit-rank="visitRankData"
          :stock-warning="stockWarningData"
          :loading="loading"
        />
      </div>

      <!-- 商品好评榜 (占 1/3 宽度) -->
      <div class="lg:col-span-1">
        <ProductCategoryAnalysis :data="reviewRankData" :loading="loading" />
      </div>
    </div>
  </div>
</template>
