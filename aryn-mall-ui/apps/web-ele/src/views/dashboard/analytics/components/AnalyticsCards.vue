<script lang="ts" setup>
import { computed } from 'vue';

import {
  Headset,
  List,
  Money,
  RefreshLeft,
  User,
} from '@element-plus/icons-vue';

import { calculateDelta } from '../../shared/compare';
import MetricCard from '../../shared/MetricCard.vue';

interface CardsData {
  afterSalesOrders: number;
  aov: number;
  gmv: number;
  paidBuyers: number;
  paidOrders: number;
  refundAmount: number;
}

const props = withDefaults(
  defineProps<{
    data: CardsData;
    deltaLabel?: string;
    loading?: boolean;
    previous?: CardsData | null;
  }>(),
  {
    deltaLabel: '较上期',
    loading: false,
    previous: null,
  },
);

const delta = (key: keyof CardsData) =>
  calculateDelta(props.data[key], props.previous?.[key]);

const cards = computed(
  () =>
    [
      {
        decimals: 2,
        delta: delta('gmv'),
        icon: Money,
        invert: false,
        prefix: '¥',
        title: '成交额 (GMV)',
        tone: 'blue',
        tooltip: '统计周期内已支付订单金额',
        value: props.data.gmv,
      },
      {
        delta: delta('paidOrders'),
        icon: List,
        invert: false,
        title: '支付订单数',
        tone: 'green',
        tooltip: '统计周期内已支付的订单数',
        value: props.data.paidOrders,
      },
      {
        delta: delta('paidBuyers'),
        icon: User,
        invert: false,
        title: '支付买家数',
        tone: 'orange',
        tooltip: '统计周期内有支付行为的用户数',
        value: props.data.paidBuyers,
      },
      {
        decimals: 2,
        delta: delta('aov'),
        icon: Money,
        invert: false,
        prefix: '¥',
        title: '客单价',
        tone: 'purple',
        tooltip: '成交额 / 支付订单数',
        value: props.data.aov,
      },
      {
        delta: delta('afterSalesOrders'),
        icon: Headset,
        invert: true,
        title: '售后订单数',
        tone: 'red',
        tooltip: '统计周期内创建的售后单对应订单数，下降为好',
        value: props.data.afterSalesOrders,
      },
      {
        decimals: 2,
        delta: delta('refundAmount'),
        icon: RefreshLeft,
        invert: true,
        prefix: '¥',
        title: '退款金额',
        tone: 'red',
        tooltip: '统计周期内创建且已到账的退款金额，下降为好',
        value: props.data.refundAmount,
      },
    ] as const,
);
</script>

<template>
  <div class="mb-4 grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
    <MetricCard
      v-for="card in cards"
      :key="card.title"
      v-bind="card"
      :delta-label="deltaLabel"
      :loading="loading"
    />
  </div>
</template>
