<script setup lang="ts">
/**
 * 船舶工作台 · 编辑器预览
 *
 * 真实数据来自「当前登录用户此刻的船舶与靠港」，编辑器无法取得，
 * 因此这里渲染一条与移动端等高的示意状态条，让运营能判断位置与视觉重量。
 */
import type { ShipWorkbenchProps } from './types';

import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';

defineProps<{ showData: ShipWorkbenchProps }>();

// 无异步请求：真实船舶与靠港取决于访问者，编辑器只需呈现位置与视觉重量，
// 因此直接给一个静态的 data 状态，而不是走 createRetailPreviewController。
const previewStatus = 'data' as const;
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :status="previewStatus"
  >
    <div class="ship-bar">
      <span class="ship-bar__icon">⚓</span>
      <strong>悦航1号</strong>
      <span class="ship-bar__dot">·</span>
      <span class="ship-bar__summary">上海港 3号泊位 · 明天 00:15</span>
      <span class="ship-bar__chevron">›</span>
      <span v-if="showData.showFrequent" class="ship-bar__frequent">常购</span>
    </div>
    <p class="ship-bar__hint">
      预览为示意数据；实际展示取决于访问者是否登录且已关联船舶
    </p>
  </RetailPreviewFrame>
</template>

<style scoped>
.ship-bar {
  display: flex;
  align-items: center;
  height: 44px;
  padding: 0 12px;
  overflow: hidden;
  font-size: 13px;
  background: #fff;
  border-radius: 8px;
}

.ship-bar__icon {
  margin-right: 6px;
  font-size: 16px;
}

.ship-bar strong {
  flex: none;
  font-weight: 700;
}

.ship-bar__dot {
  margin: 0 5px;
  color: #cbd5e1;
}

.ship-bar__summary {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.ship-bar__chevron {
  margin-left: 3px;
  color: #cbd5e1;
}

.ship-bar__frequent {
  flex: none;
  padding-left: 10px;
  margin-left: 8px;
  font-size: 12px;
  color: #b45309;
  border-left: 1px solid #e2e8f0;
}

.ship-bar__hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
