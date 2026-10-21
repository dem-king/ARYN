<script lang="ts" setup>
import type { DashboardTargetVO } from '#/api/order/order-statistics';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Aim, Edit } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInputNumber,
  ElMessage,
  ElProgress,
} from 'element-plus';

import {
  getDashboardTarget,
  saveDashboardTarget,
} from '#/api/order/order-statistics';

import { formatCurrency, formatNumber } from '../../shared/chart';

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const loadError = ref(false);
const selectedMonth = ref(dayjs().format('YYYY-MM'));
const target = ref<DashboardTargetVO | null>(null);

const form = reactive({
  targetMonth: dayjs().format('YYYY-MM'),
  gmvTarget: 0,
  orderTarget: 0,
  newUserTarget: 0,
});

const metrics = computed(() => [
  {
    actual: target.value?.gmvActual || 0,
    color: '#409EFF',
    format: formatCurrency,
    label: 'GMV',
    target: target.value?.gmvTarget || 0,
  },
  {
    actual: target.value?.orderActual || 0,
    color: '#67C23A',
    format: formatNumber,
    label: '支付订单',
    target: target.value?.orderTarget || 0,
  },
  {
    actual: target.value?.newUserActual || 0,
    color: '#E6A23C',
    format: formatNumber,
    label: '新增用户',
    target: target.value?.newUserTarget || 0,
  },
]);

const hasTarget = computed(() => metrics.value.some((item) => item.target > 0));

const percent = (item: { actual: number; target: number }) =>
  item.target > 0
    ? Math.min(100, (Number(item.actual || 0) / item.target) * 100)
    : 0;

const currentMonth = dayjs().format('YYYY-MM');
const isCurrentMonth = computed(() => selectedMonth.value === currentMonth);
const isPastMonth = computed(() => selectedMonth.value < currentMonth);

const timeProgress = computed(() => {
  if (isPastMonth.value) {
    return 100;
  }
  if (!isCurrentMonth.value) {
    return 0;
  }
  const now = dayjs();
  return (now.date() / now.daysInMonth()) * 100;
});

const averageProgress = computed(() => {
  const tracked = metrics.value.filter((item) => item.target > 0);
  if (tracked.length === 0) {
    return 0;
  }
  return tracked.reduce((sum, item) => sum + percent(item), 0) / tracked.length;
});

const progressBehind = computed(
  () =>
    isCurrentMonth.value &&
    hasTarget.value &&
    averageProgress.value + 10 < timeProgress.value,
);

const loadTarget = async () => {
  loading.value = true;
  loadError.value = false;
  try {
    target.value = await getDashboardTarget(selectedMonth.value);
  } catch {
    target.value = null;
    loadError.value = true;
  } finally {
    loading.value = false;
  }
};

const openDialog = () => {
  form.targetMonth = selectedMonth.value;
  form.gmvTarget = target.value?.gmvTarget || 0;
  form.orderTarget = target.value?.orderTarget || 0;
  form.newUserTarget = target.value?.newUserTarget || 0;
  dialogVisible.value = true;
};

const save = async () => {
  saving.value = true;
  try {
    await saveDashboardTarget({
      gmvTarget: Number(form.gmvTarget) || 0,
      newUserTarget: Number(form.newUserTarget) || 0,
      orderTarget: Number(form.orderTarget) || 0,
      targetMonth: form.targetMonth,
    });
    selectedMonth.value = form.targetMonth;
    dialogVisible.value = false;
    ElMessage.success('月度目标已保存');
    await loadTarget();
  } catch {
    ElMessage.error('目标保存失败，请稍后重试');
  } finally {
    saving.value = false;
  }
};

watch(selectedMonth, loadTarget);

onMounted(loadTarget);
</script>

<template>
  <ElCard v-loading="loading" shadow="hover" class="mb-4">
    <template #header>
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-2 font-bold">
          <ElIcon class="text-blue-500"><Aim /></ElIcon>
          <span>目标达成</span>
          <span v-if="loadError" class="text-xs font-normal text-orange-500">
            目标数据暂不可用
          </span>
        </div>
        <div class="flex items-center gap-2">
          <ElDatePicker
            v-model="selectedMonth"
            type="month"
            size="small"
            :clearable="false"
            value-format="YYYY-MM"
            class="w-32"
          />
          <ElButton size="small" :icon="Edit" @click="openDialog">
            设置目标
          </ElButton>
        </div>
      </div>
    </template>

    <div
      v-if="!hasTarget"
      class="mb-3 rounded-lg bg-gray-50 p-3 text-xs text-gray-500 dark:bg-gray-800 dark:text-gray-400"
    >
      尚未设置 {{ selectedMonth }} 的目标，点击右上角“设置目标”开始跟踪。
    </div>

    <div
      v-else-if="isCurrentMonth"
      class="mb-3 flex flex-wrap items-center gap-3 rounded-lg p-3 text-xs"
      :class="
        progressBehind
          ? 'bg-orange-50 text-orange-600 dark:bg-orange-900/20 dark:text-orange-400'
          : 'bg-green-50 text-green-600 dark:bg-green-900/20 dark:text-green-400'
      "
    >
      <span>时间进度 {{ timeProgress.toFixed(0) }}%</span>
      <span>平均目标进度 {{ averageProgress.toFixed(0) }}%</span>
      <span v-if="progressBehind">
        当前进度落后于时间进度，建议重点关注 GMV 与订单转化。
      </span>
      <span v-else>当前进度正常。</span>
    </div>
    <div v-else class="mb-3 text-xs text-gray-400">
      {{ isPastMonth ? '历史月份，目标已结算。' : '未来月份，尚未开始。' }}
    </div>

    <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
      <div
        v-for="item in metrics"
        :key="item.label"
        class="rounded-lg bg-gray-50 p-4 dark:bg-gray-800"
      >
        <div class="flex items-center justify-between">
          <span class="text-sm text-gray-500">{{ item.label }}</span>
          <span class="text-xs text-gray-400">
            {{ percent(item).toFixed(1) }}%
          </span>
        </div>
        <div class="mt-2 text-xl font-bold">
          {{ item.format(item.actual) }}
        </div>
        <div class="text-xs text-gray-400">
          目标 {{ item.format(item.target) }}
        </div>
        <ElProgress
          :percentage="percent(item)"
          :show-text="false"
          :color="item.color"
          class="mt-2"
        />
      </div>
    </div>

    <ElDialog v-model="dialogVisible" title="设置月度目标" width="420px">
      <ElForm label-width="100px">
        <ElFormItem label="目标月份">
          <ElDatePicker
            v-model="form.targetMonth"
            type="month"
            :clearable="false"
            value-format="YYYY-MM"
          />
        </ElFormItem>
        <ElFormItem label="GMV 目标">
          <ElInputNumber
            v-model="form.gmvTarget"
            :min="0"
            :precision="2"
            :step="1000"
            class="w-full"
          />
        </ElFormItem>
        <ElFormItem label="支付订单目标">
          <ElInputNumber
            v-model="form.orderTarget"
            :min="0"
            :step="10"
            class="w-full"
          />
        </ElFormItem>
        <ElFormItem label="新增用户目标">
          <ElInputNumber
            v-model="form.newUserTarget"
            :min="0"
            :step="10"
            class="w-full"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">
          保存
        </ElButton>
      </template>
    </ElDialog>
  </ElCard>
</template>
