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
        <div class="preview-coupon__face">
          <div class="preview-coupon__notch preview-coupon__notch--left"></div>
          <div class="preview-coupon__notch preview-coupon__notch--right"></div>
          <span class="preview-coupon__value">¥10</span>
          <div class="preview-coupon__info">
            <span class="preview-coupon__pill">满减券</span>
            <span class="preview-coupon__title">优惠券</span>
            <span class="preview-coupon__threshold">满100元可用</span>
          </div>
        </div>
        <span class="preview-coupon__btn">领取</span>
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

/* 与小程序 diy-coupon-combo 票面同源（1rpx = 0.5px） */
.preview-coupon {
  display: flex;
  align-items: center;
  padding: 3px;
  margin-bottom: 6px;
  overflow: hidden;
  background: linear-gradient(135deg, #ff6a00 0%, #ff2d2d 100%);
  border-radius: 10px;

  &__face {
    position: relative;
    display: flex;
    flex: 1;
    align-items: center;
    min-height: 50px;
    padding: 7px 10px;
    background: linear-gradient(180deg, #fff9f9 0%, #fff0eb 100%);
    border-radius: 7px;
  }

  &__notch {
    position: absolute;
    top: 50%;
    z-index: 2;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    transform: translateY(-50%);

    &--left {
      left: -4.5px;
      background: #f9531a;
    }

    &--right {
      right: -4.5px;
      background: #fa2431;
    }
  }

  &__value {
    flex-shrink: 0;
    width: 70px;
    font-size: 22px;
    font-weight: 700;
    color: #ec3d2a;
    text-align: center;
  }

  &__info {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    padding-left: 8px;
  }

  &__pill {
    align-self: flex-start;
    padding: 1px 7px;
    font-size: 10px;
    color: #e8402f;
    background: #fde1d6;
    border-radius: 999px;
  }

  &__title {
    margin-top: 3px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 13px;
    font-weight: 600;
    color: #222;
    white-space: nowrap;
  }

  &__threshold {
    margin-top: 2px;
    font-size: 10px;
    color: #9a8f8c;
  }

  &__btn {
    flex-shrink: 0;
    padding: 4px 11px;
    margin-left: 6px;
    font-size: 11px;
    font-weight: 600;
    color: #fff;
    background: linear-gradient(135deg, #ff6a00 0%, #ff2d2d 100%);
    border-radius: 999px;
  }
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
