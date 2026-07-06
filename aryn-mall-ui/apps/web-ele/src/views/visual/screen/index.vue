<script lang="ts" setup>
import type {
  VisualCategoryRankVO,
  VisualOrderTrendVO,
  VisualUserFunnelVO,
} from '#/api/visual/screen';

import { onMounted, onUnmounted, reactive, ref } from 'vue';

import {
  getVisualCategoryRank,
  getVisualOrderTrend,
  getVisualOverview,
  getVisualUserFunnel,
} from '#/api/visual/screen';

import CategoryRankChart from './components/CategoryRankChart.vue';
import FlipCards from './components/FlipCards.vue';
import OrderTrendChart from './components/OrderTrendChart.vue';
import ScreenHeader from './components/ScreenHeader.vue';
import UserFunnelChart from './components/UserFunnelChart.vue';

/** 设计稿尺寸 */
const DESIGN_WIDTH = 1920;
const DESIGN_HEIGHT = 1080;

const scale = ref(1);
const loading = ref(false);

const overviewData = reactive({
  gmv: 0,
  orderCount: 0,
  onlineUsers: 0,
  conversionRate: 0,
});

const trendData = ref<VisualOrderTrendVO[]>([]);
const categoryData = ref<VisualCategoryRankVO[]>([]);
const funnelData = ref<null | VisualUserFunnelVO>(null);

let refreshTimer: null | ReturnType<typeof setInterval> = null;

/** 计算 scale 缩放比 */
const updateScale = () => {
  const scaleX = window.innerWidth / DESIGN_WIDTH;
  const scaleY = window.innerHeight / DESIGN_HEIGHT;
  scale.value = Math.min(scaleX, scaleY);
};

/** 拉取所有数据 */
const fetchAllData = async () => {
  loading.value = true;
  try {
    const [overview, trend, category, funnel] = await Promise.all([
      getVisualOverview(),
      getVisualOrderTrend('7d'),
      getVisualCategoryRank(),
      getVisualUserFunnel(),
    ]);

    if (overview) {
      overviewData.gmv = overview.gmv || 0;
      overviewData.orderCount = overview.orderCount || 0;
      overviewData.onlineUsers = overview.onlineUsers || 0;
      overviewData.conversionRate = overview.conversionRate || 0;
    }

    if (trend) {
      trendData.value = trend;
    }

    if (category) {
      categoryData.value = category;
    }

    if (funnel) {
      funnelData.value = funnel;
    }
  } catch (error) {
    console.error('Failed to fetch visual screen data', error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  updateScale();
  window.addEventListener('resize', updateScale);
  fetchAllData();

  // 每 30 秒自动刷新概览数据
  refreshTimer = setInterval(fetchAllData, 30_000);
});

onUnmounted(() => {
  window.removeEventListener('resize', updateScale);
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }
});
</script>

<template>
  <div class="screen-wrapper">
    <div
      class="screen-container"
      :style="{
        transform: `scale(${scale})`,
        width: `${DESIGN_WIDTH}px`,
        height: `${DESIGN_HEIGHT}px`,
      }"
    >
      <!-- 顶部标题栏 -->
      <ScreenHeader />

      <!-- 第一行：4 个数字翻牌器 -->
      <div class="row row-cards">
        <FlipCards :data="overviewData" />
      </div>

      <!-- 第二行：趋势图 + 品类排行 -->
      <div class="row row-charts">
        <div class="chart-left">
          <OrderTrendChart :data="trendData" :loading="loading" />
        </div>
        <div class="chart-right">
          <CategoryRankChart :data="categoryData" :loading="loading" />
        </div>
      </div>

      <!-- 第三行：用户漏斗 -->
      <div class="row row-funnel">
        <UserFunnelChart :data="funnelData" :loading="loading" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.screen-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #0a1a3a 0%, #0d2137 50%, #0a1628 100%);
}

.screen-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0 20px 20px;
  transform-origin: center center;
}

.row {
  width: 100%;
}

.row-cards {
  flex-shrink: 0;
}

.row-charts {
  display: flex;
  gap: 20px;
  height: 340px;
}

.chart-left {
  flex: 3;
  min-width: 0;
}

.chart-right {
  flex: 2;
  min-width: 0;
}

.row-funnel {
  flex: 1;
  min-height: 280px;
}
</style>
