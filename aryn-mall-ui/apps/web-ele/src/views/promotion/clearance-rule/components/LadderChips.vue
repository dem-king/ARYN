<script lang="ts" setup>
import type { LadderBand } from '../logic';

import { ElTooltip } from 'element-plus';

import { formatBandDiscount, formatBandRange, formatDiscount } from '../logic';

/**
 * 生效价格阶梯展示（顶部概览与弹窗预览共用）。
 *
 * 只负责渲染 `buildLadder` / `mergeAdjacentBands` 的输出，不参与业务计算：
 * - `highlight-deepest`：顶部概览高亮最临近到期的档；
 * - `current-index`：弹窗预览高亮「正在编辑的规则所在档」；
 * - `show-tooltip`：顶部概览用 tooltip 补充命中条数与规则名（键盘可聚焦）。
 */
withDefaults(
  defineProps<{
    bands: LadderBand[];
    compact?: boolean;
    currentIndex?: number;
    highlightDeepest?: boolean;
    /** 列表的无障碍名称，如「当前生效价格阶梯」 */
    label?: string;
    showTooltip?: boolean;
  }>(),
  {
    compact: false,
    currentIndex: -1,
    highlightDeepest: false,
    label: '生效价格阶梯',
    showTooltip: false,
  },
);

function bandTooltip(band: LadderBand) {
  if (band.rate === null) {
    return '剩余时长超过所有已启用档位，按基础出清价销售';
  }
  const drivers =
    band.driverNames.length > 0 ? `（${band.driverNames.join('、')}）` : '';
  return `最多命中 ${band.hitCount} 条规则，只取最深一档 ${formatDiscount(
    band.rate,
  )}${drivers}，不连乘`;
}
</script>

<template>
  <div
    class="cr-ladder"
    :class="{ 'cr-ladder--compact': compact }"
    role="list"
    :aria-label="label"
  >
    <ElTooltip
      v-for="(band, index) in bands"
      :key="`${band.fromHours}-${band.toHours}`"
      :content="bandTooltip(band)"
      :disabled="!showTooltip"
      placement="top"
    >
      <div
        class="cr-band"
        :class="{
          'is-current': index === currentIndex,
          'is-deepest':
            highlightDeepest &&
            index === bands.length - 1 &&
            band.rate !== null,
          'is-original': band.rate === null,
        }"
        role="listitem"
        :tabindex="showTooltip ? 0 : undefined"
      >
        <span class="cr-band__range">{{ formatBandRange(band) }}</span>
        <span class="cr-band__discount">{{ formatBandDiscount(band) }}</span>
      </div>
    </ElTooltip>
  </div>
</template>

<style scoped>
.cr-ladder {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cr-band {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 132px;
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.cr-band:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.cr-band.is-original {
  background: transparent;
  border-style: dashed;
}

.cr-band.is-deepest {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.cr-band.is-current {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7) inset;
}

.cr-band__range {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.cr-band__discount {
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

.cr-band.is-deepest .cr-band__discount {
  color: var(--el-color-primary);
}

.cr-band.is-original .cr-band__discount {
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.cr-ladder--compact .cr-band {
  min-width: 110px;
  padding: 6px 10px;
}

.cr-ladder--compact .cr-band__discount {
  font-size: 14px;
}
</style>
