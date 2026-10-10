<script setup lang="ts">
/**
 * 补给单卡片 · 编辑器预览
 *
 * 真实数据来自「当前登录用户此刻进行中的共享购物车」，编辑器无法取得，
 * 因此这里渲染一张示意卡片，让运营能判断位置与视觉重量。
 *
 * 无数据策略影响预览形态（与小程序 diy-replenish-card 同口径）：
 * - hide：渲染示意数据卡（有单时的视觉重量）；
 * - placeholder：渲染空态引导卡（发起 / 导入清单两个入口），与 C 端占位一致。
 */
import type { ReplenishCardProps } from './types';

import { computed } from 'vue';

import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';

const props = defineProps<{ showData: ReplenishCardProps }>();

const previewStatus = 'data' as const;
// 按钮在画布上不可点：预览只呈现形态，跳转由 C 端实现
const showPlaceholder = computed(
  () => props.showData.emptyStrategy === 'placeholder',
);
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :status="previewStatus"
  >
    <!-- 空态占位引导卡：与小程序 guide-* 逐值对齐（px = rpx / 2） -->
    <div v-if="showPlaceholder" class="guide-inner">
      <span class="guide-title">还没有进行中的补给单</span>
      <span class="guide-desc"
        >发起补给单与同船成员合并采购，或直接导入 Excel 清单一键下单</span
      >
      <div class="guide-actions">
        <span class="guide-btn guide-btn--ghost">发起补给单</span>
        <span class="guide-btn guide-btn--primary">导入清单下单</span>
      </div>
    </div>
    <div v-else class="replenish-card">
      <div class="card-head">
        <div class="card-main">
          <div class="card-title-row">
            <span class="card-title">{{ showData.title }}</span>
            <span class="card-location">翠屏港 3 号泊位</span>
          </div>
          <span class="card-overview">12 项 · 3 人参与 · 预估 ¥1,286</span>
        </div>
        <span v-if="showData.showBatchAdd" class="card-action">按单加购</span>
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

/* 空态占位引导卡：与小程序 guide-* 逐值对齐（px = rpx / 2） */
.guide-inner {
  padding: 12px;
  text-align: center;
  background: #fff;
  border: 1px dashed rgb(11 99 229 / 35%);
  border-radius: 12px;
}

.guide-title {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: #172033;
}

.guide-desc {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  color: #7a8699;
}

.guide-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.guide-btn {
  flex: 1;
  height: 32px;
  font-size: 12px;
  line-height: 32px;
  border-radius: 999px;
}

.guide-btn--ghost {
  color: #0b63e5;
  background: #fff;
  border: 1px solid #0b63e5;
}

.guide-btn--primary {
  color: #fff;
  background: #0b63e5;
}
</style>
