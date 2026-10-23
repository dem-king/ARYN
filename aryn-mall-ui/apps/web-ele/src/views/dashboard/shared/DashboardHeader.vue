<script lang="ts" setup>
import { computed, watch } from 'vue';

import { Download, Refresh } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDatePicker,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSpace,
  ElTooltip,
} from 'element-plus';

const props = withDefaults(
  defineProps<{
    compare?: boolean;
    compareMode?: 'previous' | 'year';
    customEnd?: string;
    customStart?: string;
    exportable?: boolean;
    lastUpdated?: string;
    loading?: boolean;
    rangeKey?: string;
  }>(),
  {
    compare: true,
    compareMode: 'previous',
    customEnd: '',
    customStart: '',
    exportable: false,
    lastUpdated: '',
    loading: false,
    rangeKey: '30d',
  },
);

const emit = defineEmits([
  'update:rangeKey',
  'update:customStart',
  'update:customEnd',
  'update:compare',
  'update:compareMode',
  'refresh',
  'export',
]);

const rangeKey = computed({
  get: () => props.rangeKey,
  set: (value: string) => emit('update:rangeKey', value),
});
const compareSelection = computed({
  get: (): 'off' | 'previous' | 'year' =>
    props.compare ? props.compareMode : 'off',
  set: (value: 'off' | 'previous' | 'year') => {
    if (value === 'off') {
      emit('update:compare', false);
      return;
    }
    emit('update:compare', true);
    emit('update:compareMode', value);
  },
});
const customRange = computed<string[] | undefined>({
  get: () =>
    props.customStart && props.customEnd
      ? [props.customStart, props.customEnd]
      : undefined,
  set: (value?: string[]) => {
    emit('update:customStart', value?.[0] ?? '');
    emit('update:customEnd', value?.[1] ?? '');
  },
});

watch(rangeKey, (value) => {
  if (value === 'custom' && (!props.customStart || !props.customEnd)) {
    const end = dayjs();
    emit('update:customStart', end.subtract(6, 'day').format('YYYY-MM-DD'));
    emit('update:customEnd', end.format('YYYY-MM-DD'));
  }
});
</script>

<template>
  <div
    class="mb-4 flex flex-col items-start justify-between gap-3 rounded-lg bg-[var(--el-bg-color-overlay)] p-4 shadow-sm md:flex-row md:items-center"
  >
    <div class="flex w-full flex-wrap items-center gap-3 md:w-auto">
      <span class="text-sm font-bold text-[var(--el-text-color-primary)]">
        时间范围
      </span>
      <ElRadioGroup v-model="rangeKey" size="default">
        <ElRadioButton value="today">今日</ElRadioButton>
        <ElRadioButton value="7d">近7天</ElRadioButton>
        <ElRadioButton value="30d">近30天</ElRadioButton>
        <ElRadioButton value="custom">自定义</ElRadioButton>
      </ElRadioGroup>
      <ElDatePicker
        v-if="rangeKey === 'custom'"
        v-model="customRange"
        type="daterange"
        size="default"
        unlink-panels
        :clearable="false"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
      />
      <ElSelect v-model="compareSelection" size="default" class="w-40">
        <ElOption label="不对比" value="off" />
        <ElOption label="对比上期" value="previous" />
        <ElOption label="对比去年同期" value="year" />
      </ElSelect>
    </div>
    <ElSpace class="shrink-0" wrap>
      <span
        v-if="loading"
        class="text-xs text-[var(--el-text-color-secondary)]"
      >
        加载中…
      </span>
      <span
        v-else-if="lastUpdated"
        class="text-xs text-[var(--el-text-color-secondary)]"
      >
        更新于 {{ lastUpdated }}
      </span>
      <ElTooltip content="刷新数据" placement="top">
        <ElButton
          :icon="Refresh"
          :loading="loading"
          circle
          @click="emit('refresh')"
        />
      </ElTooltip>
      <ElButton
        v-if="exportable"
        :icon="Download"
        :disabled="loading"
        @click="emit('export')"
      >
        导出
      </ElButton>
    </ElSpace>
  </div>
</template>
