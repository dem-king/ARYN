<script setup lang="ts">
import type { PriceParts } from '@/components/diy/price-display'
import { computed } from 'vue'
import { splitAmountText, splitPrice } from '@/components/diy/price-display'

interface Props {
  /** 金额值（数字或数字字符串） */
  value: unknown
  /**
   * fixed：两位小数（与 wd-text mode=price 同口径，含千分位）；
   * trim：去掉末尾 0（与卡片原 formatAmount 同口径）。
   */
  format?: 'fixed' | 'trim'
  /** 货币符号；传空串不渲染符号 */
  symbol?: string
}

const props = withDefaults(defineProps<Props>(), {
  format: 'fixed',
  symbol: '¥',
})

const parts = computed<PriceParts | null>(() => {
  const amount = Number(props.value)
  if (!Number.isFinite(amount) || amount < 0)
    return null
  if (props.format === 'trim')
    return splitAmountText(String(Number(amount.toFixed(2))))
  return splitPrice(amount)
})
</script>

<template>
  <text v-if="parts" class="price-text">
    <text v-if="symbol" class="price-text__symbol">{{ symbol }}</text>
    <text class="price-text__integer">{{ parts?.integer }}</text>
    <text v-if="parts?.decimal" class="price-text__decimal">.{{ parts.decimal }}</text>
  </text>
</template>

<style lang="scss" scoped>
/**
 * 价格排版：整数大、小数小、货币符号降一档（对齐小象超市的价格标签）。
 * 子级尺寸用 em 跟随宿主字号；颜色/字重/行高天然从宿主继承。
 */
.price-text__symbol {
  font-size: var(--price-symbol-size, 0.68em);
}

.price-text__integer {
  font-size: 1em;
}

.price-text__decimal {
  font-size: var(--price-decimal-size, 0.72em);
}
</style>
