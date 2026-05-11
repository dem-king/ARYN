<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';

import { Refresh } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElRadioButton,
  ElRadioGroup,
  ElSpace,
  ElTooltip,
} from 'element-plus';

const props = defineProps<{
  timeRange?: string;
}>();

const emit = defineEmits(['update:timeRange', 'change', 'init']);

const timeRange = ref(props.timeRange || '7d');
const lastUpdateTime = ref('');

const handleRefresh = () => {
  lastUpdateTime.value = dayjs().format('HH:mm:ss');
  emit('change', { timeRange: timeRange.value });
};

watch(
  () => props.timeRange,
  (val) => {
    if (val) timeRange.value = val;
    lastUpdateTime.value = dayjs().format('HH:mm:ss');
  },
);

watch(timeRange, (val) => {
  emit('update:timeRange', val);
  emit('change', { timeRange: val });
  lastUpdateTime.value = dayjs().format('HH:mm:ss');
});

onMounted(() => {
  lastUpdateTime.value = dayjs().format('HH:mm:ss');
});
</script>

<template>
  <div
    class="mb-4 flex flex-col items-center justify-between gap-4 rounded-lg bg-white p-4 shadow-sm md:flex-row dark:bg-zinc-900"
  >
    <div class="w-full md:w-auto">
      <ElSpace wrap>
        <span class="text-sm font-bold text-gray-700">时间范围</span>
        <ElRadioGroup v-model="timeRange" size="default">
          <ElRadioButton value="today">今日</ElRadioButton>
          <ElRadioButton value="7d">近7天</ElRadioButton>
          <ElRadioButton value="30d">近30天</ElRadioButton>
        </ElRadioGroup>
        <div class="flex items-center gap-2">
          <span class="text-xs text-gray-400" v-if="lastUpdateTime">
            更新于 {{ lastUpdateTime }}
          </span>
          <ElTooltip content="刷新数据" placement="top">
            <ElButton :icon="Refresh" circle @click="handleRefresh" />
          </ElTooltip>
        </div>
      </ElSpace>
    </div>
  </div>
</template>
