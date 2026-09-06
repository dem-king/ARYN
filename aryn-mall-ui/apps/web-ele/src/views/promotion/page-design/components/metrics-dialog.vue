<script setup lang="ts">
import type { PageDesignMetricDaily } from '#/api/promotion/page-design';

import { computed, ref, watch } from 'vue';

import {
  ElDialog,
  ElRadio,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getMetrics } from '#/api/promotion/page-design';

const props = defineProps<{
  modelValue: boolean;
  pageId: string;
  pageName: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const loading = ref(false);
const days = ref<7 | 30>(7);
const metrics = ref<PageDesignMetricDaily[]>([]);

const pageRows = computed(() =>
  metrics.value.filter((item) => item.componentType === '-'),
);
const componentRows = computed(() =>
  metrics.value.filter((item) => item.componentType !== '-'),
);

const totals = computed(() => {
  const summary = { clicks: 0, errors: 0, views: 0 };
  for (const item of pageRows.value) {
    summary.views += item.viewCount;
  }
  for (const item of metrics.value) {
    summary.clicks += item.clickCount;
    summary.errors += item.errorCount;
  }
  return summary;
});

async function loadMetrics() {
  if (!props.modelValue || !props.pageId) return;
  loading.value = true;
  try {
    metrics.value = await getMetrics(props.pageId, days.value);
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) void loadMetrics();
  },
);
watch(days, loadMetrics);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="`${pageName} · 数据看板`"
    width="820px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="metric-toolbar">
      <div class="metric-cards">
        <div class="metric-card">
          <span class="metric-value">{{ totals.views }}</span>
          <span class="metric-label">页面访问</span>
        </div>
        <div class="metric-card">
          <span class="metric-value">{{ totals.clicks }}</span>
          <span class="metric-label">组件点击</span>
        </div>
        <div class="metric-card">
          <span class="metric-value">{{ totals.errors }}</span>
          <span class="metric-label">渲染错误</span>
        </div>
      </div>
      <ElRadioGroup v-model="days" size="small">
        <ElRadio :value="7">近 7 天</ElRadio>
        <ElRadio :value="30">近 30 天</ElRadio>
      </ElRadioGroup>
    </div>

    <p class="metric-section">页面访问趋势（按版本）</p>
    <ElTable v-loading="loading" :data="pageRows" max-height="220" size="small">
      <ElTableColumn label="日期" prop="metricDate" width="120" />
      <ElTableColumn label="版本" prop="versionId" min-width="180" show-overflow-tooltip />
      <ElTableColumn label="访问" prop="viewCount" width="90" align="center" />
      <ElTableColumn label="渲染错误" prop="errorCount" width="100" align="center" />
    </ElTable>

    <p class="metric-section">组件点击 / 渲染错误 Top</p>
    <ElTable
      v-loading="loading"
      :data="componentRows"
      max-height="220"
      size="small"
    >
      <ElTableColumn label="日期" prop="metricDate" width="120" />
      <ElTableColumn label="组件" prop="componentType" width="160" />
      <ElTableColumn label="版本" prop="versionId" min-width="160" show-overflow-tooltip />
      <ElTableColumn label="点击" prop="clickCount" width="90" align="center" />
      <ElTableColumn label="渲染错误" prop="errorCount" width="100" align="center" />
    </ElTable>
    <p v-if="metrics.length === 0 && !loading" class="metric-empty">
      暂无数据：移动端访问页面后指标将在此聚合展示。
    </p>
  </ElDialog>
</template>

<style scoped>
.metric-toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.metric-cards {
  display: flex;
  gap: 12px;
}

.metric-card {
  display: grid;
  min-width: 96px;
  padding: 8px 14px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.metric-value {
  font-size: 18px;
  font-weight: 700;
}

.metric-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-section {
  margin: 14px 0 6px;
  font-size: 13px;
  font-weight: 600;
}

.metric-empty {
  padding: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
