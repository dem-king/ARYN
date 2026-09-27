<script setup lang="ts">
/**
 * 补给单卡片 · 编辑器预览
 *
 * 真实数据来自「当前登录用户此刻进行中的共享购物车」，编辑器无法取得，
 * 因此这里渲染一张示意卡片，让运营能判断位置与视觉重量。
 */
import type { ReplenishCardProps } from './types';

import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';

defineProps<{ showData: ReplenishCardProps }>();

// 无异步请求：真实清单取决于访问者，编辑器只需呈现位置与视觉重量。
const previewStatus = 'data' as const;
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :status="previewStatus"
  >
    <div class="replenish-card">
      <div class="card-head">
        <div class="card-main">
          <div class="card-title-row">
            <span class="card-title">{{ showData.title }}</span>
            <span class="card-location">翠屏港 3 号泊位</span>
          </div>
          <span class="card-overview">12 项 · 3 人参与 · 合计 ¥1,286</span>
        </div>
        <span v-if="showData.showBatchAdd" class="card-action">按单加购</span>
      </div>
      <div class="card-progress">
        <div class="progress-track">
          <div class="progress-bar" style="width: 62%"></div>
        </div>
        <span class="progress-text">已采 62%</span>
      </div>
      <div v-if="showData.showPreview" class="card-preview">
        <span class="card-preview-text">番茄 2 · 矿泉水 5 · 抽纸 10 …</span>
        <span class="card-chevron">›</span>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-replenish-card 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半。真实清单取决于访问者，此处为示意数据。
-->
<style scoped lang="scss">
.card-inner,
.replenish-card {
  padding: 12px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(135deg, #082e63, #0b63e5);
  border-radius: 12px;
}

.card-head {
  display: flex;
  align-items: flex-start;
}

.card-main {
  flex: 1;
  min-width: 0;
}

.card-title-row {
  display: flex;
  align-items: center;
}

.card-title {
  flex: none;
  font-size: 15px;
  font-weight: 800;
}

.card-location {
  padding: 1px 6px;
  margin-left: 6px;
  font-size: 10px;
  background: rgb(255 255 255 / 22%);
  border-radius: 999px;
}

.card-overview {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.85;
}

.card-action {
  flex: none;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 700;
  color: #0a4da3;
  background: #fff;
  border-radius: 999px;
}

.card-progress {
  margin-top: 8px;
}

.progress-track {
  height: 5px;
  overflow: hidden;
  background: rgb(255 255 255 / 24%);
  border-radius: 999px;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #ffb25c, #f2741d);
  border-radius: 999px;
}

.progress-text {
  display: block;
  margin-top: 5px;
  font-size: 11px;
  opacity: 0.9;
}

.card-preview {
  display: flex;
  align-items: center;
  margin-top: 7px;
  font-size: 11px;
  opacity: 0.92;
}

.card-preview-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-chevron {
  flex: none;
  margin-left: 4px;
  font-size: 12px;
}
</style>
