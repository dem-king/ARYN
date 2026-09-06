<script setup lang="ts">
import { computed } from 'vue'

import { followDecorationLink } from '@/components/diy/link-resolver'
import type { VideoLiveProps } from '@/components/diy/retail-types'
import { useDiyStyle } from '@/composables/useDiyStyle'

const props = defineProps({
  showData: {
    type: Object,
    default: () => ({}),
  },
})

const componentProps = computed(() => props.showData as unknown as VideoLiveProps)
const dynamicStyles = useDiyStyle(computed(() => componentProps.value.commonStyle))

function enterLive() {
  followDecorationLink({
    params: {},
    path: componentProps.value.liveId
      ? `/sub-pages/promotion/live/index?liveId=${componentProps.value.liveId}`
      : '',
    type: 'custom',
  })
}
</script>

<template>
  <view class="diy-video-live" :style="dynamicStyles">
    <view v-if="componentProps.title" class="videolive-title">
      {{ componentProps.title }}
    </view>
    <template v-if="componentProps.mode === 'video' && componentProps.videoUrl">
      <video
        class="videolive-video"
        :src="componentProps.videoUrl"
        :poster="componentProps.coverUrl || undefined"
        controls
      />
    </template>
    <view
      v-else-if="componentProps.mode === 'live' || (!componentProps.videoUrl && componentProps.coverUrl)"
      class="videolive-cover"
      @click="enterLive"
    >
      <image
        v-if="componentProps.coverUrl"
        class="videolive-cover-img"
        :src="componentProps.coverUrl"
        mode="aspectFill"
      />
      <view v-else class="videolive-cover-fallback">直播入口</view>
      <view class="videolive-live-tag">直播</view>
    </view>
    <view v-else class="videolive-empty">
      请在装修中配置视频地址或直播入口
    </view>
  </view>
</template>

<style lang="scss" scoped>
.diy-video-live {
  padding: 20rpx;
}

.videolive-title {
  margin-bottom: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.videolive-video {
  width: 100%;
  height: 380rpx;
  border-radius: 12rpx;
}

.videolive-cover {
  position: relative;
  overflow: hidden;
  border-radius: 12rpx;
}

.videolive-cover-img {
  width: 100%;
  height: 380rpx;
}

.videolive-cover-fallback {
  display: grid;
  height: 380rpx;
  place-items: center;
  color: #fff;
  font-size: 32rpx;
  background: linear-gradient(135deg, #ff5000, #ff8a00);
}

.videolive-live-tag {
  position: absolute;
  top: 16rpx;
  left: 16rpx;
  padding: 4rpx 16rpx;
  color: #fff;
  font-size: 22rpx;
  background: rgb(255 80 0 / 90%);
  border-radius: 999rpx;
}

.videolive-empty {
  padding: 40rpx;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}
</style>
