<script setup lang="ts">
import type { RetailPreviewStatus } from '../common/retail-preview/use-retail-preview';
import type { CountdownProps } from './types';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { validateCountdown } from './types';

const props = defineProps<{ showData: CountdownProps }>();
const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;

const validationErrors = computed(() => validateCountdown(props.showData));
const remainingSeconds = computed(() =>
  Math.max(
    0,
    Math.floor((Date.parse(props.showData.targetTime) - now.value) / 1000),
  ),
);
const previewStatus = computed<RetailPreviewStatus>(() =>
  validationErrors.value.length > 0 ? 'invalid' : 'data',
);
const segments = computed(() => {
  let rest = remainingSeconds.value;
  const days = Math.floor(rest / 86_400);
  rest %= 86_400;
  const hours = Math.floor(rest / 3600);
  rest %= 3600;
  const minutes = Math.floor(rest / 60);
  return [
    { label: '天', value: days },
    { label: '时', value: hours },
    { label: '分', value: minutes },
    { label: '秒', value: rest % 60 },
  ];
});

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
});
onBeforeUnmount(() => clearInterval(timer));
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="validationErrors[0]"
    :status="previewStatus"
    :title="showData.title"
  >
    <div v-if="remainingSeconds > 0" class="countdown-row">
      <div
        v-for="segment in segments"
        :key="segment.label"
        class="countdown-segment"
      >
        <strong>{{ String(segment.value).padStart(2, '0') }}</strong>
        <span>{{ segment.label }}</span>
      </div>
    </div>
    <div v-else class="countdown-completed">{{ showData.completedText }}</div>
  </RetailPreviewFrame>
</template>

<style scoped>
.countdown-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.countdown-segment {
  display: grid;
  place-items: center;
  align-content: center;
  min-height: 64px;
  color: #fff;
  background: #172033;
  border-radius: 6px;
}

.countdown-segment strong {
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.countdown-segment span {
  margin-top: 5px;
  font-size: 11px;
  color: #cbd5e1;
}

.countdown-completed {
  display: grid;
  place-items: center;
  min-height: 64px;
  color: #64748b;
  background: #f1f5f9;
  border-radius: 6px;
}
</style>
