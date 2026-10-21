<script lang="ts" setup>
import type { Component } from 'vue';

import { computed } from 'vue';

import { CountTo } from '@vben/common-ui';

import { InfoFilled } from '@element-plus/icons-vue';
import { ElCard, ElIcon, ElTooltip } from 'element-plus';

import DeltaBadge from './DeltaBadge.vue';

const props = withDefaults(
  defineProps<{
    decimals?: number;
    delta?: null | number;
    deltaLabel?: string;
    icon?: Component;
    invert?: boolean;
    loading?: boolean;
    prefix?: string;
    suffix?: string;
    title: string;
    tone?: Tone;
    tooltip?: string;
    value: number;
  }>(),
  {
    decimals: 0,
    delta: null,
    deltaLabel: '较上期',
    icon: undefined,
    invert: false,
    loading: false,
    prefix: '',
    suffix: '',
    tone: 'blue',
    tooltip: '',
  },
);

const TONES = {
  blue: {
    iconClass:
      'bg-blue-50 text-blue-500 dark:bg-blue-900/20 dark:text-blue-400',
    valueClass: '',
  },
  green: {
    iconClass:
      'bg-green-50 text-green-500 dark:bg-green-900/20 dark:text-green-400',
    valueClass: '',
  },
  orange: {
    iconClass:
      'bg-orange-50 text-orange-500 dark:bg-orange-900/20 dark:text-orange-400',
    valueClass: '',
  },
  purple: {
    iconClass:
      'bg-purple-50 text-purple-500 dark:bg-purple-900/20 dark:text-purple-400',
    valueClass: '',
  },
  red: {
    iconClass: 'bg-red-50 text-red-500 dark:bg-red-900/20 dark:text-red-400',
    valueClass: 'text-red-500',
  },
} as const;

type Tone = keyof typeof TONES;

const toneStyles = computed(() => TONES[props.tone] ?? TONES.blue);
</script>

<template>
  <ElCard
    v-loading="loading"
    shadow="hover"
    class="h-full transition-all duration-300 hover:-translate-y-1 hover:shadow-lg"
  >
    <div class="flex items-start justify-between gap-2">
      <div class="min-w-0">
        <div class="flex items-center gap-1 text-sm text-gray-500">
          <span class="truncate">{{ title }}</span>
          <ElTooltip v-if="tooltip" :content="tooltip" placement="top">
            <ElIcon class="cursor-help text-gray-400"><InfoFilled /></ElIcon>
          </ElTooltip>
        </div>
        <div class="mt-2 text-2xl font-bold" :class="toneStyles.valueClass">
          <CountTo
            :end-val="value"
            :prefix="prefix"
            :suffix="suffix"
            :decimals="decimals"
          />
        </div>
        <DeltaBadge :delta="delta" :invert="invert" :label="deltaLabel" />
      </div>
      <div v-if="icon" class="rounded-full p-3" :class="toneStyles.iconClass">
        <ElIcon :size="24"><component :is="icon" /></ElIcon>
      </div>
    </div>
  </ElCard>
</template>
