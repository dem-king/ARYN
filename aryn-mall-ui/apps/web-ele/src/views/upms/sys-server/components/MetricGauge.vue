<script lang="ts" setup>
import type { Component } from 'vue';

import { computed } from 'vue';

import { ElIcon } from 'element-plus';

const props = withDefaults(
  defineProps<{
    accent?: string;
    accentEnd?: string;
    caption?: string;
    icon: Component;
    percent: number;
    title: string;
  }>(),
  {
    accent: '#22d3ee',
    accentEnd: '#3b82f6',
    caption: '',
  },
);

let gradientSeed = 0;

const gradientId = `metric-gauge-gradient-${gradientSeed++}`;
const circumference = 2 * Math.PI * 50;

const safePercent = computed(() => {
  if (!Number.isFinite(props.percent)) {
    return 0;
  }
  return Math.min(100, Math.max(0, Number(props.percent.toFixed(1))));
});

const tone = computed<'danger' | 'success' | 'warning'>(() => {
  if (safePercent.value >= 88) {
    return 'danger';
  }
  if (safePercent.value >= 72) {
    return 'warning';
  }
  return 'success';
});

const toneLabel = computed(() => {
  return { danger: '告警', success: '平稳', warning: '偏高' }[tone.value];
});

const stops = computed<[string, string]>(() => {
  if (tone.value === 'danger') {
    return ['#fb7185', '#ef4444'];
  }
  if (tone.value === 'warning') {
    return ['#fbbf24', '#f97316'];
  }
  return [props.accent, props.accentEnd];
});

const dashOffset = computed(() => {
  return circumference * (1 - safePercent.value / 100);
});
</script>

<template>
  <div class="metric-card" :style="{ '--accent': stops[0] }">
    <div class="metric-head">
      <div class="metric-title">
        <span class="metric-icon" :style="{ backgroundColor: `${stops[0]}1f` }">
          <ElIcon :size="16" :style="{ color: stops[0] }">
            <component :is="icon" />
          </ElIcon>
        </span>
        <span>{{ title }}</span>
      </div>
      <span class="metric-status" :class="`is-${tone}`">
        <i></i>
        {{ toneLabel }}
      </span>
    </div>

    <div class="gauge-wrap">
      <svg class="gauge-svg" :style="{ color: stops[0] }" viewBox="0 0 120 120">
        <defs>
          <linearGradient :id="gradientId" x1="0%" x2="100%" y1="0%" y2="100%">
            <stop offset="0%" :stop-color="stops[0]" />
            <stop offset="100%" :stop-color="stops[1]" />
          </linearGradient>
        </defs>
        <circle class="gauge-track" cx="60" cy="60" r="50" />
        <circle class="gauge-ticks" cx="60" cy="60" r="57" />
        <circle
          class="gauge-progress"
          cx="60"
          cy="60"
          r="50"
          :stroke="`url(#${gradientId})`"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="dashOffset"
        />
      </svg>
      <div class="gauge-center">
        <span class="gauge-number font-mono tabular-nums">
          {{ safePercent.toFixed(1) }}<em>%</em>
        </span>
        <span v-if="caption" class="gauge-caption">{{ caption }}</span>
      </div>
    </div>

    <div class="metric-detail">
      <slot name="detail"></slot>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.metric-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 16px 18px 14px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
  transition:
    transform 0.25s ease,
    border-color 0.25s ease,
    box-shadow 0.25s ease;

  &::before {
    position: absolute;
    top: 0;
    right: 18%;
    left: 18%;
    height: 2px;
    content: '';
    background: linear-gradient(90deg, transparent, var(--accent), transparent);
    opacity: 0.7;
  }

  &:hover {
    border-color: color-mix(in srgb, var(--accent) 35%, var(--el-border-color));
    box-shadow: 0 10px 28px -18px
      color-mix(in srgb, var(--accent) 65%, transparent);
    transform: translateY(-2px);
  }
}

.metric-head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.metric-title {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
}

.metric-status {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  padding: 2px 8px;
  font-size: 11px;
  line-height: 18px;
  border-radius: 999px;

  i {
    width: 5px;
    height: 5px;
    background: currentcolor;
    border-radius: 50%;
    box-shadow: 0 0 0 3px color-mix(in srgb, currentcolor 18%, transparent);
  }

  &.is-success {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
  }

  &.is-warning {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
  }

  &.is-danger {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
  }
}

.gauge-wrap {
  position: relative;
  width: 126px;
  height: 126px;
  margin: 10px auto 8px;
}

.gauge-svg {
  width: 100%;
  height: 100%;
  overflow: visible;
  transform: rotate(-90deg);
}

.gauge-track {
  fill: none;
  stroke: var(--el-fill-color);
  stroke-width: 8;
}

.gauge-ticks {
  opacity: 0.6;
  fill: none;
  stroke: var(--el-border-color);
  stroke-width: 1;
  stroke-dasharray: 1 5.2;
}

.gauge-progress {
  filter: drop-shadow(0 0 5px currentcolor);
  fill: none;
  stroke-width: 8;
  stroke-linecap: round;
  transition:
    stroke-dashoffset 1.1s cubic-bezier(0.22, 1, 0.36, 1),
    stroke 0.3s ease;
}

.gauge-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  justify-content: center;
}

.gauge-number {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  color: var(--el-text-color-primary);
  letter-spacing: -0.02em;

  em {
    margin-left: 2px;
    font-size: 12px;
    font-style: normal;
    font-weight: 500;
    color: var(--el-text-color-secondary);
    letter-spacing: 0;
  }
}

.gauge-caption {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.metric-detail {
  min-height: 20px;
  padding-top: 10px;
  margin-top: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-top: 1px dashed var(--el-border-color-lighter);
}
</style>
