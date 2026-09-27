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
const previewStatus = computed<RetailPreviewStatus>(() =>
  validationErrors.value.length > 0 ? 'invalid' : 'data',
);

/**
 * 剩余时长格式化：与小程序 useCountdown.formatRemainingTime 同一口径
 * （天 + HH:mm:ss，已结束或时间不可解析返回空串）。
 */
function formatRemainingTime(targetTime: string, current: number) {
  const remaining = Math.max(0, Date.parse(targetTime) - current);
  if (!Number.isFinite(remaining) || remaining <= 0) return '';
  const totalSeconds = Math.floor(remaining / 1000);
  const days = Math.floor(totalSeconds / 86_400);
  const hours = Math.floor((totalSeconds % 86_400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const time = [hours, minutes, seconds]
    .map((value) => String(value).padStart(2, '0'))
    .join(':');
  return days > 0 ? `${days}天 ${time}` : time;
}

const remaining = computed(() =>
  formatRemainingTime(props.showData.targetTime, now.value),
);
/** 时间不可解析时同样按已结束呈现，与小程序 invalid 判断一致 */
const invalid = computed(() => {
  const target = Date.parse(props.showData.targetTime);
  return !props.showData.targetTime || Number.isNaN(target);
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
  >
    <div class="countdown">
      <span class="countdown-title">{{ showData.title }}</span>
      <span class="countdown-value">
        {{ !invalid && remaining ? remaining : showData.completedText }}
      </span>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-countdown 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.countdown {
  display: flex;
  gap: 9px;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
}

.countdown-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.countdown-value {
  font-family: monospace;
  font-size: 16px;
  font-weight: 700;
  color: #e5484d;
}
</style>
