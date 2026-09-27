<script setup lang="ts">
import type { LimitedActivityProps } from './types';

import { computed, watch } from 'vue';

import { Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import { loadLimitedActivities } from '../common/retail-preview/retail-data';
import RetailPreviewFrame from '../common/retail-preview/retail-preview-frame.vue';
import { createRetailPreviewController } from '../common/retail-preview/use-retail-preview';
import { validateLimitedActivity } from './types';

const props = defineProps<{ showData: LimitedActivityProps }>();
const controller =
  createRetailPreviewController<
    Awaited<ReturnType<typeof loadLimitedActivities>>[number]
  >();
const { state } = controller;

watch(
  () => props.showData,
  (showData) =>
    controller.load(validateLimitedActivity(showData), () =>
      loadLimitedActivities(showData),
    ),
  { deep: true, immediate: true },
);

/** 与小程序一致：倒计时按键口径展示剩余时长，已结束或时间不可解析时给结束文案 */
function remainingLabel(endTime: string, status: string) {
  if (status === 'ended') return '活动已结束';
  const target = Date.parse(endTime);
  if (Number.isNaN(target)) return '活动已结束';
  const diff = target - Date.now();
  if (diff <= 0) return '活动已结束';
  const totalMinutes = Math.floor(diff / 60_000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  const clock = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
  return days > 0 ? `剩余 ${days}天${clock}` : `剩余 ${clock}`;
}

const items = computed(() =>
  state.items.map((item) => ({
    ...item,
    timeLabel: remainingLabel(item.endTime, item.status),
  })),
);
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    type-label="拼团"
  >
    <div class="section-title">{{ showData.title }}</div>
    <div class="activity-list">
      <article v-for="item in items" :key="item.id" class="activity-item">
        <ElImage
          v-if="item.imageUrl"
          :alt="item.name"
          class="activity-image"
          :src="item.imageUrl"
          fit="cover"
        >
          <template #error>
            <div class="activity-image activity-image--empty">
              <ElIcon><Picture /></ElIcon>
            </div>
          </template>
        </ElImage>
        <div v-else class="activity-image activity-image--empty">
          <ElIcon><Picture /></ElIcon>
        </div>
        <div class="activity-content">
          <div class="activity-name">{{ item.name || '拼团' }}</div>
          <div v-if="showData.showCountdown" class="activity-time">
            {{ item.timeLabel }}
          </div>
          <div class="activity-price-row">
            <span class="activity-price">
              ￥{{ item.activityPrice.toFixed(2) }}
            </span>
            <span v-if="item.originalPrice" class="activity-original">
              ￥{{ item.originalPrice.toFixed(2) }}
            </span>
          </div>
        </div>
      </article>
    </div>
  </RetailPreviewFrame>
</template>

<!--
  样式与小程序 diy-limited-activity 逐值对齐。
  换算口径：小程序屏宽在 rpx 下恒为 750，画布正好 375px，故 1rpx = 0.5px，
  本文件所有 px 值都是对应 rpx 值的一半；改任一端都要同步另一端。
-->
<style scoped lang="scss">
.section-title {
  margin-bottom: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.activity-item {
  display: flex;
  gap: 9px;
  min-height: 70px;
  overflow: hidden;
  background: #fff;
  border: 0.5px solid #ffe1dc;
  border-radius: 4px;
}

.activity-image {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 75px;
  min-height: 70px;
  font-size: 11px;
  color: #e5484d;
  background: #fff1ee;
}

.activity-content {
  flex: 1;
  min-width: 0;
  padding: 8px 8px 8px 0;
}

.activity-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13.5px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
}

.activity-time {
  margin-top: 5px;
  font-size: 11px;
  color: #e5484d;
}

.activity-price-row {
  display: flex;
  gap: 5px;
  align-items: baseline;
  margin-top: 7px;
}

.activity-price {
  font-size: 15px;
  font-weight: 700;
  color: #e5484d;
}

.activity-original {
  font-size: 10.5px;
  color: #a8abb2;
  text-decoration: line-through;
}
</style>
