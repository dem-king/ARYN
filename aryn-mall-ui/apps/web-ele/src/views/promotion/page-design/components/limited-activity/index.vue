<script setup lang="ts">
import type { LimitedActivityProps } from './types';

import { watch } from 'vue';

import { Clock, Picture } from '@element-plus/icons-vue';
import { ElIcon, ElImage, ElTag } from 'element-plus';

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
</script>

<template>
  <RetailPreviewFrame
    :common-style="showData.commonStyle"
    :message="state.message"
    :status="state.status"
    :title="showData.title"
  >
    <div class="activity-list">
      <article v-for="item in state.items" :key="item.id" class="activity-card">
        <ElImage
          v-if="item.imageUrl"
          :alt="item.name"
          :src="item.imageUrl"
          fit="cover"
        >
          <template #error>
            <div class="activity-card__image">
              <ElIcon><Picture /></ElIcon>
            </div>
          </template>
        </ElImage>
        <div v-else class="activity-card__image">
          <ElIcon><Picture /></ElIcon>
        </div>
        <div class="activity-card__body">
          <div class="activity-card__title">{{ item.name }}</div>
          <ElTag effect="light" size="small" type="danger">限时价</ElTag>
          <div class="activity-card__price">
            <strong>¥{{ item.activityPrice.toFixed(2) }}</strong>
            <del>¥{{ item.originalPrice.toFixed(2) }}</del>
          </div>
          <div v-if="showData.showCountdown" class="activity-card__time">
            <ElIcon><Clock /></ElIcon>
            <span>{{ item.endTime || '以活动时间为准' }}</span>
          </div>
        </div>
      </article>
    </div>
  </RetailPreviewFrame>
</template>

<style scoped>
.activity-list {
  display: grid;
  gap: 8px;
}

.activity-card {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 10px;
  min-height: 92px;
  padding: 8px;
  background: #fff;
  border: 1px solid #fee2e2;
  border-radius: 6px;
}

.activity-card :deep(.el-image),
.activity-card__image {
  display: grid;
  place-items: center;
  width: 92px;
  height: 92px;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 4px;
}

.activity-card__body {
  min-width: 0;
}

.activity-card__title {
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.activity-card__price {
  display: flex;
  gap: 6px;
  align-items: baseline;
  margin-top: 5px;
}

.activity-card__price strong {
  font-size: 16px;
  color: #dc2626;
}

.activity-card__price del {
  font-size: 11px;
  color: #94a3b8;
}

.activity-card__time {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-top: 7px;
  overflow: hidden;
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
}
</style>
