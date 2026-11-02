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
 *
 * 2026-11 视觉重构：旧版是「深蓝渐变整块 + 半透明地址胶囊」，地址一长就折行
 * 撑高卡片；新版对齐全站「白卡 + 发丝线 + 轻投影 + 中性灰 + 品牌色点状强调」，
 * 结构 class 与 C 端同名，样式逐值换算（1rpx = 0.5px），见文件末尾注释。
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
    <!-- 有进行中的补给单：整卡示意（画布不可点，只呈现结构 / 视觉重量） -->
    <div v-if="!showPlaceholder" class="rp-card">
      <div class="rp-head">
        <!-- 品牌色实底图标块：整卡唯一的实心色块，作为视觉锚点 -->
        <div class="rp-icon">
          <svg
            aria-hidden="true"
            class="rp-icon-glyph"
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            viewBox="0 0 24 24"
          >
            <path
              d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"
            />
            <rect height="3.5" rx="1" width="6" x="9" y="3.5" />
            <path d="m8.8 13.2 1.7 1.7 3.7-3.7" />
            <path d="M14.5 17h2" />
          </svg>
        </div>
        <div class="rp-heading">
          <div class="rp-title-row">
            <span class="rp-title">{{ showData.title }}</span>
            <span class="rp-status">
              <span class="rp-status-dot"></span>
              <span class="rp-status-text">收集中</span>
            </span>
          </div>
          <!-- 长地址收成 1 行省略：老版本在这里折 3 行把卡片撑高、压住按钮 -->
          <div class="rp-loc">
            <svg
              aria-hidden="true"
              class="rp-loc-icon"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              viewBox="0 0 24 24"
            >
              <path
                d="M12 21s6.5-5.5 6.5-10.5a6.5 6.5 0 1 0-13 0C5.5 15.5 12 21 12 21Z"
              />
              <circle cx="12" cy="10.5" r="2.5" />
            </svg>
            <span class="rp-loc-text"
              >北京市东城区东直门南大街甲1号（悦航配 …</span
            >
          </div>
        </div>
        <div v-if="showData.showBatchAdd" class="rp-action">
          <span class="rp-action-text">按单加购</span>
          <svg
            aria-hidden="true"
            class="rp-action-icon"
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            viewBox="0 0 24 24"
          >
            <path d="M5 12h14" />
            <path d="m13 6 6 6-6 6" />
          </svg>
        </div>
      </div>

      <div class="rp-divider"></div>

      <div class="rp-meta">
        <div class="rp-meta-stats">
          <div class="rp-stat">
            <span class="rp-stat-num">3</span>
            <span class="rp-stat-unit">项商品</span>
          </div>
          <span class="rp-stat-sep">·</span>
          <div class="rp-stat">
            <span class="rp-stat-num">1</span>
            <span class="rp-stat-unit">人参与</span>
          </div>
        </div>
        <div class="rp-amount">
          <span class="rp-amount-label">预估</span>
          <span class="rp-amount-symbol">¥</span>
          <span class="rp-amount-value">189.20</span>
        </div>
      </div>

      <!-- 明细预览：浅灰面板收口，1 行省略 -->
      <div v-if="showData.showPreview" class="rp-preview">
        <span class="rp-preview-text"
          >烟台大樱桃 ×2 · 广西百香果 ×5 · 上海青小青菜 ×10 …</span
        >
        <svg
          aria-hidden="true"
          class="rp-preview-icon"
          fill="none"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          viewBox="0 0 24 24"
        >
          <path d="m9 6 6 6-6 6" />
        </svg>
      </div>
    </div>

    <!-- 空态占位引导卡：与 C 端 rp-guide 同构（发起 / 导入清单两个入口） -->
    <div v-else class="rp-guide">
      <div class="rp-guide-head">
        <div class="rp-icon">
          <svg
            aria-hidden="true"
            class="rp-icon-glyph"
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            viewBox="0 0 24 24"
          >
            <path d="M12 5v14" />
            <path d="M5 12h14" />
          </svg>
        </div>
        <div class="rp-guide-heading">
          <span class="rp-guide-title">还没有进行中的补给单</span>
          <span class="rp-guide-desc"
            >发起补给单与同船成员合并采购，或直接导入 Excel 清单一键下单</span
          >
        </div>
      </div>
      <div class="rp-guide-actions">
        <span class="rp-guide-btn rp-guide-btn--ghost">发起补给单</span>
        <span class="rp-guide-btn rp-guide-btn--primary">导入清单下单</span>
      </div>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-replenish-card 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半。真实清单取决于访问者，此处为示意数据。

  配色令牌与 C 端同一套（2026-11 重设计）：白卡 + 发丝线 + 轻投影 + 中性灰，
  品牌色只做点状强调（图标块 / 状态点 / 主 CTA）。画布固定用演示租户主色
  #ff5500（与 coupon-receive 预览的固定促销色同口径），C 端走主题变量。
  图标在 C 端是 UnoCSS 图标字体，画布用同尺寸内联 SVG 示意。
