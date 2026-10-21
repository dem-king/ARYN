<script lang="ts" setup>
import { useRouter } from 'vue-router';

import {
  Bell,
  CircleCheck,
  InfoFilled,
  Warning,
} from '@element-plus/icons-vue';
import { ElButton, ElCard, ElIcon } from 'element-plus';

interface Insight {
  description: string;
  title: string;
  tone: 'danger' | 'info' | 'success' | 'warning';
  to?: string;
}

const props = withDefaults(
  defineProps<{
    insights: Insight[];
    loading?: boolean;
  }>(),
  {
    loading: false,
  },
);

const TONES = {
  danger: {
    bg: 'bg-red-50 dark:bg-red-900/20',
    icon: Warning,
    text: 'text-red-500',
  },
  warning: {
    bg: 'bg-orange-50 dark:bg-orange-900/20',
    icon: Warning,
    text: 'text-orange-500',
  },
  info: {
    bg: 'bg-blue-50 dark:bg-blue-900/20',
    icon: InfoFilled,
    text: 'text-blue-500',
  },
  success: {
    bg: 'bg-green-50 dark:bg-green-900/20',
    icon: CircleCheck,
    text: 'text-green-500',
  },
} as const;

const router = useRouter();

const tone = (value: Insight['tone']) => TONES[value];
const go = (to: string) => {
  router.push(to);
};
</script>

<template>
  <ElCard
    v-if="props.insights.length > 0"
    v-loading="loading"
    shadow="hover"
    class="mb-4"
  >
    <template #header>
      <div class="flex items-center gap-2 font-bold">
        <ElIcon class="text-orange-500"><Bell /></ElIcon>
        <span>经营提醒</span>
      </div>
    </template>
    <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="item in insights"
        :key="item.title"
        class="flex items-start gap-3 rounded-lg p-3"
        :class="tone(item.tone).bg"
      >
        <ElIcon :size="20" :class="tone(item.tone).text">
          <component :is="tone(item.tone).icon" />
        </ElIcon>
        <div class="min-w-0 flex-1">
          <div class="text-sm font-bold" :class="tone(item.tone).text">
            {{ item.title }}
          </div>
          <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {{ item.description }}
          </div>
        </div>
        <ElButton
          v-if="item.to"
          link
          size="small"
          type="primary"
          @click="go(item.to)"
        >
          去处理
        </ElButton>
      </div>
    </div>
  </ElCard>
</template>
