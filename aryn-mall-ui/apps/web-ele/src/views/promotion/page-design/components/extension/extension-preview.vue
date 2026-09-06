<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  showData: Record<string, unknown>;
  type: string;
}>();

const entries = computed(
  () =>
    (props.showData.entries ?? props.showData.items ?? []) as Array<
      Record<string, unknown>
    >,
);
const title = computed(() => (props.showData.title ?? '') as string);
</script>

<template>
  <div class="extension-preview">
    <div v-if="type === 'goods-waterfall'" class="preview-block">
      <p v-if="title" class="preview-title">{{ title }}</p>
      <div class="preview-waterfall">
        <div
          v-for="index in Number(showData.count) || 4"
          :key="index"
          class="preview-goods"
        ></div>
      </div>
    </div>
    <div v-else-if="type === 'coupon-combo'" class="preview-block">
      <div
        v-for="index in Number(showData.count) || 3"
        :key="index"
        class="preview-coupon"
      >
        <span class="coupon-value">¥</span>
        <span class="coupon-line"></span>
      </div>
    </div>
    <div
      v-else-if="type === 'member-benefits' || type === 'service-promise'"
      class="preview-block"
    >
      <p v-if="title" class="preview-title">{{ title }}</p>
      <div class="preview-grid">
        <div
          v-for="(entry, index) in entries"
          :key="index"
          class="preview-entry"
        >
          <span class="entry-icon"></span>
          <span class="entry-text">{{ entry.title }}</span>
        </div>
      </div>
    </div>
    <div
      v-else-if="type === 'bottom-nav'"
      class="preview-block preview-bottomnav"
    >
      <div
        v-for="(item, index) in entries"
        :key="index"
        class="preview-bottomnav-item"
        :style="{ color: (showData.activeColor as string) || '#ff5000' }"
      >
        {{ item.text }}
      </div>
    </div>
    <div v-else-if="type === 'video-live'" class="preview-block">
      <p v-if="title" class="preview-title">{{ title }}</p>
      <div class="preview-video" :data-mode="showData.mode">视频 / 直播</div>
    </div>
    <div v-else class="preview-block">{{ type }}</div>
  </div>
</template>

<style scoped>
.extension-preview {
  padding: 10px;
}

.preview-block {
  padding: 8px;
}

.preview-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.preview-waterfall {
  column-count: 2;
  column-gap: 8px;
}

.preview-goods {
  height: 88px;
  margin-bottom: 8px;
  background: var(--el-fill-color);
  border-radius: 6px;
  break-inside: avoid;
}

.preview-coupon {
  display: flex;
  gap: 10px;
  align-items: center;
  height: 44px;
  padding: 0 12px;
  margin-bottom: 6px;
  background: linear-gradient(135deg, #ff5000, #ff8a00);
  border-radius: 6px;
}

.coupon-value {
  font-weight: 700;
  color: #fff;
}

.coupon-line {
  flex: 1;
  height: 6px;
  background: rgb(255 255 255 / 40%);
  border-radius: 3px;
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.preview-entry {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px;
  background: var(--el-fill-color);
  border-radius: 6px;
}

.entry-icon {
  width: 18px;
  height: 18px;
  background: var(--el-color-primary-light-7);
  border-radius: 4px;
}

.entry-text {
  font-size: 12px;
}

.preview-bottomnav {
  display: flex;
  justify-content: space-around;
  padding: 10px 0;
  background: var(--el-fill-color-light);
}

.preview-bottomnav-item {
  font-size: 12px;
}

.preview-video {
  display: grid;
  place-items: center;
  height: 120px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  border-radius: 6px;
}
</style>
