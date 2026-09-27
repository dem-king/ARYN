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
    <div class="ship-workbench">
      <span class="ship-icon">⚓</span>
      <strong class="ship-name">悦航1号</strong>
      <span class="ship-dot">·</span>
      <span class="ship-summary">上海港 3号泊位 · 明天 00:15</span>
      <span class="ship-chevron">›</span>
      <span v-if="showData.showFrequent" class="ship-frequent">常购</span>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-ship-workbench 逐值对齐（该组件高 88rpx）。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px。
  真实船舶与靠港取决于访问者，此处为示意文案。
-->
<style scoped lang="scss">
.ship-workbench {
  display: flex;
  align-items: center;
  height: 44px;
  overflow: hidden;
  font-size: 13px;
  background: #fff;
}

.ship-icon {
  flex: none;
  margin-right: 6px;
  font-size: 16px;
  color: #4d7fff;
}

.ship-name {
  flex: none;
  font-size: 13px;
  font-weight: 700;
}

.ship-dot {
  flex: none;
  margin: 0 5px;
  color: #d1d5db;
}

.ship-summary {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}

.ship-chevron {
  flex: none;
  margin-left: 3px;
  font-size: 12px;
  color: #9ca3af;
}

.ship-frequent {
  flex: none;
  padding-left: 10px;
  margin-left: 8px;
  font-size: 12px;
  color: #b45309;
  border-left: 1px solid #e5e7eb;
}
</style>
