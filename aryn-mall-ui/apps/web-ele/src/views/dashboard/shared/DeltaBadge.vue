<script lang="ts" setup>
import { computed } from 'vue';

import { CaretBottom, CaretTop } from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

const props = withDefaults(
  defineProps<{
    delta?: null | number;
    invert?: boolean;
    label?: string;
  }>(),
  {
    delta: null,
    invert: false,
    label: '较上期',
  },
);

const trend = computed(() => {
  if (props.delta === null || Number.isNaN(props.delta)) {
    return 'none';
  }
  if (Math.abs(props.delta) < 0.005) {
    return 'flat';
  }
  return props.delta > 0 ? 'up' : 'down';
});

const deltaClass = computed(() => {
  if (trend.value === 'up' || trend.value === 'down') {
    const isGood = trend.value === (props.invert ? 'down' : 'up');
    return isGood ? 'text-green-600' : 'text-red-500';
  }
  return 'text-gray-400';
});
</script>

<template>
  <div
    v-if="delta !== null"
    class="mt-1 flex items-center gap-1 text-xs"
    :class="deltaClass"
  >
    <ElIcon v-if="trend === 'up'"><CaretTop /></ElIcon>
    <ElIcon v-else-if="trend === 'down'"><CaretBottom /></ElIcon>
    <span v-else>—</span>
    <span>{{ Math.abs(delta).toFixed(1) }}%</span>
    <span class="text-gray-400">{{ label }}</span>
  </div>
</template>
