<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { CountTo } from '@vben/common-ui';

interface CardItem {
  color: string;
  decimals?: number;
  icon: string;
  label: string;
  prefix?: string;
  suffix?: string;
  value: number;
}

interface Props {
  data: {
    conversionRate: number;
    gmv: number;
    onlineUsers: number;
    orderCount: number;
  };
}

const props = defineProps<Props>();

const cards = computed<CardItem[]>(() => [
  {
    label: '实时 GMV',
    value: props.data.gmv,
    prefix: '¥',
    decimals: 2,
    icon: '💰',
    color: '#00b4ff',
  },
  {
    label: '订单数',
    value: props.data.orderCount,
    icon: '📦',
    color: '#00e396',
  },
  {
    label: '在线用户',
    value: props.data.onlineUsers,
    icon: '👥',
    color: '#feb019',
  },
  {
    label: '转化率',
    value: props.data.conversionRate,
    suffix: '%',
    decimals: 2,
    icon: '📈',
    color: '#ff4560',
  },
]);

// 数字翻牌动画：检测值变化触发
const animatingIndex = ref<null | number>(null);

watch(
  () => [
    props.data.gmv,
    props.data.orderCount,
    props.data.onlineUsers,
    props.data.conversionRate,
  ],
  () => {
    animatingIndex.value = null;
  },
);
</script>

<template>
  <div class="flip-cards">
    <div
      v-for="card in cards"
      :key="card.label"
      class="flip-card"
      :style="{ '--card-color': card.color }"
    >
      <div class="card-glow"></div>
      <div class="card-icon">{{ card.icon }}</div>
      <div class="card-label">{{ card.label }}</div>
      <div class="card-value">
        <CountTo
          :end-val="card.value"
          :decimals="card.decimals || 0"
          :prefix="card.prefix || ''"
          :suffix="card.suffix || ''"
          :duration="1500"
          separator=","
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.flip-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  padding: 0 20px;
}

.flip-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 16px;
  overflow: hidden;
  background: linear-gradient(
    135deg,
    rgb(10 40 80 / 80%) 0%,
    rgb(5 20 50 / 90%) 100%
  );
  border: 1px solid rgb(0 180 255 / 20%);
  border-radius: 8px;
  transition: all 0.3s;
}

.flip-card:hover {
  border-color: var(--card-color);
  box-shadow: 0 0 20px rgb(0 180 255 / 15%);
}

.card-glow {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 2px;
  background: linear-gradient(
    90deg,
    transparent,
    var(--card-color),
    transparent
  );
}

.card-icon {
  margin-bottom: 8px;
  font-size: 28px;
}

.card-label {
  margin-bottom: 8px;
  font-size: 14px;
  color: rgb(126 200 255 / 70%);
  letter-spacing: 1px;
}

.card-value {
  font-size: 28px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #fff;
}
</style>
