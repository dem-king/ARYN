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
      <div class="replenish-card__head">
        <div class="replenish-card__main">
          <div class="replenish-card__title">
            {{ showData.title }}
            <span class="replenish-card__tag">翠屏港 3 号泊位</span>
          </div>
          <div class="replenish-card__overview">
            12 项 · 3 人参与 · 合计 ¥1,286
          </div>
        </div>
        <span v-if="showData.showBatchAdd" class="replenish-card__action"
          >按单加购</span
        >
      </div>
      <div v-if="showData.showPreview" class="replenish-card__preview">
        番茄 2 · 矿泉水 5 · 抽纸 10 …
      </div>
    </div>
    <p class="replenish-card__hint">
      预览为示意数据；实际展示取决于访问者是否登录、已关联船舶且存在进行中的清单
    </p>
  </RetailPreviewFrame>
</template>

<style scoped>
.replenish-card {
  padding: 12px;
  color: #fff;
  background: linear-gradient(135deg, #082e63, #0b63e5);
  border-radius: 12px;
}

.replenish-card__head {
  display: flex;
  align-items: flex-start;
}

.replenish-card__main {
  flex: 1;
  min-width: 0;
}

.replenish-card__title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 800;
}

.replenish-card__tag {
  padding: 1px 6px;
  margin-left: 6px;
  font-size: 10px;
  font-weight: 400;
  background: rgb(255 255 255 / 22%);
  border-radius: 999px;
}

.replenish-card__overview {
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.85;
}

.replenish-card__action {
  flex: none;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 700;
  color: #0a4da3;
  background: #fff;
  border-radius: 999px;
}

.replenish-card__preview {
  margin-top: 9px;
  font-size: 11px;
  opacity: 0.92;
}

.replenish-card__hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
