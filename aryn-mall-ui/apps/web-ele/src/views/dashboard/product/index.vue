<script lang="ts" setup>
import type { ProductLifecycleVO } from '#/api/product/goods-statistics';

import { computed, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';
import { ElAlert } from 'element-plus';

import { getProductSalesAnalysis } from '#/api/order/order-statistics';
import { getProductDashboardStatistics } from '#/api/product/goods-statistics';
import { formatPercent } from '#/utils/percent';

import { downloadCsv } from '../shared/csv';
import DashboardHeader from '../shared/DashboardHeader.vue';
import DashboardSkeleton from '../shared/DashboardSkeleton.vue';
import { settle } from '../shared/settle';
import { useDashboardRange } from '../shared/use-dashboard-range';
import ProductCoreMetrics from './components/ProductCoreMetrics.vue';
import ProductLifecycle from './components/ProductLifecycle.vue';
import ProductPraiseRanking from './components/ProductPraiseRanking.vue';
import ProductSalesRanking from './components/ProductSalesRanking.vue';
import ProductTrend from './components/ProductTrend.vue';

interface SalesData {
  avgPrice: number;
  salesAmount: number;
  salesItemCount: number;
  salesProductCount: number;
  sellThroughRate: number;
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

const scaleData = ref({
  total: 0,
  onSale: 0,
  offShelf: 0,
  totalStock: 0,
  lowStock: 0,
});

const salesData = ref<SalesData>({
  salesProductCount: 0,
  salesItemCount: 0,
  salesAmount: 0,
  avgPrice: 0,
  sellThroughRate: 0,
});
const previousSalesData = ref<null | SalesData>(null);
const lifecycleData = ref<null | ProductLifecycleVO>(null);

const trendData = ref({
  dates: [] as string[],
  pv: [] as number[],
  uv: [] as number[],
});

const visitRankData = ref<any[]>([]);
const stockWarningData = ref<any[]>([]);
const praiseRankData = ref<any[]>([]);

const rangeLabel = computed(
  () =>
    ({
      '30d': '近30天',
      '7d': '近7天',
      custom: '自定义',
      today: '今日',
    })[rangeKey.value] ?? '商品分析',
);

const deltaLabel = computed(() =>
  compareMode.value === 'year' ? '较去年同期' : '较上期',
);

const fetchData = useDebounceFn(async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const [productRes, salesRes, previousSalesRes] = await Promise.all([
      settle(getProductDashboardStatistics(currentRange.value)),
      settle(getProductSalesAnalysis(currentRange.value)),
      compare.value
        ? settle(getProductSalesAnalysis(previousRange.value))
        : Promise.resolve({ data: undefined, error: undefined }),
    ]);

    const failed: string[] = [];

    if (productRes.error) {
      failed.push('商品统计');
    } else if (productRes.data) {
      const dashboard = productRes.data;

      if (dashboard.overview) {
        scaleData.value = {
          total:
            (dashboard.overview.onSaleCount || 0) +
            (dashboard.overview.offShelfCount || 0),
          onSale: dashboard.overview.onSaleCount || 0,
          offShelf: dashboard.overview.offShelfCount || 0,
          totalStock: dashboard.overview.totalStock || 0,
          lowStock: dashboard.overview.lowStockCount || 0,
        };
      }

      if (Array.isArray(dashboard.visitTrend)) {
        trendData.value = {
          dates: dashboard.visitTrend.map((item) => item.timePoint),
          pv: dashboard.visitTrend.map((item) => item.pv),
          uv: dashboard.visitTrend.map((item) => item.uv),
        };
      }

      visitRankData.value = Array.isArray(dashboard.visitTop10)
        ? dashboard.visitTop10.map((item, index) => ({
            id: String(index),
            name: item.spuName,
            cover: item.picUrl,
            pv: item.pv,
            uv: item.uv,
          }))
        : [];

      praiseRankData.value = Array.isArray(dashboard.praiseTop10)
        ? dashboard.praiseTop10.map((item, index) => ({
            id: String(index),
            name: item.spuName,
            rate: Number(item.goodAppraiseRate) || 0,
            count: item.goodAppraiseCount || 0,
          }))
        : [];

      stockWarningData.value = Array.isArray(dashboard.lowStockTop10)
        ? dashboard.lowStockTop10.map((item, index) => ({
            id: item.id || String(index),
            name: item.name,
            cover: item.picUrl,
            stock: item.stock,
            status: '库存紧张',
          }))
        : [];

      lifecycleData.value = dashboard.lifecycle || null;
    }

    if (salesRes.error) {
      failed.push('销售能力');
    } else if (salesRes.data) {
      salesData.value = {
        salesProductCount: salesRes.data.activeProductCount || 0,
        salesItemCount: salesRes.data.salesCount || 0,
        salesAmount: salesRes.data.salesAmount || 0,
        avgPrice: salesRes.data.averageTicketSize || 0,
        sellThroughRate: salesRes.data.activeProductRate || 0,
      };
    }

    if (compare.value && previousSalesRes.error) {
      failed.push('对比数据');
    }
    previousSalesData.value =
      salesRes.data && previousSalesRes.data
        ? {
            salesProductCount: previousSalesRes.data.activeProductCount || 0,
            salesItemCount: previousSalesRes.data.salesCount || 0,
            salesAmount: previousSalesRes.data.salesAmount || 0,
            avgPrice: previousSalesRes.data.averageTicketSize || 0,
            sellThroughRate: previousSalesRes.data.activeProductRate || 0,
          }
        : null;

    loadError.value =
      failed.length > 0 ? `部分数据加载失败：${failed.join('、')}` : '';
    lastUpdated.value = dayjs().format('HH:mm:ss');
    hasLoaded.value = true;
  } catch (error) {
    console.error('Failed to fetch product analytics data', error);
    loadError.value = '商品分析数据加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}, 100);

const handleExport = () => {
  const rows: unknown[][] = [
    ['商品规模', '数值'],
    ['商品总数', scaleData.value.total],
    ['在售商品', scaleData.value.onSale],
    ['下架商品', scaleData.value.offShelf],
    ['库存总量', scaleData.value.totalStock],
    ['库存预警', scaleData.value.lowStock],
    [],
    ['商品生命周期', '数量'],
    ['近30天新品', lifecycleData.value?.newCount || 0],
    ['动销商品', lifecycleData.value?.activeCount || 0],
    ['滞销商品', lifecycleData.value?.slowMovingCount || 0],
    ['缺货商品', lifecycleData.value?.outOfStockCount || 0],
    [],
    ['销售能力', '数值'],
    ['动销商品数', salesData.value.salesProductCount],
    ['销售件数', salesData.value.salesItemCount],
    ['销售额', salesData.value.salesAmount],
    ['客单价', salesData.value.avgPrice],
    ['动销率', formatPercent(salesData.value.sellThroughRate)],
    [],
    ['日期', '浏览量(PV)', '访客数(UV)'],
    ...trendData.value.dates.map((date, index) => [
      date,
      trendData.value.pv[index] ?? 0,
      trendData.value.uv[index] ?? 0,
    ]),
    [],
    ['浏览排行 TOP10', '商品名称', '浏览量(PV)', '访客数(UV)'],
    ...visitRankData.value.map((item, index) => [
      index + 1,
      item.name,
      item.pv,
      item.uv,
    ]),
    [],
    ['库存预警', '剩余库存'],
    ...stockWarningData.value.map((item) => [item.name, item.stock]),
    [],
    ['好评榜 TOP10', '商品名称', '好评率', '好评数'],
    ...praiseRankData.value.map((item, index) => [
      index + 1,
      item.name,
      formatPercent(item.rate, 1),
      item.count,
    ]),
  ];
  downloadCsv(
    `商品分析_${rangeLabel.value}_${dayjs().format('YYYYMMDD_HHmm')}.csv`,
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
  <div class="p-4">
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
      <div class="mb-4">
        <ProductCoreMetrics
          :scale="scaleData"
          :sales="salesData"
          :previous="previousSalesData"
          :delta-label="deltaLabel"
          :loading="loading"
        />
      </div>

      <ProductLifecycle :data="lifecycleData" :loading="loading" />

      <ProductTrend :data="trendData" :loading="loading" />

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div class="lg:col-span-2">
          <ProductSalesRanking
            :visit-rank="visitRankData"
            :stock-warning="stockWarningData"
            :loading="loading"
          />
        </div>

        <div class="lg:col-span-1">
          <ProductPraiseRanking :data="praiseRankData" :loading="loading" />
        </div>
      </div>
    </template>
  </div>
</template>