-->
<style scoped lang="scss">
.rp-card {
  padding: 12px 12px 11px;
  overflow: hidden;
  color: #1f2329;
  background: #fff;
  border: 0.5px solid #eef0f3;
  border-radius: 12px;
  box-shadow:
    0 1px 3px rgb(15 23 42 / 3%),
    0 6px 14px rgb(15 23 42 / 5%);
}

.rp-head {
  display: flex;
  align-items: center;
}

/* 品牌色实底图标块：38px × 38px ↔ C 端 76rpx */
.rp-icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: #fff;
  background: #f50;
  border-radius: 11px;
}

/* C 端是 38rpx 图标字体；画布用 19px 内联 SVG 示意（视觉尺寸同值） */
.rp-icon-glyph {
  width: 19px;
  height: 19px;
  line-height: 1;
}

.rp-heading {
  flex: 1;
  min-width: 0;
  margin-left: 10px;
}

.rp-title-row {
  display: flex;
  align-items: center;
}

.rp-title {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.25;
  color: #1f2329;
  white-space: nowrap;
}

/* 状态胶囊：中性底 + 品牌色状态点，只表达「阶段」，不抢主 CTA */
.rp-status {
  display: flex;
  flex: none;
  align-items: center;
  padding: 2px 6px 2px 5px;
  margin-left: 6px;
  background: #f4f5f7;
  border-radius: 4px;
}

.rp-status-dot {
  flex: none;
  width: 4px;
  height: 4px;
  margin-right: 3px;
  background: #f50;
  border-radius: 50%;
}

.rp-status-text {
  flex: none;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.2;
  color: #5b616e;
}

/* 位置行：长地址必须收成 1 行省略，不再折行撑高卡片 */
.rp-loc {
  display: flex;
  align-items: center;
  min-width: 0;
  margin-top: 4px;
}

.rp-loc-icon {
  flex: none;
  width: 11px;
  height: 11px;
  margin-right: 3px;
  color: #9ca3af;
}

.rp-loc-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  line-height: 1.3;
  color: #6b7280;
  white-space: nowrap;
}

/* 主 CTA：品牌实底胶囊（与券体系重设计同口径：主行动用实底，不用渐变） */
.rp-action {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 12px 0 13px;
  margin-left: 8px;
  background: #f50;
  border-radius: 999px;
  box-shadow: 0 4px 10px rgb(15 23 42 / 12%);
}

.rp-action-text {
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  color: #fff;
}

.rp-action-icon {
  width: 11px;
  height: 11px;
  margin-left: 2px;
  color: #fff;
}

.rp-divider {
  height: 0.5px;
  margin: 8px 0 7px;
  background: #eef0f3;
}

/* 摘要行：左边「N 项商品 · M 人参与」，右边「预估 ¥X.XX」——金额靠右形成张力 */
.rp-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.rp-meta-stats {
  display: flex;
  flex: 1;
  align-items: baseline;
  min-width: 0;
}

.rp-stat {
  display: flex;
  align-items: baseline;
}

.rp-stat-num {
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  color: #1f2329;
}

.rp-stat-unit {
  margin-left: 2px;
  font-size: 11px;
  line-height: 1;
  color: #6b7280;
}

.rp-stat-sep {
  margin: 0 6px;
  font-size: 11px;
  line-height: 1;
  color: #c9cdd4;
}

.rp-amount {
  display: flex;
  flex: none;
  align-items: baseline;
  margin-left: 8px;
}

.rp-amount-label {
  margin-right: 3px;
  font-size: 10px;
  line-height: 1;
  color: #6b7280;
}

.rp-amount-symbol {
  margin-right: 1px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  color: #5b616e;
}

.rp-amount-value {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  color: #1f2329;
}

/* 明细预览：浅灰面板收口，1 行省略 */
.rp-preview {
  display: flex;
  align-items: center;
  padding: 6px 9px;
  margin-top: 6px;
  background: #f7f8fa;
  border-radius: 8px;
}

.rp-preview-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  line-height: 1.4;
  color: #5b616e;
  white-space: nowrap;
}

.rp-preview-icon {
  flex: none;
  width: 11px;
  height: 11px;
  margin-left: 4px;
  color: #9ca3af;
}

/* ---------- 空态占位引导卡 ---------- */
.rp-guide {
  padding: 12px;
  background: #fff;
  border: 0.5px solid #eef0f3;
  border-radius: 12px;
  box-shadow:
    0 1px 3px rgb(15 23 42 / 3%),
    0 6px 14px rgb(15 23 42 / 5%);
}

.rp-guide-head {
  display: flex;
  align-items: center;
}

.rp-guide-heading {
  flex: 1;
  min-width: 0;
  margin-left: 10px;
}

.rp-guide-title {
  display: block;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.3;
  color: #1f2329;
}

.rp-guide-desc {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.5;
  color: #6b7280;
}

.rp-guide-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.rp-guide-btn {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  height: 36px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  border-radius: 999px;
}

.rp-guide-btn--ghost {
  color: #1f2329;
  background: #f4f5f7;
}

.rp-guide-btn--primary {
  color: #fff;
  background: #f50;
  box-shadow: 0 4px 10px rgb(15 23 42 / 12%);
}
</style>
